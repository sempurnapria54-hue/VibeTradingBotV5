package com.example.statistics.integration.internal.event;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Ожидаемое смещение по каждой назначенной партиции — операнд <b>второго</b>
 * момента обнаружения разрыва
 * (docs/rules/durable-consumer-reception.md §«Обнаружение разрыва — сравнение
 * смещений»).
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
 * <p><b>Ошибка возможна только в запретительную сторону.</b> Партиция без
 * ожидания разрыва не объявляет: на тропе назначения это одно состояние —
 * назначение не знало позиции вовсе, ни смещения группы, ни наименьшего
 * доступного, и наблюдение там уже начато заново
 * (docs/rules/durable-consumer-reception.md §«Операнд второго момента
 * сажает первый»). Повторно доставленная запись приходит со смещением
 * <b>не больше</b> ожидаемого и разрывом тоже не считается.
 *
 * <p><b>Состояние «ожидания нет» назначение тоже ЗАПИСЫВАЕТ, а не
 * оставляет.</b> Партиция, отозванная и вернувшаяся в тот же процесс без
 * позиции, иначе встретила бы первую доставку ожиданием прошлого назначения
 * и объявила бы ложный разрыв. Чистки на отзыве для этого не нужно: всякое
 * назначение переписывает ожидание своей партиции на каждом исходе
 * сравнения, и отсутствие позиции — один из них.
 */
@Component
public class ReceptionOffsetTracker {

    private final Map<TopicPartition, Long> expected = new ConcurrentHashMap<>();

    /**
     * Запомнить, с какого смещения ожидается чтение партиции.
     *
     * <p>Запись безусловная, в том числе запись отсутствия: пустое смещение
     * снимает ожидание партиции — назначение позиции не знает, и посадит её
     * первая доставка.
     */
    public void expect(TopicPartition partition, Long offset) {
        if (isNull(offset)) {
            expected.remove(partition);
            return;
        }
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
