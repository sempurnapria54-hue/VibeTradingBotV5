package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_ORDER;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MANUAL_CLEARED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MANUAL_REQUESTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.TEARDOWN_ATTEMPTS;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.clear;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.commands;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealOutbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealStatus;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeConfirmsClose;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.haltFully;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.passUntilEmergencyClosed;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.payload;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.reports;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.standAtObservation;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E6} тропы снятия риска: ручная пара — постановка, доведение и
 * снятие (.claude/tests/cases/e2e-safety-teardown.md §«E6 — Ручная пара:
 * постановка, доведение и снятие»).
 *
 * <p><b>Ядер три.</b> Первое — автоматическая жёсткая ступень счёта
 * ({@code E1.2}) и снятие риска, подтверждённое площадкой: на нём ручной
 * повтор {@code E6.2} и прыжок {@code E6.8}; команды его снятия риска —
 * эталон состава, с которым сверяются ручные тропы. Второе — ручная полная
 * постановка на чистом счёте ({@code E6.1}). Третье — состояние {@code E4.5}:
 * снятие риска автоматикой не подтвердилось; на нём отказ снятия
 * {@code E6.5}, поглощённый автоматический сигнал {@code E6.4}, ручное
 * доведение {@code E6.3}, а после аварийного терминала сделки — снятие
 * {@code E6.6} и его повтор {@code E6.7}.
 *
 * <p><b>Журнал стороны догоняет тему по порядку:</b> события ядра едут с
 * ключом тенанта в одну партицию, и строка журнала последнего события
 * означает, что приняты и все прежние, — после неё счёт строк журнала
 * неподвижен.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E6 — Ручная пара: постановка, доведение и снятие")
class TeardownManualPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final Integer ATTEMPTS = 3;

    private static final List<String> INTERMEDIATE = List.of("CREATED", "IN_PROGRESS", "KILL_SWITCH_EXECUTED");

    private static Trail trail;

    private static List<String> automaticCommands;

    private static String deal;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t6");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
        trail.side(Party.TRADING_CORE).set(TEARDOWN_ATTEMPTS, String.valueOf(ATTEMPTS));
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
    @DisplayName("E6.2 — Ручной повтор той же ступени, поднятой автоматикой, заводит строку и реакции не гоняет")
    void e6_2_aManualRepeatOfAnAutomaticRungGetsARowAndNoReaction() {
        walkToExposure(trail);
        standAtObservation(trail);
        trail.forgetTraces();
        detect(trail);
        trail.relayCore();
        automaticCommands = commands(trail);
        assertThat(automaticCommands).as("предусловие E6.2: автоматика сняла риск командами").isNotEmpty();
        assertThat(safetyState(trail).path("accountSafetyRung").asString())
                .as("предусловие E6.2: жёсткая ступень поднята автоматикой").isEqualTo("TRADE_BLOCKED");
        assertThat(reports(trail, FOREIGN_ORDER).getLast().get("status"))
                .as("предусловие E6.2: снятие риска подтверждено").isEqualTo("COMPLETED");
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.2: постановка принята — " + answer.body()).isEqualTo(202);
        List<Map<String, Object>> rows = reports(trail, MANUAL_REQUESTED);
        assertThat(rows).as("E6.2: строка отчёта ручным кодом заведена").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E6.2: критичная — по составу затребованной реакции")
                .isEqualTo("CRITICAL");
        assertThat(outbox(trail, HOLD_RAISED)).as("E6.2: ступень не переставлена — строки подъёма не прибавилось")
                .hasSize(raised);
        assertThat(commands(trail)).as("E6.2: снятия риска нет — команд у стаба нет ни одной").isEmpty();
        List<Map<String, Object>> events = outbox(trail, ANOMALY_REPORTED);
        assertThat(events).as("E6.2: строка outbox класса отчёта одна").hasSize(reported + 1);
        awaitJournalRow(trail, events.getLast().get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E6.2: подъёмов столько же")
                .isEqualTo(counter(before, "raised_holds"));
        assertThat(counter(after, "anomaly_reports")).as("E6.2: отчётов на один больше")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
        assertThat(counter(after, "critical_anomaly_reports")).as("E6.2: критичных на один больше")
                .isEqualTo(counter(before, "critical_anomaly_reports") + 1);
        assertThat(counter(after, "manual_operation_reports")).as("E6.2: отчётов ручной операции на один больше")
                .isEqualTo(counter(before, "manual_operation_reports") + 1);
    }

    @Test
    @Order(2)
    @DisplayName("E6.8 — Снятие мягкого класса под стоящей жёсткой ступенью — прыжок, и он отвергается")
    void e6_8_clearingTheSoftClassUnderAStandingHardRungIsAJumpAndIsRefused() {
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long reportRows = trail.database(Party.TRADING_CORE).count("anomaly_reports");
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");

        Answer answer = clear(trail, "FREEZE", null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.8: синхронный отказ — " + answer.body()).isEqualTo(400);
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E6.8: ступень счёта не изменилась")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(trail.database(Party.TRADING_CORE).count("anomaly_reports")).as("E6.8: строки отчёта нет")
                .isEqualTo(reportRows);
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E6.8: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E6.8: строк журнала нет")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E6.8: фактов статистики нет")
                .isEqualTo(facts);
    }

    @Test
    @Order(3)
    @DisplayName("E6.1 — Ручная полная постановка идёт тем же составом, а различают её код и разрез счётчика")
    void e6_1_aManualFullHaltRunsTheSameCompositionDistinguishedByCodeAndCounter() {
        deal = walkToExposure(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        trail.forgetTraces();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.1: постановка принята — " + answer.body()).isEqualTo(202);
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E6.1: подъём один").hasSize(1);
        JsonNode content = payload(raised.getFirst());
        assertThat(content.path("code").asString()).as("E6.1: код причины — ручной").isEqualTo(MANUAL_REQUESTED);
        assertThat(content.path("rung").asString()).as("E6.1: ступень жёсткая").isEqualTo("HARD");
        Map<String, Object> report = trail.database(Party.TRADING_CORE).query("select severity, status, created_at, "
                + "modified_at from anomaly_reports where code = ?", MANUAL_REQUESTED).getFirst();
        assertThat(report.get("severity")).as("E6.1: отчёт критичный").isEqualTo("CRITICAL");
        assertThat(report.get("status")).as("E6.1: снятие подтверждено — терминал отчёта").isEqualTo("COMPLETED");
        List<String> manualCommands = commands(trail);
        assertThat(manualCommands).as("E6.1: те же команды в том же порядке, что у автоматической тропы")
                .containsExactlyElementsOf(automaticCommands);
        Instant firstCommand = trail.exchange().requests().stream()
                .filter(request -> manualCommands.contains(request.getUrl()))
                .map(request -> request.getLoggedDate().toInstant())
                .findFirst().orElseThrow();
        assertThat(moment(report.get("created_at"))).as("E6.1: отчёт открыт раньше первой команды")
                .isBefore(firstCommand);
        List<Map<String, Object>> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal);
        assertThat(shutdown).as("E6.1: каскад остановил сделку").hasSize(1);
        assertThat(moment(shutdown.getFirst().get("occurred_at"))).as("E6.1: каскад после терминала отчёта")
                .isAfter(moment(report.get("modified_at")));
        Map<String, Object> reportedEvent = outbox(trail, ANOMALY_REPORTED).getLast();
        Map<String, Object> raisedRow = awaitJournalRow(trail, raised.getFirst().get("event_id"));
        Map<String, Object> reportedRow = awaitJournalRow(trail, reportedEvent.get("event_id"));
        Map<String, Object> stoppedRow = awaitJournalRow(trail, shutdown.getFirst().get("event_id"));
        assertThat(raisedRow.get("content").toString()).as("E6.1: в журнале подъёма код — ручной")
                .contains(MANUAL_REQUESTED);
        assertThat(reportedRow.get("content").toString()).as("E6.1: в журнале отчёта код — ручной")
                .contains(MANUAL_REQUESTED);
        assertThat(moment(raisedRow.get("occurred_at"))).as("E6.1: подъём раньше отчёта")
                .isBefore(moment(reportedRow.get("occurred_at")));
        assertThat(moment(reportedRow.get("occurred_at"))).as("E6.1: отчёт раньше остановки")
                .isBefore(moment(stoppedRow.get("occurred_at")));
        Map<String, Object> after = incidents(trail);
        for (String column : List.of("raised_holds", "hard_raised_holds", "manually_raised_holds",
                "anomaly_reports", "critical_anomaly_reports", "manual_operation_reports")) {
            assertThat(counter(after, column)).as("E6.1: счётчик " + column + " на один больше")
                    .isEqualTo(counter(before, column) + 1);
        }
        for (String column : List.of("opened_deals", "order_decisions")) {
            assertThat(counter(after, column)).as("E6.1: счётчик " + column + " не двигался")
                    .isEqualTo(counter(before, column));
        }
    }

    @Test
    @Order(4)
    @DisplayName("E6.5 — Снятие сворачивания при живом риске отвергается, и до площадки вызов не доходит")
    void e6_5_clearingTheTeardownRungOverLiveRiskIsRefusedWithoutReachingTheExchange() {
        deal = walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        standAtObservation(trail);
        detect(trail);
        trail.relayCore();
        for (int tick = 0; tick <= ATTEMPTS; tick++) {
            detect(trail);
        }
        trail.relayCore();
        assertThat(automaticReport().get("status")).as("предусловие E6.5: состояние E4.5 — отчёт не закрыт")
                .isIn(INTERMEDIATE.toArray());
        awaitJournalRow(trail, trail.database(Party.TRADING_CORE)
                .query("select event_id from outbox_events order by id").getLast().get("event_id"));
        incidents(trail);
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long reportRows = trail.database(Party.TRADING_CORE).count("anomaly_reports");
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        trail.forgetTraces();

        Answer answer = clear(trail, "FULL", null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.5: синхронный отказ поверхности — " + answer.body()).isEqualTo(400);
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E6.5: ступень счёта не изменилась")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(trail.database(Party.TRADING_CORE).count("anomaly_reports"))
                .as("E6.5: отказ при запуске строки отчёта не заводит").isEqualTo(reportRows);
        assertThat(trail.exchange().requests()).as("E6.5: к стабу площадки не ушло ни одного запроса").isEmpty();
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E6.5: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E6.5: строк журнала нет")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E6.5: фактов статистики нет")
                .isEqualTo(facts);
    }

    @Test
    @Order(5)
    @DisplayName("E6.4 — Автоматический сигнал права на доведение не имеет")
    void e6_4_anAutomaticSignalHasNoRightToFinishTheTeardown() {
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        assertThat(commands(trail)).as("E6.4: команд снятия риска не прибавилось — сигнал поглощён анкером")
                .isEmpty();
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E6.4: ступень стои́т")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E6.4: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(automaticReport().get("status")).as("E6.4: отчёт остался незакрытым")
                .isIn(INTERMEDIATE.toArray());
    }

    @Test
    @Order(6)
    @DisplayName("E6.3 — Ручное доведение при непогашенном риске гоняет снятие заново, а события подъёма не рождает")
    void e6_3_aManualFinishOverUnclearedRiskRerunsTheTeardownWithoutARaiseEvent() {
        exchangeConfirmsClose(trail);
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        trail.forgetTraces();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.3: постановка принята — " + answer.body()).isEqualTo(202);
        assertThat(commands(trail)).as("E6.3: команды снятия ушли заново — полным составом и в том же порядке")
                .containsExactlyElementsOf(automaticCommands);
        assertThat(outbox(trail, HOLD_RAISED)).as("E6.3: ступень не переставлена — строки подъёма не прибавилось")
                .hasSize(raised);
        List<Map<String, Object>> manual = reports(trail, MANUAL_REQUESTED);
        assertThat(manual).as("E6.3: отчёт ручным кодом заведён").hasSize(1);
        assertThat(manual.getFirst().get("status")).as("E6.3: и завершён по подтверждённому снятию")
                .isEqualTo("COMPLETED");
        assertThat(automaticReport().get("status")).as("E6.3: автоматическая строка осталась незакрытой")
                .isIn(INTERMEDIATE.toArray());
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E6.3: подъёмов столько же")
                .isEqualTo(counter(before, "raised_holds"));
        assertThat(counter(after, "manual_operation_reports")).as("E6.3: ручных отчётов на один больше")
                .isEqualTo(counter(before, "manual_operation_reports") + 1);
    }

    @Test
    @Order(7)
    @DisplayName("E6.6 — Снятие сворачивания при погашенном риске переводит счёт в мягкую ступень, а не в рабочее состояние")
    void e6_6_clearingTheTeardownRungOverClearedRiskMovesTheAccountToTheSoftRung() {
        passUntilEmergencyClosed(trail, deal);
        assertThat(dealStatus(trail, deal)).as("предусловие E6.6: сделка радиуса терминальна")
                .isEqualTo("EMERGENCY_CLOSED");
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        trail.forgetTraces();

        Answer answer = clear(trail, "FULL", null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.6: снятие применено — " + answer.body()).isEqualTo(204);
        assertThat(safetyState(trail).path("accountSafetyRung").asString())
                .as("E6.6: ступень счёта — мягкая, а не рабочее состояние").isEqualTo("HOLD");
        List<Map<String, Object>> rows = reports(trail, MANUAL_CLEARED);
        assertThat(rows).as("E6.6: строка отчёта кодом снятия заведена").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E6.6: некритичная").isEqualTo("NON_CRITICAL");
        assertThat(outbox(trail, HOLD_RAISED)).as("E6.6: строки подъёма нет").hasSize(raised);
        List<Map<String, Object>> fresh = trail.database(Party.TRADING_CORE)
                .query("select event_id, event_type from outbox_events order by id")
                .subList(outboxRows.intValue(), trail.database(Party.TRADING_CORE).count("outbox_events").intValue());
        assertThat(fresh).as("E6.6: прибавилась одна строка — отчёт; класса снятия нет").singleElement()
                .satisfies(row -> assertThat(row.get("event_type")).isEqualTo(ANOMALY_REPORTED));
        assertThat(trail.exchange().requests()).as("E6.6: запросов к площадке нет ни одного").isEmpty();
        assertThat(awaitJournalRow(trail, fresh.getFirst().get("event_id")).get("content").toString())
                .as("E6.6: строка журнала об отчёте снятия").contains(MANUAL_CLEARED);
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "anomaly_reports")).as("E6.6: отчётов на один больше")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
        assertThat(counter(after, "manual_operation_reports")).as("E6.6: отчётов ручной операции на один больше")
                .isEqualTo(counter(before, "manual_operation_reports") + 1);
        assertThat(counter(after, "raised_holds")).as("E6.6: подъёмов столько же")
                .isEqualTo(counter(before, "raised_holds"));
    }

    @Test
    @Order(8)
    @DisplayName("E6.7 — Второе снятие той же ступени холостое, и строки не даёт")
    void e6_7_aSecondClearanceOfTheSameRungIsANoOp() {
        Long outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");

        Answer answer = clear(trail, "FULL", null);
        trail.relayCore();

        assertThat(answer.status()).as("E6.7: отказа нет — " + answer.body()).isEqualTo(204);
        assertThat(safetyState(trail).path("accountSafetyRung").asString())
                .as("E6.7: ступень осталась мягкой — вниз по лестнице вызов не шагает").isEqualTo("HOLD");
        assertThat(reports(trail, MANUAL_CLEARED)).as("E6.7: второй строки отчёта нет").hasSize(1);
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events")).as("E6.7: строк outbox не прибавилось")
                .isEqualTo(outboxRows);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E6.7: строк журнала нет")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E6.7: фактов статистики нет")
                .isEqualTo(facts);
    }

    /** Критичная строка отчёта автоматической тропы — кодом чужой заявки. */
    private static Map<String, Object> automaticReport() {
        return trail.database(Party.TRADING_CORE).query("select status from anomaly_reports "
                + "where code = ? and severity = 'CRITICAL' order by id", FOREIGN_ORDER).getLast();
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
