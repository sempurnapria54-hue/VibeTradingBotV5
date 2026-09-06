package com.example.tradingcore.domain.event;

import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.EventEnvelope;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingcore.persistence.model.OutboxEntity;
import com.example.tradingcore.persistence.service.OutboxDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Пишет строку outbox <b>в транзакции вызывающего</b>: решение и его
 * событие ложатся одним ходом (docs/architecture/contracts.md §«У каждого
 * класса события назван писатель, и он же писатель решения»).
 *
 * <p><b>Публикацией не занимается вовсе</b> — её ведёт реле, и начинается
 * оно там, где транзакция решения закончилась
 * (docs/components/OutboxRelayJob.md).
 *
 * <p><b>Тема выводится, а не приходит параметром.</b> Правило имени —
 * «производитель и род» (docs/architecture/contracts.md §«Событие →
 * тема»), а род у всех классов ядра один: это события-происшествия.
 * Параметр темы позволил бы писателю положить факт в тему состояний, где
 * компакция его потеряет.
 *
 * <p><b>Контекст трассировки пуст, и пустота законна:</b> она означает
 * «трассировки не было», а не «потеряна» — инструментирование экспорта
 * трейсов в сервисах ещё не подключено
 * (docs/architecture/contracts.md §«Конверт события»).
 */
@Service
@RequiredArgsConstructor
public class OutboxWriter {

    /** Тема ядра: производитель и род. Род у всех его классов один — факты. */
    private static final String FACTS_TOPIC = "trading-core.facts";

    /**
     * Версия формы содержимого. Растёт, когда меняется состав полей;
     * потребитель старой версии обязан оставаться рабочим.
     */
    private static final Integer FORM_VERSION = 1;

    private final OutboxDataService outboxDataService;
    private final ObjectMapper objectMapper;

    /**
     * Записать событие тем же ходом, что и решение.
     *
     * @param tenantId идентичность тенанта-владельца; она же ключ
     *                 партиции, и без неё порядок событий внутри тенанта
     *                 не гарантируется
     * @param type     класс события
     * @param content  содержимое — идентичности и значения, ради которых
     *                 событие заведено
     */
    public void write(String tenantId, CoreEventType type, Object content) {
        EventEnvelope envelope = EventEnvelope.builder()
                .eventId(InternalIdFactory.forInternalEntity())
                .tenantId(tenantId)
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .version(FORM_VERSION)
                .build();
        OutboxEntity entity = new OutboxEntity();
        entity.setEventId(envelope.getEventId());
        entity.setTenantId(envelope.getTenantId());
        entity.setEventType(type.name());
        entity.setVersion(envelope.getVersion());
        entity.setOccurredAt(envelope.getOccurredAt());
        entity.setTraceContext(envelope.getTraceContext());
        entity.setTopic(FACTS_TOPIC);
        entity.setPayload(serialize(content));
        outboxDataService.save(entity);
    }

    /**
     * Содержимое строкой JSON.
     *
     * <p>Отказ сериализации <b>роняет транзакцию решения</b>, и это
     * названный выбор: решение, записанное без своего события, оставляет
     * потребителя без факта навсегда — а требование атомарности как раз и
     * заведено против этого.
     */
    private String serialize(Object content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialization failed", e);
        }
    }
}
