package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
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
import static com.example.tests.e2e.safetyteardown.TeardownTrail.ORDERS_PENDING;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.commands;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealOutbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealStatus;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsForeignOrder;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.reports;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.sliceReads;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToScaledIn;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E8} тропы снятия риска: порядок, отсутствие следов и повтор
 * тропы (.claude/tests/cases/e2e-safety-teardown.md §«E8 — Порядок,
 * отсутствие следов и повтор тропы»).
 *
 * <p><b>Ядро одно, тропа одна.</b> Пролог с добором
 * ({@link TeardownTrail#walkToScaledIn}) даёт снятию риска все три звена
 * порядка — входную ногу, позицию и защиту; дальше автоматическая тропа идёт
 * от {@code E1.1} до аварийного терминала сделки, и журнал стаба площадки не
 * забывается от расхождения до конца каскада. Проходы сопровождения подаются
 * по одному: число чтений раскладки сверяется с числом построений контекста.
 * {@code E8.4} идёт раньше повтора {@code E8.3}, чьё состояние после себя
 * оставляет.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E8 — Порядок, отсутствие следов и повтор тропы")
class TeardownTracePathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final Integer PASS_LIMIT = 30;

    private static final List<String> LIVE_ORDER = List.of("CREATED", "PENDING", "ACTIVE", "PARTIALLY_COMPLETED");

    private static Trail trail;

    private static String deal;

    private static List<LoggedRequest> exchangeJournal;

    private static Long strategyOutbox;

    private static List<Map<String, Object>> strategyStatuses;

    private static Integer observationFeatureReads;

    private static Integer reactionFeatureReads;

    private static Integer passes;

    private static Integer passFeatureReads;

    private static Integer lossCount;

    @BeforeAll
    static void walkTheTrail() {
        trail = Trail.open("t8");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
        trail.renew(Party.TRADING_CORE);
        deal = walkToScaledIn(trail);
        trail.relayCore();
        strategyOutbox = trail.database(Party.STRATEGIES).count("outbox_events");
        strategyStatuses = trail.database(Party.STRATEGIES).query("select internal_id, status from strategies "
                + "order by id");
        lossCount = safetyState(trail).path("consecutiveLossCount").asInt();
        trail.forgetTraces();

        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();
        observationFeatureReads = trail.marketData().requests(Trail.PEER_FEATURES).size();
        trail.marketData().forgetRequests();
        detect(trail);
        trail.relayCore();
        exchangeJournal = trail.exchange().requests();
        reactionFeatureReads = trail.marketData().requests(Trail.PEER_FEATURES).size();
        trail.marketData().forgetRequests();
        ExitTrail.exchangeKeepsBills(trail, System.currentTimeMillis(), "0", "");
        passes = 0;
        while (isFalse(Objects.equals("EMERGENCY_CLOSED", dealStatus(trail, deal))) && passes < PASS_LIMIT) {
            trail.orchestrate();
            passes++;
        }
        passFeatureReads = trail.marketData().requests(Trail.PEER_FEATURES).size();
        trail.relayCore();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E8.1 — След стороны не появляется раньше причины на предыдущей")
    void e8_1_noSideTraceAppearsBeforeItsCauseOnThePreviousSide() {
        Database core = trail.database(Party.TRADING_CORE);
        List<Map<String, Object>> rows = reports(trail, FOREIGN_ORDER);
        assertThat(rows).as("предусловие E8.1: строки наблюдения и подтверждения").hasSize(2);
        Instant observed = moment(rows.getFirst().get("created_at"));
        Instant confirmed = moment(rows.getLast().get("created_at"));
        Map<String, Object> critical = core.query("select modified_at from anomaly_reports "
                + "where code = ? and severity = 'CRITICAL'", FOREIGN_ORDER).getFirst();
        Instant reportTerminal = moment(critical.get("modified_at"));
        Instant raised = moment(outbox(trail, HOLD_RAISED).getFirst().get("occurred_at"));
        Map<String, Object> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal).getFirst();
        Instant edge = moment(shutdown.get("occurred_at"));

        Instant divergence = at(exchangeJournal.stream()
                .filter(request -> Objects.equals(ORDERS_PENDING, path(request))).findFirst().orElseThrow());
        Integer cancelLeg = first(ExitTrail.CANCEL_ORDER, ExitTrail.SECOND_ORDER);
        Integer close = first(ExitTrail.CLOSE_POSITION, Trail.EXTERNAL_INSTRUMENT);
        Integer protections = first(ExitTrail.CANCEL_ALGOS, "");
        Instant firstCommand = at(exchangeJournal.stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .findFirst().orElseThrow());
        LoggedRequest flatRead = exchangeJournal.subList(close + 1, exchangeJournal.size()).stream()
                .filter(request -> Objects.equals(Trail.EXCHANGE_POSITIONS, path(request)))
                .findFirst().orElseThrow();

        assertThat(divergence).as("E8.1: расхождение в срезе стаба раньше первой строки отчёта").isBefore(observed);
        assertThat(observed).as("E8.1: строка наблюдения раньше подтверждения").isBefore(confirmed);
        assertThat(raised).as("E8.1: ребро подъёма ступени раньше открытия критичного отчёта").isBefore(confirmed);
        assertThat(confirmed).as("E8.1: открытие отчёта раньше первой команды снятия риска").isBefore(firstCommand);
        assertThat(cancelLeg).as("E8.1: отмена входной ноги раньше закрытия позиции").isNotNegative()
                .isLessThan(close);
        assertThat(protections).as("E8.1: закрытие раньше снятия защит").isGreaterThan(close);
        assertThat(at(flatRead)).as("E8.1: подтверждение плоского среза раньше терминала отчёта")
                .isBefore(reportTerminal);
        assertThat(reportTerminal).as("E8.1: терминал отчёта раньше первого ребра каскада").isBefore(edge);
        Map<String, Object> journal = awaitJournalRow(trail, shutdown.get("event_id"));
        assertThat(moment(journal.get("recorded_at"))).as("E8.1: ребро каскада раньше строки журнала о нём")
                .isAfter(edge);
        Map<String, Object> grain = incidents(trail);
        Map<String, Object> fact = trail.database(Party.STATISTICS).query("select occurred_at from incident_facts "
                + "where event_id = ?", String.valueOf(outbox(trail, HOLD_RAISED).getFirst().get("event_id")))
                .getFirst();
        assertThat(moment(grain.get("assembled_at"))).as("E8.1: факт раньше строки агрегата")
                .isAfter(moment(fact.get("occurred_at")));
    }

    @Test
    @Order(2)
    @DisplayName("E8.2 — Сторона вне тропы следа не оставляет ни одного")
    void e8_2_aSideOutsideTheTrailLeavesNoTrace() {
        assertThat(trail.database(Party.STRATEGIES).count("outbox_events"))
                .as("E8.2: у strategies строк outbox не прибавилось").isEqualTo(strategyOutbox);
        assertThat(trail.database(Party.STRATEGIES).query("select internal_id, status from strategies order by id"))
                .as("E8.2: определение аварией не трогается").isEqualTo(strategyStatuses);
        assertThat(observationFeatureReads).as("E8.2: тик наблюдения — одно чтение раскладки: детекторы "
                + "инварианта сделки строят контекст по нетерминальной сделке").isEqualTo(1);
        assertThat(reactionFeatureReads).as("E8.2: тик реакции — два: детекторы инварианта и счёт-широкое "
                + "снятие риска, по одному на нетерминальную сделку").isEqualTo(2);
        assertThat(passes).as("предусловие E8.2: сделка дошла до терминала проходами").isLessThan(PASS_LIMIT);
        assertThat(passFeatureReads).as("E8.2: по чтению раскладки на каждый проход сопровождения — и ни одного сверх")
                .isEqualTo(passes);
        assertThat(trail.auth().requests()).as("E8.2: к auth запросов сверх пролога нет").isEmpty();
        assertThat(trail.identity().requests()).as("E8.2: к провайдеру идентичности — только выдача токенов и ключей")
                .allSatisfy(request -> assertThat(request.getUrl())
                        .containsAnyOf("token", "jwks", "certs", ".well-known"));
    }

    @Test
    @Order(3)
    @DisplayName("E8.4 — Отсутствие выходов у тропы целиком")
    void e8_4_theWholeTrailHasNoExits() {
        Database core = trail.database(Party.TRADING_CORE);
        Long deals = core.count("deals");
        trail.marketFavoursEntry();
        trail.forgetTraces();

        trail.scanEntries();
        trail.orchestrate();
        trail.relayCore();

        assertThat(core.count("deals")).as("E8.4: новых сделок на счёте нет — счёт выпал из выборки входа")
                .isEqualTo(deals);
        assertThat(core.query("select status from orders").stream()
                .filter(row -> LIVE_ORDER.contains(String.valueOf(row.get("status")))))
                .as("E8.4: живых заявок контура у счёта нет").isEmpty();
        assertThat(core.query("select status from positions"))
                .as("E8.4: живой позиции нет — эпизод закрыт").extracting(row -> row.get("status"))
                .containsOnly("CLOSED");
        assertThat(commands(trail)).as("E8.4: ни одной торговой команды после снятия риска к стабу не ушло")
                .isEmpty();
        assertThat(trail.database(Party.AUDIT).count("access_denials")).as("E8.4: строк отказа доступа у audit нет")
                .isZero();
        assertThat(trail.database(Party.STATISTICS).count("access_denials"))
                .as("E8.4: строк отказа доступа у statistics нет").isZero();
        assertThat(safetyState(trail).path("consecutiveLossCount").asInt()).as("E8.4: серия убытков не двигалась")
                .isEqualTo(lossCount);
    }

    /**
     * Красна ожиданием «ступень одна» и следующими за ним — долг
     * доминирования биржевых ступеней
     * (.claude/work/backlog.md §«Доминирование биржевых ступеней над
     * инструментными реакциями энфорсера не имеет»): после терминала сделки
     * чужая заявка сиротская, и мягкий сигнал пары поднимает её ступень под
     * стоящим сворачиванием счёта, вместо того чтобы поглотиться.
     */
    @Test
    @Order(4)
    @Tag("debt")
    @DisplayName("E8.3 — Повтор тропы целиком даёт то же состояние")
    void e8_3_aRepeatOfTheWholeTrailGivesTheSameState() {
        Database core = trail.database(Party.TRADING_CORE);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        Integer reportRows = reports(trail, FOREIGN_ORDER).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        Integer stops = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal).size();
        Integer terminals = dealOutbox(trail, DEAL_CLOSED, deal).size();
        Map<String, Object> grain = incidents(trail);
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        Long dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        for (Party party : List.of(Party.TRADING_CORE, Party.AUDIT, Party.STATISTICS)) {
            trail.stop(party);
            trail.start(party);
        }
        trail.forgetTraces();

        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();
        detect(trail);
        trail.relayCore();
        trail.orchestrate();
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E8.3: ступень счёта та же")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(safetyState(trail).path("instrumentInternalIdsWithStandingRung")).as("E8.3: и одна").isEmpty();
        assertThat(outbox(trail, HOLD_RAISED)).as("E8.3: перестановки не было").hasSize(raised);
        assertThat(reports(trail, FOREIGN_ORDER)).as("E8.3: вторых строк отчёта по тем же ключам нет")
                .hasSize(reportRows);
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E8.3: событий отчёта не прибавилось").hasSize(reported);
        assertThat(dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal)).as("E8.3: каскад не переприменён")
                .hasSize(stops);
        assertThat(dealOutbox(trail, DEAL_CLOSED, deal)).as("E8.3: терминал не переприменён").hasSize(terminals);
        assertThat(sliceReads(trail)).as("E8.3: повторные чтения срезов у стаба есть").isNotEmpty();
        assertThat(commands(trail)).as("E8.3: повторных команд нет").isEmpty();
        assertThat(core.query("select id from outbox_events where published_at is null"))
                .as("предусловие E8.3: всё опубликовано").isEmpty();
        Map<String, Object> after = incidents(trail);
        for (String column : List.of("raised_holds", "anomaly_reports", "critical_anomaly_reports")) {
            assertThat(counter(after, column)).as("E8.3: число агрегата " + column + " то же")
                    .isEqualTo(counter(grain, column));
        }
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E8.3: строк журнала столько же")
                .isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E8.3: фактов столько же")
                .isEqualTo(facts);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E8.3: сделочных фактов столько же")
                .isEqualTo(dealFacts);
    }

    /** Индекс первого запроса журнала стаба по пути, чьё тело несёт значение. */
    private static Integer first(String path, String bodyValue) {
        for (int index = 0; index < exchangeJournal.size(); index++) {
            LoggedRequest request = exchangeJournal.get(index);
            if (Objects.equals(path, path(request)) && request.getBodyAsString().contains(bodyValue)) {
                return index;
            }
        }
        return -1;
    }

    private static String path(LoggedRequest request) {
        return request.getUrl().split("\\?")[0];
    }

    private static Instant at(LoggedRequest request) {
        return request.getLoggedDate().toInstant();
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
