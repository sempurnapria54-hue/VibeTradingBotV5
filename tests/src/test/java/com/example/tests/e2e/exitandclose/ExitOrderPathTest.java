package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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

import static com.example.tests.e2e.exitandclose.ExitTrail.BILLS;
import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ALGOS;
import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ORDER;
import static com.example.tests.e2e.exitandclose.ExitTrail.CLOSE_POSITION;
import static com.example.tests.e2e.exitandclose.ExitTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.exitandclose.ExitTrail.HOLD_RAISED;
import static com.example.tests.e2e.exitandclose.ExitTrail.POSITIONS_HISTORY;
import static com.example.tests.e2e.exitandclose.ExitTrail.bill;
import static com.example.tests.e2e.exitandclose.ExitTrail.closeRecord;
import static com.example.tests.e2e.exitandclose.ExitTrail.coreOutbox;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealFactSeriesStartedYesterday;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.deleteDefinition;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTerminalTranches;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E7} тропы выхода: порядок, отсутствие следов и повтор тропы
 * (.claude/tests/cases/e2e-exit-and-close.md §«E7 — Порядок, отсутствие
 * следов и повтор тропы»).
 *
 * <p><b>Сделок у группы две.</b> {@code E7.1}, {@code E7.3} и {@code E7.4}
 * читают тропу от удаления определения до строки агрегата и повторяют её на
 * тех же базах; {@code E7.2} стои́т на штатной тропе выхода — сменой фазы
 * рынка, без удаления, — потому что её ожидание о владельце определений
 * называет именно эту тропу, и идёт последней на ядре, поднятом заново
 * её прологом.
 *
 * <p><b>Моменты читаются там, где их пишет сторона:</b> журнал стаба
 * площадки — у команд и чтений, строка outbox — у классов событий, отметка
 * приёма — у ядра и журнала, момент сборки — у строки агрегата.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E7 — Порядок, отсутствие следов и повтор тропы")
class ExitOrderPathTest {

    private static final String EXPLORATORY = "exchange-contour.exchanges.OKX.reconciliation-exploratory";

    private static final String DEAL_CLOSED = "DEAL_CLOSED";

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final List<Party> SIDES = List.of(Party.CONNECTOR, Party.TRADING_CORE, Party.STRATEGIES,
            Party.AUDIT, Party.STATISTICS);

    private static final List<String> AGGREGATE_NUMBERS = List.of("closedDeals", "riskBearingDeals",
            "winningDeals", "netResultSum", "feeSum", "fundingSum", "liquidationPenaltySum");

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long REOPENED = 1758240100000L;

    private static final Long RECLOSED = 1758240200000L;

    private static final Long SOURCE_TIME = OPENED + Duration.ofDays(1).toMillis();

    private static final String LAST_BILL = "9003";

    private static Trail trail;

    private static String deal;

    private static String definition;

    private static Object riskBase;

    private static JsonNode aggregate;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x7");
        trail.factSeriesStartedYesterday();
        dealFactSeriesStartedYesterday(trail);
        trail.side(Party.TRADING_CORE).set(EXPLORATORY, "false");
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
    @DisplayName("E7.1 — След стороны не появляется раньше причины на предыдущей")
    void e7_1_noSideTraceAppearsBeforeItsCauseOnThePreviousSide() {
        deal = walkToTerminal(() -> definition = deleteDefinition(trail));
        Map<String, Object> closed = single(coreOutbox(trail, DEAL_CLOSED, deal), "E7.1: терминал");
        Instant terminal = instant(closed.get("occurred_at"));
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E7.1: строка агрегата собрана тактом", () -> nonNull(aggregateRow()));
        aggregate = aggregateRow();
        trail.statisticsRecomputes(NEVER);

        Map<String, Object> deleted = trail.database(Party.STRATEGIES).query("select event_id, occurred_at "
                + "from outbox_events where event_type = 'STRATEGY_DELETED' and payload ->> 'strategyInternalId' = ?",
                definition).getFirst();
        Instant consumed = instant(trail.database(Party.TRADING_CORE).query("select consumed_at from inbox_events "
                + "where event_id = ?", String.valueOf(deleted.get("event_id"))).getFirst().get("consumed_at"));
        assertThat(instant(deleted.get("occurred_at"))).as("E7.1: публикация владельца раньше правки копии у ядра")
                .isBefore(consumed);
        Instant edge = instant(single(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal), "E7.1: остановка")
                .get("occurred_at"));
        Instant cancel = first(CANCEL_ORDER);
        Instant close = first(CLOSE_POSITION);
        assertThat(edge).as("E7.1: ребро статуса сделки раньше первой команды площадке")
                .isBefore(cancel).isBefore(close).isBefore(first(CANCEL_ALGOS));
        assertThat(cancel).as("E7.1: отмена входной ноги раньше закрытия позиции").isBefore(close);
        assertThat(moments(CANCEL_ALGOS)).as("E7.1: закрытие позиции раньше снятия защит транша")
                .allSatisfy(moment -> assertThat(moment).isAfter(close));
        Instant flat = moments(POSITIONS_HISTORY).stream().filter(moment -> moment.isAfter(close)).findFirst()
                .orElseThrow(() -> new IllegalStateException("E7.1: подтверждения плоской позиции нет"));
        List<Instant> bills = moments(BILLS);
        assertThat(bills).as("E7.1: движения добывались").isNotEmpty();
        assertThat(flat).as("E7.1: подтверждение плоской позиции раньше добычи движений").isBefore(bills.getFirst());
        assertThat(bills.getLast()).as("E7.1: движения раньше терминала").isBefore(terminal);
        Map<String, Object> journal = trail.database(Party.AUDIT).query("select recorded_at from audit_records "
                + "where event_id = ?", String.valueOf(closed.get("event_id"))).getFirst();
        assertThat(instant(journal.get("recorded_at"))).as("E7.1: терминал раньше строки журнала").isAfter(terminal);
        assertThat(OffsetDateTime.parse(aggregate.path("assembledAt").asString()).toInstant())
                .as("E7.1: терминал раньше строки агрегата").isAfter(terminal);
    }

    @Test
    @Order(2)
    @DisplayName("E7.3 — Повтор тропы целиком даёт то же состояние")
    void e7_3_replayingTheWholeTrailGivesTheSameState() {
        Database core = trail.database(Party.TRADING_CORE);
        Object dealId = core.query("select id from deals where internal_id = ?", deal).getFirst().get("id");
        Long deals = core.count("deals");
        Integer terminals = coreOutbox(trail, DEAL_CLOSED, deal).size();
        Integer flows = core.query("select id from deal_cash_flows where deal_id = ?", dealId).size();
        Object streak = lossStreak();
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        Long incidentFacts = trail.database(Party.STATISTICS).count("incident_facts");
        for (Party party : SIDES) {
            trail.stop(party);
        }
        for (Party party : SIDES) {
            trail.start(party);
        }
        trail.forgetTraces();

        trail.orchestrate();
        trail.orchestrate();
        trail.relayCore();
        trail.relayOwner();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E7.3: такт пересчёта собрал строку заново", () -> isFalse(Objects.equals(
                aggregate.path("assembledAt").asString(), aggregateRow().path("assembledAt").asString())));
        JsonNode replayed = aggregateRow();
        trail.statisticsRecomputes(NEVER);

        assertThat(core.count("deals")).as("E7.3: второй сделки не заведено").isEqualTo(deals);
        assertThat(coreOutbox(trail, DEAL_CLOSED, deal)).as("E7.3: терминал не переприменён").hasSize(terminals);
        assertThat(core.query("select id from deal_cash_flows where deal_id = ?", dealId))
                .as("E7.3: строк разбивки не прибавилось").hasSize(flows);
        assertThat(lossStreak()).as("E7.3: счётчик серии не двинулся").isEqualTo(streak);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E7.3: строк журнала столько же")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E7.3: сделочных фактов столько же")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E7.3: фактов происшествий столько же")
                .isEqualTo(incidentFacts);
        for (String number : AGGREGATE_NUMBERS) {
            assertThat(replayed.path(number).decimalValue()).as("E7.3: число агрегата " + number + " то же")
                    .isEqualByComparingTo(aggregate.path(number).decimalValue());
        }
        assertThat(commands()).as("E7.3: повторных команд площадке нет").isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("E7.4 — Отсутствие выходов у тропы целиком")
    void e7_4_theWholeTrailLeavesNoOutputsBeyondItsOwn() {
        Database core = trail.database(Party.TRADING_CORE);

        assertThat(core.query("select id from deals where status not in ('CLOSED', 'EMERGENCY_CLOSED')"))
                .as("E7.4: новой сделки на паре нет").isEmpty();
        assertThat(core.query("select id from positions where status = 'ACTIVE'"))
                .as("E7.4: живой позиции у счёта нет").isEmpty();
        assertThat(core.query("select id from orders where external_status = 'live'"))
                .as("E7.4: живых заявок у счёта нет").isEmpty();
        assertThat(core.query("select safety_rung from exchange_accounts").getFirst().get("safety_rung"))
                .as("E7.4: ступеней не поднято").isEqualTo("ACTIVE");
        assertThat(core.query("select id from outbox_events where event_type = ?", HOLD_RAISED))
                .as("E7.4: события подъёма ступени нет").isEmpty();
        assertThat(core.query("select code from anomaly_reports")).as("E7.4: отчётов нет").isEmpty();
        for (Party party : List.of(Party.AUDIT, Party.STATISTICS)) {
            assertThat(trail.database(party).count("access_denials"))
                    .as("E7.4: строк отказа доступа у " + party.module() + " нет").isZero();
        }
        assertThat(core.query("select risk_base from exchange_accounts").getFirst().get("risk_base"))
                .as("E7.4: база риска счёта не двигалась").isEqualTo(riskBase);
    }

    @Test
    @Order(4)
    @DisplayName("E7.2 — Сторона вне тропы следа не оставляет ни одного")
    void e7_2_aSideOffTheTrailLeavesNoTrace() {
        List<Long> owner = new ArrayList<>();
        String second = walkToTerminal(() -> {
            owner.add(trail.database(Party.STRATEGIES).count("strategies"));
            owner.add(trail.database(Party.STRATEGIES).count("outbox_events"));
            trail.marketPhaseIs("BEAR_TREND");
        });
        Integer passes = passes();
        Integer reads = trail.marketData().requests(Trail.PEER_FEATURES).size();

        trail.orchestrate();
        trail.orchestrate();

        assertThat(dealRead(trail, second).path("status").asString()).as("предусловие E7.2: терминал штатный")
                .isEqualTo("CLOSED");
        assertThat(reads).as("E7.2: к стабу рыночных данных — ровно одно чтение раскладки на тик прохода")
                .isPositive().isEqualTo(passes);
        assertThat(trail.marketData().requests(Trail.PEER_FEATURES))
                .as("E7.2: и ни одного сверх того — у терминальной сделки прохода нет").hasSize(reads);
        assertThat(trail.marketData().requests().stream()
                .filter(request -> isFalse(request.getUrl().startsWith(Trail.PEER_FEATURES)))
                .map(LoggedRequest::getUrl)
                .toList()).as("E7.2: ни спроса каталога, ни правил инструмента").isEmpty();
        assertThat(trail.auth().requests()).as("E7.2: к стабу владельца реестра запросов нет").isEmpty();
        assertThat(List.of(trail.database(Party.STRATEGIES).count("strategies"),
                trail.database(Party.STRATEGIES).count("outbox_events")))
                .as("E7.2: у владельца определений на тропе выхода строк не прибавилось").isEqualTo(owner);
        String token = URI.create(trail.identity().tokenUri()).getPath();
        assertThat(trail.identity().requests()).as("E7.2: к провайдеру идентичности — только выдача токенов")
                .allSatisfy(request -> assertThat(request.getUrl()).startsWith(token));
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Сделка по названной инициативе доведена до добытых движений, база
     * риска счёта отмечена, затем проходы — до терминала.
     *
     * @param initiative ход, уводящий сделку из штатного ведения
     * @return идентичность сделки
     */
    private static String walkToTerminal(Runnable initiative) {
        String walked = walkToTerminalTranches(trail, initiative, String.join(", ",
                        closeRecord(OPENED, CLOSED, "1.0", "-0.2", "-0.05", "0.75"),
                        closeRecord(REOPENED, RECLOSED, "0.5", "-0.1", "0", "0.4")),
                SOURCE_TIME, LAST_BILL, String.join(", ",
                        bill("9005", Trail.EXTERNAL_INSTRUMENT, "8", "173", "USDT", "-0.05", "0", CLOSED + 1000),
                        bill("9004", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", CLOSED),
                        bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.4", "-0.1", RECLOSED)));
        riskBase = trail.database(Party.TRADING_CORE).query("select risk_base from exchange_accounts").getFirst()
                .get("risk_base");
        trail.passUntil("сделка ушла в терминал", () -> isFalse(Objects.equals("EXIT_PENDING",
                dealRead(trail, walked).path("status").asString())));
        trail.relayCore();
        return walked;
    }

    /** Проходы сопровождения после последнего забывания — по журналу доступа ядра. */
    private static Integer passes() {
        return (int) trail.accesses(Party.TRADING_CORE).stream()
                .filter(access -> Objects.equals("POST", access.method()))
                .filter(access -> access.uri().endsWith("/jobs/deal-orchestrator"))
                .count();
    }

    private static JsonNode aggregateRow() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Answer answer = trail.call(Party.STATISTICS, "GET", ROWS + "?grain=DEAL&from=" + day + "&to=" + day,
                trail.tenant(), null);
        assertThat(answer.status()).as("чтение агрегатов — " + answer.body()).isEqualTo(200);
        List<JsonNode> rows = new ArrayList<>();
        Json.tree(answer.body()).path("dealRows").forEach(rows::add);
        return rows.stream()
                .filter(row -> Objects.equals(definition, row.path("strategyInternalId").asString()))
                .findFirst()
                .orElse(null);
    }

    /** Моменты принятых стабом площадки запросов по пути — в порядке прихода. */
    private static List<Instant> moments(String path) {
        return trail.exchange().requests(path).stream()
                .map(request -> request.getLoggedDate().toInstant())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static Instant first(String path) {
        List<Instant> moments = moments(path);
        assertThat(moments).as("запросы стаба площадки по " + path).isNotEmpty();
        return moments.getFirst();
    }

    private static Object lossStreak() {
        return trail.database(Party.TRADING_CORE).query("select consecutive_loss_count from exchange_accounts")
                .getFirst().get("consecutive_loss_count");
    }

    /** Команды площадке после последнего забывания: всё, что не чтение. */
    private static List<String> commands() {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(LoggedRequest::getUrl)
                .toList();
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows, String label) {
        assertThat(rows).as(label + " — одна строка outbox").hasSize(1);
        return rows.getFirst();
    }

    private static Instant instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((OffsetDateTime) column).toInstant();
    }
}
