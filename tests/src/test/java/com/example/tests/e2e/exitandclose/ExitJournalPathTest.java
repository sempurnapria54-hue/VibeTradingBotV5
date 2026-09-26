package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import static com.example.tests.e2e.exitandclose.ExitTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.exitandclose.ExitTrail.bill;
import static com.example.tests.e2e.exitandclose.ExitTrail.closeRecord;
import static com.example.tests.e2e.exitandclose.ExitTrail.coreOutbox;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealFactSeriesStartedYesterday;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.deleteDefinition;
import static com.example.tests.e2e.exitandclose.ExitTrail.instrumentLosesSettlementCurrency;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTerminalTranches;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E6} тропы выхода: журнал и статистика на этой тропе
 * (.claude/tests/cases/e2e-exit-and-close.md §«E6 — Журнал и статистика на
 * этой тропе»).
 *
 * <p><b>Тропа у группы одна на пять клеток</b> — от удаления определения
 * ({@code E1.3}) до штатного терминала ({@code E5.1}); клетки читают её след
 * по порядку, и {@code E6.4} сравнивает второй такт пересчёта с первым.
 * {@code E6.5} заводит вторую сделку на ядре, поднятом заново её прологом.
 *
 * <p><b>Ряд обоих зёрен статистики начат прошлыми сутками</b> — иначе сутки
 * тропы не пересчитываются вовсе (docs/spec/statistics-aggregates.json,
 * {@code dayRecomputable}); ряд у каждого зерна свой.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E6 — Журнал и статистика на этой тропе")
class ExitJournalPathTest {

    private static final String EXPLORATORY = "exchange-contour.exchanges.OKX.reconciliation-exploratory";

    private static final String DEAL_CLOSED = "DEAL_CLOSED";

    private static final String STRATEGY_DELETED = "STRATEGY_DELETED";

    private static final String AUDIT_GROUP = "audit.journal";

    private static final String STATISTICS_GROUP = "statistics.facts";

    private static final List<String> INCIDENT_CLASSES = List.of("DEAL_OPENED", "ORDER_DECIDED", "HOLD_RAISED",
            "ANOMALY_REPORTED");

    private static final List<String> MONEY_SUMS = List.of("resultBeforeFundingSum", "netResultSum", "feeSum",
            "fundingSum", "liquidationPenaltySum", "winResultSum", "lossResultSum", "plannedRiskSum", "rSum");

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long REOPENED = 1758240100000L;

    private static final Long RECLOSED = 1758240200000L;

    private static final Long SOURCE_TIME = OPENED + Duration.ofDays(1).toMillis();

    private static final String LAST_BILL = "9003";

    private static Trail trail;

    private static String deal;

    private static String definition;

    private static OffsetDateTime initiatedAt;

    private static Map<String, Object> shutdown;

    private static Map<String, Object> closed;

    private static JsonNode firstRow;

    @BeforeAll
    static void walkTheTrailToTheTerminal() {
        trail = Trail.open("x6");
        trail.factSeriesStartedYesterday();
        dealFactSeriesStartedYesterday(trail);
        trail.side(Party.TRADING_CORE).set(EXPLORATORY, "false");
        trail.renew(Party.TRADING_CORE);
        deal = walkToTerminal(() -> {
            initiatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            definition = deleteDefinition(trail);
        });
        shutdown = single(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal), "остановка");
        closed = single(coreOutbox(trail, DEAL_CLOSED, deal), "терминал");
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E6.1 — Классы тропы ложатся строками журнала одной группы в порядке происшествий")
    void e6_1_theTrailClassesLandAsJournalRowsInTheOrderOfOccurrence() {
        Database core = trail.database(Party.TRADING_CORE);
        Integer published = core.query("select id from outbox_events where published_at is not null "
                + "and payload ->> 'dealInternalId' = ?", deal).size();
        Database audit = trail.database(Party.AUDIT);
        Trail.await("E6.1: журнал принял все опубликованные строки о сделке", () -> Objects.equals(published,
                audit.query("select id from audit_records where deal_internal_id = ?", deal).size()));

        String deleted = String.valueOf(trail.database(Party.STRATEGIES).query("select event_id from outbox_events "
                + "where event_type = 'STRATEGY_DELETED' and payload ->> 'strategyInternalId' = ?", definition)
                .getFirst().get("event_id"));
        List<Map<String, Object>> rows = audit.query("select event_id, event_type, tenant_id, deal_internal_id, "
                + "strategy_internal_id from audit_records where occurred_at >= ? order by occurred_at, id",
                initiatedAt);
        assertThat(rows).as("E6.1: строки отрезка — удаление, остановка и терминал, в порядке ходов тропы")
                .extracting(row -> row.get("event_type"))
                .containsExactly(STRATEGY_DELETED, DEAL_SHUTDOWN_INITIATED, DEAL_CLOSED);
        assertThat(rows).as("E6.1: идентичность события у каждой своя — та, что у производителя")
                .extracting(row -> String.valueOf(row.get("event_id")))
                .containsExactly(deleted, String.valueOf(shutdown.get("event_id")),
                        String.valueOf(closed.get("event_id")));
        assertThat(rows).as("E6.1: строк о чужом тенанте нет ни одной")
                .allSatisfy(row -> assertThat(row.get("tenant_id")).isEqualTo(trail.tenant()));
        assertThat(rows.subList(1, rows.size())).as("E6.1: строк о чужой сделке нет ни одной")
                .allSatisfy(row -> assertThat(row.get("deal_internal_id")).isEqualTo(deal));
        assertThat(rows.getFirst().get("strategy_internal_id")).as("E6.1: удаление — о том определении")
                .isEqualTo(definition);
        assertThat(audit.query("select id from audit_records where deal_internal_id = ?", deal))
                .as("E6.1: число строк журнала о сделке равно числу опубликованных строк outbox").hasSize(published);
    }

    @Test
    @Order(2)
    @DisplayName("E6.2 — Сделочный факт даёт только терминальный класс, и момент терминала едет конвертом")
    void e6_2_onlyTheTerminalClassGivesADealFactStampedWithTheEnvelopeMoment() {
        Database statistics = trail.database(Party.STATISTICS);
        String terminal = String.valueOf(closed.get("event_id"));
        awaitRow(statistics, "deal_facts", terminal);

        List<Map<String, Object>> facts = statistics.query("select closed_at from deal_facts where event_id = ?",
                terminal);
        assertThat(facts).as("E6.2: строка сделочного факта одна — отметка обработанного есть ключ").hasSize(1);
        Instant envelope = OffsetDateTime.parse(header(record(terminal), "occurredAt")).toInstant()
                .truncatedTo(ChronoUnit.MICROS);
        assertThat(instant(facts.getFirst().get("closed_at"))).as("E6.2: ось времени — момент из конверта")
                .isEqualTo(envelope);
        List<Map<String, Object>> events = trail.database(Party.TRADING_CORE).query("select event_id, event_type "
                + "from outbox_events where payload ->> 'dealInternalId' = ? order by id", deal);
        for (Map<String, Object> event : events) {
            String eventId = String.valueOf(event.get("event_id"));
            String type = String.valueOf(event.get("event_type"));
            awaitRow(trail.database(Party.AUDIT), "audit_records", eventId);
            if (Objects.equals(DEAL_CLOSED, type)) {
                continue;
            }
            assertThat(statistics.query("select event_id from deal_facts where event_id = ?", eventId))
                    .as("E6.2: сделочного факта на нетерминальный класс " + type + " нет").isEmpty();
            if (INCIDENT_CLASSES.contains(type)) {
                awaitRow(statistics, "incident_facts", eventId);
                assertThat(statistics.query("select event_id from incident_facts where event_id = ?", eventId))
                        .as("E6.2: класс " + type + " лёг фактом происшествия — одной строкой").hasSize(1);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("E6.3 — Событие остановки несомым классом статистики не является")
    void e6_3_theShutdownEventIsNotACarriedStatisticsClass() {
        String event = String.valueOf(shutdown.get("event_id"));
        ConsumerRecord<String, String> published = record(event);
        Database statistics = trail.database(Party.STATISTICS);
        awaitCaughtUp(STATISTICS_GROUP);

        assertThat(trail.committedOffset(STATISTICS_GROUP, Substrate.CORE_TOPIC))
                .as("E6.3: смещение группы статистики продвинулось за событие").isGreaterThan(published.offset());
        assertThat(statistics.query("select event_id from deal_facts where event_id = ?", event))
                .as("E6.3: сделочного факта по классу остановки нет").isEmpty();
        assertThat(statistics.query("select event_id from incident_facts where event_id = ?", event))
                .as("E6.3: факта происшествия по нему нет").isEmpty();
        awaitRow(trail.database(Party.AUDIT), "audit_records", event);
        Map<String, String> headers = new LinkedHashMap<>();
        published.headers().forEach(header -> headers.put(header.key(),
                new String(header.value(), StandardCharsets.UTF_8)));

        trail.produce(Substrate.CORE_TOPIC, published.key(), published.value(), headers);
        awaitCaughtUp(STATISTICS_GROUP);
        awaitCaughtUp(AUDIT_GROUP);

        assertThat(statistics.query("select event_id from deal_facts where event_id = ?", event))
                .as("E6.3: повтор доставки безвреден — строки не появилось").isEmpty();
        assertThat(statistics.query("select event_id from incident_facts where event_id = ?", event)).isEmpty();
        assertThat(trail.database(Party.AUDIT).query("select id from audit_records where event_id = ?", event))
                .as("E6.3: у журнала строка о том же событии есть — и одна").hasSize(1);
    }

    @Test
    @Order(4)
    @DisplayName("E6.4 — Пересчёт даёт строку сделочного зерна с суммами тропы, а второй такт — те же числа")
    void e6_4_theRecomputeGivesTheDealGrainRowAndASecondTickTheSameNumbers() {
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E6.4: строка сделочного зерна собрана тактом", () -> nonNull(ourRow()));

        JsonNode row = ourRow();
        firstRow = row;
        Integer rowCount = dealRows().size();
        assertThat(row.path("exchangeAccountInternalId").asString()).as("E6.4: ключ — счёт")
                .isEqualTo(trail.account());
        assertThat(row.path("bucketDate").asString()).as("E6.4: ключ — сутки терминала").isEqualTo(today().toString());
        assertThat(row.path("resultCurrency").asString()).as("E6.4: ключ — расчётная валюта").isEqualTo("USDT");
        assertThat(trail.database(Party.STATISTICS).query("select tenant_id from deal_aggregates "
                        + "where strategy_internal_id = ?", definition))
                .as("E6.4: ключ — тенант").extracting(line -> line.get("tenant_id")).containsExactly(trail.tenant());
        assertThat(row.path("closedDeals").asInt()).as("E6.4: закрытая сделка одна").isEqualTo(1);
        assertThat(row.path("riskBearingDeals").asInt()).as("E6.4: сделка приняла риск").isEqualTo(1);
        assertThat(row.path("winningDeals").asInt()).as("E6.4: и знак результата — плюс").isEqualTo(1);
        assertThat(row.path("netResultSum").decimalValue()).as("E6.4: итог тропы").isEqualByComparingTo("1.15");
        assertThat(row.path("feeSum").decimalValue()).as("E6.4: комиссия тропы").isEqualByComparingTo("0.3");
        assertThat(row.path("fundingSum").decimalValue()).as("E6.4: финансирование тропы")
                .isEqualByComparingTo("0.05");
        assertThat(row.path("liquidationPenaltySum").decimalValue()).as("E6.4: штраф тропы")
                .isEqualByComparingTo("0");

        Trail.await("E6.4: второй такт собрал строку заново", () -> isFalse(Objects.equals(
                firstRow.path("assembledAt").asString(), ourRow().path("assembledAt").asString())));
        JsonNode second = ourRow();
        trail.statisticsRecomputes(NEVER);
        for (String field : List.of("closedDeals", "riskBearingDeals", "winningDeals", "netResultSum", "feeSum",
                "fundingSum", "liquidationPenaltySum")) {
            assertThat(second.path(field).decimalValue()).as("E6.4: второй такт — то же " + field)
                    .isEqualByComparingTo(firstRow.path(field).decimalValue());
        }
        assertThat(dealRows()).as("E6.4: и то же число строк").hasSize(rowCount);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E6.4: строк журнала такт не трогает")
                .isEqualTo(journal);
    }

    @Test
    @Order(5)
    @DisplayName("E6.6 — Полнота приёма обеих групп доходит до конца тем тропы")
    void e6_6_theReceptionOfBothGroupsReachesTheEndOfTheTrailTopic() {
        Instant terminal = instant(closed.get("occurred_at"));
        awaitCaughtUp(AUDIT_GROUP);
        awaitCaughtUp(STATISTICS_GROUP);

        for (Party party : List.of(Party.AUDIT, Party.STATISTICS)) {
            Database database = trail.database(party);
            Trail.await("E6.6: тик состояния приёма " + party.module() + " отметил последнее принятое", () -> {
                List<Map<String, Object>> rows = receptionOf(database);
                return rows.size() == 1 && nonNull(rows.getFirst().get("last_accepted_occurred_at"))
                        && isFalse(instant(rows.getFirst().get("last_accepted_occurred_at")).isBefore(terminal));
            });
            Map<String, Object> state = receptionOf(database).getFirst();
            assertThat(state.get("subscribed")).as("E6.6: " + party.module() + " — тема в подписке").isEqualTo(true);
            assertThat(state.get("reception_halted")).as("E6.6: приём не остановлен").isEqualTo(false);
            assertThat(state.get("lag_gap_at")).as("E6.6: разрыва по сроку хранения нет").isNull();
        }
        assertThat(trail.committedOffset(AUDIT_GROUP, Substrate.CORE_TOPIC))
                .as("E6.6: смещения групп сошлись с концом темы и друг с другом")
                .isEqualTo(trail.committedOffset(STATISTICS_GROUP, Substrate.CORE_TOPIC))
                .isEqualTo(trail.endOffset(Substrate.CORE_TOPIC));
        assertThat(trail.database(Party.TRADING_CORE).query("select id from outbox_events where published_at is null"))
                .as("E6.6: неопубликованных строк outbox не осталось").isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("E6.5 — Сделка с нерезолвленной валютой ложится в строку пустой валюты с нулевыми суммами")
    void e6_5_aDealWithUnresolvedCurrencyLandsInTheEmptyCurrencyRowWithZeroSums() {
        String second = walkToTerminal(() -> {
            instrumentLosesSettlementCurrency(trail);
            trail.marketPhaseIs("BEAR_TREND");
        });
        String secondDefinition = String.valueOf(trail.database(Party.TRADING_CORE).query("select s.internal_id "
                + "from deals d join strategy_details sd on sd.id = d.strategy_detail_id "
                + "join strategies s on s.id = sd.strategy_id where d.internal_id = ?", second).getFirst()
                .get("internal_id"));

        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> row = core.query("select status, result_profit_currency from deals where internal_id = ?",
                second).getFirst();
        assertThat(row.get("result_profit_currency")).as("E6.5: валюта результата пуста").isNull();
        assertThat(row.get("status")).as("E6.5: штатное ребро закрыто — сделка ушла аварийным терминалом")
                .isEqualTo("EMERGENCY_CLOSED");
        assertThat(core.query("select code from anomaly_reports")).as("E6.5: заведён журнальный отчёт").isNotEmpty();
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, second), "E6.5: терминал");
        awaitRow(trail.database(Party.AUDIT), "audit_records", String.valueOf(terminal.get("event_id")));
        for (Map<String, Object> reported : core.query("select event_id from outbox_events "
                + "where event_type = 'ANOMALY_REPORTED'")) {
            awaitRow(trail.database(Party.AUDIT), "audit_records", String.valueOf(reported.get("event_id")));
        }
        awaitRow(trail.database(Party.STATISTICS), "deal_facts", String.valueOf(terminal.get("event_id")));
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E6.5: строка пустой валюты собрана тактом", () -> nonNull(rowOf(secondDefinition, null)));
        JsonNode empty = rowOf(secondDefinition, null);
        trail.statisticsRecomputes(NEVER);

        assertThat(empty.path("currencyUnresolvedDeals").asInt())
                .as("E6.5: счётчик сделок с нерезолвленной валютой вырос на единицу").isEqualTo(1);
        for (String sum : MONEY_SUMS) {
            assertThat(empty.path(sum).decimalValue()).as("E6.5: сумма " + sum + " строки пустой валюты нулевая")
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
        assertThat(dealRows().stream()
                .filter(line -> Objects.equals(secondDefinition, line.path("strategyInternalId").asString()))
                .filter(line -> isFalse(line.path("resultCurrency").isNull()))
                .toList()).as("E6.5: строки зерна с известной валютой по этой сделке нет").isEmpty();
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Сделка по названной инициативе доведена до добытых движений, затем
     * проходы — до ухода из координированного выхода. Записи закрытия — два
     * эпизода с комиссией и финансированием; движения их сводят.
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
        trail.passUntil("сделка ушла в терминал", () -> {
            String status = dealRead(trail, walked).path("status").asString();
            return isFalse(Objects.equals("EXIT_PENDING", status)) && isFalse(Objects.equals("ERROR", status));
        });
        trail.relayCore();
        return walked;
    }

    private static List<JsonNode> dealRows() {
        LocalDate day = today();
        Answer answer = trail.call(Party.STATISTICS, "GET", ROWS + "?grain=DEAL&from=" + day + "&to=" + day,
                trail.tenant(), null);
        assertThat(answer.status()).as("чтение агрегатов — " + answer.body()).isEqualTo(200);
        List<JsonNode> rows = new ArrayList<>();
        Json.tree(answer.body()).path("dealRows").forEach(rows::add);
        return rows;
    }

    private static JsonNode ourRow() {
        return rowOf(definition, "USDT");
    }

    /** Строка сделочного зерна суток по определению и валюте; пусто у валюты — строка пустой валюты. */
    private static JsonNode rowOf(String strategy, String currency) {
        return dealRows().stream()
                .filter(row -> Objects.equals(strategy, row.path("strategyInternalId").asString()))
                .filter(row -> isNull(currency) ? row.path("resultCurrency").isNull()
                        || row.path("resultCurrency").isMissingNode()
                        : Objects.equals(currency, row.path("resultCurrency").asString()))
                .findFirst()
                .orElse(null);
    }

    private static List<Map<String, Object>> receptionOf(Database database) {
        return database.query("select subscribed, reception_halted, lag_gap_at, last_accepted_occurred_at "
                + "from reception_states where topic = ?", Substrate.CORE_TOPIC);
    }

    private static void awaitCaughtUp(String group) {
        Trail.await("группа " + group + " дочитала тему ядра", () -> Objects.equals(
                trail.committedOffset(group, Substrate.CORE_TOPIC), trail.endOffset(Substrate.CORE_TOPIC)));
    }

    private static void awaitRow(Database database, String table, String eventId) {
        Trail.await("строка " + table + " события " + eventId, () -> isFalse(database
                .query("select event_id from " + table + " where event_id = ?", eventId).isEmpty()));
    }

    private static ConsumerRecord<String, String> record(String eventId) {
        List<ConsumerRecord<String, String>> records = trail.records(Substrate.CORE_TOPIC).stream()
                .filter(record -> Objects.equals(eventId, header(record, "eventId")))
                .toList();
        assertThat(records).as("запись темы события " + eventId).isNotEmpty();
        return records.getFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return nonNull(header) ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows, String label) {
        assertThat(rows).as(label + " — одна строка outbox").hasSize(1);
        return rows.getFirst();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static Instant instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((OffsetDateTime) column).toInstant();
    }
}
