package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.exitandclose.ExitTrail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
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

import static com.example.tests.e2e.safetyteardown.TeardownTrail.ANOMALY_REPORTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_CLOSED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_ORDER;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealOutbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealStatus;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.halt;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.passUntilEmergencyClosed;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.recoverAfterCascade;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.standAtObservation;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E3} тропы снятия риска: каскад радиуса и остановка сделок
 * (.claude/tests/cases/e2e-safety-teardown.md §«E3 — Каскад радиуса и
 * остановка сделок»).
 *
 * <p><b>Сделка, активная под стоящей ступенью счёта, ставится восстановлением,
 * а не входом:</b> счёт под ступенью из выборки входа выпал, а пара занята,
 * пока сделка каскада не терминальна. Поэтому {@code E3.4} продолжает ядро
 * {@code E3.3}-{@code E3.5}: первая сделка доведена до аварийного терминала, и
 * площадка снова держит позицию, которую не объясняет ни одна сделка.
 * {@code E3.2} ставит то же состояние на своём ядре ручной постановкой, а
 * {@code E3.6} — на своём, с закрытием, которое площадка не исполняет.
 *
 * <p><b>Клетка {@code E3.1} здесь не написана:</b> её предусловие — две
 * активные сделки на разных инструментах. <b>{@code E3.4} — красное ожидание</b>
 * (метка {@code debt}, находка {@code F7} документа).
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E3 — Каскад радиуса и остановка сделок")
class TeardownCascadePathTest {

    private static final Integer PASSES_AFTER_EDGE = 3;

    private static Trail trail;

    private static String deal;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t3");
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
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
    @DisplayName("E3.3 — Каскад начинается после терминала отчёта, а не параллельно ему")
    void e3_3_theCascadeStartsAfterTheReportTerminal() {
        deal = walkToExposure(trail);
        standAtObservation(trail);
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> report = core.query("select created_at, modified_at, status from anomaly_reports "
                + "where code = ? and severity = 'CRITICAL'", FOREIGN_ORDER).getFirst();
        assertThat(report.get("status")).as("E3.3: снятие подтверждено — отчёт терминален").isEqualTo("COMPLETED");
        List<LoggedRequest> journal = trail.exchange().requests();
        Instant firstCommand = journal.stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(request -> request.getLoggedDate().toInstant())
                .findFirst().orElseThrow();
        assertThat(moment(report.get("created_at"))).as("E3.3: отчёт открыт раньше первой команды у стаба")
                .isBefore(firstCommand);
        Map<String, Object> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal).getFirst();
        Instant terminal = moment(report.get("modified_at"));
        Instant edge = moment(shutdown.get("occurred_at"));
        assertThat(edge).as("E3.3: ребро остановки позже терминала отчёта").isAfter(terminal);
        assertThat(journal.stream().map(request -> request.getLoggedDate().toInstant())
                .filter(at -> at.isAfter(terminal) && at.isBefore(edge)))
                .as("E3.3: между терминалом отчёта и ребром остановки к стабу не ушло ни одного чтения").isEmpty();
        Map<String, Object> raised = outbox(trail, HOLD_RAISED).getFirst();
        Map<String, Object> reported = outbox(trail, ANOMALY_REPORTED).getLast();
        Instant raisedAt = moment(awaitJournalRow(trail, raised.get("event_id")).get("occurred_at"));
        Instant reportedAt = moment(awaitJournalRow(trail, reported.get("event_id")).get("occurred_at"));
        Instant stoppedAt = moment(awaitJournalRow(trail, shutdown.get("event_id")).get("occurred_at"));
        assertThat(raisedAt).as("E3.3: в журнале подъём раньше отчёта").isBefore(reportedAt);
        assertThat(reportedAt).as("E3.3: отчёт раньше остановки").isBefore(stoppedAt);
    }

    @Test
    @Order(2)
    @DisplayName("E3.5 — Ошибочное состояние не терминал: сделка доходит до аварийного терминала следующими проходами")
    void e3_5_theErrorStateIsNotTerminalAndTheDealReachesTheEmergencyTerminal() {
        assertThat(dealStatus(trail, deal)).as("предусловие E3.5: сделка в ошибочном состоянии").isEqualTo("ERROR");

        passUntilEmergencyClosed(trail, deal);

        Database core = trail.database(Party.TRADING_CORE);
        assertThat(core.query("select close_reason from deals where internal_id = ?", deal).getFirst()
                .get("close_reason")).as("E3.5: причина закрытия — аварийная").isEqualTo("EMERGENCY_CLOSE");
        List<Map<String, Object>> closed = dealOutbox(trail, DEAL_CLOSED, deal);
        assertThat(closed).as("E3.5: строка outbox терминального класса одна").hasSize(1);
        Object event = closed.getFirst().get("event_id");
        assertThat(awaitJournalRow(trail, event).get("event_type")).as("E3.5: строка журнала о терминале")
                .isEqualTo("DEAL_CLOSED");
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E3.5: сделочный факт у статистики",
                () -> statistics.query("select event_id from deal_facts where event_id = ?", event).size() == 1);
        assertThat(moment(statistics.query("select closed_at from deal_facts where event_id = ?", event).getFirst()
                .get("closed_at"))).as("E3.5: сутки зерна взяты моментом терминала")
                .isEqualTo(moment(closed.getFirst().get("occurred_at")));
    }

    /**
     * Красна последним ожиданием — находка {@code F7} документа: шаг прохода
     * затребует только ребро энфорсмента, обработчик ошибочного состояния
     * команд не шлёт, и живая позиция сделки, ставшей активной после каскада,
     * под жёсткой ступенью не снимается никем.
     */
    @Test
    @Order(3)
    @Tag("debt")
    @DisplayName("E3.4 — Сделка, ставшая активной после каскада, подбирается шагом прохода")
    void e3_4_aDealBecomingActiveAfterTheCascadeIsPickedByThePass() {
        String recovered = recoverAfterCascade(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        trail.passUntil("E3.4: восстановленная сделка уведена в ошибочное состояние",
                () -> Objects.equals("ERROR", dealStatus(trail, recovered)));
        for (int pass = 0; pass < PASSES_AFTER_EDGE; pass++) {
            trail.orchestrate();
        }
        trail.relayCore();

        assertThat(trail.database(Party.TRADING_CORE).query("select shutdown_reason from deals where internal_id = ?",
                recovered).getFirst().get("shutdown_reason")).as("E3.4: причина — биржевая").isEqualTo("EXCHANGE_HOLD");
        List<Map<String, Object>> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, recovered);
        assertThat(shutdown).as("E3.4: строка outbox остановки по ней одна").hasSize(1);
        awaitJournalRow(trail, shutdown.getFirst().get("event_id"));
        assertThat(outbox(trail, HOLD_RAISED)).as("E3.4: второго прогона реакции ступени нет").hasSize(raised);
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E3.4: и отчёта реакции тоже").hasSize(reported);
        assertThat(trail.exchange().requests(ExitTrail.CLOSE_POSITION))
                .as("E3.4: снятие риска по ней гоняется проходом").isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("E3.2 — Сделка под стоящей биржевой ступенью при инструментном сигнале получает биржевую причину")
    void e3_2_aDealUnderAStandingExchangeRungGetsTheExchangeReasonOnAnInstrumentSignal() {
        String first = walkToExposure(trail);
        Answer account = halt(trail, "FULL", null);
        assertThat(account.status()).as("предусловие E3.2: ступень счёта поднята — " + account.body())
                .isEqualTo(202);
        Trail.await("предусловие E3.2: ступень счёта — сворачивание",
                () -> Objects.equals("TRADE_BLOCKED", safetyState(trail).path("accountSafetyRung").asString()));
        passUntilEmergencyClosed(trail, first);
        String recovered = recoverAfterCascade(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();

        Answer pair = halt(trail, "FULL", Trail.INSTRUMENT);
        Trail.await("E3.2: ступень пары переставлена", () -> outbox(trail, HOLD_RAISED).size() == raised + 1);
        trail.relayCore();

        assertThat(pair.status()).as("E3.2: постановка на паре принята — " + pair.body()).isEqualTo(202);
        assertThat(safetyState(trail).path("instrumentInternalIdsWithStandingRung"))
                .as("E3.2: ступень стои́т на паре").extracting(node -> node.asString()).contains(Trail.INSTRUMENT);
        Map<String, Object> row = trail.database(Party.TRADING_CORE)
                .query("select status, shutdown_reason from deals where internal_id = ?", recovered).getFirst();
        assertThat(row.get("status")).as("E3.2: сделка в ошибочном состоянии").isEqualTo("ERROR");
        assertThat(row.get("shutdown_reason")).as("E3.2: причина — биржевая, а не риск-политики")
                .isEqualTo("EXCHANGE_HOLD");
        List<Map<String, Object>> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, recovered);
        assertThat(shutdown).as("E3.2: строка outbox остановки одна").hasSize(1);
        assertThat(awaitJournalRow(trail, shutdown.getFirst().get("event_id")).get("content").toString())
                .as("E3.2: строка журнала несёт ту же причину").contains("EXCHANGE_HOLD");
    }

    @Test
    @Order(5)
    @DisplayName("E3.6 — Терминала нет, пока отсутствие живого риска не доказано")
    void e3_6_noTerminalWhileTheAbsenceOfLiveRiskIsNotProven() {
        String unconfirmed = walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        standAtObservation(trail);
        detect(trail);
        trail.relayCore();
        assertThat(dealStatus(trail, unconfirmed)).as("предусловие E3.6: состояние E3.1").isEqualTo("ERROR");
        ExitTrail.exchangeKeepsBills(trail, System.currentTimeMillis(), "0", "");
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        trail.forgetTraces();

        trail.orchestrate();
        trail.orchestrate();
        trail.relayCore();

        assertThat(dealStatus(trail, unconfirmed)).as("E3.6: сделка осталась в ошибочном состоянии")
                .isEqualTo("ERROR");
        assertThat(dealOutbox(trail, DEAL_CLOSED, unconfirmed)).as("E3.6: терминального события нет").isEmpty();
        assertThat(trail.exchange().requests().stream()
                .filter(request -> Objects.equals("GET", request.getMethod().getName())))
                .as("E3.6: к стабу ушли чтения добычи фактов").isNotEmpty();
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E3.6: сделочного факта нет")
                .isEqualTo(dealFacts);
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
