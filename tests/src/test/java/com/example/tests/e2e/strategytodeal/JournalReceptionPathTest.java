package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Json;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E6} тропы «активация определения → сделка»: журнал и
 * полнота приёма обеих durable-групп
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E6 — Журнал и полнота приёма
 * обеих durable-групп»).
 *
 * <p><b>Порядок методов несущий:</b>
 * {@code E6.2} останавливает приём журнала по паре с темой владельца
 * определений, и остановка переживает кейс — снимает её только правка
 * производителя (docs/rules/durable-consumer-reception.md). Поэтому
 * {@code E6.2} идёт последним, а {@code E6.3}, которому нужен неостановленный
 * приём, — первым, а {@code E6.1}, которому нужен приём обеих тем, — вторым.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E6 — Журнал и полнота приёма обеих durable-групп")
class JournalReceptionPathTest {

    private static final String AUDIT_GROUP = "audit.journal";

    private static final String STATISTICS_GROUP = "statistics.facts";

    private static final String CORE_GROUP = "trading-core.strategy-facts";

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e6");
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E6.3 — Строка состояния приёма заводится на каждую тему тропы, а не на группу")
    void e6_3_aReceptionStateRowPerTopicOfTheTrail() {
        Database audit = trail.database(Party.AUDIT);
        Trail.await("E6.3: тик состояния приёма журнала отработал такт по обеим темам",
                () -> audit.query("select id from reception_states where observed_since is not null").size() == 2);

        List<Map<String, Object>> auditRows = audit.query(
                "select consumer_group, topic, subscribed, reception_halted, observed_since from reception_states"
                        + " order by topic");
        assertThat(auditRows).as("E6.3: у журнала строка на КАЖДУЮ пару «группа × тема»")
                .extracting(row -> row.get("consumer_group"), row -> row.get("topic"))
                .containsExactly(tuple(AUDIT_GROUP, Substrate.STRATEGY_TOPIC),
                        tuple(AUDIT_GROUP, Substrate.CORE_TOPIC));
        assertThat(auditRows).allSatisfy(row -> {
            assertThat(row.get("subscribed")).as("E6.3: признак подписки истинен").isEqualTo(Boolean.TRUE);
            assertThat(row.get("reception_halted")).as("E6.3: признак остановки ложен").isEqualTo(Boolean.FALSE);
            assertThat(row.get("observed_since")).as("E6.3: момент наблюдения непрерывности проставлен").isNotNull();
        });

        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E6.3: тик состояния приёма статистики отработал такт",
                () -> statistics.query("select id from reception_states where observed_since is not null").size() == 1);
        assertThat(statistics.query("select consumer_group, topic from reception_states"))
                .as("E6.3: у статистики строка одна — на тему ядра; темы владельца определений в её подписке нет")
                .extracting(row -> row.get("consumer_group"), row -> row.get("topic"))
                .containsExactly(tuple(STATISTICS_GROUP, Substrate.CORE_TOPIC));

        Database core = trail.database(Party.TRADING_CORE);
        if (core.hasTable("reception_states")) {
            assertThat(core.count("reception_states")).as("E6.3: у ядра строки состояния приёма нет ни одной").isZero();
        }
        assertThat(trail.database(Party.STRATEGIES).hasTable("reception_states"))
                .as("E6.3: владелец определений потребителем не является — строк состояния у него нет").isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("E6.1 — Классы обеих тем тропы ложатся строками одной группы журнала")
    void e6_1_theClassesOfBothTopicsLandAsRowsOfOneJournalGroup() {
        String definition = trail.activeDefinition();
        String dealId = trail.openDeal();
        trail.entrySubmitted();
        trail.relayCore();
        Long coreOutbox = trail.database(Party.TRADING_CORE).count("outbox_events");
        Long ownerOutbox = trail.database(Party.STRATEGIES).count("outbox_events");
        trail.forgetTraces();

        Database audit = trail.database(Party.AUDIT);
        Trail.await("E6.1: журнал принял классы обоих производителей", () -> audit.query(
                "select id from audit_records where strategy_internal_id = ? or deal_internal_id = ?",
                definition, dealId).size() >= 3);

        List<Map<String, Object>> rows = audit.query(
                "select event_id, event_type, occurred_at, content from audit_records"
                        + " where (strategy_internal_id = ? and event_type = 'STRATEGY_ACTIVATED')"
                        + " or deal_internal_id = ?", definition, dealId);
        assertThat(rows).as("E6.1: строки обоих производителей — классы владельца определений и ядра")
                .extracting(row -> row.get("event_type"))
                .containsExactlyInAnyOrder("STRATEGY_ACTIVATED", "DEAL_OPENED", "ORDER_DECIDED");
        Map<String, ConsumerRecord<String, String>> published = new LinkedHashMap<>();
        for (String topic : List.of(Substrate.STRATEGY_TOPIC, Substrate.CORE_TOPIC)) {
            trail.records(topic).forEach(record -> published.put(headersOf(record).get("eventId"), record));
        }
        assertThat(rows).allSatisfy(row -> {
            ConsumerRecord<String, String> record = published.get(String.valueOf(row.get("event_id")));
            assertThat(record).as("E6.1: у строки есть запись темы").isNotNull();
            assertThat(((Timestamp) row.get("occurred_at")).toInstant())
                    .as("E6.1: момент происшествия — момент конверта, а не приёма")
                    .isEqualTo(OffsetDateTime.parse(headersOf(record).get("occurredAt")).toInstant()
                            .truncatedTo(ChronoUnit.MICROS));
            assertThat(Json.tree(String.valueOf(row.get("content"))))
                    .as("E6.1: содержимое доставлено как есть").isEqualTo(Json.tree(record.value()));
        });
        assertThat(trail.database(Party.STATISTICS).query(
                "select event_id from incident_facts where event_type like 'STRATEGY%'"))
                .as("E6.1: строк классов владельца определений у статистики нет").isEmpty();
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events"))
                .as("E6.1: приём журнала ядру следствий не возвращает").isEqualTo(coreOutbox);
        assertThat(trail.database(Party.STRATEGIES).count("outbox_events"))
                .as("E6.1: приём журнала владельцу следствий не возвращает").isEqualTo(ownerOutbox);
        for (Party party : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.CONNECTOR)) {
            assertThat(trail.accesses(party)).as("E6.1: приём журнала к стороне " + party.module() + " не ходит")
                    .isEmpty();
        }
    }

    @Test
    @Order(3)
    @DisplayName("E6.2 — Неполный конверт от производителя останавливает приём журнала")
    void e6_2_anIncompleteEnvelopeHaltsTheJournalReception() {
        trail.retireActiveDefinitions();
        String first = trail.createDefinition();
        trail.moveDefinition(first, "ACTIVE");
        trail.relayOwner();
        ConsumerRecord<String, String> activation = trail.records(Substrate.STRATEGY_TOPIC).getLast();
        Map<String, String> incomplete = headersOf(activation);
        incomplete.remove("eventType");
        String incompleteId = UUID.randomUUID().toString();
        incomplete.put("eventId", incompleteId);
        Long incompleteOffset = trail.endOffset(Substrate.STRATEGY_TOPIC);
        Long incidents = trail.database(Party.STATISTICS).count("incident_facts");

        trail.produce(Substrate.STRATEGY_TOPIC, activation.key(), activation.value(), incomplete);
        trail.moveDefinition(first, "INACTIVE");
        String second = trail.createDefinition();
        trail.moveDefinition(second, "ACTIVE");
        trail.relayOwner();

        Database audit = trail.database(Party.AUDIT);
        Trail.await("E6.2: у строки состояния приёма пары признак остановки истинен",
                () -> audit.query("select id from reception_states where topic = ? and reception_halted",
                        Substrate.STRATEGY_TOPIC).size() == 1);
        assertThat(audit.query("select id from audit_records where event_id = ?", incompleteId))
                .as("E6.2: строки неполной записи нет").isEmpty();
        assertThat(audit.query("select id from audit_records where strategy_internal_id = ? and event_type = ?",
                second, "STRATEGY_ACTIVATED")).as("E6.2: строки следующей записи нет — приём по паре остановлен")
                .isEmpty();
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E6.2: следа у статистики нет")
                .isEqualTo(incidents);
        Trail.await("E6.2: ядро неполную запись пропустило и смещение продвинуло",
                () -> trail.committedOffset(CORE_GROUP, Substrate.STRATEGY_TOPIC) > incompleteOffset);

        Database core = trail.database(Party.TRADING_CORE);
        Trail.await("E6.2: ядро завело копию второго определения — два потребителя разошлись исходами",
                () -> core.query("select id from strategies where internal_id = ?", second).size() == 1);
        assertThat(core.query("select id from inbox_events where event_id = ?", incompleteId))
                .as("E6.2: копии и отметки по неполной записи у ядра нет").isEmpty();
    }

    private static Map<String, String> headersOf(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return headers;
    }
}
