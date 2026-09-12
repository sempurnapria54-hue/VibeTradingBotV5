package com.example.auditstatistics.integration.internal.event;

import static java.util.Objects.nonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Ожидаемое смещение по каждой назначенной партиции — операнд <b>второго</b>
 * момента обнаружения разрыва
 * (docs/components/AuditEventListener.md §«Разрыв смещений — операнд,
 * которого умолчание не даёт»).
 *
 * <p><b>Зачем он вообще нужен.</b> Позиция чтения «с начала темы»
 * переставляет вышедшее за пределы смещение <b>молча</b>, поэтому
 * обнаружение разрыва не может опираться на отказ клиента. Наблюдатель у
 * ветви «брокер удалил непрочитанное на ходу» ровно один — сравнение
 * смещения доставленной записи с ожидаемым по этой партиции: группа при
 * этом жива и назначения не меняет.
 *
 * <p><b>Живёт в памяти процесса, и это верно:</b> предмет величины —
 * непрерывность <b>текущего</b> назначения. Durable-след разрыва — момент в
 * строке состояния пары, и пишет его не этот класс.
 *
 * <p><b>Ошибка возможна только в запретительную сторону.</b> Ожидание,
 * которого нет (первая запись после назначения), разрывом не объявляется;
 * повторно доставленная запись приходит со смещением <b>не больше</b>
 * ожидаемого и разрывом тоже не считается.
 */
@Component
public class ReceptionOffsetTracker {

    private final Map<TopicPartition, Long> expected = new ConcurrentHashMap<>();

    /** Запомнить, с какого смещения ожидается чтение партиции. */
    public void expect(TopicPartition partition, Long offset) {
        expected.put(partition, offset);
    }

    /**
     * Учесть доставленную запись и ответить, обнаружен ли разрыв.
     *
     * <p>Разрыв — это <b>пропуск</b>: смещение доставленной записи больше
     * ожидаемого. Ожидание сдвигается всегда, в том числе при разрыве:
     * иначе одна дыра объявлялась бы заново на каждой следующей записи.
     */
    public Boolean observeDelivery(ConsumerRecord<String, String> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        Long expectedOffset = expected.put(partition, record.offset() + 1);
        return nonNull(expectedOffset) && record.offset() > expectedOffset;
    }
}
