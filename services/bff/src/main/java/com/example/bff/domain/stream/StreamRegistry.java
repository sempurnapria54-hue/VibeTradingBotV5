package com.example.bff.domain.stream;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.util.Constants;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Открытые подписки реплики и её окно переигрывания.
 *
 * <p><b>Всё здесь живёт в памяти РЕПЛИКИ, и это следствие двух уже
 * принятых решений:</b> персистентного состояния периметр не держит, а у
 * каждой реплики своя группа потребителя — событие обязано дойти до всех
 * открытых сессий (docs/architecture/contracts.md §«Живые данные в
 * браузер»).
 *
 * <p><b>Позиция чтения выражается идентичностью события.</b> Нашлась в
 * окне — поток продолжается с неё; не нашлась (окно ушло вперёд,
 * значение выдумано, реплика другая) — поток начинается с текущего
 * момента и сообщает РАЗРЫВ отдельной записью. Молчаливого продолжения
 * не бывает: иначе фронт показал бы связную картину поверх дыры.
 *
 * <p><b>Записи периметра идентичности не несут.</b> Пульс и разрыв —
 * не факты; дай им идентичность, и браузер стал бы просить продолжения
 * с записи, которой в потоке фактов не существует.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamRegistry {

    /** Открытые подписки по тенанту. */
    private final Map<String, Set<SseEmitter>> subscriptions = new ConcurrentHashMap<>();

    /** Окно переигрывания по тенанту: последние факты в порядке доставки. */
    private final Map<String, Deque<StreamRecordApiModel>> windows = new ConcurrentHashMap<>();

    private final PerimeterProperties properties;

    /**
     * Открыть подписку тенанта.
     *
     * @param tenantId    тенант сессии; чужие события в поток не идут
     * @param lastEventId идентичность последнего полученного события;
     *                    пусто — первое подключение, и терять нечего
     * @return поток, в который реплика будет писать записи
     */
    public SseEmitter open(String tenantId, String lastEventId) {
        requireRoomFor(tenantId);
        SseEmitter emitter = new SseEmitter(properties.getStream().getConnectionTimeout().toMillis());
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(failure -> remove(tenantId, emitter));
        subscriptions.computeIfAbsent(tenantId, key -> new CopyOnWriteArraySet<>()).add(emitter);
        replay(tenantId, lastEventId, emitter);
        return emitter;
    }

    /**
     * Разослать факт открытым подпискам тенанта и положить его в окно.
     *
     * @param tenantId тенант-владелец факта
     * @param record   запись в форме периметра
     */
    public void publish(String tenantId, StreamRecordApiModel record) {
        remember(tenantId, record);
        send(tenantId, record);
    }

    /**
     * Разослать запись, порождённую периметром (пульс).
     *
     * <p>В окно она не кладётся: окно держит факты, по которым клиент
     * просит продолжения.
     *
     * @param record запись периметра
     */
    public void broadcast(StreamRecordApiModel record) {
        subscriptions.keySet().forEach(tenantId -> send(tenantId, record));
    }

    /**
     * Отказ при исчерпанном потолке подписок тенанта.
     *
     * <p>Подписки живут в памяти реплики, и число их задаёт тот, кто их
     * открывает, — не наш пользователь. Без потолка одна сессия,
     * открывающая поток в цикле, съедала бы память периметра, через
     * который идёт весь трафик браузера.
     */
    private void requireRoomFor(String tenantId) {
        Set<SseEmitter> emitters = subscriptions.get(tenantId);
        if (isNull(emitters)) {
            return;
        }
        if (emitters.size() >= properties.getStream().getMaxSubscriptionsPerTenant()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Открытых подписок тенанта больше, чем допускает потолок");
        }
    }

    /** Есть ли открытые подписки — вход джобы пульса. */
    public Boolean hasSubscriptions() {
        return isFalse(subscriptions.isEmpty());
    }

    /**
     * Переигрывание с названной позиции либо явный разрыв.
     *
     * <p>Первое подключение разрыва не получает: терять ему нечего.
     */
    private void replay(String tenantId, String lastEventId, SseEmitter emitter) {
        if (isBlank(lastEventId)) {
            return;
        }
        List<StreamRecordApiModel> tail = tailAfter(tenantId, lastEventId);
        if (isNull(tail)) {
            write(emitter, gap());
            return;
        }
        tail.forEach(record -> write(emitter, record));
    }

    /**
     * Хвост окна после названной идентичности.
     *
     * @return хвост, если идентичность в окне нашлась; пусто — не нашлась
     */
    private List<StreamRecordApiModel> tailAfter(String tenantId, String lastEventId) {
        Deque<StreamRecordApiModel> window = windows.get(tenantId);
        if (isNull(window)) {
            return null;
        }
        synchronized (window) {
            List<StreamRecordApiModel> snapshot = new ArrayList<>(window);
            int position = -1;
            for (int index = 0; index < snapshot.size(); index++) {
                if (Objects.equals(lastEventId, snapshot.get(index).id())) {
                    position = index;
                    break;
                }
            }
            return position < 0 ? null : snapshot.subList(position + 1, snapshot.size()).stream()
                    .collect(Collectors.toList());
        }
    }

    private void remember(String tenantId, StreamRecordApiModel record) {
        Deque<StreamRecordApiModel> window = windows.computeIfAbsent(tenantId, key -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(record);
            while (window.size() > properties.getStream().getReplayWindow()) {
                window.removeFirst();
            }
        }
    }

    private void send(String tenantId, StreamRecordApiModel record) {
        Set<SseEmitter> emitters = subscriptions.get(tenantId);
        if (isNull(emitters)) {
            return;
        }
        emitters.forEach(emitter -> write(emitter, record));
    }

    /**
     * Запись в поток. Отказ доставки закрывает подписку, а не роняет
     * рассылку: одна оборвавшаяся сессия не должна лишать данных
     * остальные.
     */
    private void write(SseEmitter emitter, StreamRecordApiModel record) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event().name(record.type()).data(record);
            if (isFalse(isBlank(record.id()))) {
                event = event.id(record.id());
            }
            emitter.send(event);
        } catch (IOException | IllegalStateException failure) {
            log.debug("A subscription is closed while writing type={}", record.type(), failure);
            emitter.completeWithError(failure);
        }
    }

    private void remove(String tenantId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscriptions.get(tenantId);
        if (isNull(emitters)) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscriptions.remove(tenantId, emitters);
        }
    }

    /** Явный разрыв: позиция клиента в окне не нашлась. */
    private StreamRecordApiModel gap() {
        return new StreamRecordApiModel(null, Constants.StreamRecords.GAP,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}
