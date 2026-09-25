package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.strategytodeal.DealTrace.awaitIncident;
import static com.example.tests.e2e.strategytodeal.DealTrace.awaitJournal;
import static com.example.tests.e2e.strategytodeal.DealTrace.coreOutbox;
import static com.example.tests.e2e.strategytodeal.DealTrace.coreRecordsOf;
import static com.example.tests.e2e.strategytodeal.DealTrace.dealRead;
import static com.example.tests.e2e.strategytodeal.DealTrace.paths;
import static com.example.tests.e2e.strategytodeal.DealTrace.present;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E3} тропы «активация определения → сделка»: вход по копии
 * заводит сделку, и её событие доезжает до обоих потребителей
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E3 — Вход по копии: сделка и её
 * событие»).
 *
 * <p><b>Каждый кейс начинает с пары без сделки</b> ({@link Trail#withoutDeals()}):
 * сделку на паре снять нечем, кроме её терминала, и кейс, пришедший после
 * соседа, получает свежее развёртывание ядра. Журнал, статистика и темы при
 * этом общие на класс, поэтому след отбирается идентичностью события либо
 * сделки, а не счётом строк.
 *
 * <p><b>Порядок ассертов двойной</b>: до тика реле ядра проверяется, что
 * следа потребителей ещё нет, и только потом — что он появился
 * (§«Новая ось формы — СТОРОНА ТРОПЫ И ЕЁ СЛЕД» документа).
 */
@Tag("e2e")
@DisplayName("E3 — Вход по копии: сделка и её событие")
class DealEntryPathTest {

    private static Trail trail;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e3");
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @DisplayName("E3.1 — Тик сканера входа по копии заводит сделку, и её событие доезжает до обоих потребителей")
    void e3_1_anEntryScanOverTheCopyOpensADealAndItsEventReachesBothConsumers() {
        trail.withoutDeals();
        trail.activeDefinition();
        trail.marketFavoursEntry();
        Long ownerOutbox = trail.database(Party.STRATEGIES).count("outbox_events");
        trail.forgetTraces();

        trail.scanEntries();

        List<JsonNode> deals = trail.deals();
        assertThat(deals).as("E3.1: на паре одна сделка").hasSize(1);
        JsonNode deal = deals.getFirst();
        String dealId = deal.path("internalId").asString();
        assertThat(deal.path("status").asString()).as("E3.1: сделка в активном статусе").isEqualTo("ACTIVE");
        assertThat(deal.path("exchangeAccountInternalId").asString()).isEqualTo(Trail.ACCOUNT);
        assertThat(deal.path("instrumentInternalId").asString()).isEqualTo(Trail.INSTRUMENT);
        assertThat(dealRead(trail, dealId).path("tranches")).as("E3.1: транш материализован по объявлению детали")
                .extracting(tranche -> tranche.path("entryStepType").asString())
                .containsExactly("ENTRY");
        Map<String, Object> outbox = coreOutbox(trail, dealId, DealTrace.DEAL_OPENED);
        assertThat(outbox.get("published_at")).as("E3.1: строка outbox заведена и ещё не опубликована").isNull();
        String eventId = String.valueOf(outbox.get("event_id"));
        assertThat(coreRecordsOf(trail, eventId)).as("E3.1: до тика реле ядра тема ядра не несёт события").isEmpty();
        assertThat(trail.database(Party.AUDIT).query("select id from audit_records where event_id = ?", eventId))
                .as("E3.1: до тика реле строки журнала нет").isEmpty();
        assertThat(paths(trail.marketData().requests()))
                .as("E3.1: к владельцу рыночных данных пришли только чтения раскладки фич")
                .isNotEmpty().containsOnly("POST " + Trail.PEER_FEATURES);
        assertThat(trail.accesses(Party.CONNECTOR)).as("E3.1: к коннектору — ровно одно чтение биржевого момента")
                .extracting(Side.Access::method, Side.Access::path)
                .containsExactly(tuple("GET", DealTrace.MARKET_TIME));
        assertThat(paths(trail.exchange().requests()))
                .as("E3.1: до площадки дошло только чтение её момента, команд нет")
                .containsExactly("GET " + Trail.EXCHANGE_TIME);

        trail.relayCore();

        assertThat(coreOutbox(trail, dealId, DealTrace.DEAL_OPENED).get("published_at"))
                .as("E3.1: реле пометило строку").isNotNull();
        List<ConsumerRecord<String, String>> published = coreRecordsOf(trail, eventId);
        assertThat(published).as("E3.1: в теме ядра одна запись события").hasSize(1);
        assertThat(published.getFirst().key()).as("E3.1: ключ записи — тенант").isEqualTo(Trail.TENANT);
        assertThat(awaitJournal(trail, eventId).get("event_type")).as("E3.1: строка журнала класса создания сделки")
                .isEqualTo(DealTrace.DEAL_OPENED);
        assertThat(awaitIncident(trail, eventId).get("event_type")).as("E3.1: факт происшествия этого класса")
                .isEqualTo(DealTrace.DEAL_OPENED);
        Database statistics = trail.database(Party.STATISTICS);
        assertThat(statistics.count("deal_facts")).as("E3.1: строк сделочного зерна нет ни одной").isZero();
        assertThat(statistics.count("deal_aggregates")).as("E3.1: строк агрегата нет — тик пересчёта не подавался")
                .isZero();
        assertThat(statistics.count("incident_aggregates")).isZero();
        assertThat(trail.accesses(Party.STRATEGIES)).as("E3.1: к владельцу определений обращений нет").isEmpty();
        assertThat(trail.database(Party.STRATEGIES).count("outbox_events"))
                .as("E3.1: владелец потребителем не является — его outbox не тронут").isEqualTo(ownerOutbox);
    }

    @Test
    @DisplayName("E3.2 — Сделка закрепляет деталь копии, а событие несёт идентичность определения и фазу входа")
    void e3_2_theDealPinsTheCopyDetailAndTheEventCarriesTheDefinitionAndThePhase() {
        trail.withoutDeals();
        String definition = trail.activeDefinition();
        trail.marketFavoursEntry();
        trail.forgetTraces();

        trail.scanEntries();
        trail.relayCore();

        String dealId = trail.deals().getFirst().path("internalId").asString();
        Database core = trail.database(Party.TRADING_CORE);
        List<Map<String, Object>> pinned = core.query("""
                select d.entry_market_phase as entry_phase, sd.market_phase_type as detail_phase,
                       s.internal_id as definition
                from deals d
                join strategy_details sd on sd.id = d.strategy_detail_id
                join strategies s on s.id = sd.strategy_id
                where d.internal_id = ?
                """, dealId);
        assertThat(pinned).as("E3.2: закреплённая деталь разрешается в поддерево копии этого определения")
                .extracting(row -> row.get("definition"), row -> row.get("detail_phase"),
                        row -> row.get("entry_phase"))
                .containsExactly(tuple(definition, "BULL_TREND", "BULL_TREND"));
        String eventId = String.valueOf(coreOutbox(trail, dealId, DealTrace.DEAL_OPENED).get("event_id"));
        JsonNode payload = Json.tree(coreRecordsOf(trail, eventId).getFirst().value());
        assertThat(payload.path("strategyInternalId").asString())
                .as("E3.2: содержимое несёт идентичность определения").isEqualTo(definition);
        assertThat(payload.path("entryMarketPhase").asString()).as("E3.2: содержимое несёт фазу входа")
                .isEqualTo("BULL_TREND");
        assertThat(awaitJournal(trail, eventId).get("strategy_internal_id"))
                .as("E3.2: радиус строки журнала — определение, которое вернул владелец").isEqualTo(definition);
        Map<String, Object> incident = awaitIncident(trail, eventId);
        assertThat(incident.get("tenant_id")).as("E3.2: факт несёт тенанта").isEqualTo(Trail.TENANT);
        assertThat(incident.get("exchange_account_internal_id")).as("E3.2: факт несёт счёт")
                .isEqualTo(Trail.ACCOUNT);
        assertThat(trail.database(Party.STATISTICS).query(
                "select column_name from information_schema.columns where table_name = 'incident_facts'"))
                .as("E3.2: идентичностей сделки и определения у зерна происшествий нет вовсе")
                .extracting(row -> String.valueOf(row.get("column_name")))
                .isNotEmpty()
                .noneMatch(column -> column.contains("deal") || column.contains("strategy"));
        assertThat(trail.accesses(Party.STRATEGIES)).as("E3.2: к владельцу определений обращений нет").isEmpty();
        assertThat(paths(trail.exchange().requests())).as("E3.2: площадке — только чтение момента")
                .containsExactly("GET " + Trail.EXCHANGE_TIME);
    }

    @Test
    @DisplayName("E3.3 — Деактивация у владельца закрывает вход у ядра")
    void e3_3_aDeactivationAtTheOwnerClosesTheEntryAtTheCore() {
        trail.withoutDeals();
        String definition = trail.activeDefinition();
        assertThat(trail.moveDefinition(definition, "INACTIVE").status()).isEqualTo(200);
        trail.relayOwner();
        Database core = trail.database(Party.TRADING_CORE);
        Trail.await("E3.3: копия у ядра деактивирована", () -> present(core.query(
                "select id from strategies where internal_id = ? and status = 'INACTIVE'", definition)));
        Trail.await("E3.3: строка журнала деактивации", () -> present(trail.database(Party.AUDIT).query(
                "select id from audit_records where strategy_internal_id = ? and event_type = ?",
                definition, "STRATEGY_DEACTIVATED")));
        trail.marketFavoursEntry();
        Integer coreTopic = trail.records(Substrate.CORE_TOPIC).size();
        Long journal = trail.database(Party.AUDIT).count("audit_records");
        Long incidents = trail.database(Party.STATISTICS).count("incident_facts");
        trail.forgetTraces();

        trail.scanEntries();
        trail.relayCore();

        assertThat(trail.deals()).as("E3.3: сделок нет ни одной").isEmpty();
        assertThat(core.count("outbox_events")).as("E3.3: строк outbox у ядра нет").isZero();
        assertThat(trail.marketData().requests())
                .as("E3.3: чтения фич не состоялось вовсе — отбор кончился раньше").isEmpty();
        assertThat(trail.records(Substrate.CORE_TOPIC)).as("E3.3: тема ядра не прибавила записей")
                .hasSize(coreTopic);
        assertThat(trail.database(Party.AUDIT).count("audit_records"))
                .as("E3.3: у журнала следа сверх деактивации нет").isEqualTo(journal);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.3: у статистики следа нет")
                .isEqualTo(incidents);
        assertThat(trail.accesses(Party.CONNECTOR)).as("E3.3: к коннектору обращений нет").isEmpty();
        assertThat(trail.exchange().requests()).as("E3.3: к площадке обращений нет").isEmpty();
    }

    @Test
    @DisplayName("E3.4 — Удаление определения у владельца живую сделку детали не лишает")
    void e3_4_deletingTheDefinitionLeavesTheLiveDealItsDetail() {
        trail.withoutDeals();
        String definition = trail.activeDefinition();
        String dealId = trail.openDeal();
        Database core = trail.database(Party.TRADING_CORE);
        List<String> treeBefore = DefinitionTree.ofCopy(core, definition);
        Long incidents = trail.database(Party.STATISTICS).count("incident_facts");

        assertThat(trail.moveDefinition(definition, "DELETED").status()).isEqualTo(200);
        trail.relayOwner();

        Trail.await("E3.4: статус копии DELETED", () -> present(core.query(
                "select id from strategies where internal_id = ? and status = 'DELETED'", definition)));
        assertThat(DefinitionTree.ofCopy(core, definition)).as("E3.4: строка копии и всё её поддерево на месте")
                .isNotEmpty().isEqualTo(treeBefore);
        assertThat(core.query("""
                select sd.id from deals d join strategy_details sd on sd.id = d.strategy_detail_id
                where d.internal_id = ?
                """, dealId)).as("E3.4: закреплённая деталь сделки по-прежнему разрешается").hasSize(1);
        assertThat(dealRead(trail, dealId).path("status").asString())
                .as("E3.4: сделка читается поверхностью и не терминальна").isEqualTo("ACTIVE");
        Trail.Answer owner = trail.call(Party.STRATEGIES, "GET", Trail.STRATEGIES + "/" + definition, Trail.TENANT,
                null);
        assertThat(Json.tree(owner.body()).path("status").asString())
                .as("E3.4: владелец читает определение логически удалённым").isEqualTo("DELETED");
        Trail.await("E3.4: строка журнала класса удаления", () -> present(trail.database(Party.AUDIT).query(
                "select id from audit_records where strategy_internal_id = ? and event_type = ?",
                definition, "STRATEGY_DELETED")));
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.4: у статистики следа нет")
                .isEqualTo(incidents);

        trail.scanEntries();

        assertThat(trail.deals()).as("E3.4: следующий тик сканера сделок не заводит")
                .extracting(opened -> opened.path("internalId").asString()).containsExactly(dealId);
        assertThat(core.query("select id from outbox_events where event_type = ?", DealTrace.DEAL_OPENED))
                .as("E3.4: второго события создания сделки нет").hasSize(1);
    }
}
