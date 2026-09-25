package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

import static com.example.tests.e2e.strategytodeal.DealTrace.awaitIncident;
import static com.example.tests.e2e.strategytodeal.DealTrace.coreOutbox;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E7} тропы «активация определения → сделка»: факты
 * происшествий и счётчики агрегата
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E7 — Статистика: факты
 * происшествий и счётчики агрегата»).
 *
 * <p><b>Такт пересчёта подаётся расписанием, сокращённым конфигурацией
 * процесса</b>: фасада у статистики нет намеренно, а повтор такта
 * безразличен по построению — строка агрегата есть проекция
 * (.claude/skills/test-code.md §«Уровень 3 — сквозной набор»). Сторона
 * статистики поэтому поднимается заново со своим тактом до тропы.
 *
 * <p><b>Тропа у группы одна</b> — до состояния {@code E4.1}; кейсы читают её
 * след по порядку, и {@code E7.3} сравнивает с тем, что застал {@code E7.1}.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E7 — Статистика: факты происшествий и счётчики агрегата")
class StatisticsPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static Trail trail;

    private static String dealId;

    private static JsonNode firstRow;

    @BeforeAll
    static void walkTheTrailToTheOrderDecision() {
        trail = Trail.open("e7");
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.factSeriesStartedYesterday();
        Trail.await("первый факт ряда принят статистикой",
                () -> trail.database(Party.STATISTICS).count("incident_facts") == 1L);
        trail.commonPreconditions();
        trail.activeDefinition();
        dealId = trail.openDeal();
        trail.entrySubmitted();
        trail.relayCore();
        awaitIncident(trail, String.valueOf(coreOutbox(trail, dealId, DealTrace.DEAL_OPENED).get("event_id")));
        String decided = String.valueOf(trail.database(Party.TRADING_CORE).query(
                "select event_id from outbox_events where event_type = ?", DealTrace.ORDER_DECIDED)
                .getFirst().get("event_id"));
        awaitIncident(trail, decided);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E7.1 — Тик пересчёта даёт строку зерна происшествий со счётчиками тропы")
    void e7_1_theRecomputeYieldsAnIncidentRowWithTheTrailCounters() {
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        trail.forgetTraces();

        Trail.await("E7.1: строка зерна происшествий собрана тактом", () -> incidentRows().size() == 1
                && incidentRows().getFirst().path("orderDecisions").asInt() == 1);

        JsonNode row = incidentRows().getFirst();
        firstRow = row;
        assertThat(row.path("exchangeAccountInternalId").asString()).as("E7.1: ключ — счёт").isEqualTo(Trail.ACCOUNT);
        assertThat(row.path("bucketDate").asString()).as("E7.1: ключ — сутки UTC").isEqualTo(today().toString());
        assertThat(row.path("openedDeals").asInt()).as("E7.1: заведённых сделок одна").isEqualTo(1);
        assertThat(row.path("orderDecisions").asInt()).as("E7.1: решений о заявке одно").isEqualTo(1);
        for (String zero : List.of("raisedHolds", "hardRaisedHolds", "manuallyRaisedHolds", "anomalyReports",
                "criticalAnomalyReports", "manualOperationReports")) {
            assertThat(row.path(zero).asInt()).as("E7.1: счётчик " + zero + " нулевой").isZero();
        }
        assertThat(row.path("assembledAt").asString()).as("E7.1: выдача несёт момент сборки").isNotBlank();
        assertThat(trail.database(Party.STATISTICS).query(
                "select tenant_id from incident_aggregates where bucket_date = ?", today()))
                .as("E7.1: ключ — тенант")
                .extracting(line -> line.get("tenant_id")).containsExactly(Trail.TENANT);
        assertThat(trail.database(Party.AUDIT).count("audit_records")).as("E7.1: строки журнала тиком не тронуты")
                .isEqualTo(journal);
        assertOutwardSilence("E7.1");
    }

    @Test
    @Order(2)
    @DisplayName("E7.2 — Сделочного агрегата на этой тропе нет вовсе")
    void e7_2_thereIsNoDealAggregateOnThisTrail() {
        Trail.Answer answer = read("DEAL");

        assertThat(answer.status()).as("E7.2: выдача отвечает страницей, а не отказом — " + answer.body())
                .isEqualTo(200);
        assertThat(Json.tree(answer.body()).path("dealRows")).as("E7.2: строк сделочного зерна нет ни одной")
                .isEmpty();
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("deal_aggregates")).isZero();
        assertThat(statistics.count("deal_facts")).as("E7.2: сделочных фактов нет — терминала не было").isZero();
    }

    @Test
    @Order(3)
    @DisplayName("E7.3 — Второй такт пересчёта даёт те же числа и тот же состав строк")
    void e7_3_aSecondRecomputeGivesTheSameNumbersAndRows() {
        Long facts = trail.database(Party.STATISTICS).count("incident_facts");
        trail.forgetTraces();

        Trail.await("E7.3: момент сборки обновлён следующим тактом", () -> isFalse(Objects.equals(
                firstRow.path("assembledAt").asString(), incidentRows().getFirst().path("assembledAt").asString())));

        JsonNode row = incidentRows().getFirst();
        assertThat(incidentRows()).as("E7.3: состав строк тот же").hasSize(1);
        for (String counter : List.of("openedDeals", "orderDecisions", "raisedHolds", "hardRaisedHolds",
                "manuallyRaisedHolds", "anomalyReports", "criticalAnomalyReports", "manualOperationReports")) {
            assertThat(row.path(counter).asInt()).as("E7.3: число " + counter + " то же")
                    .isEqualTo(firstRow.path(counter).asInt());
        }
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E7.3: новых фактов не появилось")
                .isEqualTo(facts);
        assertOutwardSilence("E7.3");
    }

    private static List<JsonNode> incidentRows() {
        Trail.Answer answer = read("INCIDENT");
        assertThat(answer.status()).as("чтение агрегатов — " + answer.body()).isEqualTo(200);
        List<JsonNode> rows = new ArrayList<>();
        Json.tree(answer.body()).path("incidentRows").forEach(rows::add);
        return rows;
    }

    private static Trail.Answer read(String grain) {
        LocalDate day = today();
        return trail.call(Party.STATISTICS, "GET", ROWS + "?grain=" + grain + "&from=" + day + "&to=" + day,
                Trail.TENANT, null);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Тик пересчёта наружу не ходит: ни к ядру, ни к владельцу определений, ни к коннектору. */
    private static void assertOutwardSilence(String label) {
        for (Party party : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.CONNECTOR)) {
            assertThat(trail.accesses(party)).as(label + ": к стороне " + party.module() + " обращений нет")
                    .isEmpty();
        }
    }
}
