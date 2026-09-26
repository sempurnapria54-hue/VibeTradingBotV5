package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.safetyteardown.TeardownTrail.ANOMALY_REPORTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.BLIND_PASS_LIMIT;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_INSTRUMENT_RISK;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_ORDER;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.PASS_INCOMPLETE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.absent;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.commands;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAnswersAlgoSlice;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsForeignInstrumentPosition;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsForeignOrder;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsNoForeignOrder;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeRefusesAlgoSlice;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.payload;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.reports;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.sliceReads;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E1} тропы снятия риска: детекция расхождения и гистерезис
 * (.claude/tests/cases/e2e-safety-teardown.md §«E1 — Детекция расхождения и
 * гистерезис»).
 *
 * <p><b>Ядер три, и разводит их необратимость ступени и калибровка.</b>
 * Поднятая ступень счёта не снимается ничем, кроме ручного снятия, а оно —
 * предмет своей группы, поэтому ветви одного состояния получают свежее
 * развёртывание ядра прологом: {@code E1.1}-{@code E1.2} — первое;
 * {@code E1.3} и тропа слепоты {@code E1.6}-{@code E1.8} — второе
 * ({@code E1.3} ступени не поднимает, а срез её конца — без расхождения);
 * {@code E1.4} с ненулевым минимальным возрастом и {@code E1.5}, чьему
 * детектору гистерезис не нужен, — третье.
 *
 * <p><b>Минимальный возраст подтверждения — ноль</b> у первых двух ядер:
 * два тика, поданных подряд, и есть гистерезис
 * (§«Чем достаются выходы»). Предел слепоты — три, названный явно.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E1 — Детекция расхождения и гистерезис")
class TeardownDetectionPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final Integer SLICE_READS = 5;

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t1");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
        trail.side(Party.TRADING_CORE).set(BLIND_PASS_LIMIT, "3");
        trail.renew(Party.TRADING_CORE);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E1.1 — Чужая живая заявка в срезе: первый тик даёт отчёт наблюдения и ни одной команды")
    void e1_1_aForeignLiveOrderGivesAnObservationOnTheFirstTickAndNoCommand() {
        walkToExposure(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();

        JsonNode state = safetyState(trail);
        assertThat(state.path("accountSafetyRung").asString()).as("E1.1: ступени счёта нет").isEqualTo("ACTIVE");
        assertThat(state.path("instrumentInternalIdsWithStandingRung")).as("E1.1: ступени пары нет").isEmpty();
        List<Map<String, Object>> rows = reports(trail, FOREIGN_ORDER);
        assertThat(rows).as("E1.1: строка отчёта кодом чужой заявки одна").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E1.1: некритичная").isEqualTo("NON_CRITICAL");
        assertThat(rows.getFirst().get("scope")).as("E1.1: радиус счётный").isEqualTo("EXCHANGE_ACCOUNT");
        assertThat(outbox(trail, HOLD_RAISED)).as("E1.1: строки outbox подъёма ступени нет").isEmpty();
        List<Map<String, Object>> reportedRows = outbox(trail, ANOMALY_REPORTED);
        assertThat(reportedRows).as("E1.1: строка outbox класса отчёта одна").hasSize(reported + 1);
        assertThat(sliceReads(trail)).as("E1.1: к стабу ушли пять чтений среза").hasSize(SLICE_READS);
        assertThat(commands(trail)).as("E1.1: ни одной команды площадке").isEmpty();
        Map<String, Object> journal = awaitJournalRow(trail, reportedRows.getLast().get("event_id"));
        assertThat(journal.get("event_type")).as("E1.1: строка журнала об отчёте").isEqualTo(ANOMALY_REPORTED);
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "anomaly_reports")).as("E1.1: отчётов на один больше")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
        assertThat(counter(after, "critical_anomaly_reports")).as("E1.1: критичных столько же")
                .isEqualTo(counter(before, "critical_anomaly_reports"));
        assertThat(counter(after, "raised_holds")).as("E1.1: поднятых ступеней столько же")
                .isEqualTo(counter(before, "raised_holds"));
    }

    @Test
    @Order(2)
    @DisplayName("E1.2 — Второй тик с тем же признаком поднимает жёсткую ступень счёта и рождает событие подъёма")
    void e1_2_theSecondTickWithTheSameSignRaisesTheHardAccountRung() {
        Map<String, Object> before = incidents(trail);
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E1.2: ступень счёта — сворачивание")
                .isEqualTo("TRADE_BLOCKED");
        List<Map<String, Object>> rows = reports(trail, FOREIGN_ORDER);
        assertThat(rows).as("E1.2: строк отчёта кодом чужой заявки две").hasSize(2);
        assertThat(rows.getLast().get("severity")).as("E1.2: вторая — критичная").isEqualTo("CRITICAL");
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E1.2: строка outbox класса подъёма одна").hasSize(1);
        JsonNode content = payload(raised.getFirst());
        assertThat(raised.getFirst().get("tenant_id")).as("E1.2: тенант — конвертом").isEqualTo(trail.tenant());
        assertThat(content.path("exchangeAccountInternalId").asString()).as("E1.2: в содержимом — счёт")
                .isEqualTo(trail.account());
        assertThat(content.path("rung").asString()).as("E1.2: ступень сигнала жёсткая").isEqualTo("HARD");
        assertThat(content.path("code").asString()).as("E1.2: код причины").isEqualTo(FOREIGN_ORDER);
        assertThat(content.path("actor").asString()).as("E1.2: актор").isNotBlank();
        assertThat(absent(content.path("instrumentInternalId"))).as("E1.2: инструмента нет — радиус счётный")
                .isTrue();
        assertThat(commands(trail)).as("E1.2: у стаба появились команды снятия риска").isNotEmpty();
        awaitJournalRow(trail, raised.getFirst().get("event_id"));
        List<Map<String, Object>> reportedRows = outbox(trail, ANOMALY_REPORTED);
        assertThat(reportedRows).as("E1.2: строка outbox класса отчёта одна").hasSize(reported + 1);
        awaitJournalRow(trail, reportedRows.getLast().get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E1.2: поднятых ступеней на одну больше")
                .isEqualTo(counter(before, "raised_holds") + 1);
        assertThat(counter(after, "hard_raised_holds")).as("E1.2: жёстких на одну больше")
                .isEqualTo(counter(before, "hard_raised_holds") + 1);
        assertThat(counter(after, "manually_raised_holds")).as("E1.2: ручных столько же")
                .isEqualTo(counter(before, "manually_raised_holds"));
        assertThat(counter(after, "anomaly_reports")).as("E1.2: отчётов на один больше")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
        assertThat(counter(after, "critical_anomaly_reports")).as("E1.2: критичных на один больше")
                .isEqualTo(counter(before, "critical_anomaly_reports") + 1);
    }

    @Test
    @Order(3)
    @DisplayName("E1.3 — Признак исчез ко второму тику: ступени нет, у площадки команд нет")
    void e1_3_theSignIsGoneByTheSecondTickSoNoRungAndNoCommand() {
        incidents(trail);
        walkToExposure(trail);
        trail.relayCore();
        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();
        assertThat(reports(trail, FOREIGN_ORDER)).as("предусловие E1.3: строка наблюдения стои́т").hasSize(1);
        Map<String, Object> before = incidents(trail);
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        trail.forgetTraces();

        exchangeHoldsNoForeignOrder(trail);
        detect(trail);
        trail.relayCore();

        JsonNode state = safetyState(trail);
        assertThat(state.path("accountSafetyRung").asString()).as("E1.3: ступени счёта нет").isEqualTo("ACTIVE");
        assertThat(state.path("instrumentInternalIdsWithStandingRung")).as("E1.3: ступени пары нет").isEmpty();
        List<Map<String, Object>> rows = reports(trail, FOREIGN_ORDER);
        assertThat(rows).as("E1.3: второй строки отчёта нет, строка наблюдения осталась").hasSize(1);
        assertThat(rows.getFirst().get("severity")).isEqualTo("NON_CRITICAL");
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E1.3: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(commands(trail)).as("E1.3: команд у стаба нет").isEmpty();
        assertThat(sliceReads(trail)).as("E1.3: чтения срезов повторились").hasSize(SLICE_READS);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E1.3: новых строк журнала нет")
                .isEqualTo(journal);
        assertThat(incidents(trail)).as("E1.3: числа статистики те же").satisfies(after -> {
            for (String column : List.of("anomaly_reports", "critical_anomaly_reports", "raised_holds")) {
                assertThat(counter(after, column)).as(column).isEqualTo(counter(before, column));
            }
        });
    }

    @Test
    @Order(4)
    @DisplayName("E1.6 — Неполный срез: прочие детекторы молчат, и молчание записано")
    void e1_6_anIncompleteSliceSilencesTheDetectorsAndTheSilenceIsRecorded() {
        Integer foreign = reports(trail, FOREIGN_ORDER).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        exchangeHoldsForeignOrder(trail);
        exchangeRefusesAlgoSlice(trail);
        detect(trail);
        trail.relayCore();

        JsonNode state = safetyState(trail);
        assertThat(state.path("accountSafetyRung").asString()).as("E1.6: ступени счёта нет").isEqualTo("ACTIVE");
        assertThat(state.path("instrumentInternalIdsWithStandingRung")).as("E1.6: ступени пары нет").isEmpty();
        assertThat(state.path("blindPassCount").asInt()).as("E1.6: счёт слепоты — единица").isEqualTo(1);
        List<Map<String, Object>> rows = reports(trail, PASS_INCOMPLETE);
        assertThat(rows).as("E1.6: строка отчёта кодом неполного прохода одна").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E1.6: некритичная").isEqualTo("NON_CRITICAL");
        assertThat(reports(trail, FOREIGN_ORDER)).as("E1.6: отчёта по чужой заявке нет").hasSize(foreign);
        assertThat(outbox(trail, HOLD_RAISED)).as("E1.6: подъёма ступени нет").isEmpty();
        assertThat(commands(trail)).as("E1.6: команд снятия риска нет").isEmpty();
        List<Map<String, Object>> reportedRows = outbox(trail, ANOMALY_REPORTED);
        assertThat(reportedRows).as("E1.6: строка outbox отчёта одна").hasSize(reported + 1);
        Map<String, Object> journal = awaitJournalRow(trail, reportedRows.getLast().get("event_id"));
        assertThat(journal.get("content").toString()).as("E1.6: строка журнала об отчёте неполного прохода")
                .contains(PASS_INCOMPLETE);
    }

    @Test
    @Order(5)
    @DisplayName("E1.7 — Третий подряд неполный проход поднимает МЯГКУЮ ступень счёта, и снятия риска в ней нет")
    void e1_7_theThirdIncompletePassRaisesTheSoftAccountRungWithoutTeardown() {
        detect(trail);
        trail.relayCore();
        JsonNode standing = safetyState(trail);
        assertThat(standing.path("blindPassCount").asInt()).as("предусловие E1.7: счёт слепоты — предел минус один")
                .isEqualTo(2);
        assertThat(standing.path("accountSafetyRung").asString()).isEqualTo("ACTIVE");
        Map<String, Object> before = incidents(trail);
        Integer incomplete = reports(trail, PASS_INCOMPLETE).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        JsonNode state = safetyState(trail);
        assertThat(state.path("accountSafetyRung").asString()).as("E1.7: ступень счёта мягкая").isEqualTo("HOLD");
        assertThat(state.path("blindPassCount").asInt()).as("E1.7: счёт слепоты равен пределу").isEqualTo(3);
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E1.7: строка outbox класса подъёма одна").hasSize(1);
        JsonNode content = payload(raised.getFirst());
        assertThat(content.path("rung").asString()).as("E1.7: ступень в содержимом мягкая").isEqualTo("SOFT");
        assertThat(content.path("code").asString()).as("E1.7: код неполного прохода").isEqualTo(PASS_INCOMPLETE);
        assertThat(reports(trail, PASS_INCOMPLETE)).as("E1.7: отдельной строки отчёта нет — ключ тот же")
                .hasSize(incomplete);
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E1.7: строки outbox отчёта не прибавилось")
                .hasSize(reported);
        assertThat(commands(trail)).as("E1.7: команд снятия риска нет ни одной").isEmpty();
        awaitJournalRow(trail, raised.getFirst().get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E1.7: поднятых ступеней на одну больше")
                .isEqualTo(counter(before, "raised_holds") + 1);
        assertThat(counter(after, "hard_raised_holds")).as("E1.7: жёстких столько же")
                .isEqualTo(counter(before, "hard_raised_holds"));
    }

    @Test
    @Order(6)
    @DisplayName("E1.8 — Наблюдённый проход обнуляет счёт слепоты и ступени не снимает")
    void e1_8_anObservedPassResetsTheBlindCountAndKeepsTheRung() {
        Map<String, Object> before = incidents(trail);
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long journal = trail.database(Party.AUDIT).count("audit_records");

        exchangeHoldsNoForeignOrder(trail);
        exchangeAnswersAlgoSlice(trail);
        detect(trail);
        trail.relayCore();

        JsonNode state = safetyState(trail);
        assertThat(state.path("blindPassCount").asInt()).as("E1.8: счёт слепоты обнулён").isZero();
        assertThat(state.path("accountSafetyRung").asString()).as("E1.8: ступень осталась мягкой").isEqualTo("HOLD");
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E1.8: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E1.8: новых строк журнала нет")
                .isEqualTo(journal);
        assertThat(incidents(trail)).as("E1.8: числа статистики те же").satisfies(after -> {
            for (String column : List.of("anomaly_reports", "critical_anomaly_reports", "raised_holds",
                    "hard_raised_holds")) {
                assertThat(counter(after, column)).as(column).isEqualTo(counter(before, column));
            }
        });
    }

    @Test
    @Order(7)
    @DisplayName("E1.4 — Строка подтверждения моложе минимального возраста ступени не поднимает")
    void e1_4_aConfirmationRowYoungerThanTheMinimumAgeRaisesNoRung() {
        incidents(trail);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "10m");
        trail.renew(Party.TRADING_CORE);
        walkToExposure(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        trail.forgetTraces();

        exchangeHoldsForeignOrder(trail);
        detect(trail);
        detect(trail);
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E1.4: ступени нет")
                .isEqualTo("ACTIVE");
        List<Map<String, Object>> rows = reports(trail, FOREIGN_ORDER);
        assertThat(rows).as("E1.4: строка отчёта по коду ровно одна").hasSize(1);
        assertThat(rows.getFirst().get("severity")).isEqualTo("NON_CRITICAL");
        assertThat(outbox(trail, HOLD_RAISED)).as("E1.4: строки outbox подъёма нет").isEmpty();
        assertThat(commands(trail)).as("E1.4: команд нет ни одной").isEmpty();
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "anomaly_reports")).as("E1.4: отчётов на один больше за оба тика")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
    }

    @Test
    @Order(8)
    @DisplayName("E1.5 — Живой риск по инструменту вне контура поднимает ступень первым же тиком")
    void e1_5_liveRiskOnAnInstrumentOutsideTheContourRaisesTheRungOnTheFirstTick() {
        exchangeHoldsNoForeignOrder(trail);
        BigDecimal size = (BigDecimal) trail.database(Party.TRADING_CORE)
                .query("select external_size from positions where status = 'ACTIVE'").getFirst().get("external_size");
        exchangeHoldsForeignInstrumentPosition(trail, size.stripTrailingZeros().toPlainString());
        Map<String, Object> before = incidents(trail);
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString())
                .as("E1.5: ступень счёта — сворачивание уже на первом тике").isEqualTo("TRADE_BLOCKED");
        List<Map<String, Object>> rows = reports(trail, FOREIGN_INSTRUMENT_RISK);
        assertThat(rows).as("E1.5: строка отчёта одна").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E1.5: и сразу критичная").isEqualTo("CRITICAL");
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E1.5: событие подъёма одно").hasSize(1);
        assertThat(payload(raised.getFirst()).path("code").asString()).isEqualTo(FOREIGN_INSTRUMENT_RISK);
        assertThat(commands(trail)).as("E1.5: команды снятия риска у стаба есть").isNotEmpty();
        awaitJournalRow(trail, raised.getFirst().get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "hard_raised_holds")).as("E1.5: жёстких подъёмов на один больше")
                .isEqualTo(counter(before, "hard_raised_holds") + 1);
        assertThat(counter(after, "critical_anomaly_reports")).as("E1.5: критичных отчётов на один больше")
                .isEqualTo(counter(before, "critical_anomaly_reports") + 1);
        assertThat(rows.stream().filter(row -> Objects.equals("NON_CRITICAL", row.get("severity"))))
                .as("E1.5: некритичной строки по коду нет ни одной").isEmpty();
    }
}
