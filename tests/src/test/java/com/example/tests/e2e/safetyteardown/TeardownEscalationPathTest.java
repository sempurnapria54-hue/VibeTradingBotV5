package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.exitandclose.ExitTrail;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.TEARDOWN_ATTEMPTS;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.absent;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealOutbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.halt;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.payload;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E5} тропы снятия риска: эскалация радиуса и исчерпанный предел
 * (.claude/tests/cases/e2e-safety-teardown.md §«E5 — Эскалация радиуса и
 * исчерпанный предел»).
 *
 * <p><b>Площадка во всей группе принимает закрытие и не исполняет его</b> —
 * снятие риска не подтверждается ни на одном радиусе. {@code E5.1} и
 * {@code E5.2} читают один ход — ручную постановку на паре; {@code E5.4} ставит
 * ступень счёта, и её состояние есть предусловие {@code E5.3}, которая идёт
 * следом на том же ядре.
 *
 * <p><b>Конец реакции читается журналом ядра:</b> ручная постановка отвечает
 * до реакции, а отсутствие второго факта подъёма утверждается только после
 * того, как развилка эскалации пройдена.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E5 — Эскалация радиуса и исчерпанный предел")
class TeardownEscalationPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String MANUAL = "MANUAL_HALT_REQUESTED";

    private static final String RESIDUAL = "EXCHANGE_KILL_SWITCH_RESIDUAL";

    private static final String ESCALATING = "escalating to the account radius";

    private static final String KEPT_OPEN = "the anomaly report is kept open";

    private static final Integer ATTEMPTS = 3;

    private static final Set<String> REACTION_CLASSES = Set.of(HOLD_RAISED, ANOMALY_REPORTED, DEAL_SHUTDOWN_INITIATED);

    private static Trail trail;

    private static String deal;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t5");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
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
    @DisplayName("E5.1 — Неподтверждённое снятие на инструментном радиусе поднимает счётную ступень своим кодом")
    void e5_1_anUnconfirmedInstrumentTeardownRaisesTheAccountRungWithItsOwnCode() {
        deal = walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        trail.forgetTraces();

        Answer answer = haltAndAwait(Trail.INSTRUMENT, ESCALATING);
        Trail.await("E5.1: оба факта подъёма", () -> outbox(trail, HOLD_RAISED).size() == 2);
        trail.relayCore();

        assertThat(answer.status()).as("E5.1: постановка на паре принята — " + answer.body()).isEqualTo(202);
        JsonNode state = safetyState(trail);
        assertThat(state.path("instrumentInternalIdsWithStandingRung")).as("E5.1: ступень пары стои́т")
                .extracting(JsonNode::asString).contains(Trail.INSTRUMENT);
        assertThat(state.path("accountSafetyRung").asString()).as("E5.1: сверх неё — жёсткая ступень счёта")
                .isEqualTo("TRADE_BLOCKED");
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        JsonNode pair = payload(raised.get(0));
        JsonNode account = payload(raised.get(1));
        assertThat(pair.path("scope").asString()).as("E5.1: первый подъём — пары").isEqualTo("INSTRUMENT");
        assertThat(pair.path("code").asString()).isEqualTo(MANUAL);
        assertThat(account.path("scope").asString()).as("E5.1: второй — счётный").isEqualTo("EXCHANGE_ACCOUNT");
        assertThat(account.path("code").asString()).as("E5.1: кодом остатка снятия, а не исходного сигнала")
                .isEqualTo(RESIDUAL);
        assertThat(absent(account.path("instrumentInternalId"))).as("E5.1: инструмента у счётного нет").isTrue();
        assertThat(trail.database(Party.TRADING_CORE).query("select status from anomaly_reports "
                + "where code = ? and scope = 'INSTRUMENT' and severity = 'CRITICAL'", MANUAL).getFirst()
                .get("status")).as("E5.1: отчёт инструментной реакции терминала не получил").isNotEqualTo("COMPLETED");
        assertThat(raised).as("E5.1: две строки журнала о подъёме, с разными радиусами и кодами")
                .extracting(row -> awaitJournalRow(trail, row.get("event_id")).get("content").toString())
                .satisfiesExactly(first -> assertThat(first).contains("INSTRUMENT").contains(MANUAL),
                        second -> assertThat(second).contains("EXCHANGE_ACCOUNT").contains(RESIDUAL));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E5.1: подъёмов на два больше")
                .isEqualTo(counter(before, "raised_holds") + 2);
        assertThat(counter(after, "hard_raised_holds")).as("E5.1: жёстких на два больше")
                .isEqualTo(counter(before, "hard_raised_holds") + 2);
        assertThat(counter(after, "manually_raised_holds")).as("E5.1: ручных — на один: эскалация ручной тропой не является")
                .isEqualTo(counter(before, "manually_raised_holds") + 1);
    }

    @Test
    @Order(2)
    @DisplayName("E5.2 — Каскад инструментного радиуса идёт после всей счётной реакции эскалации")
    void e5_2_theInstrumentCascadeFollowsTheWholeAccountEscalation() {
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        List<Map<String, Object>> stops = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal);

        assertThat(stops).as("E5.2: строк остановки не больше, чем сделок радиуса, — сделка уведена один раз")
                .hasSize(1);
        Instant pairAt = moment(raised.get(0).get("occurred_at"));
        Instant accountAt = moment(raised.get(1).get("occurred_at"));
        Instant stoppedAt = moment(stops.getFirst().get("occurred_at"));
        assertThat(pairAt).as("E5.2: подъём пары раньше подъёма счёта").isBefore(accountAt);
        assertThat(stoppedAt).as("E5.2: ребро остановки позже подъёма счётной ступени").isAfter(accountAt);
        Instant first = moment(awaitJournalRow(trail, raised.get(0).get("event_id")).get("occurred_at"));
        Instant last = moment(awaitJournalRow(trail, stops.getFirst().get("event_id")).get("occurred_at"));
        List<Map<String, Object>> window = trail.database(Party.AUDIT).query("select event_type, occurred_at "
                + "from audit_records where occurred_at between ? and ? order by occurred_at",
                Timestamp.from(first), Timestamp.from(last));
        assertThat(window).as("E5.2: строк чужих классов между ними нет")
                .allSatisfy(row -> assertThat(REACTION_CLASSES).contains(String.valueOf(row.get("event_type"))));
        assertThat(window.stream().filter(row -> HOLD_RAISED.equals(row.get("event_type"))))
                .as("E5.2: в журнале оба подъёма лежат раньше остановки").hasSize(2);
    }

    @Test
    @Order(3)
    @DisplayName("E5.4 — На счётном радиусе эскалировать некуда: второй реакции нет, отчёт остаётся незакрытым")
    void e5_4_onTheAccountRadiusThereIsNowhereToEscalate() {
        walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        trail.relayCore();
        trail.forgetTraces();

        Answer answer = haltAndAwait(null, KEPT_OPEN);
        trail.relayCore();

        assertThat(answer.status()).as("E5.4: постановка на счёте принята — " + answer.body()).isEqualTo(202);
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E5.4: строка outbox класса подъёма одна").hasSize(1);
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E5.4: ступень счёта — сворачивание")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(trail.database(Party.TRADING_CORE).query("select status from anomaly_reports "
                + "where code = ? and severity = 'CRITICAL'", MANUAL).getFirst().get("status"))
                .as("E5.4: отчёт в промежуточном статусе").isIn("CREATED", "IN_PROGRESS", "KILL_SWITCH_EXECUTED");
        assertThat(trail.exchange().requests(ExitTrail.CLOSE_POSITION))
                .as("E5.4: проход снятия риска один — закрытий не больше предела попыток").isNotEmpty()
                .hasSizeLessThanOrEqualTo(ATTEMPTS);
        assertThat(payload(raised.getFirst()).path("code").asString()).as("E5.4: остатка снятия не эскалировали")
                .isEqualTo(MANUAL);
        awaitJournalRow(trail, raised.getFirst().get("event_id"));
    }

    @Test
    @Order(4)
    @DisplayName("E5.3 — Эскалация на счёте со стоящей счётной ступенью поглощается, и события подъёма нет")
    void e5_3_anEscalationOnAStandingAccountRungIsAbsorbed() {
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();

        haltAndAwait(Trail.INSTRUMENT, ESCALATING);
        trail.relayCore();

        List<Map<String, Object>> rows = outbox(trail, HOLD_RAISED);
        assertThat(rows).as("E5.3: факт подъёма один — пары").hasSize(raised + 1);
        assertThat(payload(rows.getLast()).path("scope").asString()).isEqualTo("INSTRUMENT");
        assertThat(safetyState(trail).path("instrumentInternalIdsWithStandingRung"))
                .as("E5.3: ступень пары переставлена").extracting(JsonNode::asString).contains(Trail.INSTRUMENT);
        assertThat(counter(incidents(trail), "raised_holds")).as("E5.3: подъёмов ровно на один больше")
                .isEqualTo(counter(before, "raised_holds") + 1);
    }

    /**
     * Ручная постановка полного класса и ожидание записи ядра, которой
     * реакция кончается.
     */
    private static Answer haltAndAwait(String instrument, String reactionEnd) {
        Side core = trail.side(Party.TRADING_CORE);
        Long mark = core.logMark();
        Answer answer = halt(trail, "FULL", instrument);
        Trail.await("реакция на постановку кончилась", () -> core.logSince(mark).contains(reactionEnd));
        return answer;
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
