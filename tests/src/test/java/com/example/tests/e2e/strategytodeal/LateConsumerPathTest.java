package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.strategytodeal.DealTrace.present;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кейс {@code E8.4} тропы «активация определения → сделка»: потребитель,
 * поднятый после публикации, добирает тему с начала
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E8.4 — Потребитель, поднятый
 * после публикации, добирает тему с начала»).
 *
 * <p><b>Своя тропа, а не кейс общей:</b> оба потребителя обязаны не
 * работать с момента, раньше которого тропа ничего не публиковала, — то
 * есть с подъёма сторон; у тропы соседних кейсов они к этому моменту уже
 * читают темы.
 */
@Tag("e2e")
@DisplayName("E8.4 — Потребитель, поднятый после публикации, добирает тему с начала")
class LateConsumerPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static Trail trail;

    @BeforeAll
    static void openTrailWithoutConsumers() {
        trail = Trail.open("e84");
        trail.stop(Party.AUDIT);
        trail.stop(Party.STATISTICS);
        trail.factSeriesStartedYesterday();
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @DisplayName("E8.4 — Потребитель, поднятый после публикации, добирает тему с начала")
    void e8_4_aConsumerRaisedAfterThePublicationReadsTheTopicFromTheStart() {
        String definition = trail.activeDefinition();
        String dealId = trail.openDeal();
        trail.entrySubmitted();
        trail.relayCore();
        Long coreOutbox = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long ownerOutbox = trail.database(Party.STRATEGIES).count("outbox_events");
        trail.side(Party.STATISTICS).set("jobs.aggregate-recompute.cron", RECOMPUTE_EVERY_TWO_SECONDS);
        Instant raised = Instant.now();

        trail.start(Party.AUDIT);
        trail.start(Party.STATISTICS);

        Database audit = trail.database(Party.AUDIT);
        Trail.await("E8.4: журнал добрал классы обоих производителей", () -> audit.query(
                "select id from audit_records where (strategy_internal_id = ? and event_type = 'STRATEGY_ACTIVATED')"
                        + " or deal_internal_id = ?", definition, dealId).size() == 3);
        assertThat(audit.query("select event_type from audit_records where deal_internal_id = ?", dealId))
                .as("E8.4: у журнала строки всех классов, произведённых до его подъёма")
                .extracting(row -> row.get("event_type"))
                .containsExactlyInAnyOrder(DealTrace.DEAL_OPENED, DealTrace.ORDER_DECIDED);
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E8.4: статистика добрала факты своих классов и собрала строку суток", () -> present(statistics
                .query("select id from incident_aggregates where bucket_date = ? and opened_deals = 1"
                        + " and order_decisions = 1", LocalDate.now(ZoneOffset.UTC))));
        assertThat(statistics.query("select event_type from incident_facts where occurred_at >= ?",
                Timestamp.from(raised.minusSeconds(3600))))
                .as("E8.4: факты своих классов, произведённые до подъёма")
                .extracting(row -> row.get("event_type"))
                .containsExactlyInAnyOrder(DealTrace.DEAL_OPENED, DealTrace.ORDER_DECIDED);

        Instant journalBound = lowerBound(journalRead());
        Instant expectedJournal = Stream.of(earliest(audit, "select min(recorded_at) as moment from audit_records"),
                latest(audit)).max(Instant::compareTo).orElseThrow();
        assertThat(journalBound).as("E8.4: граница журнала — позднейший из начала ряда строк и наблюдения тем")
                .isEqualTo(expectedJournal);
        Instant statisticsBound = lowerBound(statisticsRead());
        assertThat(statisticsBound).as("E8.4: граница статистики — позднейший момент наблюдения тем, без ряда строк")
                .isEqualTo(latest(statistics));
        assertThat(List.of(journalBound, statisticsBound)).as("E8.4: ни одна граница не равна моменту подъёма")
                .doesNotContain(raised);
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events"))
                .as("E8.4: подъём потребителей состояния ядра не тронул").isEqualTo(coreOutbox);
        assertThat(trail.database(Party.STRATEGIES).count("outbox_events"))
                .as("E8.4: подъём потребителей состояния владельца не тронул").isEqualTo(ownerOutbox);
    }

    private static JsonNode journalRead() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Trail.Answer answer = trail.call(Party.AUDIT, "GET", "/api/v1/audit/journal/records?from="
                + now.minusHours(12) + "&to=" + now.plusMinutes(5), Trail.TENANT, null);
        assertThat(answer.status()).as("чтение журнала — " + answer.body()).isEqualTo(200);
        return Json.tree(answer.body());
    }

    private static JsonNode statisticsRead() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Trail.Answer answer = trail.call(Party.STATISTICS, "GET", "/api/v1/statistics/aggregates/rows?grain=INCIDENT"
                + "&from=" + day + "&to=" + day, Trail.TENANT, null);
        assertThat(answer.status()).as("чтение агрегатов — " + answer.body()).isEqualTo(200);
        return Json.tree(answer.body());
    }

    private static Instant lowerBound(JsonNode page) {
        return OffsetDateTime.parse(page.path("completeness").path("lowerBound").asString()).toInstant();
    }

    private static Instant latest(Database database) {
        return earliest(database, "select max(observed_since) as moment from reception_states");
    }

    private static Instant earliest(Database database, String sql) {
        return ((Timestamp) database.query(sql).getFirst().get("moment")).toInstant();
    }
}
