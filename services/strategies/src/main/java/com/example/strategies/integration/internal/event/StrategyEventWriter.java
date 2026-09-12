package com.example.strategies.integration.internal.event;

import com.example.strategies.mapping.StrategyEventMessageMapper;
import com.example.strategies.persistence.model.OutboxEntity;
import com.example.strategies.persistence.service.OutboxDataService;
import com.example.strategies.util.Constants;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.util.InternalIdFactory;
import com.example.tradingbot.message.EventEnvelopeMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Записывает факт владельца определений в outbox <b>в транзакции
 * вызывающего</b>: переход статуса и его событие ложатся одним ходом
 * (docs/architecture/contracts.md §«У каждого класса события назван
 * писатель, и он же писатель решения»).
 *
 * <p><b>Поверхность у него доменная, а форма провода за ней.</b> Он
 * принимает определение, класс события и актора; перевод в форму
 * сообщения, конверт и сериализацию держит этот класс
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»). Прежде
 * он жил в доменном пакете, принимал уже собранное содержимое параметром
 * {@code Object} и строил конверт сам.
 *
 * <p><b>Какую форму несёт класс события — знает шина, а не домен.</b>
 * Активация везёт снимок дерева, прочие переходы — только идентичности;
 * развилка стои́т здесь, потому что она о форме провода, а не о переходе.
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
@Component
@RequiredArgsConstructor
public class StrategyEventWriter {

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
    private final StrategyEventMessageMapper eventMessageMapper;

    /**
     * Записать факт перехода тем же ходом, что и сам переход.
     *
     * @param definition определение, чей переход записывается; у активации
     *                   это снимок с деревом — он же поедет содержимым
     * @param type       класс события
     * @param actor      кто инициировал ход
     */
    public void record(Strategy definition, StrategyEventType type, String actor) {
        write(definition.getTenantId(), type, content(definition, type, actor));
    }

    /**
     * Содержимое класса события.
     *
     * <p>Активация везёт снимок дерева: копия у потребителя заводится
     * только ею. Прочие переходы дерева не везут — копия у него уже лежит
     * и неизменяема.
     */
    private Object content(Strategy definition, StrategyEventType type, String actor) {
        return StrategyEventType.STRATEGY_ACTIVATED.equals(type)
                ? eventMessageMapper.domainToActivatedMessage(definition, actor)
                : eventMessageMapper.domainToLifecycleMessage(definition, actor);
    }

    /**
     * Строка outbox: конверт, тема и сериализованное содержимое.
     *
     * <p>Приватен намеренно: публичным он принимал бы содержимое
     * параметром, и форма сообщения снова стала бы видна вызывающему.
     *
     * @param tenantId идентичность тенанта-владельца; она же ключ
     *                 партиции, и без неё порядок событий внутри тенанта
     *                 не гарантируется
     * @param type     класс события
     * @param content  форма сообщения, собранная маппером
     */
    private void write(String tenantId, StrategyEventType type, Object content) {
        EventEnvelopeMessage envelope = EventEnvelopeMessage.builder()
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
