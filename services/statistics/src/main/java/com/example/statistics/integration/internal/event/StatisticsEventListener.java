package com.example.statistics.integration.internal.event;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.statistics.domain.model.AggregateGrain;
import com.example.statistics.domain.model.DealFact;
import com.example.statistics.domain.model.IncidentFact;
import com.example.statistics.domain.service.StatisticsReceptionService;
import com.example.statistics.integration.internal.event.model.StatisticsEventMessage;
import com.example.statistics.mapping.FactMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Принимает событие в факт своего зерна
 * (docs/models/domain/other/StatisticsFact.md).
 *
 * <p>Больше ничего: агрегатов не двигает, решений не принимает, содержимого
 * как доставлено не хранит. Строка агрегата пересчитывается проекцией по
 * фактам целиком и событием не двигается
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Группа СВОЯ, а не журнальная</b>, и это несущее: вторая сторона,
 * читающая ту же тему, обязана иметь собственное смещение — иначе одна из
 * двух получит часть темы непрочитанной, и ни один признак об этом не
 * скажет.
 *
 * <p><b>Метод вызывается сообщением.</b> Опрос существует, но он у
 * контейнера: холостого прохода в прикладном коде нет по построению.
 * Отсюда границы транзакций называет прикладной код, а не контейнер.
 *
 * <p><b>Неполный вход роняет обработку, а не пропускается.</b> Факт есть
 * носитель принятого, и пропущенное им не восстанавливается ничем: тема
 * хранится ограниченный срок, а тропы восстановления у статистики нет —
 * журнал аудита источником ей не служит.
 */
@Component
@RequiredArgsConstructor
public class StatisticsEventListener {

    private final EnvelopeReader envelopeReader;
    private final FactMapper factMapper;
    private final ReceptionOffsetTracker offsetTracker;
    private final StatisticsReceptionService receptionService;

    /**
     * Принять сообщение.
     *
     * <p><b>Порядок ходов несущий.</b> Разрыв смещений отмечается
     * <b>своей</b> транзакцией до приёма: он есть свидетельство о ходе
     * приёма, а не следствие принятого сообщения, и откат обработки не
     * вправе его унести.
     *
     * <p><b>Отбор по признаку несомого класса идёт ЗДЕСЬ, у потребителя.</b>
     * Единица подписки в Kafka — тема, а не класс, и тема несёт классы
     * одного производителя целиком. Событие класса, признаку не
     * отвечающего, доезжает и факта не порождает; смещение при этом
     * двигается, и повтор такого события безвреден по построению — отметка
     * есть ключ строки факта, а строки нет.
     *
     * <p><b>Ключ конфигурации разбирается ЗДЕСЬ, и второго его читателя не
     * заводится.</b> Форма значения — скаляр через запятую; объявленное
     * рядом поле {@code List<String>} потребителя бы не нашло, а перечнем
     * YAML этот плейсхолдер не разрешается (.claude/rules/policy-home.md).
     */
    @KafkaListener(topics = "#{'${reception.topics}'.split(',')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        if (isTrue(offsetTracker.observeDelivery(record))) {
            receptionService.noteGap(record.topic(), OffsetDateTime.now(ZoneOffset.UTC));
        }
        StatisticsEventMessage message = envelopeReader.read(record);
        requireEnvelope(message, record);
        AggregateGrain grain = AggregateGrain.carrying(message.getEventType());
        if (isNull(grain)) {
            receptionService.acceptWithoutFact(record.topic(), message.getOccurredAt());
            return;
        }
        if (AggregateGrain.DEAL == grain) {
            acceptDeal(message, record);
            return;
        }
        acceptIncident(message, record);
    }

    private void acceptDeal(StatisticsEventMessage message, ConsumerRecord<String, String> record) {
        DealFact fact = factMapper.messageToDealFact(message);
        if (isFalse(fact.hasCompleteInput())) {
            throw incomplete("сделочного факта", record);
        }
        receptionService.accept(fact, record.topic());
    }

    private void acceptIncident(StatisticsEventMessage message, ConsumerRecord<String, String> record) {
        IncidentFact fact = factMapper.messageToIncidentFact(message);
        if (isFalse(fact.hasCompleteInput())) {
            throw incomplete("факта происшествия", record);
        }
        receptionService.accept(fact, record.topic());
    }

    /**
     * Конверт обязан быть полон и у события, фактом не становящегося:
     * момент происшествия двигает возраст последнего принятого, а класс
     * решает, становится ли событие фактом. Пустой любой из них делает
     * ветвление и измерение произвольными.
     */
    private void requireEnvelope(StatisticsEventMessage message, ConsumerRecord<String, String> record) {
        if (isNull(message.getEventType()) || isNull(message.getOccurredAt())) {
            throw incomplete("конверта", record);
        }
    }

    private IncompleteEventException incomplete(String what, ConsumerRecord<String, String> record) {
        return new IncompleteEventException("Событие без обязательного значения " + what
                + " topic=" + record.topic() + " offset=" + record.offset());
    }
}
