package com.example.tradingcore.integration;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.example.tradingcore.domain.service.StrategyDefinitionApplier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Потребитель событий владельца определений — <b>первый потребитель
 * событий в конструкции</b> (docs/architecture/contracts.md §«Первый
 * потребитель в конструкции — ядро на трёх классах определения
 * стратегии»).
 *
 * <p><b>Он же целевой писатель копии.</b> Команда приёма определения на
 * поверхности ядра снята тем же ходом, которым появился этот потребитель:
 * иначе у копии осталось бы два писателя
 * (docs/architecture/data-ownership.md §«Копии чужих данных»).
 *
 * <p><b>Конверт читается ЗАГОЛОВКАМИ, содержимое — телом.</b> Так его
 * кладёт реле производителя, и второго носителя формы конверта не
 * заводится (docs/architecture/contracts.md §«Конверт события»).
 *
 * <p><b>Повтор доставки штатен и безопасен:</b> реле публикует раньше,
 * чем помечает опубликованное, а дедуп идёт по идентичности события в
 * inbox — той же транзакцией, что и следствие
 * (docs/rules/idempotency-via-unique.md).
 *
 * <p><b>Неизвестный класс события пропускается, а не роняет проход:</b>
 * производитель вправе завести новый класс раньше, чем появится его
 * читатель, и падение на неизвестном имени остановило бы обработку тех
 * классов, которые читать умеем.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyDefinitionConsumer {

    /** Имена полей конверта в заголовках сообщения. */
    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final ObjectMapper objectMapper;
    private final StrategyDefinitionApplier applier;

    @KafkaListener(topics = "${consumers.strategy-facts.topic}",
            groupId = "${consumers.strategy-facts.group-id}")
    public void onStrategyFact(ConsumerRecord<String, String> record) {
        String eventId = header(record, HEADER_EVENT_ID);
        String eventTypeName = header(record, HEADER_EVENT_TYPE);
        if (isNull(eventId) || isNull(eventTypeName)) {
            log.error("Strategy fact without envelope headers is skipped offset={}", record.offset());
            return;
        }
        if (isFalse(EnumUtils.isValidEnum(StrategyEventType.class, eventTypeName))) {
            log.info("Strategy fact of an unknown class is skipped eventType={}", eventTypeName);
            return;
        }
        apply(eventId, StrategyEventType.valueOf(eventTypeName), record.value());
    }

    private void apply(String eventId, StrategyEventType type, String payload) {
        switch (type) {
            case STRATEGY_ACTIVATED ->
                    applier.applyActivated(eventId, read(payload, StrategyActivatedContent.class));
            case STRATEGY_DEACTIVATED, STRATEGY_DELETED ->
                    applier.applyLifecycle(eventId, type, read(payload, StrategyLifecycleContent.class));
        }
    }

    /**
     * Разбор содержимого. Отказ разбора <b>роняет обработку</b>: пропуск
     * оставил бы копию в прежнем состоянии молча, а событие — потерянным
     * без следа.
     */
    private <T> T read(String payload, Class<T> form) {
        try {
            return objectMapper.readValue(payload, form);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Strategy fact payload is not readable as " + form.getSimpleName(), e);
        }
    }

    /** Пустой заголовок означает «значения не было», а не пустую строку. */
    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return isNull(header) ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
