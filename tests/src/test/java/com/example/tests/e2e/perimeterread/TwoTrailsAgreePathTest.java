package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.example.tests.e2e.perimeterread.StreamTrace.JOURNAL;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.instantOf;
import static com.example.tests.e2e.perimeterread.StreamTrace.momentOf;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static com.example.tests.e2e.perimeterread.StreamTrace.warmTheCache;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E6} тропы периметра: что показано, то и читается
 * (.claude/tests/cases/e2e-perimeter-read.md §«E6 — Согласие двух троп: что
 * показано, то и читается»).
 *
 * <p><b>Состояние {@code E3.1} ставится ходом группы {@code E3}</b>, а до него
 * статистика один раз складывает сутки: иначе клетке «до такта число не
 * включает свежий факт» не с чем было бы сравнивать — строки суток не было бы
 * вовсе.
 *
 * <p><b>{@code E6.4} — последняя:</b> неполный конверт останавливает приём
 * журнала, и после неё журнал тропы не читает ничего.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E6 — Согласие двух троп: что показано, то и читается")
class TwoTrailsAgreePathTest {

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final List<String> TERMINAL_DEAL = List.of("CLOSED", "EMERGENCY_CLOSED", "ERROR");

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    private static Subscription first;

    private static Frame record;

    @BeforeAll
    static void standInTheStateOfE31() {
        trail = Trail.openPerimeter("p10");
        token = trail.identity().browserToken("subject-s1", "Trader One");
        prologue = Prologue.walkToOpenDeal(trail, token);
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("сутки сложены до хода, порождающего факт", () -> isFalse(trail.database(Party.STATISTICS)
                .query("select id from incident_aggregates where tenant_id = ? and opened_deals = 1",
                        prologue.tenant()).isEmpty()));
        trail.statisticsRecomputes(NEVER);
        warmTheCache(trail, token);
        first = Subscription.open(trail, ticket(trail, token));
        trail.entrySubmitted();
        trail.relayCore();
        record = first.awaitType(ORDER_DECIDED);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(first)) {
            first.close();
        }
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E6.1 — Происшествие, приехавшее записью, читается строкой журнала той же идентичностью")
    void e6_1_theArrivedOccurrenceReadsAsAJournalRowOfTheSameIdentity() {
        awaitJournal(trail, record.id());
        Long rows = trail.database(Party.AUDIT).count("audit_records");
        Instant moment = momentOf(record);
        String path = JOURNAL + "?from=" + moment.minusSeconds(60) + "&to=" + moment.plusSeconds(60);
        warmTheCache(trail, token);

        Answer read = trail.callWith(token, Party.BFF, "GET", path, null, null);

        assertThat(read.status()).as("E6.1: чтение журнала через периметр — " + read.body()).isEqualTo(200);
        List<JsonNode> matching = rowsOf(read).stream()
                .filter(row -> Objects.equals(record.id(), row.path("eventId").asString()))
                .toList();
        assertThat(matching).as("E6.1: строка журнала с идентичностью записи в выдаче есть").hasSize(1);
        assertThat(matching.getFirst().path("eventType").asString()).as("E6.1: класс совпадает")
                .isEqualTo(record.type());
        assertThat(OffsetDateTime.parse(matching.getFirst().path("occurredAt").asString()).toInstant())
                .as("E6.1: момент происшествия совпадает").isEqualTo(moment);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E6.1: чтение своей записи не сделало")
                .isEqualTo(rows);
    }

    @Test
    @Order(2)
    @DisplayName("E6.2 — Сделка, о которой приехало событие, читается поверхностью ядра через периметр")
    void e6_2_theDealOfTheEventReadsThroughThePerimeter() {
        String deal = record.content().path("dealInternalId").asString();
        warmTheCache(trail, token);

        Answer read = trail.callWith(token, Party.BFF, "GET", Trail.CORE + "/deals/" + deal, null, null);

        assertThat(read.status()).as("E6.2: сделка найдена по идентичности из записи — " + read.body())
                .isEqualTo(200);
        JsonNode found = Json.tree(read.body());
        assertThat(found.path("internalId").asString()).as("E6.2: идентичность пригодна без перевода")
                .isEqualTo(deal).isEqualTo(prologue.deal());
        assertThat(found.path("status").asString()).as("E6.2: решение о заявке — у живой сделки")
                .isNotIn(TERMINAL_DEAL);
        assertThat(trail.accesses(Party.TRADING_CORE)).as("E6.2: к ядру ушло одно чтение этой сделки")
                .extracting(Side.Access::path).containsExactly(Trail.CORE + "/deals/" + deal);
    }

    @Test
    @Order(3)
    @DisplayName("E6.3 — Число агрегата, выросшее от того же факта, читается через периметр после такта")
    void e6_3_theAggregateGrownByTheFactReadsAfterTheTact() {
        String rows = "/api/v1/statistics/aggregates/rows?grain=INCIDENT&from=" + LocalDate.now(ZoneOffset.UTC)
                + "&to=" + LocalDate.now(ZoneOffset.UTC);
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E6.3: факт принят статистикой", () -> isFalse(statistics
                .query("select event_id from incident_facts where event_id = ?", record.id()).isEmpty()));

        JsonNode before = rowOf(rows);
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E6.3: такт пересчёта сложил сутки заново", () -> isFalse(statistics.query(
                "select id from incident_aggregates where tenant_id = ? and order_decisions = 1",
                prologue.tenant()).isEmpty()));
        JsonNode after = rowOf(rows);
        Object assembled = statistics.query("select assembled_at from incident_aggregates where tenant_id = ?",
                prologue.tenant()).getFirst().get("assembled_at");
        Trail.await("E6.3: следующий такт прошёл", () -> isFalse(Objects.equals(instantOf(statistics.query(
                "select assembled_at from incident_aggregates where tenant_id = ?", prologue.tenant())
                .getFirst().get("assembled_at")), instantOf(assembled))));
        JsonNode repeated = rowOf(rows);
        trail.statisticsRecomputes(NEVER);

        assertThat(before.path("orderDecisions").asInt()).as("E6.3: до такта число свежего факта не включает")
                .isZero();
        assertThat(after.path("orderDecisions").asInt()).as("E6.3: после такта выросло ровно на этот факт")
                .isEqualTo(1);
        assertThat(after.path("bucketDate").asString()).as("E6.3: зерно — сутки, которым факт принадлежит")
                .isEqualTo(LocalDate.ofInstant(momentOf(record), ZoneOffset.UTC).toString());
        assertThat(withoutAssembly(repeated)).as("E6.3: повтор такта тех же чисел не меняет")
                .isEqualTo(withoutAssembly(after));
        assertThat(awaitJournal(trail, record.id())).as("E6.3: строка журнала того же факта на месте").isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("E6.4 — Неполный конверт: браузер записи не получает, журнал приём останавливает")
    void e6_4_anIncompleteEnvelopeSkipsTheBrowserAndHaltsTheJournal() {
        Long mark = trail.side(Party.BFF).logMark();
        String incomplete = UUID.randomUUID().toString();
        Map<String, String> partial = new LinkedHashMap<>();
        partial.put("eventId", incomplete);
        partial.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        partial.put("version", "1");
        trail.produce(Substrate.CORE_TOPIC, prologue.tenant(), dealOpened(), partial);
        Long incompleteOffset = trail.endOffset(Substrate.CORE_TOPIC) - 1;
        String complete = UUID.randomUUID().toString();
        Map<String, String> full = new LinkedHashMap<>(partial);
        full.put("eventId", complete);
        full.put("eventType", "DEAL_OPENED");
        trail.produce(Substrate.CORE_TOPIC, prologue.tenant(), dealOpened(), full);

        first.awaitId(complete);
        assertThat(first.facts()).as("E6.4: неполной записи браузеру не уехало, раздача продолжилась")
                .extracting(Frame::id).containsExactly(record.id(), complete);
        assertThat(trail.side(Party.BFF).logSince(mark)).as("E6.4: отказ записан логом периметра")
                .contains("An event without envelope headers is skipped");
        Database audit = trail.database(Party.AUDIT);
        Trail.await("E6.4: приём журнала остановлен", () -> isFalse(audit.query(
                "select id from reception_states where topic = ? and reception_halted", Substrate.CORE_TOPIC)
                .isEmpty()));
        assertThat(audit.query("select id from audit_records where event_id in (?, ?)", incomplete, complete))
                .as("E6.4: ни неполной записи, ни полной за ней в журнале нет").isEmpty();
        assertThat(trail.committedOffset("audit.journal", Substrate.CORE_TOPIC))
                .as("E6.4: смещение журнала не продвинулось за неполную запись").isLessThanOrEqualTo(incompleteOffset);
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static List<JsonNode> rowsOf(Answer read) {
        List<JsonNode> rows = new ArrayList<>();
        Json.tree(read.body()).path("records").forEach(rows::add);
        return rows;
    }

    /** Строка суток тенанта пролога через периметр. */
    private static JsonNode rowOf(String rows) {
        warmTheCache(trail, token);
        Answer read = trail.callWith(token, Party.BFF, "GET", rows, null, null);
        assertThat(read.status()).as("чтение агрегатов — " + read.body()).isEqualTo(200);
        JsonNode incidentRows = Json.tree(read.body()).path("incidentRows");
        assertThat(incidentRows).as("строка суток тенанта пролога").hasSize(1);
        return incidentRows.get(0);
    }

    /** Строка агрегата без момента сборки — его такт переписывает законно. */
    private static JsonNode withoutAssembly(JsonNode row) {
        return ((ObjectNode) row.deepCopy()).without("assembledAt");
    }

    private static String dealOpened() {
        return """
                {"dealInternalId": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s",
                 "strategyInternalId": "%s", "entryReason": "STRATEGY", "direction": "LONG",
                 "entryMarketPhase": "BULL_TREND"}
                """.formatted(UUID.randomUUID(), prologue.account(), Trail.INSTRUMENT, UUID.randomUUID());
    }
}
