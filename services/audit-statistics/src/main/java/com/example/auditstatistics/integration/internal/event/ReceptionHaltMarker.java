package com.example.auditstatistics.integration.internal.event;

import com.example.auditstatistics.domain.service.AuditReceptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Ставит флаг остановки приёма по паре, на которой отказала обработка.
 *
 * <p><b>Писатель постановки — обработчик ошибок, а не слушатель</b>
 * (docs/models/domain/other/AuditRecord.md, таблица писателей): слушатель
 * флаг только снимает, и делает это транзакцией приёма. Постановка идёт
 * <b>отдельной</b> транзакцией — той же она откатилась бы вместе с
 * обработкой.
 *
 * <p><b>Пишется на ПЕРВОЙ неудачной доставке.</b> Повторов у группы журнала
 * не ограничено ничем, и запись на каждом из них била бы в базу с частотой
 * паузы, ничего не меняя: флаг уже стои́т.
 *
 * <p><b>Отказ самой записи флага проход не рвёт.</b> Он и так идёт по
 * тропе отказа; исключение отсюда подменило бы причину остановки, а
 * повторная доставка вернётся сюда же — окно ненаблюдаемости ограничено
 * тактом повтора.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceptionHaltMarker implements RetryListener {

    /** Номер первой доставки: повторы приходят с бо́льшим номером. */
    private static final int FIRST_ATTEMPT = 1;

    private final AuditReceptionService receptionService;

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
        if (deliveryAttempt > FIRST_ATTEMPT) {
            return;
        }
        log.error("Приём остановлен на сообщении topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        markHalted(record.topic());
    }

    private void markHalted(String topic) {
        try {
            receptionService.noteHalt(topic);
        } catch (RuntimeException e) {
            log.error("Флаг остановки приёма не записан topic={}", topic, e);
        }
    }
}
