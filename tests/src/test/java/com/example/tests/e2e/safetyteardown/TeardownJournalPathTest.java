package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_CLOSED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MANUAL_CLEARED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MANUAL_REQUESTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.absent;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.clear;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsForeignOrder;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.haltFully;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.passUntilEmergencyClosed;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.payload;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E7} тропы снятия риска: журнал и статистика на этой тропе
 * (.claude/tests/cases/e2e-safety-teardown.md §«E7 — Журнал и статистика на
 * этой тропе»).
 *
 * <p><b>Ядер два, статистика одна.</b> Первое ядро проходит автоматическую
 * тропу от {@code E1.1} до аварийного терминала сделки ({@code E3.5}) и два
 * поглощённых повтора ({@code E4.3}); второе — ручную постановку
 * ({@code E6.1}) и ручное снятие ({@code E6.6}). Статистика между ними не
 * пересоздаётся, поэтому строка зерна суток складывает обе тропы, а числа
 * первого ядра, чья база уходит с пересозданием, сняты до него.
 *
 * <p><b>Клетка {@code E7.5} здесь не написана:</b> её предусловие — две
 * сделки счёта к моменту каскада, то есть пролог двух инструментов.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E7 — Журнал и статистика на этой тропе")
class TeardownJournalPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String JOURNAL = "/api/v1/audit/journal/records";

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static final String AUDIT_GROUP = "audit.journal";

    private static final String STATISTICS_GROUP = "statistics.facts";

    private static final Set<String> TRAIL_CLASSES = Set.of(ANOMALY_REPORTED, HOLD_RAISED, DEAL_SHUTDOWN_INITIATED,
            DEAL_CLOSED);

    private static final Set<String> ACTOR_CLASSES = Set.of(ANOMALY_REPORTED, HOLD_RAISED, DEAL_SHUTDOWN_INITIATED);

    private static final List<String> COUNTERS = List.of("raised_holds", "hard_raised_holds",
            "manually_raised_holds", "anomaly_reports", "critical_anomaly_reports", "manual_operation_reports");

    private static Trail trail;

    private static Map<String, Object> start;

    private static Instant from;

    private static String automaticDeal;

    private static Integer firstCoreRaised;

    private static Integer firstCoreReports;

    private static Integer firstCoreCritical;

    private static String automaticActor;

    private static String manualDeal;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t7");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
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
    @DisplayName("E7.4 — Поглощённый повтор ни одного числа не двигает")
    void e7_4_anAbsorbedRepeatMovesNoNumber() {
        automaticDeal = walkToExposure(trail);
        trail.relayCore();
        start = incidents(trail);
        from = Instant.now();
        exchangeHoldsForeignOrder(trail);
        detect(trail);
        trail.relayCore();
        detect(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        Long published = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long journal = trail.database(Party.AUDIT).count("audit_records");

        detect(trail);
        detect(trail);
        trail.relayCore();

        Database core = trail.database(Party.TRADING_CORE);
        awaitJournalRow(trail, core.query("select event_id from outbox_events order by id").getLast()
                .get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(outbox(trail, HOLD_RAISED)).as("предусловие E7.4: перестановок не было").hasSize(raised);
        assertThat(counter(after, "raised_holds")).as("E7.4: подъёмов столько же — два сигнала, ноль перестановок")
                .isEqualTo(counter(before, "raised_holds"));
        assertThat(counter(after, "raised_holds") - counter(start, "raised_holds"))
                .as("E7.4: счётчик подъёмов равен числу состоявшихся перестановок").isEqualTo(raised);
        assertThat(counter(after, "anomaly_reports") - counter(start, "anomaly_reports"))
                .as("E7.4: счётчик отчётов равен числу заведённых строк")
                .isEqualTo(core.count("anomaly_reports").intValue());
        assertThat(trail.database(Party.AUDIT).count("audit_records") - journal)
                .as("E7.4: строк журнала прибавилось столько, сколько классов опубликовано")
                .isEqualTo(core.count("outbox_events") - published);
    }

    @Test
    @Order(2)
    @DisplayName("E7.1 — Классы тропы ложатся строками журнала одной группы в порядке происшествий")
    void e7_1_theTrailClassesLandAsJournalRowsInTheOrderOfOccurrence() {
        passUntilEmergencyClosed(trail, automaticDeal);
        List<Map<String, Object>> produced = trail.database(Party.TRADING_CORE).query("select event_id, event_type, "
                + "occurred_at, published_at from outbox_events where occurred_at >= ? order by id",
                Timestamp.from(from));
        awaitJournalRow(trail, produced.getLast().get("event_id"));

        Answer answer = trail.call(Party.AUDIT, "GET", JOURNAL + "?from=" + from.minusSeconds(1) + "&to="
                + Instant.now().plus(1, ChronoUnit.MINUTES), trail.tenant(), null);

        assertThat(answer.status()).as("E7.1: выборка журнала — " + answer.body()).isEqualTo(200);
        List<JsonNode> records = new ArrayList<>();
        Json.tree(answer.body()).path("records").forEach(records::add);
        records.sort(Comparator.comparing(record -> OffsetDateTime.parse(record.path("occurredAt").asString())));
        assertThat(records).as("E7.1: строк чужих классов в окне нет")
                .allSatisfy(record -> assertThat(TRAIL_CLASSES).contains(record.path("eventType").asString()));
        assertThat(records).as("E7.1: все четыре класса тропы в окне есть")
                .extracting(record -> record.path("eventType").asString())
                .containsAll(TRAIL_CLASSES);
        assertThat(records).as("E7.1: строки лежат в порядке происшествий — порядке решений производителя")
                .extracting(record -> record.path("eventId").asString())
                .containsExactlyElementsOf(produced.stream().map(row -> String.valueOf(row.get("event_id"))).toList());
        for (JsonNode record : records) {
            String type = record.path("eventType").asString();
            Map<String, Object> row = trail.database(Party.AUDIT).query("select tenant_id from audit_records "
                    + "where event_id = ?", record.path("eventId").asString()).getFirst();
            assertThat(row.get("tenant_id")).as("E7.1: тенант из конверта — " + type).isEqualTo(trail.tenant());
            assertThat(record.path("exchangeAccountInternalId").asString()).as("E7.1: счёт — " + type)
                    .isEqualTo(trail.account());
            assertThat(record.path("occurredAt").asString()).as("E7.1: момент — " + type).isNotBlank();
            JsonNode actor = record.path("content").path("actor");
            if (ACTOR_CLASSES.contains(type)) {
                assertThat(actor.asString()).as("E7.1: актор едет содержимым — " + type).isNotBlank();
            } else {
                assertThat(absent(actor)).as("E7.1: у терминала ручной тропы нет — актора нет").isTrue();
            }
        }
        assertThat(produced).as("E7.1: все строки outbox окна опубликованы")
                .allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
        assertThat(records).as("E7.1: число опубликованных строк outbox равно числу строк журнала окна")
                .hasSize(produced.size());
        firstCoreRaised = outbox(trail, HOLD_RAISED).size();
        firstCoreReports = outbox(trail, ANOMALY_REPORTED).size();
        firstCoreCritical = (int) outbox(trail, ANOMALY_REPORTED).stream()
                .filter(row -> Objects.equals("CRITICAL", payload(row).path("severity").asString())).count();
        automaticActor = payload(outbox(trail, HOLD_RAISED).getFirst()).path("actor").asString();
    }

    @Test
    @Order(3)
    @DisplayName("E7.2 — Счётчики происшествий растут по классам и разрезам, а не по предметам")
    void e7_2_theIncidentCountersGrowByClassAndSliceNotBySubject() {
        manualDeal = walkToExposure(trail);
        trail.relayCore();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("предусловие E7.2: ручная постановка принята — " + answer.body())
                .isEqualTo(202);
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        List<Map<String, Object>> reported = outbox(trail, ANOMALY_REPORTED);
        Map<String, Object> after = incidents(trail);
        assertThat(delta(after, "raised_holds")).as("E7.2: подъёмов — по числу подъёмов обеих троп")
                .isEqualTo(firstCoreRaised + raised.size());
        assertThat(delta(after, "hard_raised_holds")).as("E7.2: жёстких — их доля, здесь все")
                .isEqualTo(firstCoreRaised + raised.size());
        assertThat(delta(after, "manually_raised_holds")).as("E7.2: ручных — доля ручной тропы")
                .isEqualTo((int) raised.stream()
                        .filter(row -> Objects.equals(MANUAL_REQUESTED, payload(row).path("code").asString()))
                        .count())
                .isEqualTo(1);
        assertThat(delta(after, "anomaly_reports")).as("E7.2: отчётов — обеих троп")
                .isEqualTo(firstCoreReports + reported.size());
        assertThat(delta(after, "critical_anomaly_reports")).as("E7.2: критичных — критичная доля")
                .isEqualTo(firstCoreCritical + (int) reported.stream()
                        .filter(row -> Objects.equals("CRITICAL", payload(row).path("severity").asString()))
                        .count());
        assertThat(delta(after, "manual_operation_reports")).as("E7.2: отчётов ручной операции — ручная доля")
                .isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("E7.3 — Разрез ручной тропы читается СЛОВОМ провода, и слово это у писателя не запинено")
    void e7_3_theManualSliceIsReadByTheWireWord() {
        passUntilEmergencyClosed(trail, manualDeal);
        Answer answer = clear(trail, "FULL", null);
        trail.relayCore();
        assertThat(answer.status()).as("предусловие E7.3: ручное снятие применено — " + answer.body())
                .isEqualTo(204);
        JsonNode manualRaise = payload(outbox(trail, HOLD_RAISED).getFirst());
        assertThat(manualRaise.path("actor").asString())
                .as("предусловие E7.3: актор тика, запущенного ручным фасадом, равен актору ручной постановки")
                .isEqualTo(automaticActor);

        Map<String, Object> after = incidents(trail);

        assertThat(delta(after, "manually_raised_holds"))
                .as("E7.3: ручных подъёмов один — отбор по коду, а не по актору").isEqualTo(1);
        assertThat(delta(after, "manual_operation_reports"))
                .as("E7.3: отчётов ручной операции два — постановка и снятие").isEqualTo(2);
        assertThat(manualRaise.path("code").asString()).as("E7.3: в содержимом подъёма — слово, по которому "
                + "отбирает потребитель").isEqualTo("MANUAL_HALT_REQUESTED");
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E7.3: в содержимом отчётов ручной тропы — те же слова")
                .extracting(row -> payload(row).path("code").asString())
                .contains("MANUAL_HALT_REQUESTED", "MANUAL_HALT_CLEARED")
                .contains(MANUAL_REQUESTED, MANUAL_CLEARED);
    }

    @Test
    @Order(5)
    @DisplayName("E7.7 — Второй такт пересчёта даёт те же числа")
    void e7_7_aSecondRecomputeTickGivesTheSameNumbers() {
        Map<String, Object> first = incidents(trail);
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        Long rows = trail.database(Party.STATISTICS).count("incident_aggregates");
        Long journal = trail.database(Party.AUDIT).count("audit_records");

        Map<String, Object> second = incidents(trail);

        assertThat(second.get("assembled_at")).as("предусловие E7.7: второй такт собрал строку заново")
                .isNotEqualTo(first.get("assembled_at"));
        for (String column : COUNTERS) {
            assertThat(counter(second, column)).as("E7.7: второй такт — то же " + column)
                    .isEqualTo(counter(first, column));
        }
        assertThat(trail.database(Party.STATISTICS).count("incident_aggregates")).as("E7.7: строк агрегата столько же")
                .isEqualTo(rows);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E7.7: фактов не прибавилось")
                .isEqualTo(facts);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E7.7: строк журнала столько же")
                .isEqualTo(journal);
    }

    @Test
    @Order(6)
    @DisplayName("E7.6 — Полнота приёма обеих групп доходит до конца тропы")
    void e7_6_theReceptionOfBothGroupsReachesTheEndOfTheTrail() {
        Instant last = instant(trail.database(Party.TRADING_CORE)
                .query("select occurred_at from outbox_events order by id").getLast().get("occurred_at"));
        for (String group : List.of(AUDIT_GROUP, STATISTICS_GROUP)) {
            Trail.await("группа " + group + " дочитала тему ядра", () -> Objects.equals(
                    trail.committedOffset(group, Substrate.CORE_TOPIC), trail.endOffset(Substrate.CORE_TOPIC)));
        }

        for (Party party : List.of(Party.AUDIT, Party.STATISTICS)) {
            Database database = trail.database(party);
            Trail.await("E7.6: тик состояния приёма " + party.module() + " отметил последнее принятое", () -> {
                List<Map<String, Object>> rows = reception(database);
                return rows.size() == 1 && nonNull(rows.getFirst().get("last_accepted_occurred_at"))
                        && isFalse(instant(rows.getFirst().get("last_accepted_occurred_at")).isBefore(last));
            });
            Map<String, Object> state = reception(database).getFirst();
            assertThat(state.get("subscribed")).as("E7.6: " + party.module() + " — тема в подписке").isEqualTo(true);
            assertThat(state.get("reception_halted")).as("E7.6: " + party.module() + " — приём не остановлен")
                    .isEqualTo(false);
            assertThat(state.get("lag_gap_at")).as("E7.6: " + party.module() + " — разрывов нет").isNull();
        }
        JsonNode journal = Json.tree(trail.call(Party.AUDIT, "GET", JOURNAL + "?from="
                + Instant.now().minus(1, ChronoUnit.HOURS) + "&to=" + Instant.now(), trail.tenant(), null).body());
        assertThat(journal.path("completeness").path("lowerBound").asString())
                .as("E7.6: журнал отдаёт нижнюю границу полноты").isNotBlank();
        assertThat(journal.path("completeness").path("continuityClaimable").asBoolean())
                .as("E7.6: предикат непрерывности журнала истинен").isTrue();
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        JsonNode aggregates = Json.tree(trail.call(Party.STATISTICS, "GET", ROWS + "?grain=INCIDENT&from=" + day
                + "&to=" + day, trail.tenant(), null).body());
        assertThat(aggregates.path("incidentRows")).as("E7.6: выборка агрегатов отдаёт числа").isNotEmpty();
        assertThat(aggregates.path("completeness").path("lowerBound").asString())
                .as("E7.6: рядом с числами — своя нижняя граница одним операндом").isNotBlank();
        assertThat(aggregates.path("completeness").path("continuityClaimable").asBoolean())
                .as("E7.6: предикат непрерывности статистики истинен").isTrue();
    }

    /** Прирост счётчика от начала тропы. */
    private static Integer delta(Map<String, Object> after, String column) {
        return counter(after, column) - counter(start, column);
    }

    private static List<Map<String, Object>> reception(Database database) {
        return database.query("select subscribed, reception_halted, lag_gap_at, last_accepted_occurred_at "
                + "from reception_states where topic = ?", Substrate.CORE_TOPIC);
    }

    private static Instant instant(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
