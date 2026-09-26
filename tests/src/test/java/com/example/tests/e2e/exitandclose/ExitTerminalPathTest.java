package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
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
import static com.example.tests.e2e.exitandclose.ExitTrail.BILLS_ARCHIVE;
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
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeCancelsUnfilledEntry;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeKeepsBills;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeMirrorsClose;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeTriggersAttachedStop;
import static com.example.tests.e2e.exitandclose.ExitTrail.expiringFirstTrancheDefinition;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeMirrorsProtection;
import static com.example.tests.e2e.exitandclose.ExitTrail.filledSize;
import static com.example.tests.e2e.exitandclose.ExitTrail.passUntilLeaves;
import static com.example.tests.e2e.exitandclose.ExitTrail.raiseHalt;
import static com.example.tests.e2e.exitandclose.ExitTrail.riskAppetiteIs;
import static com.example.tests.e2e.exitandclose.ExitTrail.standAtExposure;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToFilledEntry;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToRecoveredDeal;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToSubmittedEntry;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTerminalTranches;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTwoTranches;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E5} тропы выхода: терминал сделки и его событие
 * (.claude/tests/cases/e2e-exit-and-close.md §«E5 — Терминал сделки и его
 * событие»).
 *
 * <p><b>Сделок у группы шесть, и каждая идёт на ядре, поднятом заново своим
 * прологом:</b> {@code E5.1}, {@code E5.2} и {@code E5.5} стоят на штатном
 * терминале одной сделки; {@code E5.4} заводит вторую — с ценовым убытком
 * и порогом серии, равным счётчику после её терминала; {@code E5.3} —
 * третью, уведённую жёсткой ступенью из координированного выхода;
 * {@code E5.6} — четвёртую, уведённую удалением определения до налива
 * входной ноги; {@code E5.7} — пятую, двух траншей, закрытых своими
 * инициаторами; {@code E5.8} — шестую, заведённую восстановлением по
 * позиции, закрытой вне приложения до первого прохода.
 *
 * <p><b>Режим допуска — боевой</b>, как у {@code E4.1}, чьё состояние
 * {@code E5.1} называет предусловием; время площадки на добыче движений —
 * сутки после начала окна, тем же доводом, что у группы {@code E4}.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E5 — Терминал сделки и его событие")
class ExitTerminalPathTest {

    private static final String EXPLORATORY = "exchange-contour.exchanges.OKX.reconciliation-exploratory";

    private static final String DEAL_CLOSED = "DEAL_CLOSED";

    private static final String ANOMALY_REPORTED = "ANOMALY_REPORTED";

    private static final String LOSS_STREAK = "LOSS_STREAK_LIMIT_REACHED";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final List<String> FEATURES = List.of("close_outcome", "reconciliation_status",
            "breakdown_incomplete", "risk_benchmark_availability");

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long REOPENED = 1758240100000L;

    private static final Long RECLOSED = 1758240200000L;

    private static final Long SOURCE_TIME = OPENED + Duration.ofDays(1).toMillis();

    private static final String LAST_BILL = "9003";

    private static Trail trail;

    private static String deal;

    private static Object dealId;

    private static String definition;

    private static Long dealFacts;

    private static Map<String, Object> closed;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x5");
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
    @DisplayName("E5.1 — Штатный терминал: статус, причина по маршруту, четыре признака, событие закрытия")
    void e5_1_theCleanTerminalCarriesTheRouteReasonFourFeaturesAndTheEvent() {
        walkToTerminal(String.join(", ",
                        closeRecord(OPENED, CLOSED, "1.0", "-0.2", "-0.05", "0.75"),
                        closeRecord(REOPENED, RECLOSED, "0.5", "-0.1", "0", "0.4")),
                String.join(", ",
                        bill("9005", Trail.EXTERNAL_INSTRUMENT, "8", "173", "USDT", "-0.05", "0", CLOSED + 1000),
                        bill("9004", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", CLOSED),
                        bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.4", "-0.1", RECLOSED)),
                4);

        Map<String, Object> row = dealRow();
        assertThat(dealRead(trail, deal).path("status").asString()).as("E5.1: статус — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(row.get("close_reason")).as("E5.1: причина — плановый выход с ребра шага EXIT, не переписана")
                .isEqualTo("STRATEGY_EXIT");
        for (String feature : FEATURES) {
            assertThat(row.get(feature)).as("E5.1: признак отбора " + feature + " стои́т").isNotNull();
        }
        assertThat(liveSystemRows()).as("E5.1: живые системные строки исполнения сделки закрыты").isEmpty();
        closed = single(outbox(DEAL_CLOSED), "E5.1: терминал");
        JsonNode content = Json.tree(String.valueOf(closed.get("payload")));
        assertThat(content.path("status").asString()).as("E5.1: событие — тот же статус").isEqualTo("CLOSED");
        assertThat(content.path("closeReason").asString()).as("E5.1: та же причина").isEqualTo("STRATEGY_EXIT");
        assertThat(content.path("closeOutcome").asString()).as("E5.1: тот же торговый исход")
                .isEqualTo(row.get("close_outcome"));
        assertThat(content.path("reconciliationStatus").asString()).as("E5.1: то же состояние сверки")
                .isEqualTo(row.get("reconciliation_status"));
        assertThat(content.path("breakdownIncomplete").asString()).as("E5.1: та же полнота разбивки")
                .isEqualTo(row.get("breakdown_incomplete"));
        assertThat(content.path("riskBenchmarkAvailability").asString()).as("E5.1: та же доступность знаменателя")
                .isEqualTo(row.get("risk_benchmark_availability"));
        assertThat(commands()).as("E5.1: новых команд площадке нет").isEmpty();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(closed).get("content")));
        assertThat(recorded.path("dealInternalId").asString()).as("E5.1: строка журнала — о сделке")
                .isEqualTo(deal);
        assertThat(recorded.path("exchangeAccountInternalId").asString()).as("E5.1: со счётом")
                .isEqualTo(trail.account());
        assertThat(recorded.path("instrumentInternalId").asString()).as("E5.1: с инструментом")
                .isEqualTo(Trail.INSTRUMENT);
        assertThat(recorded.path("strategyInternalId").asString()).as("E5.1: и с определением")
                .isEqualTo(definition);
        awaitDealFact(closed);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E5.1: сделочный факт один")
                .isEqualTo(dealFacts + 1);
    }

    @Test
    @Order(2)
    @DisplayName("E5.2 — Содержимое едет числами: издержки положительные, идентичности — строками")
    void e5_2_theContentTravelsAsNumbersWithPositiveCostsAndStringIdentities() {
        String eventId = String.valueOf(closed.get("event_id"));
        List<ConsumerRecord<String, String>> records = trail.records(Substrate.CORE_TOPIC).stream()
                .filter(record -> Objects.equals(eventId, header(record, "eventId")))
                .toList();

        assertThat(records).as("E5.2: в теме — одна запись события закрытия").hasSize(1);
        JsonNode content = Json.tree(records.getFirst().value());
        Map<String, String> identities = Map.of("dealInternalId", deal,
                "exchangeAccountInternalId", trail.account(),
                "instrumentInternalId", Trail.INSTRUMENT,
                "strategyInternalId", definition);
        identities.forEach((field, value) -> {
            assertThat(content.path(field).isString()).as("E5.2: идентичность " + field + " — строкой").isTrue();
            assertThat(content.path(field).asString()).as("E5.2: идентичность " + field).isEqualTo(value);
        });
        for (String field : List.of("result", "fee", "funding", "liquidationPenalty", "plannedRisk")) {
            assertThat(content.path(field).isNumber()).as("E5.2: " + field + " — числом").isTrue();
        }
        assertThat(content.path("result").decimalValue()).as("E5.2: итог — сумма net эпизодов")
                .isEqualByComparingTo("1.15");
        assertThat(content.path("fee").decimalValue()).as("E5.2: комиссия — издержкой, положительной")
                .isEqualByComparingTo("0.3");
        assertThat(content.path("funding").decimalValue()).as("E5.2: финансирование — издержкой, положительным")
                .isEqualByComparingTo("0.05");
        assertThat(content.path("liquidationPenalty").decimalValue()).as("E5.2: штраф — нулём")
                .isEqualByComparingTo("0");
        for (String field : List.of("tookRisk", "graphComplete")) {
            assertThat(content.path(field).isBoolean()).as("E5.2: " + field + " — булевым").isTrue();
            assertThat(content.path(field).asBoolean()).as("E5.2: " + field + " — истинен").isTrue();
        }
        Map<String, Object> fact = trail.database(Party.STATISTICS).query("select net_result, fee, funding, "
                + "liquidation_penalty from deal_facts where event_id = ?", eventId).getFirst();
        assertThat((BigDecimal) fact.get("net_result")).as("E5.2: у статистики итог тот же")
                .isEqualByComparingTo(content.path("result").decimalValue());
        assertThat((BigDecimal) fact.get("fee")).as("E5.2: комиссия — без пересчёта знака")
                .isEqualByComparingTo(content.path("fee").decimalValue());
        assertThat((BigDecimal) fact.get("funding")).as("E5.2: финансирование — без пересчёта знака")
                .isEqualByComparingTo(content.path("funding").decimalValue());
        assertThat((BigDecimal) fact.get("liquidation_penalty")).as("E5.2: штраф — тот же")
                .isEqualByComparingTo(content.path("liquidationPenalty").decimalValue());
        assertThat(Json.tree(String.valueOf(awaitJournal(closed).get("content"))))
                .as("E5.2: строка журнала несёт то же содержимое").isEqualTo(content);
    }

    @Test
    @Order(3)
    @DisplayName("E5.5 — Терминал применяется один раз: повтор прохода второго события не производит")
    void e5_5_theTerminalAppliesOnceAndARepeatedPassProducesNoSecondEvent() {
        Database core = trail.database(Party.TRADING_CORE);
        Integer systemRows = systemRows().size();
        Object streak = lossStreak();
        Long facts = trail.database(Party.STATISTICS).count("deal_facts");
        trail.forgetTraces();

        trail.orchestrate();
        trail.orchestrate();
        trail.relayCore();

        assertThat(systemRows().size()).as("E5.5: терминальная сделка в выборку прохода не попала")
                .isEqualTo(systemRows);
        assertThat(outbox(DEAL_CLOSED)).as("E5.5: второй строки outbox класса терминала нет").hasSize(1);
        assertThat(lossStreak()).as("E5.5: счётчик серии второй раз не двинулся").isEqualTo(streak);
        assertThat(commands()).as("E5.5: команд по этой сделке нет").isEmpty();
        assertThat(core.query("select status from deals where id = ?", dealId).getFirst().get("status"))
                .as("E5.5: статус тот же").isEqualTo("CLOSED");
        assertThat(trail.database(Party.AUDIT).query("select id from audit_records where event_type = ? "
                + "and deal_internal_id = ?", DEAL_CLOSED, deal)).as("E5.5: строка журнала о терминале одна")
                .hasSize(1);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E5.5: сделочный факт один")
                .isEqualTo(facts);
    }

    @Test
    @Order(4)
    @DisplayName("E5.4 — Счётчик серии убытков двигается терминалом, и порог поднимает ступень")
    void e5_4_theTerminalMovesTheLossStreakAndTheLimitRaisesARung() {
        walkToTerminal(String.join(", ",
                        closeRecord(OPENED, CLOSED, "-1.0", "-0.2", "0", "-1.2"),
                        closeRecord(REOPENED, RECLOSED, "-0.5", "-0.1", "0", "-0.6")),
                String.join(", ",
                        bill("9004", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "-1.2", "-0.2", CLOSED),
                        bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "-0.6", "-0.1", RECLOSED)),
                1);

        Database core = trail.database(Party.TRADING_CORE);
        assertThat(dealRead(trail, deal).path("status").asString()).as("E5.4: терминал применён")
                .isEqualTo("CLOSED");
        assertThat(lossStreak()).as("E5.4: счётчик серии вырос на единицу").isEqualTo(1);
        assertThat(core.query("select safety_rung from exchange_accounts").getFirst().get("safety_rung"))
                .as("E5.4: порог достигнут — поднята мягкая ступень счёта").isEqualTo("HOLD");
        Map<String, Object> terminal = single(outbox(DEAL_CLOSED), "E5.4: терминал");
        Map<String, Object> hold = single(outbox(HOLD_RAISED), "E5.4: подъём ступени");
        assertThat(hold.get("payload").toString()).as("E5.4: ступень — с машинным кодом серии").contains(LOSS_STREAK);
        assertThat(instant(hold.get("occurred_at"))).as("E5.4: ступень поднята после коммита терминала")
                .isAfter(instant(terminal.get("occurred_at")));
        assertThat(core.query("select code from anomaly_reports")).as("E5.4: заведён отчёт о происшествии")
                .extracting(report -> report.get("code")).contains(LOSS_STREAK);
        Map<String, Object> reported = outbox(ANOMALY_REPORTED).stream()
                .filter(event -> event.get("payload").toString().contains(LOSS_STREAK))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("E5.4: события отчёта о серии нет"));
        assertThat(commands()).as("E5.4: команд площадке нет").isEmpty();
        for (Map<String, Object> event : List.of(terminal, reported, hold)) {
            awaitJournal(event);
        }
        awaitDealFact(terminal);
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("deal_facts")).as("E5.4: сделочный факт один").isEqualTo(dealFacts + 1);
        Trail.await("E5.4: факты отчёта и подъёма приняты — счётчики выросли", () -> statistics.query(
                "select event_id from incident_facts where event_id in (?, ?)",
                String.valueOf(reported.get("event_id")), String.valueOf(hold.get("event_id"))).size() == 2);
    }

    /**
     * Сделка состояния {@code E1.4}: определение удалено у владельца, сделка
     * ушла в координированный выход с причиной, а жёсткая ступень счёта
     * увела её в ошибочное состояние раньше, чем выход снял риск. Снимает
     * риск аварийное снятие ступени; площадка отражает его сценариями.
     *
     * <p>Красна по находке {@code F10} документа: аварийный обработчик пишет
     * причину закрытия, только когда она пуста, и сделка несёт причину
     * выхода, а дом ребра — «аварийное закрытие»
     * (.claude/work/backlog.md §«Аварийный терминал оставляет причину
     * закрытия, записанную ребром выхода»). Ассерт причины стои́т последним:
     * прочие выходы клетки прогон с меткой проверяет до него.
     */
    @Test
    @Order(5)
    @Tag("debt")
    @DisplayName("E5.3 — Аварийный терминал: число «неисчислимо» едет пустым, и исход у события другой")
    void e5_3_theEmergencyTerminalCarriesTheUncomputableNumberAsAbsent() {
        trail.exchange().forgetScenarios();
        deal = walkToFilledEntry(trail);
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        exchangeMirrorsProtection(trail, filledSize(trail));
        exchangeMirrorsClose(trail);
        standAtExposure(trail, deal);
        trail.relayCore();
        definition = deleteDefinition(trail);
        passUntilLeaves(trail, deal, "ACTIVE");
        Answer raised = raiseHalt(trail, "FULL");
        assertThat(raised.status()).as("предусловие E5.3: жёсткая ступень поднята — " + raised.body())
                .isEqualTo(202);
        trail.passUntil("сделка в ошибочном состоянии", () -> Objects.equals("ERROR",
                dealRead(trail, deal).path("status").asString()));
        trail.relayCore();
        Long facts = trail.database(Party.STATISTICS).count("deal_facts");
        trail.forgetTraces();

        trail.passUntil("аварийный терминал", () -> Objects.equals("EMERGENCY_CLOSED",
                dealRead(trail, deal).path("status").asString()));
        trail.relayCore();

        List<LoggedRequest> reads = trail.exchange().requests().stream()
                .filter(request -> Objects.equals("GET", request.getMethod().getName()))
                .toList();
        assertThat(reads).as("E5.3: к стабу ушли чтения, подтверждающие отсутствие живого риска").isNotEmpty();
        assertThat(trail.exchange().requests(BILLS)).as("E5.3: чтения движений нет").isEmpty();
        assertThat(trail.exchange().requests(BILLS_ARCHIVE)).as("E5.3: и архива движений нет").isEmpty();
        Map<String, Object> row = dealRow();
        assertThat(row.get("result_profit")).as("E5.3: итог пуст, не ноль").isNull();
        assertThat(row.get("reconciliation_status")).as("E5.3: признак сверки — «не гонялась», а не пустота")
                .isEqualTo("NOT_RUN");
        Map<String, Object> terminal = single(outbox(DEAL_CLOSED), "E5.3: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("status").asString()).as("E5.3: исход у события — аварийный терминал")
                .isEqualTo("EMERGENCY_CLOSED");
        assertThat(content.path("result").isNumber() || content.path("result").isString())
                .as("E5.3: число едет отсутствующим — не нулём и не строкой").isFalse();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("status").asString()).as("E5.3: строка журнала — с аварийным исходом")
                .isEqualTo("EMERGENCY_CLOSED");
        awaitDealFact(terminal);
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("deal_facts")).as("E5.3: сделочный факт один").isEqualTo(facts + 1);
        assertThat(statistics.query("select net_result from deal_facts where event_id = ?",
                String.valueOf(terminal.get("event_id"))).getFirst().get("net_result"))
                .as("E5.3: сделочный факт с пустым результатом").isNull();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E5.3: сделка попала в класс «результат недоступен»", () -> ((Number) statistics.query(
                "select coalesce(sum(result_unavailable_deals), 0) as unavailable from deal_aggregates "
                        + "where tenant_id = ? and bucket_date = ?", trail.tenant(), LocalDate.now(ZoneOffset.UTC))
                .getFirst().get("unavailable")).intValue() == 1);
        trail.statisticsRecomputes(NEVER);
        assertThat(dealRead(trail, deal).path("status").asString()).as("E5.3: статус — аварийный терминал")
                .isEqualTo("EMERGENCY_CLOSED");
        assertThat(row.get("close_reason")).as("E5.3: причина закрытия — аварийная, хотя стоя́ла с ребра выхода")
                .isEqualTo("EMERGENCY_CLOSE");
    }

    @Test
    @Order(6)
    @DisplayName("E5.6 — Сделка, уведённая в выход до налива входной ноги, закрывается нулём без добычи")
    void e5_6_aDealCollapsedBeforeItsEntryFilledClosesAtZeroWithoutHarvest() {
        trail.exchange().forgetScenarios();
        deal = walkToSubmittedEntry(trail);
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        exchangeCancelsUnfilledEntry(trail);
        definition = deleteDefinition(trail);
        trail.forgetTraces();

        trail.passUntil("E5.6: сделка дошла до терминала", () -> List.of("CLOSED", "EMERGENCY_CLOSED")
                .contains(dealRead(trail, deal).path("status").asString()));
        trail.relayCore();

        assertThat(dealRead(trail, deal).path("status").asString()).as("E5.6: терминал штатный")
                .isEqualTo("CLOSED");
        assertThat(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal))
                .as("E5.6: сделка ушла в координированный выход ребром остановки").hasSize(1);
        assertThat(trail.exchange().requests(CANCEL_ORDER)).as("E5.6: отмена входной ноги ушла одна").hasSize(1);
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E5.6: закрытия позиции нет — позиции не было")
                .isEmpty();
        assertThat(trail.exchange().requests(POSITIONS_HISTORY))
                .as("E5.6: звено расчёта пропущено — чтения закрытых эпизодов нет").isEmpty();
        assertThat(trail.exchange().requests(BILLS)).as("E5.6: и движений средств").isEmpty();
        assertThat(trail.exchange().requests(BILLS_ARCHIVE)).as("E5.6: и их архива").isEmpty();
        Map<String, Object> row = trail.database(Party.TRADING_CORE).query("select close_reason, close_outcome, "
                + "reconciliation_status, breakdown_incomplete, risk_benchmark_availability, result_profit, "
                + "result_profit_currency from deals where id = ?", dealId).getFirst();
        assertThat((BigDecimal) row.get("result_profit")).as("E5.6: число — ноль, записанный терминальным звеном")
                .isNotNull()
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.get("result_profit_currency")).as("E5.6: ноль выражен расчётной валютой инструмента")
                .isEqualTo("USDT");
        assertThat(row.get("close_reason")).as("E5.6: причина закрытия — с ребра выхода, терминалом не переписана")
                .isEqualTo("STRATEGY_EXIT");
        assertThat(row.get("risk_benchmark_availability")).as("E5.6: знаменатель — «неприменимо»")
                .isEqualTo("NOT_APPLICABLE");
        assertThat(List.of("close_outcome", "reconciliation_status", "breakdown_incomplete"))
                .as("E5.6: прочие три признака отбора пусты")
                .allSatisfy(column -> assertThat(row.get(column)).as(column).isNull());
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E5.6: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("status").asString()).as("E5.6: событие закрытия — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(content.path("result").decimalValue()).as("E5.6: число в событии — ноль")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(content.path("tookRisk").isBoolean() && isFalse(content.path("tookRisk").asBoolean()))
                .as("E5.6: сделка риска не принимала").isTrue();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("status").asString()).as("E5.6: строка журнала о терминале").isEqualTo("CLOSED");
    }

    /**
     * Красна по находке {@code F12} документа: сработавшую у площадки защиту
     * транша в сопровождении не наблюдает ни один проход — ни защиту, ни
     * позицию сделка в штатном ведении не читает, экспозиция транша не
     * схлопывается, и маршрут «все транши терминальны при состоявшемся
     * входе» не наступает (.claude/work/backlog.md §«Сработавшую защиту
     * транша в сопровождении не наблюдает ни один проход»). Предусловия
     * клетки прогон с меткой проверяет до ожидания, на котором она красна.
     */
    @Test
    @Order(7)
    @Tag("debt")
    @DisplayName("E5.7 — Все транши терминальны при состоявшемся входе: сделка несёт старшую из их причин")
    void e5_7_allTranchesTerminalAfterEntryGiveTheDealTheSeniorReason() {
        trail.exchange().forgetScenarios();
        deal = walkToTwoTranches(trail, expiringFirstTrancheDefinition(), 1, () -> {
        });
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        trail.passUntil("невошедший транш закрыт истёкшим условием, вошедший ведётся", () -> tranches().stream()
                .anyMatch(tranche -> Objects.equals("ENTRY_CONDITION_EXPIRED", tranche.get("close_reason")))
                && tranches().stream().anyMatch(tranche -> Objects.equals("MANAGING", tranche.get("status"))));
        List<Map<String, Object>> before = tranches();
        assertThat(before).as("предусловие E5.7: транша два — один не вошёл, условие истекло, другой вошёл "
                        + "и ведётся")
                .extracting(tranche -> tranche.get("status"), tranche -> tranche.get("close_reason"))
                .containsExactlyInAnyOrder(tuple("CLOSED", "ENTRY_CONDITION_EXPIRED"), tuple("MANAGING", null));
        Object expired = before.stream()
                .filter(tranche -> Objects.equals("CLOSED", tranche.get("status")))
                .findFirst().orElseThrow().get("id");
        exchangeTriggersAttachedStop(trail);
        trail.forgetTraces();

        trail.passUntil("E5.7: сделка ушла из штатного ведения", () -> isFalse(Objects.equals("ACTIVE",
                dealRead(trail, deal).path("status").asString())));
        trail.relayCore();

        assertThat(tranches()).as("E5.7: вошедший транш терминален и закрыт своим инициатором — стопом; "
                        + "причина невошедшего не переписана")
                .extracting(tranche -> Objects.equals(expired, tranche.get("id")), tranche -> tranche.get("status"),
                        tranche -> tranche.get("close_reason"))
                .containsExactlyInAnyOrder(tuple(true, "CLOSED", "ENTRY_CONDITION_EXPIRED"),
                        tuple(false, "CLOSED", "STOP_LOSS"));
        assertThat(dealRead(trail, deal).path("status").asString())
                .as("E5.7: маршрут «все транши терминальны при состоявшемся входе» — координированный выход")
                .isEqualTo("EXIT_PENDING");
        assertThat(trail.database(Party.TRADING_CORE).query("select close_reason, shutdown_reason from deals "
                        + "where id = ?", dealId).getFirst())
                .as("E5.7: сделка несёт старшую из причин траншей — стоп старше истёкшего условия")
                .containsEntry("close_reason", "STOP_LOSS")
                .containsEntry("shutdown_reason", null);
        assertThat(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal))
                .as("E5.7: события остановки на этом маршруте нет").isEmpty();
        assertThat(trail.exchange().requests(CLOSE_POSITION))
                .as("E5.7: команды закрытия позиции нет — позицию закрыл стоп у площадки").isEmpty();
    }

    /**
     * Красна по находке {@code F13} документа: у восстановленной сделки
     * позиция наблюдалась, а эпизода в зеркале нет, и граф сделки не
     * предъявлен целиком никогда — матрица отвергает терминал её транша на
     * каждом проходе, а добычи, которая дополнила бы граф, не эмитит никто
     * (.claude/work/backlog.md §«Восстановленная сделка с позицией, закрытой
     * до первого прохода, не доходит до терминала»). Предусловие клетки
     * прогон с меткой проверяет до ожидания, на котором она красна.
     */
    @Test
    @Order(8)
    @Tag("debt")
    @DisplayName("E5.8 — Штатный терминал восстановленной сделки: идентичность определения едет отсутствующей")
    void e5_8_theCleanTerminalOfARecoveredDealCarriesNoDefinitionIdentity() {
        deal = walkToRecoveredDeal(trail, "31");
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        assertThat(dealRead(trail, deal).path("entryReason").asString())
                .as("предусловие E5.8: сделка заведена восстановлением").isEqualTo("RECOVERY");
        trail.exchange().answers(Trail.EXCHANGE_POSITIONS, """
                {"code": "0", "msg": "", "data": []}
                """);
        trail.exchange().answers(POSITIONS_HISTORY, """
                {"code": "0", "msg": "", "data": [%s]}
                """.formatted(closeRecord(OPENED, CLOSED, "1.0", "-0.2", "0", "0.8")));
        exchangeKeepsBills(trail, SOURCE_TIME, LAST_BILL,
                bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", CLOSED));
        trail.forgetTraces();

        trail.passUntil("E5.8: сделка дошла до терминала", () -> List.of("CLOSED", "EMERGENCY_CLOSED")
                .contains(dealRead(trail, deal).path("status").asString()));
        trail.relayCore();

        assertThat(dealRead(trail, deal).path("status").asString()).as("E5.8: терминал штатный")
                .isEqualTo("CLOSED");
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E5.8: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("status").asString()).as("E5.8: событие закрытия — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(content.path("dealInternalId").asString()).as("E5.8: идентичность сделки — строкой")
                .isEqualTo(deal);
        assertThat(content.path("strategyInternalId").isMissingNode() || content.path("strategyInternalId").isNull())
                .as("E5.8: идентичность определения едет отсутствующей, а не строкой — " + content).isTrue();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("strategyInternalId").isTextual())
                .as("E5.8: у строки журнала идентичности определения тоже нет").isFalse();
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Транши сделки строками ядра — в порядке заведения. */
    private static List<Map<String, Object>> tranches() {
        return trail.database(Party.TRADING_CORE).query("select id, status, close_reason from deal_tranches "
                + "where deal_id = ? order by id", dealId);
    }

    /**
     * Сделка доведена до добытых движений, порог серии назван, затем
     * проходы — до терминала.
     *
     * @param records   записи закрытия эпизодов
     * @param bills     движения окна; последняя — якорь пустой следующей страницы
     * @param lossLimit порог серии убытков
     */
    private static void walkToTerminal(String records, String bills, Integer lossLimit) {
        deal = walkToTerminalTranches(trail, records, SOURCE_TIME, LAST_BILL, bills);
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        definition = activeDefinition();
        trail.passUntil("движения добыты", () -> nonNull(dealRow().get("bills_fetched_through")));
        riskAppetiteIs(trail, "5", lossLimit);
        trail.relayCore();
        dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        trail.forgetTraces();

        trail.passUntil("сделка ушла в терминал", () -> isFalse(Objects.equals("EXIT_PENDING",
                dealRead(trail, deal).path("status").asString())));
        trail.relayCore();
    }

    private static String activeDefinition() {
        return String.valueOf(trail.database(Party.STRATEGIES)
                .query("select internal_id from strategies where status = 'ACTIVE'").getFirst().get("internal_id"));
    }

    private static Map<String, Object> dealRow() {
        return trail.database(Party.TRADING_CORE).query("select close_reason, close_outcome, reconciliation_status, "
                + "breakdown_incomplete, risk_benchmark_availability, result_profit, bills_fetched_through "
                + "from deals where id = ?", dealId).getFirst();
    }

    private static List<Map<String, Object>> systemRows() {
        return trail.database(Party.TRADING_CORE).query("select id from deal_system_action_states "
                + "where deal_id = ?", dealId);
    }

    private static List<Map<String, Object>> liveSystemRows() {
        return trail.database(Party.TRADING_CORE).query("select id from deal_system_action_states "
                + "where deal_id = ? and status in ('PLANNED', 'CREATED', 'SUBMITTED', 'RETRY_PENDING')", dealId);
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

    private static List<Map<String, Object>> outbox(String eventType) {
        return trail.database(Party.TRADING_CORE).query("select event_id, occurred_at, payload::text as payload "
                + "from outbox_events where event_type = ? order by id", eventType);
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows, String label) {
        assertThat(rows).as(label + " — одна строка outbox").hasSize(1);
        return rows.getFirst();
    }

    private static Map<String, Object> awaitJournal(Map<String, Object> event) {
        Database audit = trail.database(Party.AUDIT);
        String eventId = String.valueOf(event.get("event_id"));
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select event_type, occurred_at, content::text as content from audit_records "
                + "where event_id = ?", eventId).getFirst();
    }

    private static void awaitDealFact(Map<String, Object> event) {
        Database statistics = trail.database(Party.STATISTICS);
        String eventId = String.valueOf(event.get("event_id"));
        Trail.await("сделочный факт " + eventId, () -> statistics
                .query("select event_id from deal_facts where event_id = ?", eventId).size() == 1);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return nonNull(header) ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private static Instant instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((OffsetDateTime) column).toInstant();
    }
}
