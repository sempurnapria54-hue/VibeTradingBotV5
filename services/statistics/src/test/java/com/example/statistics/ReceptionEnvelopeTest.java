package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.statistics.domain.model.DealFact;
import com.example.statistics.domain.model.IncidentFact;
import com.example.statistics.domain.service.StatisticsReceptionService;
import com.example.statistics.exception.IncompleteEventException;
import com.example.statistics.integration.internal.event.EnvelopeReader;
import com.example.statistics.integration.internal.event.ReceptionOffsetTracker;
import com.example.statistics.integration.internal.event.StatisticsEventListener;
import com.example.statistics.mapping.FactMapper;
import com.example.statistics.mapping.FactMapperImpl;
import com.example.statistics.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Что слушатель приёма берёт с провода, в какое зерно раскладывает и на чём
 * останавливается (docs/models/domain/other/StatisticsFact.md §«Признак
 * несомого класса», §«Структура»).
 *
 * <p><b>Тест собирает состояние, а не подменяет предикат:</b> сообщение
 * строится настоящими заголовками и настоящим телом, а полнота входа
 * считается самой моделью (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»).
 */
class ReceptionEnvelopeTest {

    /** Перевод прочитанного сообщения в факт — маппер границы. */
    private static final FactMapper MESSAGES = new FactMapperImpl();

    private static final String TOPIC = "trading-core.facts";
    private static final String TENANT = "tenant-1";
    private static final String EVENT_ID = "evt-1";
    private static final String OCCURRED_AT = "2026-09-10T03:00:00Z";
    private static final String VERSION = "2";
    private static final String ACCOUNT = "acct-9";

    /**
     * Содержимое терминала сделки.
     *
     * <p><b>Числа записаны с длинной дробной частью намеренно:</b> прочитанные
     * через {@code double}, они потеряли бы младшие разряды, и проба
     * отличает десятичное чтение от двоичного
     * (docs/rules/decimal-arithmetic.md).
     */
    private static final String DEAL_CONTENT = """
            {"dealInternalId":"deal-7","exchangeAccountInternalId":"acct-9",
             "strategyInternalId":"strat-2","resultCurrency":"USDT",
             "tookRisk":true,"graphComplete":true,
             "result":"12.123456789012345678","fee":"0.510000000000000001",
             "funding":"-0.250000000000000003","liquidationPenalty":"0",
             "plannedRisk":"100.000000000000000001",
             "closeOutcome":"NORMAL","reconciliationStatus":"MATCHED",
             "breakdownIncomplete":"COMPLETE","riskBenchmarkAvailability":"PRESENT"}""";

    /** Содержимое подъёма ступени защиты: несёт оба разреза класса. */
    private static final String HOLD_CONTENT = """
            {"exchangeAccountInternalId":"acct-9","rung":"HARD",
             "code":"MANUAL_HALT_REQUESTED"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EnvelopeReader reader = new EnvelopeReader(objectMapper);
    private final StatisticsReceptionService receptionService = mock(StatisticsReceptionService.class);
    private final StatisticsEventListener listener =
            new StatisticsEventListener(reader, MESSAGES, new ReceptionOffsetTracker(), receptionService);

    @Test
    @DisplayName("Терминал сделки раскладывается в сделочный факт со всеми операндами зерна")
    void aTerminalEventBecomesADealFact() {
        listener.onEvent(message(envelope(Constants.CarriedEvent.DEAL_CLOSED), TENANT, DEAL_CONTENT));

        ArgumentCaptor<DealFact> captured = ArgumentCaptor.forClass(DealFact.class);
        verify(receptionService).accept(captured.capture(), eq(TOPIC));
        DealFact fact = captured.getValue();

        assertThat(fact.getEventId()).isEqualTo(EVENT_ID);
        assertThat(fact.getTenantId())
                .as("тенант едет ключом записи, а не заголовком")
                .isEqualTo(TENANT);
        assertThat(fact.getExchangeAccountInternalId()).isEqualTo(ACCOUNT);
        assertThat(fact.getStrategyInternalId()).isEqualTo("strat-2");
        assertThat(fact.getResultCurrency()).isEqualTo("USDT");
        assertThat(fact.getClosedAt())
                .as("ось времени сделочного факта — момент происшествия конверта")
                .isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
        assertThat(fact.getTookRisk()).isTrue();
        assertThat(fact.getGraphComplete()).isTrue();
        assertThat(fact.getNetResult())
                .as("денежная величина читается десятичной записью: через double младшие разряды пропали бы")
                .isEqualByComparingTo(new BigDecimal("12.123456789012345678"));
        assertThat(fact.getPlannedRisk())
                .isEqualByComparingTo(new BigDecimal("100.000000000000000001"));
        assertThat(fact.getCloseOutcome()).isEqualTo("NORMAL");
        assertThat(fact.getReconciliationStatus()).isEqualTo("MATCHED");
        assertThat(fact.getBreakdownIncomplete()).isEqualTo("COMPLETE");
        assertThat(fact.getRiskBenchmarkAvailability()).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("Подъём ступени раскладывается в факт происшествия с обоими разрезами класса")
    void aHoldEventBecomesAnIncidentFact() {
        listener.onEvent(message(envelope(Constants.CarriedEvent.HOLD_RAISED), TENANT, HOLD_CONTENT));

        ArgumentCaptor<IncidentFact> captured = ArgumentCaptor.forClass(IncidentFact.class);
        verify(receptionService).accept(captured.capture(), eq(TOPIC));
        IncidentFact fact = captured.getValue();

        assertThat(fact.getEventType()).isEqualTo(Constants.CarriedEvent.HOLD_RAISED);
        assertThat(fact.getOccurredAt()).isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
        assertThat(fact.getHoldRung()).isEqualTo("HARD");
        assertThat(fact.getOperationCode())
                .as("ручную тропу различает КОД операции, а не актор строки")
                .isEqualTo(Constants.ManualOperation.HALT_REQUESTED);
        assertThat(fact.getAnomalySeverity())
                .as("разрез, которого у этого класса нет, остаётся пустым — это значение, а не потеря")
                .isNull();
    }

    /**
     * Ветвь третья: событие класса, признаку не отвечающего.
     *
     * <p>Факта оно не порождает, но <b>принято</b>, и молчание об этом
     * сделало бы возраст последнего принятого ложным
     * (docs/models/domain/other/StatisticsFact.md §«Признак несомого
     * класса»).
     */
    @Test
    @DisplayName("Класс, признаку не отвечающий, факта не порождает, но момент приёма двигает")
    void anUncarriedClassLeavesNoFactAndStillCountsAsAccepted() {
        listener.onEvent(message(envelope("STRATEGY_ACTIVATED"), TENANT, HOLD_CONTENT));

        verify(receptionService).acceptWithoutFact(TOPIC, OffsetDateTime.parse(OCCURRED_AT));
        verify(receptionService, never()).accept(any(DealFact.class), eq(TOPIC));
        verify(receptionService, never()).accept(any(IncidentFact.class), eq(TOPIC));
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.EventHeaders.EVENT_TYPE, Constants.EventHeaders.OCCURRED_AT})
    @DisplayName("Отсутствие заголовка, решающего ветвление или измерение, останавливает приём")
    void anAbsentBranchingHeaderHaltsReception(String absent) {
        Map<String, String> headers = envelope(Constants.CarriedEvent.DEAL_CLOSED);
        headers.remove(absent);

        assertThatThrownBy(() -> listener.onEvent(message(headers, TENANT, DEAL_CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("Отсутствие ключа зерна в содержимом останавливает приём")
    void anAbsentGrainKeyHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(envelope(Constants.CarriedEvent.DEAL_CLOSED),
                TENANT, "{\"dealInternalId\":\"deal-7\"}")))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("Сообщение без ключа записи остаётся без тенанта и останавливает приём")
    void anAbsentTenantHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(envelope(Constants.CarriedEvent.DEAL_CLOSED),
                null, DEAL_CONTENT)))
                .isInstanceOf(IncompleteEventException.class);
    }

    @Test
    @DisplayName("Неразбираемое содержимое не подменяется пустотой, а роняет обработку")
    void unreadableContentHaltsReception() {
        assertThatThrownBy(() -> listener.onEvent(message(envelope(Constants.CarriedEvent.DEAL_CLOSED),
                TENANT, "не документ")))
                .isInstanceOf(IncompleteEventException.class);
    }

    /**
     * Испорченное число роняет обработку, а не подменяется пустотой:
     * подстановка пустоты выдала бы испорченный операнд за «не приехало»
     * (docs/concept.md, П1).
     */
    @Test
    @DisplayName("Неразбираемый числовой операнд роняет обработку")
    void anUnreadableNumberHaltsReception() {
        String content = """
                {"exchangeAccountInternalId":"acct-9","result":"не число"}""";

        assertThatThrownBy(() -> listener.onEvent(message(envelope(Constants.CarriedEvent.DEAL_CLOSED),
                TENANT, content)))
                .isInstanceOf(IncompleteEventException.class);
    }

    /** Заголовки полного конверта — теми же именами, что ставит публикатор. */
    private Map<String, String> envelope(String eventType) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(Constants.EventHeaders.EVENT_ID, EVENT_ID);
        headers.put(Constants.EventHeaders.EVENT_TYPE, eventType);
        headers.put(Constants.EventHeaders.OCCURRED_AT, OCCURRED_AT);
        headers.put(Constants.EventHeaders.VERSION, VERSION);
        headers.put(Constants.EventHeaders.TRACE_CONTEXT, "00-trace-span-01");
        return headers;
    }

    private ConsumerRecord<String, String> message(Map<String, String> headers, String key, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, key, value);
        headers.forEach((name, headerValue) ->
                record.headers().add(new RecordHeader(name, headerValue.getBytes(StandardCharsets.UTF_8))));
        return record;
    }
}
