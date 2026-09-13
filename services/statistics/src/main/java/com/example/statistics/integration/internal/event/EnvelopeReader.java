package com.example.statistics.integration.internal.event;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.statistics.integration.internal.event.model.StatisticsEventMessage;
import com.example.statistics.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

/**
 * Читает конверт и содержимое принятой записи в форму слоя сообщения
 * (docs/architecture/contracts.md §«Конверт события»).
 *
 * <p><b>Содержимое разбирается ЗДЕСЬ, а не в домене.</b> Документ есть
 * форма провода, и знание его полей — предмет границы: доменный код формы
 * сообщения не строит и не читает
 * (.claude/rules/codestyle.md §«Слой сообщения: внутренняя шина»).
 *
 * <p><b>Неразбираемое содержимое роняет обработку, а не пропускается.</b>
 * Факт есть носитель, и пропущенное им не восстанавливается ничем: тема
 * хранится ограниченный срок.
 *
 * <p><b>Пустой операнд содержимого остаётся пустым и НЕ гасится нулём.</b>
 * Подстановка нуля выдала бы недобытое за исход (docs/concept.md, П1); что
 * означает пустота у каждого операнда, разбирает свёртка пересчёта.
 */
@Component
@RequiredArgsConstructor
public class EnvelopeReader {

    private final ObjectMapper objectMapper;

    /** Прочитать запись целиком: конверт из заголовков, операнды из содержимого. */
    public StatisticsEventMessage read(ConsumerRecord<String, String> record) {
        JsonNode content = contentTree(record);
        return StatisticsEventMessage.builder()
                .eventId(header(record, Constants.EventHeaders.EVENT_ID))
                .tenantId(record.key())
                .eventType(header(record, Constants.EventHeaders.EVENT_TYPE))
                .occurredAt(moment(header(record, Constants.EventHeaders.OCCURRED_AT)))
                .version(version(header(record, Constants.EventHeaders.VERSION)))
                .exchangeAccountInternalId(text(content, Constants.RadiusFields.EXCHANGE_ACCOUNT_INTERNAL_ID))
                .strategyInternalId(text(content, Constants.RadiusFields.STRATEGY_INTERNAL_ID))
                .resultCurrency(text(content, Constants.ContentFields.RESULT_CURRENCY))
                .tookRisk(flag(content, Constants.ContentFields.TOOK_RISK))
                .graphComplete(flag(content, Constants.ContentFields.GRAPH_COMPLETE))
                .netResult(number(content, Constants.ContentFields.RESULT))
                .fee(number(content, Constants.ContentFields.FEE))
                .funding(number(content, Constants.ContentFields.FUNDING))
                .liquidationPenalty(number(content, Constants.ContentFields.LIQUIDATION_PENALTY))
                .plannedRisk(number(content, Constants.ContentFields.PLANNED_RISK))
                .closeOutcome(text(content, Constants.ContentFields.CLOSE_OUTCOME))
                .reconciliationStatus(text(content, Constants.ContentFields.RECONCILIATION_STATUS))
                .breakdownIncomplete(text(content, Constants.ContentFields.BREAKDOWN_INCOMPLETE))
                .riskBenchmarkAvailability(text(content, Constants.ContentFields.RISK_BENCHMARK_AVAILABILITY))
                .holdRung(text(content, Constants.ContentFields.RUNG))
                .anomalySeverity(text(content, Constants.ContentFields.SEVERITY))
                .operationCode(text(content, Constants.ContentFields.CODE))
                .build();
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return isNull(header) ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private OffsetDateTime moment(String value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IncompleteEventException("Момент происшествия не разбирается: " + value, e);
        }
    }

    private Integer version(String value) {
        if (isNull(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IncompleteEventException("Версия формы не разбирается: " + value, e);
        }
    }

    private JsonNode contentTree(ConsumerRecord<String, String> record) {
        if (isNull(record.value())) {
            return null;
        }
        try {
            return objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            throw new IncompleteEventException("Содержимое события не разбирается как документ", e);
        }
    }

    private String text(JsonNode content, String name) {
        JsonNode value = value(content, name);
        return isNull(value) || isFalse(value.isTextual()) ? null : value.textValue();
    }

    private Boolean flag(JsonNode content, String name) {
        JsonNode value = value(content, name);
        return isNull(value) || isFalse(value.isBoolean()) ? null : value.booleanValue();
    }

    /**
     * Числовой операнд.
     *
     * <p><b>Читается текстом, а не двоичным типом узла:</b> денежная
     * величина пересекает провод десятичной записью, и чтение её через
     * {@code double} внесло бы двоичную погрешность в сумму, которую потом
     * увидит человек (docs/rules/decimal-arithmetic.md).
     *
     * <p><b>Неразбираемое число роняет обработку:</b> подставленная на его
     * месте пустота выдала бы испорченный операнд за «не приехало».
     */
    private BigDecimal number(JsonNode content, String name) {
        JsonNode value = value(content, name);
        if (isNull(value) || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            throw new IncompleteEventException("Числовой операнд не разбирается: " + name, e);
        }
    }

    private JsonNode value(JsonNode content, String name) {
        if (isNull(content)) {
            return null;
        }
        JsonNode value = content.get(name);
        return isNull(value) || value.isNull() ? null : value;
    }
}
