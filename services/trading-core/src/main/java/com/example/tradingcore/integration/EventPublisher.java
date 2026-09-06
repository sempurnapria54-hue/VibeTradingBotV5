package com.example.tradingcore.integration;

import static java.util.Objects.isNull;

import com.example.tradingcore.persistence.model.OutboxEntity;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Публикация строки outbox в тему её производителя.
 *
 * <p><b>Ключ сообщения — тенант.</b> Он же ключ партиции: порядок событий
 * внутри тенанта держится ровно им, и публикация без ключа рассыпала бы
 * события одного тенанта по партициям
 * (docs/architecture/contracts.md §«Конверт события»).
 *
 * <p><b>Поля конверта едут ЗАГОЛОВКАМИ, а тело — содержимым.</b> Конверт
 * записан колонками строки, а не внутри содержимого: собирать его обратно
 * в тело значило бы завести второй носитель формы. Заголовки здесь —
 * транспортная переупаковка уже объявленной формы, а не её дом.
 *
 * <p><b>Ход синхронный.</b> Отметка «опубликовано» ставится ПОСЛЕ
 * подтверждения брокером, поэтому ждать подтверждения обязательно:
 * асинхронная отправка позволила бы пометить строку, которой брокер не
 * принял.
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    /** Заголовки конверта: имена — поля формы, значения — их строки. */
    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";
    private static final String HEADER_VERSION = "version";
    private static final String HEADER_OCCURRED_AT = "occurredAt";
    private static final String HEADER_TRACE_CONTEXT = "traceContext";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Опубликовать строку и дождаться подтверждения брокера. */
    public void publish(OutboxEntity row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.getTopic(), row.getTenantId(),
                row.getPayload());
        header(record, HEADER_EVENT_ID, row.getEventId());
        header(record, HEADER_EVENT_TYPE, row.getEventType());
        header(record, HEADER_VERSION, String.valueOf(row.getVersion()));
        header(record, HEADER_OCCURRED_AT, String.valueOf(row.getOccurredAt()));
        header(record, HEADER_TRACE_CONTEXT, row.getTraceContext());
        kafkaTemplate.send(record).join();
    }

    /** Пустой заголовок не ставится: его отсутствие и есть «значения не было». */
    private void header(ProducerRecord<String, String> record, String name, String value) {
        if (isNull(value)) {
            return;
        }
        record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
