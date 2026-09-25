package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E2} тропы «активация определения → сделка»: активация у
 * владельца доезжает до копии у ядра и до строки журнала
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E2 — Активация доезжает до
 * копии у ядра»).
 *
 * <p><b>Каждый кейс заводит СВОЁ определение:</b> тропа общая, и чужая
 * активация в той же теме иначе читалась бы как след кейса. След отбирается
 * идентичностью события либо определения, а не счётом строк таблицы.
 *
 * <p><b>Ассерты по копии у ядра стоят последними.</b> Пока снимок нёс
 * числовые ключи базы владельца, копия не заводилась (находка {@code F6},
 * закрыта кодом 2026-09-24); порядок ассертов сохранён — прогон сперва
 * проходит всё, что стык держит у владельца, темы, журнала и молчания
 * прочих, и только потом спрашивает копию.
 */
@Tag("e2e")
@DisplayName("E2 — Активация доезжает до копии у ядра")
class DefinitionActivationPathTest {

    private static final String ACTIVATED = "STRATEGY_ACTIVATED";

    private static final String DEACTIVATED = "STRATEGY_DEACTIVATED";

    private static final String DELETED = "STRATEGY_DELETED";

    private static final String CORE_GROUP = "trading-core.strategy-facts";

    private static final String AUDIT_GROUP = "audit.journal";

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e2");
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @DisplayName("E2.1 — Штатная активация: статус, outbox, публикация, копия, строка журнала")
    void e2_1_anOrdinaryActivationReachesTheCopyAndTheJournal() {
        trail.retireActiveDefinitions();
        String definition = trail.createDefinition();
        trail.forgetTraces();

        assertThat(trail.moveDefinition(definition, "ACTIVE").status()).as("E2.1: переход принят").isEqualTo(200);
        Map<String, Object> pending = outboxRow(definition, ACTIVATED);
        assertThat(pending.get("published_at")).as("E2.1: строка outbox заведена переходом и ещё не опубликована")
                .isNull();

        trail.relayOwner();

        String eventId = String.valueOf(pending.get("event_id"));
        assertThat(ownerStatusOf(definition)).as("E2.1: чтение владельца отдаёт ACTIVE").isEqualTo("ACTIVE");
        assertThat(outboxRow(definition, ACTIVATED).get("published_at")).as("E2.1: реле пометило строку").isNotNull();
        List<ConsumerRecord<String, String>> published = recordsOf(eventId);
        assertThat(published).as("E2.1: в теме владельца одна запись события").hasSize(1);
        assertThat(published.getFirst().key()).as("E2.1: ключ записи — тенант").isEqualTo(Trail.TENANT);
        Map<String, Object> journal = awaitJournal(eventId);
        assertThat(journal.get("event_type")).isEqualTo(ACTIVATED);
        assertThat(journal.get("strategy_internal_id")).as("E2.1: радиус строки — определение").isEqualTo(definition);
        assertThat(journal.get("exchange_account_internal_id")).isEqualTo(Trail.ACCOUNT);
        assertThat(journal.get("instrument_internal_id")).isEqualTo(Trail.INSTRUMENT);
        assertStatisticsSilent("E2.1");
        assertExchangeSilent("E2.1");
        assertThat(trail.records(Substrate.CORE_TOPIC)).as("E2.1: ядро событий не публикует").isEmpty();

        Map<String, Object> copy = awaitCopy(definition);
        Database core = trail.database(Party.TRADING_CORE);
        assertThat(copy.get("status")).as("E2.1: копия активна").isEqualTo("ACTIVE");
        assertThat(copy.get("exchange_account_id")).as("E2.1: счёт резолвлен в ключ базы ядра")
                .isEqualTo(keyOf(core, "exchange_accounts", Trail.ACCOUNT));
        assertThat(copy.get("instrument_id")).as("E2.1: инструмент резолвлен в ключ базы ядра")
                .isEqualTo(keyOf(core, "instruments", Trail.INSTRUMENT));
        assertThat(DefinitionTree.ofCopy(core, definition)).as("E2.1: дерево копии поэлементно равно снимку")
                .isEqualTo(DefinitionTree.ofSnapshot(snapshotOf(published.getFirst())));
        assertThat(core.query("select event_type from inbox_events where event_id = ?", eventId))
                .as("E2.1: строка inbox по идентичности события").hasSize(1);
    }

    @Test
    @DisplayName("E2.2 — Имена конверта у публикатора и у обоих потребителей совпадают")
    void e2_2_envelopeNamesAgreeAcrossThePublisherAndBothConsumers() {
        trail.retireActiveDefinitions();
        String definition = trail.createDefinition();
        trail.moveDefinition(definition, "ACTIVE");
        String eventId = String.valueOf(outboxRow(definition, ACTIVATED).get("event_id"));

        trail.relayOwner();

        ConsumerRecord<String, String> record = recordsOf(eventId).getFirst();
        Map<String, String> headers = headersOf(record);
        assertThat(headers).as("E2.2: обязательные имена конверта едут заголовками")
                .containsKeys("eventId", "eventType", "occurredAt", "version");
        assertThat(headers.keySet()).as("E2.2: тенант едет ключом записи, одноимённого заголовка нет")
                .noneMatch(name -> name.toLowerCase().contains("tenant"));
        assertThat(OffsetDateTime.parse(headers.get("occurredAt")).getOffset())
                .as("E2.2: момент — ISO-8601 со смещением").isNotNull();
        assertThat(headers.get("version")).as("E2.2: версия — десятичным числом").matches("\\d+");
        if (headers.containsKey("traceContext")) {
            assertThat(headers.get("traceContext")).as("E2.2: пустой заголовок не ставится вовсе").isNotBlank();
        }
        Map<String, Object> journal = awaitJournal(eventId);
        assertThat(journal.get("tenant_id")).as("E2.2: журнал взял тенанта из ключа").isEqualTo(Trail.TENANT);
        assertThat(journal.get("event_type")).isEqualTo(headers.get("eventType"));
        assertThat(String.valueOf(journal.get("version"))).isEqualTo(headers.get("version"));
        assertThat(journal.get("occurred_at")).as("E2.2: момент прочитан из заголовка").isNotNull();
        assertStatisticsSilent("E2.2");

        awaitCopy(definition);
        assertThat(trail.database(Party.TRADING_CORE)
                .query("select event_type from inbox_events where event_id = ?", eventId))
                .as("E2.2: ядро прочло идентичность и класс события по тем же именам")
                .extracting(row -> row.get("event_type")).containsExactly(headers.get("eventType"));
    }

    @Test
    @DisplayName("E2.3 — Повтор доставки: копия одна, строка журнала одна")
    void e2_3_aRedeliveryLeavesOneCopyAndOneJournalRow() {
        trail.retireActiveDefinitions();
        String definition = trail.createDefinition();
        trail.moveDefinition(definition, "ACTIVE");
        String eventId = String.valueOf(outboxRow(definition, ACTIVATED).get("event_id"));
        trail.relayOwner();
        awaitJournal(eventId);
        ConsumerRecord<String, String> original = recordsOf(eventId).getFirst();
        Long ownerOutbox = trail.database(Party.STRATEGIES).count("outbox_events");

        trail.produce(Substrate.STRATEGY_TOPIC, original.key(), original.value(), headersOf(original));

        Long end = trail.endOffset(Substrate.STRATEGY_TOPIC);
        Trail.await("E2.3: журнал продвинул смещение за повтор",
                () -> trail.committedOffset(AUDIT_GROUP, Substrate.STRATEGY_TOPIC) >= end);
        Trail.await("E2.3: ядро продвинуло смещение за повтор",
                () -> trail.committedOffset(CORE_GROUP, Substrate.STRATEGY_TOPIC) >= end);
        assertThat(trail.database(Party.AUDIT).query("select id from audit_records where event_id = ?", eventId))
                .as("E2.3: строка журнала одна").hasSize(1);
        assertThat(trail.database(Party.STRATEGIES).count("outbox_events"))
                .as("E2.3: владелец потребителем не является — его состояние не тронуто").isEqualTo(ownerOutbox);
        assertStatisticsSilent("E2.3");

        Database core = trail.database(Party.TRADING_CORE);
        assertThat(core.query("select id from strategies where internal_id = ?", definition))
                .as("E2.3: копия одна").hasSize(1);
        assertThat(core.query("select id from inbox_events where event_id = ?", eventId))
                .as("E2.3: строка inbox одна").hasSize(1);
    }

    @Test
    @DisplayName("E2.5 — Деактивация двигает только статус копии, дерево не переписывается")
    void e2_5_deactivationMovesOnlyTheCopyStatus() {
        trail.retireActiveDefinitions();
        String definition = trail.createDefinition();
        trail.moveDefinition(definition, "ACTIVE");
        trail.relayOwner();
        String activation = String.valueOf(outboxRow(definition, ACTIVATED).get("event_id"));
        awaitJournal(activation);
        awaitCopy(definition);
        Database core = trail.database(Party.TRADING_CORE);
        List<Object> keysBefore = DefinitionTree.nodeKeys(core, definition);
        List<String> treeBefore = DefinitionTree.ofCopy(core, definition);
        trail.forgetTraces();

        assertThat(trail.moveDefinition(definition, "INACTIVE").status()).isEqualTo(200);
        trail.relayOwner();

        String deactivation = String.valueOf(outboxRow(definition, DEACTIVATED).get("event_id"));
        assertThat(ownerStatusOf(definition)).isEqualTo("INACTIVE");
        assertThat(outboxRow(definition, DEACTIVATED).get("published_at")).as("E2.5: вторая строка опубликована")
                .isNotNull();
        assertThat(awaitJournal(deactivation).get("event_type")).isEqualTo(DEACTIVATED);
        assertStatisticsSilent("E2.5");
        assertExchangeSilent("E2.5");
        Trail.await("E2.5: статус копии", () -> core.query("select status from strategies where internal_id = ?",
                definition).stream().anyMatch(row -> Objects.equals("INACTIVE", row.get("status"))));
        assertThat(DefinitionTree.ofCopy(core, definition)).as("E2.5: дерево то же").isEqualTo(treeBefore);
        assertThat(DefinitionTree.nodeKeys(core, definition))
                .as("E2.5: ни одна строка поддерева не пересоздана").isEqualTo(keysBefore);
        assertThat(core.query("select id from inbox_events where event_id = ?", deactivation)).hasSize(1);
    }

    @Test
    @DisplayName("E2.6 — Факт о определении, копии которого у ядра не было, приём не роняет")
    void e2_6_aFactAboutAnUncopiedDefinitionDoesNotStopTheReception() {
        trail.retireActiveDefinitions();
        String untouched = trail.createDefinition();
        assertThat(trail.moveDefinition(untouched, "DELETED").status()).isEqualTo(200);
        trail.relayOwner();
        String deletion = String.valueOf(outboxRow(untouched, DELETED).get("event_id"));
        String second = trail.createDefinition();
        trail.moveDefinition(second, "ACTIVE");
        trail.relayOwner();
        String activation = String.valueOf(outboxRow(second, ACTIVATED).get("event_id"));

        assertThat(awaitJournal(deletion).get("event_type")).isEqualTo(DELETED);
        assertThat(awaitJournal(activation).get("event_type")).isEqualTo(ACTIVATED);
        assertThat(ownerStatusOf(second)).isEqualTo("ACTIVE");
        assertThat(trail.database(Party.STRATEGIES).query("select status from strategies where internal_id = ?",
                untouched)).as("E2.6: первое определение удалено у владельца")
                .extracting(row -> row.get("status")).containsExactly("DELETED");
        assertStatisticsSilent("E2.6");

        Database core = trail.database(Party.TRADING_CORE);
        awaitCopy(second);
        assertThat(core.query("select id from strategies where internal_id = ?", untouched))
                .as("E2.6: копии удалённого не заводится").isEmpty();
        assertThat(core.query("select id from inbox_events where event_id = ?", deletion))
                .as("E2.6: удаление помечено обработанным").hasSize(1);
    }

    // ---------------------------------------------------------------- чтения

    private static Map<String, Object> outboxRow(String definition, String eventType) {
        List<Map<String, Object>> rows = trail.database(Party.STRATEGIES).query(
                "select * from outbox_events where event_type = ? and payload ->> 'strategyInternalId' = ?",
                eventType, definition);
        assertThat(rows).as("строка outbox класса " + eventType + " определения " + definition).hasSize(1);
        return rows.getFirst();
    }

    private static String ownerStatusOf(String definition) {
        Trail.Answer answer = trail.call(Party.STRATEGIES, "GET", Trail.STRATEGIES + "/" + definition,
                Trail.TENANT, null);
        assertThat(answer.status()).as("чтение определения у владельца — " + answer.body()).isEqualTo(200);
        return String.valueOf(Json.object(answer.body()).get("status"));
    }

    private static List<ConsumerRecord<String, String>> recordsOf(String eventId) {
        return trail.records(Substrate.STRATEGY_TOPIC).stream()
                .filter(record -> Objects.equals(eventId, headersOf(record).get("eventId")))
                .toList();
    }

    private static Map<String, String> headersOf(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return headers;
    }

    private static tools.jackson.databind.JsonNode snapshotOf(ConsumerRecord<String, String> record) {
        return Json.tree(record.value()).path("definition");
    }

    private static Map<String, Object> awaitJournal(String eventId) {
        Trail.await("строка журнала события " + eventId, () -> trail.database(Party.AUDIT)
                .query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return trail.database(Party.AUDIT).query("select * from audit_records where event_id = ?", eventId)
                .getFirst();
    }

    private static Map<String, Object> awaitCopy(String definition) {
        Database core = trail.database(Party.TRADING_CORE);
        Trail.await("копия определения " + definition + " у ядра",
                () -> core.query("select id from strategies where internal_id = ?", definition).size() == 1);
        return core.query("select * from strategies where internal_id = ?", definition).getFirst();
    }

    private static Object keyOf(Database core, String table, String internalId) {
        return core.query("select id from " + table + " where internal_id = ?", internalId).getFirst().get("id");
    }

    /** Статистика тему владельца определений не читает: ни фактов, ни пары «группа × тема». */
    private static void assertStatisticsSilent(String label) {
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("incident_facts")).as(label + ": фактов статистики нет").isZero();
        assertThat(statistics.query("select id from reception_states where topic = ?", Substrate.STRATEGY_TOPIC))
                .as(label + ": пары с темой владельца определений у статистики нет").isEmpty();
    }

    /** Коннектор и площадка на активации не участвуют. */
    private static void assertExchangeSilent(String label) {
        assertThat(trail.accesses(Party.CONNECTOR)).as(label + ": к коннектору обращений нет").isEmpty();
        assertThat(trail.exchange().requests()).as(label + ": к стабу площадки обращений нет").isEmpty();
    }
}
