package com.example.bff.integration;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.bff.api.model.StreamRecordApiModel;
import com.example.bff.domain.stream.StreamRegistry;
import com.example.bff.mapping.StreamEventMapper;
import com.example.bff.util.Constants;
import com.example.tradingbot.domain.event.AnomalyReportedContent;
import com.example.tradingbot.domain.event.CoreEventType;
import com.example.tradingbot.domain.event.DealClosedContent;
import com.example.tradingbot.domain.event.DealOpenedContent;
import com.example.tradingbot.domain.event.HoldRaisedContent;
import com.example.tradingbot.domain.event.OrderDecidedContent;
import com.example.tradingbot.domain.event.StrategyActivatedContent;
import com.example.tradingbot.domain.event.StrategyEventType;
import com.example.tradingbot.domain.event.StrategyLifecycleContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Потребитель тем, у которых есть производитель, — вход живых данных в
 * браузер (docs/architecture/contracts.md §«Подписка идёт на темы, у
 * которых есть производитель»).
 *
 * <p><b>Группа своя у каждой реплики</b> (конфигурация,
 * {@code bff-${random.uuid}}): событие обязано дойти до ВСЕХ открытых
 * сессий, а общая группа отдала бы его одной реплике. Дедупа на сервере
 * нет и не требуется — дедуплицирует клиент по идентичности события, и
 * это часть внешнего контракта.
 *
 * <p><b>Конверт читается ЗАГОЛОВКАМИ, содержимое — телом.</b> Так его
 * кладёт реле производителя, и второго носителя формы конверта не
 * заводится.
 *
 * <p><b>Тенант берётся из КЛЮЧА записи:</b> он же ключ партиции, и
 * фильтр «по тенанту сессии» стои́т на нём.
 *
 * <p><b>Неизвестный класс события пропускается, а не роняет проход:</b>
 * производитель вправе завести новый класс раньше, чем появится его
 * читатель, и падение остановило бы раздачу тех классов, которые читать
 * умеем. То же с неразбираемым содержимым: поток — не решение, и
 * потерянная запись стоит одной строки лога, а остановленный поток —
 * всей картины.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamEventConsumer {

    private final ObjectMapper objectMapper;
    private final StreamEventMapper streamEventMapper;
    private final StreamRegistry streamRegistry;

    @KafkaListener(topics = "#{'${perimeter.stream.topics}'.split(',')}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String tenantId = record.key();
        String eventId = header(record, Constants.EventHeaders.EVENT_ID);
        String eventType = header(record, Constants.EventHeaders.EVENT_TYPE);
        if (isBlank(tenantId) || isBlank(eventId) || isBlank(eventType)) {
            log.error("An event without envelope headers is skipped topic={} offset={}",
                    record.topic(), record.offset());
            return;
        }
        Object content = contentOf(eventType, record.value());
        if (isNull(content)) {
            return;
        }
        streamRegistry.publish(tenantId, new StreamRecordApiModel(eventId, eventType,
                occurredAt(record), content));
    }

    /**
     * Содержимое в форме периметра либо пусто, если класс неизвестен или
     * тело не разобрано.
     */
    private Object contentOf(String eventType, String payload) {
        if (EnumUtils.isValidEnum(CoreEventType.class, eventType)) {
            return coreContent(CoreEventType.valueOf(eventType), payload);
        }
        if (EnumUtils.isValidEnum(StrategyEventType.class, eventType)) {
            return strategyContent(StrategyEventType.valueOf(eventType), payload);
        }
        log.info("An event of an unknown class is skipped eventType={}", eventType);
        return null;
    }

    private Object coreContent(CoreEventType type, String payload) {
        return switch (type) {
            case ORDER_DECIDED -> streamEventMapper.toApi(read(payload, OrderDecidedContent.class));
            case DEAL_OPENED -> streamEventMapper.toApi(read(payload, DealOpenedContent.class));
            case DEAL_CLOSED -> streamEventMapper.toApi(read(payload, DealClosedContent.class));
            case HOLD_RAISED -> streamEventMapper.toApi(read(payload, HoldRaisedContent.class));
            case ANOMALY_REPORTED -> streamEventMapper.toApi(read(payload, AnomalyReportedContent.class));
        };
    }

    private Object strategyContent(StrategyEventType type, String payload) {
        return switch (type) {
            case STRATEGY_ACTIVATED -> streamEventMapper.toApi(read(payload, StrategyActivatedContent.class));
            case STRATEGY_DEACTIVATED, STRATEGY_DELETED ->
                    streamEventMapper.toApi(read(payload, StrategyLifecycleContent.class));
        };
    }

    /**
     * Разбор содержимого. Отказ разбора пропускает запись: поток решений
     * не порождает, и остановленная раздача стоит дороже потерянной
     * записи.
     */
    private <T> T read(String payload, Class<T> form) {
        try {
            return objectMapper.readValue(payload, form);
        } catch (JsonProcessingException failure) {
            log.error("An event payload is not readable as {} and is skipped", form.getSimpleName(), failure);
            return null;
        }
    }

    /** Момент происшествия; заголовка нет — момент раздачи. */
    private OffsetDateTime occurredAt(ConsumerRecord<String, String> record) {
        String occurredAt = header(record, Constants.EventHeaders.OCCURRED_AT);
        return isBlank(occurredAt) ? OffsetDateTime.now(ZoneOffset.UTC) : OffsetDateTime.parse(occurredAt);
    }

    /** Пустой заголовок означает «значения не было», а не пустую строку. */
    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return isNull(header) ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
