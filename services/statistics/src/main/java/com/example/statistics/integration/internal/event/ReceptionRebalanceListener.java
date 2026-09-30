package com.example.statistics.integration.internal.event;

import static java.util.Objects.isNull;

import com.example.statistics.domain.service.StatisticsReceptionService;
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
 * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва — сравнение
 * смещений»).
 *
 * <p><b>Почему именно этот момент.</b> Он предшествует первому опросу,
 * поэтому зафиксированное группой смещение ещё не переписано сбросом
 * позиции; сравнить его с наименьшим доступным в теме можно только здесь.
 *
 * <p><b>Исходов сравнения три, и писатель есть у каждого</b>
 * (docs/rules/durable-consumer-reception.md §«Третий исход сравнения смещений
 * двигает границу, а не предикат»):
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
 * <p><b>Наименьшего доступного брокер не отдал — сравнения нет</b>, и это не
 * четвёртый исход, а его невозможность
 * (docs/rules/durable-consumer-reception.md §«Наименьшего доступного брокер
 * не отдал — сравнения нет, и это не ещё один исход, а его невозможность»):
 * проверка переносится на второй момент обнаружения, а граница двигается
 * только там, где двинул бы её и без наименьшего доступного.
 * <ul>
 *   <li>смещение группы есть — ожидание садится на него, момент наблюдения
 *       не двигается, разрыв объявит доставка;</li>
 *   <li>смещения нет — наблюдение начинается заново, как в третьем исходе,
 *       а ожидание посадит первая доставка: позиции назначение не знает
 *       вовсе.</li>
 * </ul>
 *
 * <p><b>Строки состояния пары может ещё не быть</b> — её заводит тик, а не
 * этот класс: тогда запись не находит цели и не делает ничего. Окно
 * ограничено одним тактом тика.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceptionRebalanceListener implements ConsumerAwareRebalanceListener {

    private final StatisticsReceptionService receptionService;
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
     * темы». Без наименьшего доступного сравнивать не с чем, и ожидание
     * садится на зафиксированное смещение как есть: вышедшее за пределы
     * смещение позиция чтения переставит вперёд, и разрыв объявит первая же
     * доставка.
     */
    private void compare(TopicPartition partition, OffsetAndMetadata committedOffset,
                         Long earliestOffset, OffsetDateTime moment) {
        if (isNull(committedOffset)) {
            restartObservation(partition, earliestOffset, moment);
            return;
        }
        if (isNull(earliestOffset)) {
            log.warn("Наименьшее доступное смещение партиции не отдано брокером: сравнение переносится на доставку "
                    + "partition={} committed={}", partition, committedOffset.offset());
            offsetTracker.expect(partition, committedOffset.offset());
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

    /**
     * Смещения группы нет: наблюдение начинается заново моментом назначения.
     *
     * <p>Ожидание садится на наименьшее доступное — с него чтение и
     * начнётся. Не отдал его брокер — ожидания нет, и посадит его первая
     * доставка; наименьшее доступное этому исходу не нужно.
     */
    private void restartObservation(TopicPartition partition, Long earliestOffset, OffsetDateTime moment) {
        log.info("Смещения группы по партиции не осталось: наблюдение начинается заново partition={}", partition);
        receptionService.restartObservation(partition.topic(), moment);
        if (isNull(earliestOffset)) {
            log.warn("Наименьшее доступное смещение партиции не отдано брокером: ожидание посадит первая доставка "
                    + "partition={}", partition);
            return;
        }
        offsetTracker.expect(partition, earliestOffset);
    }
}
