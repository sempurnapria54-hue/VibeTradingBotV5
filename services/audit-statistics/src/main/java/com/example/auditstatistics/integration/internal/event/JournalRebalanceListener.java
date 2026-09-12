package com.example.auditstatistics.integration.internal.event;

import static java.util.Objects.isNull;

import com.example.auditstatistics.domain.service.AuditReceptionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.stereotype.Component;

/**
 * Первый момент обнаружения разрыва — <b>назначение партиций</b>
 * (docs/components/AuditEventListener.md §«Разрыв смещений — операнд,
 * которого умолчание не даёт»).
 *
 * <p><b>Почему именно этот момент.</b> Он предшествует первому опросу,
 * поэтому зафиксированное группой смещение ещё не переписано сбросом
 * позиции; сравнить его с наименьшим доступным в теме можно только здесь.
 *
 * <p><b>Исходов сравнения три, и писатель есть у каждого</b>
 * (docs/models/domain/other/AuditRecord.md §«Третий исход сравнения
 * смещений двигает границу, а не предикат»):
 * <ul>
 *   <li>смещение есть и не ниже наименьшего доступного — чтение штатно, не
 *       пишется ничего;</li>
 *   <li>смещение есть и ниже — брокер удалил непрочитанное, пока
 *       потребителя не было: пишется момент разрыва;</li>
 *   <li>смещения нет вовсе — позиция назначена умолчанием, а не
 *       восстановлена, и доказать непрерывность с прежнего момента нечем:
 *       наблюдение начинается заново, момент наблюдения переписывается,
 *       <b>разрыв не пишется</b>.</li>
 * </ul>
 *
 * <p><b>Строки состояния пары может ещё не быть</b> — её заводит тик, а не
 * этот класс: тогда запись не находит цели и не делает ничего. Окно
 * ограничено одним тактом тика.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JournalRebalanceListener implements ConsumerAwareRebalanceListener {

    private final AuditReceptionService receptionService;
    private final ReceptionOffsetTracker offsetTracker;

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(new HashSet<>(partitions));
        Map<TopicPartition, Long> earliest = consumer.beginningOffsets(partitions);
        OffsetDateTime moment = OffsetDateTime.now(ZoneOffset.UTC);
        for (TopicPartition partition : partitions) {
            compare(partition, committed.get(partition), earliest.get(partition), moment);
        }
    }

    /**
     * Сравнить зафиксированное смещение с наименьшим доступным и посадить
     * ожидание чтения партиции.
     *
     * <p>Ожидание — та позиция, с которой чтение действительно начнётся:
     * зафиксированное смещение, а при его отсутствии либо отставании —
     * наименьшее доступное, потому что позиция чтения группы «с начала
     * темы».
     */
    private void compare(TopicPartition partition, OffsetAndMetadata committedOffset,
                         Long earliestOffset, OffsetDateTime moment) {
        if (isNull(earliestOffset)) {
            log.warn("Наименьшее доступное смещение партиции не отдано брокером partition={}", partition);
            return;
        }
        if (isNull(committedOffset)) {
            log.info("Смещения группы по партиции не осталось: наблюдение начинается заново partition={}", partition);
            receptionService.restartObservation(partition.topic(), moment);
            offsetTracker.expect(partition, earliestOffset);
            return;
        }
        if (committedOffset.offset() < earliestOffset) {
            log.error("Разрыв смещений на назначении партиции: непрочитанное удалено брокером partition={} "
                    + "committed={} earliest={}", partition, committedOffset.offset(), earliestOffset);
            receptionService.noteGap(partition.topic(), moment);
            offsetTracker.expect(partition, earliestOffset);
            return;
        }
        offsetTracker.expect(partition, committedOffset.offset());
    }
}
