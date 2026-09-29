package com.example.bff.domain.stream;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.config.PerimeterProperties;
import com.example.bff.util.Constants;
import java.io.IOException;
import java.time.Instant;
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
 *
 * <p><b>Окно эфемерно и со сроком, как и кэш членств.</b> Реплика читает
 * темы всех тенантов, и окно, заводимое на всякую доехавшую запись, росло
 * бы числом тенантов системы. Поэтому окно заводит ПОДПИСКА, а не запись:
 * тенанту, у которого подписки нет и не было, помнить нечего. Последнюю
 * подписку окно переживает на срок билета — ровно на столько, сколько
 * длится пересоздание подписки клиентом, — и по его истечении уходит.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamRegistry {

    /** Открытые подписки по тенанту. */
    private final Map<String, Set<SseEmitter>> subscriptions = new ConcurrentHashMap<>();

    /** Окно переигрывания по тенанту: последние факты в порядке доставки. */
    private final Map<String, Deque<StreamRecordApiModel>> windows = new ConcurrentHashMap<>();

    /** С какого момента у тенанта с окном нет ни одной открытой подписки. */
    private final Map<String, Instant> unsubscribedSince = new ConcurrentHashMap<>();

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
        SseEmitter emitter = new SseEmitter(properties.getStream().getConnectionTimeout().toMillis());
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> expire(tenantId, emitter));
        emitter.onError(failure -> remove(tenantId, emitter));
        evictExpiredWindows();
        Deque<StreamRecordApiModel> window = windowOf(tenantId);
        unsubscribedSince.remove(tenantId);
        synchronized (window) {
            register(tenantId, emitter);
            confirm(emitter);
            replay(window, lastEventId, emitter);
        }
        return emitter;
    }

    /**
     * Разослать факт открытым подпискам тенанта и положить его в окно.
     *
     * <p>Тенанту без подписки и без окна факт не нужен никому: ни
     * рассылать, ни держать для продолжения его некому, и окна он не
     * заводит.
     *
     * @param tenantId тенант-владелец факта
     * @param record   запись в форме периметра
     */
    public void publish(String tenantId, StreamRecordApiModel record) {
        if (isFalse(subscriptions.containsKey(tenantId)) && isFalse(windows.containsKey(tenantId))) {
            return;
        }
        Deque<StreamRecordApiModel> window = windowOf(tenantId);
        synchronized (window) {
            remember(window, record);
            send(tenantId, record);
        }
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
     * Завести подписку в набор тенанта — либо отказать при исчерпанном
     * потолке.
     *
     * <p>Подписки живут в памяти реплики, и число их задаёт тот, кто их
     * открывает, — не наш пользователь. Без потолка одна сессия,
     * открывающая поток в цикле, съедала бы память периметра, через
     * который идёт весь трафик браузера.
     *
     * <p><b>Сверка с потолком и добавление — одна операция над
     * отображением тенанта.</b> Потолок сравнивается со СЧЁТОМ открытых, и
     * отсутствие набора есть счёт ноль, а не свободное место: иначе нулевой
     * потолок пропускал бы первую подписку каждого тенанта. Снятие
     * последней подписки ({@link #remove}) идёт тем же путём, и подписка,
     * открытая одновременно со снятием, в снятый набор не попадает.
     */
    private void register(String tenantId, SseEmitter emitter) {
        subscriptions.compute(tenantId, (key, emitters) -> {
            Set<SseEmitter> opened = isNull(emitters) ? new CopyOnWriteArraySet<>() : emitters;
            if (opened.size() >= properties.getStream().getMaxSubscriptionsPerTenant()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Открытых подписок тенанта больше, чем допускает потолок");
            }
            opened.add(emitter);
            return opened;
        });
    }

    /**
     * Подтверждение открытия — комментарием протокола, а не записью.
     *
     * <p>Заголовки ответа уходят клиенту вместе с первым, что написано в
     * поток; без подтверждения тенант без фактов держал бы браузер в
     * состоянии «подключаюсь» до первого такта пульса, и «подключён, но
     * тихо» было бы неотличимо от «не подключился». Записью подтверждение
     * не делается: запись несёт класс, а комментарий клиент протокола не
     * показывает и в окно он не попадает.
     */
    private void confirm(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment(Constants.StreamRecords.OPENED_COMMENT));
        } catch (IOException | IllegalStateException failure) {
            log.debug("A subscription is closed while confirming the opening", failure);
            complete(emitter, failure);
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
     *
     * <p><b>Идёт под замком окна тенанта, как и рассылка факта.</b>
     * Подписка к этому ходу уже в наборе, и факт, принятый между
     * регистрацией и переигрыванием, ушёл бы в провод раньше хвоста —
     * клиент получил бы {@code E4, E2, E3}, а восстановить порядок ему
     * нечем: порядкового номера у записи нет.
     */
    private void replay(Deque<StreamRecordApiModel> window, String lastEventId, SseEmitter emitter) {
        if (isBlank(lastEventId)) {
            return;
        }
        List<StreamRecordApiModel> tail = tailAfter(window, lastEventId);
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
    private List<StreamRecordApiModel> tailAfter(Deque<StreamRecordApiModel> window, String lastEventId) {
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

    /**
     * Окно тенанта — оно же замок его потока: положить факт в окно и
     * переиграть хвост новой подписке есть два хода над одним предметом,
     * и порядок между ними держит один замок.
     */
    private Deque<StreamRecordApiModel> windowOf(String tenantId) {
        return windows.computeIfAbsent(tenantId, key -> new ArrayDeque<>());
    }

    private void remember(Deque<StreamRecordApiModel> window, StreamRecordApiModel record) {
        window.addLast(record);
        while (window.size() > properties.getStream().getReplayWindow()) {
            window.removeFirst();
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
            complete(emitter, failure);
        }
    }

    /**
     * Завершение оборвавшейся подписки. Отказ самого завершения (контейнер
     * уже закрыл запрос) остаётся здесь: вышедший из рассылки, он вернул бы
     * факт слушателю темы на повтор — факт лёг бы в окно второй раз, а при
     * исчерпанных повторах не дошёл бы ни до одной сессии.
     */
    private void complete(SseEmitter emitter, Throwable failure) {
        try {
            emitter.completeWithError(failure);
        } catch (RuntimeException completionFailure) {
            log.debug("A broken subscription fails to complete", completionFailure);
        }
    }

    /**
     * Срок соединения истёк: подписка снимается и поток ЗАВЕРШАЕТСЯ
     * штатно. Незавершённый, он достался бы каркасу, и тот разрешил бы
     * истёкший запрос отказом — у подписки, не получившей ни одной записи,
     * клиент прочитал бы документ отказа вместо штатно закрытого потока.
     */
    private void expire(String tenantId, SseEmitter emitter) {
        remove(tenantId, emitter);
        emitter.complete();
    }

    /**
     * Снятие подписки; опустевший набор уходит тем же ходом над
     * отображением, и с этого момента окно тенанта живёт свой срок.
     */
    private void remove(String tenantId, SseEmitter emitter) {
        subscriptions.computeIfPresent(tenantId, (key, emitters) -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                unsubscribedSince.put(key, Instant.now());
                return null;
            }
            return emitters;
        });
        evictExpiredWindows();
    }

    /**
     * Вытеснение окон, переживших последнюю подписку тенанта дольше срока
     * билета. Идёт на открытии и снятии подписки — ходах, меняющих состав
     * подписок, — и обходит только тенантов без подписок.
     *
     * <p>Окно уходит, только если отметка снята ЭТИМ ходом: подписка,
     * открытая тенантом одновременно, отметку снимает сама, и её окно
     * остаётся.
     */
    private void evictExpiredWindows() {
        Instant expiredBefore = Instant.now().minus(properties.getTicket().getTtl());
        unsubscribedSince.forEach((tenantId, since) -> {
            if (since.isAfter(expiredBefore)) {
                return;
            }
            if (unsubscribedSince.remove(tenantId, since)) {
                windows.remove(tenantId);
            }
        });
    }

    /** Явный разрыв: позиция клиента в окне не нашлась. */
    private StreamRecordApiModel gap() {
        return new StreamRecordApiModel(null, Constants.StreamRecords.GAP,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}
