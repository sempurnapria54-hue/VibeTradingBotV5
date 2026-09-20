package com.example.statistics.unit.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statistics.exception.IncompleteEventException;
import com.example.statistics.integration.internal.event.EnvelopeReader;
import com.example.statistics.integration.internal.event.model.StatisticsEventMessage;
import com.example.statistics.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Чтец конверта статистики: операнды содержимого и три политики типа —
 * группа `U8` документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Группа своя у дерева, и это объявленное расхождение копий:</b>
 * содержимое у двух потребителей разное, а колонок радиуса здесь две, а
 * не четыре (docs/models/domain/other/StatisticsFact.md §«Колонок
 * идентичности радиуса у факта происшествия нет, и это решение»).
 *
 * <p><b>Заголовков на входе пять, а значений в форме четыре:</b> контекст
 * трассировки чтец статистики не читает, и поля у формы нет. Что несущее
 * поле конверта не переживает границу приёма, дома не имеет — клетка
 * `U8.17` стои́т описанной и не прогоняется.
 */
class StatisticsEnvelopeReaderTest {

    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "DEAL_CLOSED";
    private static final String OCCURRED_AT = "2026-09-10T03:00:00Z";
    private static final String VERSION = "2";

    private static final String DEAL_CONTENT = """
            {"exchangeAccountInternalId":"acct-9","strategyInternalId":"strat-5",
             "resultCurrency":"USDT","tookRisk":true,"graphComplete":true,
             "result":"123.45","fee":"-0.17","funding":"0.02",
             "liquidationPenalty":"0","plannedRisk":"50.00",
             "closeOutcome":"UNDEFINED","reconciliationStatus":"MATCHED",
             "breakdownIncomplete":"COMPLETE","riskBenchmarkAvailability":"AVAILABLE"}""";

    private static final String INCIDENT_CONTENT = """
            {"exchangeAccountInternalId":"acct-9","rung":"HARD","severity":"CRITICAL","code":"ORD-42"}""";

    private final EnvelopeReader reader = new EnvelopeReader(new ObjectMapper());

    @Test
    @DisplayName("U8.1 — полный конверт и сделочное зерно: четыре значения конверта и все операнды")
    void u8_1_aCompleteDealGrainIsReadWhole() {
        StatisticsEventMessage message = reader.read(record(fullEnvelope(), TENANT, DEAL_CONTENT));

        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(message.getOccurredAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 3, 0, 0, 0, ZoneOffset.UTC));
        assertThat(message.getVersion()).isEqualTo(2);
        assertThat(message.getTenantId()).isEqualTo(TENANT);
        assertThat(message.getExchangeAccountInternalId()).isEqualTo("acct-9");
        assertThat(message.getStrategyInternalId()).isEqualTo("strat-5");
        assertThat(message.getResultCurrency()).isEqualTo("USDT");
        assertThat(message.getTookRisk()).isTrue();
        assertThat(message.getGraphComplete()).isTrue();
        assertThat(message.getNetResult()).isEqualByComparingTo("123.45");
        assertThat(message.getFee()).isEqualByComparingTo("-0.17");
        assertThat(message.getFunding()).isEqualByComparingTo("0.02");
        assertThat(message.getLiquidationPenalty()).isEqualByComparingTo("0");
        assertThat(message.getPlannedRisk()).isEqualByComparingTo("50.00");
        assertThat(message.getCloseOutcome()).isEqualTo("UNDEFINED");
        assertThat(message.getReconciliationStatus()).isEqualTo("MATCHED");
        assertThat(message.getBreakdownIncomplete()).isEqualTo("COMPLETE");
        assertThat(message.getRiskBenchmarkAvailability()).isEqualTo("AVAILABLE");
        assertThat(fieldNames())
                .as("пятого значения конверта у формы нет вовсе")
                .doesNotContain("traceContext");
    }

    @Test
    @DisplayName("U8.2 — зерно происшествия: три разреза, денежные поля пусты")
    void u8_2_anIncidentGrainCarriesItsThreeDimensions() {
        StatisticsEventMessage message = reader.read(record(fullEnvelope(), TENANT, INCIDENT_CONTENT));

        assertThat(message.getHoldRung()).isEqualTo("HARD");
        assertThat(message.getAnomalySeverity()).isEqualTo("CRITICAL");
        assertThat(message.getOperationCode()).isEqualTo("ORD-42");
        assertThat(message.getNetResult()).isNull();
        assertThat(message.getFee()).isNull();
        assertThat(message.getPlannedRisk()).isNull();
    }

    @Test
    @DisplayName("U8.3 — колонок радиуса две: сделка и инструмент из содержимого не читаются")
    void u8_3_theRadiusHasTwoColumnsAndNotFour() {
        assertThat(fieldNames())
                .as("у факта происшествия колонок идентичности радиуса нет, и это решение")
                .contains("exchangeAccountInternalId", "strategyInternalId")
                .doesNotContain("dealInternalId", "instrumentInternalId");
    }

    @Test
    @DisplayName("U8.4 — десятичная запись с длинной мантиссой читается без потери знаков")
    void u8_4_aLongMantissaSurvivesTheRead() {
        String content = "{\"result\":\"0.123456789012345678901234567890\"}";

        assertThat(reader.read(record(fullEnvelope(), TENANT, content)).getNetResult())
                .as("денежная величина пересекает провод десятичной записью")
                .isEqualTo(new BigDecimal("0.123456789012345678901234567890"));
    }

    @Test
    @Tag("debt")
    @DisplayName("U8.5 — JSON-число: ожидание из дома — знаки не теряются (долг F4)")
    void u8_5_aJsonFloatIsReadWithoutLosingDigits() {
        String scaled = "{\"result\":1.10}";
        String longMantissa = "{\"plannedRisk\":0.123456789012345678901234567890}";

        assertThat(reader.read(record(fullEnvelope(), TENANT, scaled)).getNetResult())
                .as("десятичная величина пересекает провод десятичной записью, и масштаб — её часть")
                .isEqualTo(new BigDecimal("1.10"));
        assertThat(reader.read(record(fullEnvelope(), TENANT, longMantissa)).getPlannedRisk())
                .as("чтение через `double` вносит двоичную погрешность в сумму, которую увидит человек")
                .isEqualTo(new BigDecimal("0.123456789012345678901234567890"));
    }

    @Test
    @DisplayName("U8.6 — денежный операнд отсутствует: поле пусто, нуля не подставляется")
    void u8_6_anAbsentAmountIsNotAZero() {
        assertThat(reader.read(record(fullEnvelope(), TENANT, "{\"fee\":\"0.1\"}")).getNetResult()).isNull();
    }

    @Test
    @DisplayName("U8.7 — денежный операнд явным JSON-`null`: то же, что отсутствие")
    void u8_7_anExplicitJsonNullIsTheSameAsAnAbsentValue() {
        assertThat(reader.read(record(fullEnvelope(), TENANT, "{\"result\":null}")).getNetResult()).isNull();
    }

    @Test
    @DisplayName("U8.8 — денежный операнд не разбирается как число: отказ")
    void u8_8_anUnparseableAmountStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, "{\"result\":\"много\"}")))
                .as("испорченный операнд не выдаётся за «не приехало»")
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U8.11 — булев операнд `false`: поле несёт ложь, а не пустоту")
    void u8_11_aFalseFlagIsAValue() {
        assertThat(reader.read(record(fullEnvelope(), TENANT, "{\"tookRisk\":false}")).getTookRisk())
                .as("ложь — значение")
                .isFalse();
    }

    @Test
    @DisplayName("U8.12 — тела записи нет: операнды пусты, конверт собран, отказа нет")
    void u8_12_anAbsentBodyLeavesTheOperandsEmpty() {
        StatisticsEventMessage message = reader.read(record(fullEnvelope(), TENANT, null));

        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getExchangeAccountInternalId()).isNull();
        assertThat(message.getNetResult()).isNull();
        assertThat(message.getTookRisk()).isNull();
        assertThat(message.getHoldRung()).isNull();
    }

    @Test
    @DisplayName("U8.13 — тело не разбирается как документ: отказ")
    void u8_13_anUnparseableBodyStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, "не документ")))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U8.14 — `occurredAt` не разбирается: та же тропа, что у журнала")
    void u8_14_anUnparseableMomentStopsTheReader() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.OCCURRED_AT, "вчера"),
                TENANT, DEAL_CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("U8.15 — операнды на вложенном уровне: все пусты")
    void u8_15_nestedOperandsAreNotRead() {
        String nested = "{\"deal\":{\"result\":\"123.45\",\"tookRisk\":true,\"resultCurrency\":\"USDT\"}}";

        StatisticsEventMessage message = reader.read(record(fullEnvelope(), TENANT, nested));

        assertThat(message.getNetResult()).isNull();
        assertThat(message.getTookRisk()).isNull();
        assertThat(message.getResultCurrency()).isNull();
    }

    @Test
    @DisplayName("U8.16 — конверт общий, содержимое своё: у формы четыре значения конверта и тенант")
    void u8_16_theEnvelopeIsSharedAndTheContentIsOwn() {
        StatisticsEventMessage message = reader.read(record(fullEnvelope(), TENANT, DEAL_CONTENT));

        assertThat(message.getEventId()).isEqualTo(EVENT_ID);
        assertThat(message.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(message.getOccurredAt()).isNotNull();
        assertThat(message.getVersion()).isEqualTo(2);
        assertThat(message.getTenantId()).isEqualTo(TENANT);
        assertThat(fieldNames())
                .as("содержимого как доставлено форма не несёт: журнал у системы один")
                .doesNotContain("content", "traceContext");
    }

    @Test
    @DisplayName("U8.18 — отказ на числовом операнде несёт его ИМЯ и причину")
    void u8_18_theAmountFailureNamesTheOperandAndKeepsItsCause() {
        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, "{\"plannedRisk\":\"много\"}")))
                .as("человек в логе обязан узнать, какой именно операнд испорчен")
                .hasMessageContaining(Constants.ContentFields.PLANNED_RISK)
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("U8.19 — отказ на моменте несёт неразбираемое значение и причину")
    void u8_19_theMomentFailureCarriesItsInputAndCause() {
        assertThatThrownBy(() -> reader.read(record(with(Constants.EventHeaders.OCCURRED_AT, "вчера"),
                TENANT, DEAL_CONTENT)))
                .hasMessageContaining("вчера")
                .hasCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("U8.20 — отказ на содержимом несёт причину, а тела в сообщение не кладёт")
    void u8_20_theContentFailureCarriesItsCauseWithoutTheBody() {
        String body = "не документ, а строка с секретом supersecret";

        assertThatThrownBy(() -> reader.read(record(fullEnvelope(), TENANT, body)))
                .hasMessageNotContaining("supersecret")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // --- оснастка ---------------------------------------------------------

    private static Map<String, String> fullEnvelope() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(Constants.EventHeaders.EVENT_ID, EVENT_ID);
        headers.put(Constants.EventHeaders.EVENT_TYPE, EVENT_TYPE);
        headers.put(Constants.EventHeaders.OCCURRED_AT, OCCURRED_AT);
        headers.put(Constants.EventHeaders.VERSION, VERSION);
        headers.put(Constants.EventHeaders.TRACE_CONTEXT, "00-4bf92f-00f067aa0ba902b7-01");
        return headers;
    }

    private static Map<String, String> with(String header, String value) {
        Map<String, String> headers = fullEnvelope();
        headers.put(header, value);
        return headers;
    }

    private static ConsumerRecord<String, String> record(Map<String, String> headers, String key, String body) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 12L, key, body);
        headers.forEach((name, value) ->
                record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8))));
        return record;
    }

    private static List<String> fieldNames() {
        List<String> names = new ArrayList<>();
        for (Field field : StatisticsEventMessage.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
