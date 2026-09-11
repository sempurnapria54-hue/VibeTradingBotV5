package com.example.strategies.domain.event;

import com.example.strategies.persistence.model.OutboxEntity;
import com.example.strategies.persistence.service.OutboxDataService;
import com.example.strategies.util.Constants;
import com.example.tradingbot.domain.event.EventEnvelope;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Пишет строку outbox <b>в транзакции вызывающего</b>: переход статуса и
 * его событие ложатся одним ходом (docs/architecture/contracts.md §«У
 * каждого класса события назван писатель, и он же писатель решения»).
 *
 * <p><b>Публикацией не занимается вовсе</b> — её ведёт реле, и начинается
 * оно там, где транзакция перехода закончилась
 * (docs/components/OutboxRelayJob.md).
 *
 * <p><b>Тема выводится, а не приходит параметром.</b> Правило имени —
 * «производитель и род», а род у всех классов владельца определений один:
 * это события-происшествия. Параметр темы позволил бы писателю положить
 * факт в тему состояний, где компакция его потеряет.
 *
 * <p><b>Контекст трассировки пуст, и пустота законна:</b> она означает
 * «трассировки не было», а не «потеряна» — экспорт трейсов в сервисах ещё
 * не подключён (docs/architecture/contracts.md §«Конверт события»).
 */
@Service
@RequiredArgsConstructor
public class OutboxWriter {

    /**
     * Версия формы содержимого. Растёт, когда меняется состав полей;
     * потребитель старой версии обязан оставаться рабочим.
     *
     * <p><b>Версия одна на весь состав, а не на класс</b>
     * (docs/architecture/contracts.md §События): состав приезжает одним
     * ходом, и обе формы производителя — снимок активации и идентичности
     * прочих переходов — этим ходом изменились обе. Вторая редакция: к
     * идентичностям добавлен актор, а к содержимому активации — три
     * идентичности радиуса верхнего уровня.
     */
    private static final Integer FORM_VERSION = 2;

    private final OutboxDataService outboxDataService;
    private final ObjectMapper objectMapper;

    /**
     * Записать событие тем же ходом, что и переход статуса.
     *
     * @param tenantId идентичность тенанта-владельца; она же ключ
     *                 партиции, и без неё порядок событий внутри тенанта
     *                 не гарантируется
     * @param type     класс события
     * @param content  содержимое — идентичности и значения, ради которых
     *                 событие заведено
     */
    public void write(String tenantId, StrategyEventType type, Object content) {
        EventEnvelope envelope = EventEnvelope.builder()
                .eventId(InternalIdFactory.forInternalEntity())
                .tenantId(tenantId)
                .eventType(type.name())
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                .version(FORM_VERSION)
                .build();
        OutboxEntity entity = new OutboxEntity();
        entity.setEventId(envelope.getEventId());
        entity.setTenantId(envelope.getTenantId());
        entity.setEventType(envelope.getEventType());
        entity.setVersion(envelope.getVersion());
        entity.setOccurredAt(envelope.getOccurredAt());
        entity.setTraceContext(envelope.getTraceContext());
        entity.setTopic(Constants.Topic.FACTS);
        entity.setPayload(serialize(content));
        outboxDataService.save(entity);
    }

    /**
     * Содержимое строкой JSON.
     *
     * <p>Отказ сериализации <b>роняет транзакцию перехода</b>, и это
     * названный выбор: статус, переставленный без своего события,
     * оставляет потребителя без факта навсегда — а требование
     * атомарности как раз и заведено против этого.
     */
    private String serialize(Object content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialization failed", e);
        }
    }
}
