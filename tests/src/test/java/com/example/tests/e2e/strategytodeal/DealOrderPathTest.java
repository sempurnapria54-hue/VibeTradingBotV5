package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.example.tests.e2e.strategytodeal.DealTrace.awaitIncident;
import static com.example.tests.e2e.strategytodeal.DealTrace.awaitJournal;
import static com.example.tests.e2e.strategytodeal.DealTrace.dealRead;
import static com.example.tests.e2e.strategytodeal.DealTrace.paths;
import static com.example.tests.e2e.strategytodeal.DealTrace.present;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E4} тропы «активация определения → сделка»: заявка доходит
 * до площадки через коннектор
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E4 — Заявка доходит до площадки
 * через коннектор»).
 *
 * <p><b>Каждый кейс начинает с состояния после {@code E3.1}</b> — своей
 * сделки на паре без заявок, — и стаб площадки перед ним возвращается к
 * общему предусловию: кейс отказа перекрывает ответ на постановку своим.
 *
 * <p><b>Вход кейсов — проходы оркестратора, а не один тик.</b> Проход
 * дробит работу по звену за раз — снимок средств, заведение заявки, её
 * отправка, — и число проходов есть свойство тропы
 * ({@link Trail#passUntil}).
 */
@Tag("e2e")
@DisplayName("E4 — Заявка доходит до площадки через коннектор")
class DealOrderPathTest {

    private static final String CONNECTOR_ORDERS = "/api/v1/accounts/" + Trail.ACCOUNT + "/orders";

    private static final List<String> SIGNATURE_HEADERS =
            List.of("OK-ACCESS-KEY", "OK-ACCESS-SIGN", "OK-ACCESS-TIMESTAMP", "OK-ACCESS-PASSPHRASE");

    private static Trail trail;

    private String dealId;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("e4");
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @BeforeEach
    void openDealOverTheCopy() {
        trail.exchangeAcceptsCommands();
        trail.withoutDeals();
        trail.activeDefinition();
        dealId = trail.openDeal();
        trail.relayCore();
        trail.forgetTraces();
    }

    @Test
    @DisplayName("E4.1 — Проход оркестратора: решение о заявке, команда коннектору, подписанный запрос у стаба площадки")
    void e4_1_theOrchestratorPassDecidesTheOrderAndItReachesTheExchangeSigned() {
        Database core = trail.database(Party.TRADING_CORE);

        trail.passUntil("E4.1: заявка заведена", () -> present(core.query("select id from orders")));

        Map<String, Object> order = core.query("select * from orders").getFirst();
        String clientId = String.valueOf(order.get("internal_id"));
        Map<String, Object> decided = decidedOutbox(clientId);
        assertThat(decided.get("published_at")).as("E4.1: строка решения о заявке заведена вместе со строкой заявки")
                .isNull();

        trail.entrySubmitted();
        trail.relayCore();

        assertThat(core.query("select external_id from orders where internal_id = ?", clientId))
                .as("E4.1: зеркало несёт внешний идентификатор из подтверждения")
                .extracting(row -> row.get("external_id")).containsExactly(Trail.EXTERNAL_ORDER);
        assertThat(decidedOutbox(clientId).get("published_at")).as("E4.1: реле пометило строку").isNotNull();
        List<Side.Access> placements = trail.accesses(Party.CONNECTOR).stream()
                .filter(access -> Objects.equals("POST", access.method())
                        && Objects.equals(CONNECTOR_ORDERS, access.path()))
                .toList();
        assertThat(placements).as("E4.1: к коннектору пришла одна команда постановки").hasSize(1);
        assertThat(placements.getFirst().bearer()).as("E4.1: команда несёт токен службы, выданный провайдером")
                .isEqualTo(trail.identity().issuedServiceToken());
        List<LoggedRequest> commands = commands();
        assertThat(paths(commands)).as("E4.1: площадке ровно две команды — плечо, затем постановка")
                .containsExactly("POST " + Trail.EXCHANGE_LEVERAGE, "POST " + Trail.EXCHANGE_ORDER);
        assertThat(commands).as("E4.1: обе команды подписаны").allSatisfy(command ->
                SIGNATURE_HEADERS.forEach(header -> assertThat(command.getHeader(header)).as(header).isNotBlank()));
        assertThat(Json.tree(commands.getFirst().getBodyAsString()).path("lever").asString())
                .as("E4.1: плечо — назначенное плечо пары").isEqualTo("10");
        String eventId = String.valueOf(decided.get("event_id"));
        assertThat(awaitJournal(trail, eventId).get("event_type")).as("E4.1: строка журнала решения о заявке")
                .isEqualTo(DealTrace.ORDER_DECIDED);
        assertThat(awaitIncident(trail, eventId).get("event_type")).as("E4.1: факт происшествия того же класса")
                .isEqualTo(DealTrace.ORDER_DECIDED);
        assertThat(trail.accesses(Party.STRATEGIES)).as("E4.1: к владельцу определений обращений нет").isEmpty();
    }

    @Test
    @DisplayName("E4.2 — Клиентский идентификатор ядра доезжает до площадки неизменным")
    void e4_2_theCoreClientIdReachesTheExchangeUnchanged() {
        Database core = trail.database(Party.TRADING_CORE);

        trail.entrySubmitted();
        trail.relayCore();

        String clientId = String.valueOf(core.query("select internal_id from orders").getFirst().get("internal_id"));
        LoggedRequest placement = commands().getLast();
        assertThat(Json.tree(placement.getBodyAsString()).path("clOrdId").asString())
                .as("E4.2: clOrdId у площадки посимвольно равен идентификатору заявки ядра").isEqualTo(clientId);
        assertThat(clientId).as("E4.2: маркер источника сохранён").startsWith("vtb");

        trail.orchestrate();

        assertThat(core.query("select internal_id from orders")).as("E4.2: идентификатор не пересоздан тиком")
                .extracting(row -> row.get("internal_id")).containsExactly(clientId);
        String tranche = dealRead(trail, dealId).path("tranches").get(0).path("internalId").asString();
        String eventId = String.valueOf(decidedOutbox(clientId).get("event_id"));
        assertThat(awaitJournal(trail, eventId).get("content").toString())
                .as("E4.2: строка журнала решения несёт идентичность транша").contains(tranche);
        assertThat(trail.database(Party.STATISTICS).query(
                "select column_name from information_schema.columns where table_name = 'incident_facts'"))
                .as("E4.2: идентичности заявки у факта происшествия нет вовсе")
                .extracting(row -> String.valueOf(row.get("column_name")))
                .isNotEmpty()
                .noneMatch(column -> column.contains("order"));
    }

    @Test
    @DisplayName("E4.3 — Ключи счёта берёт коннектор, и ядро их не видит")
    void e4_3_theConnectorTakesTheAccountKeysAndTheCoreNeverSeesThem() {
        trail.entrySubmitted();

        List<LoggedRequest> commands = commands();
        assertThat(commands).as("E4.3: команды подписаны ключами счёта из хранилища").isNotEmpty()
                .allSatisfy(command -> {
                    assertThat(command.getHeader("OK-ACCESS-KEY")).isEqualTo(Substrate.apiKeyOf(Trail.ACCOUNT));
                    assertThat(command.getHeader("OK-ACCESS-PASSPHRASE")).isEqualTo("passphrase-" + Trail.ACCOUNT);
                    assertThat(command.getHeaders().getHeader("OK-ACCESS-SIGN").values())
                            .as("E4.3: подпись ровно одна").hasSize(1);
                });
        List<String> secrets = List.of(Substrate.apiKeyOf(Trail.ACCOUNT), "secret-" + Trail.ACCOUNT,
                "passphrase-" + Trail.ACCOUNT);
        assertThat(trail.accesses(Party.CONNECTOR)).as("E4.3: исходящие ядра к коннектору — токен службы, не ключи")
                .isNotEmpty()
                .allSatisfy(access -> {
                    assertThat(access.bearer()).isEqualTo(trail.identity().issuedServiceToken());
                    secrets.forEach(secret -> assertThat(access.uri()).doesNotContain(secret));
                });
        String coreContent = coreDump();
        secrets.forEach(secret -> assertThat(coreContent).as("E4.3: в базе ядра нет " + secret)
                .doesNotContain(secret));
        assertThat(trail.accesses(Party.STRATEGIES)).as("E4.3: к владельцу определений обращений нет").isEmpty();
    }

    @Test
    @DisplayName("E4.4 — Отказ площадки: ядро заявку отправленной не считает")
    void e4_4_anExchangeRejectionIsNotTakenForASubmission() {
        Database core = trail.database(Party.TRADING_CORE);
        trail.exchangeRejectsPlacement();

        trail.passUntil("E4.4: постановка ушла площадке", () -> present(placements()));
        trail.relayCore();

        Map<String, Object> order = core.query("select internal_id, external_id from orders").getFirst();
        String clientId = String.valueOf(order.get("internal_id"));
        assertThat(order.get("external_id")).as("E4.4: внешнего идентификатора у заявки нет").isNull();
        trail.passUntil("E4.4: сделка в объявленном состоянии разбора отказа",
                () -> Objects.equals("ERROR", dealRead(trail, dealId).path("status").asString()));
        assertThat(trail.accesses(Party.CONNECTOR)).as("E4.4: коннектор перевёл отказ в свою форму и отдал ядру")
                .filteredOn(access -> Objects.equals(CONNECTOR_ORDERS, access.path()))
                .extracting(Side.Access::status).containsExactly(200);
        String eventId = String.valueOf(decidedOutbox(clientId).get("event_id"));
        awaitJournal(trail, eventId);
        awaitIncident(trail, eventId);
        trail.forgetTraces();

        for (Integer pass = 0; pass < 3; pass++) {
            trail.orchestrate();
        }
        trail.relayCore();

        assertThat(placements()).as("E4.4: второй постановки нет — сделка в разборе отказа").isEmpty();
        assertThat(trail.exchange().requests()).as("E4.4: обращения площадке — чтения тем же clOrdId")
                .isNotEmpty()
                .filteredOn(request -> request.getUrl().contains("clOrdId="))
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.getUrl()).contains("clOrdId=" + clientId));
        assertThat(core.query("select id from outbox_events where event_type = ?", DealTrace.ORDER_DECIDED))
                .as("E4.4: второго решения о заявке нет").hasSize(1);
        assertThat(trail.database(Party.AUDIT).query(
                "select id from audit_records where deal_internal_id = ? and event_type = ?", dealId,
                DealTrace.ORDER_DECIDED)).as("E4.4: строка решения о заявке у журнала одна").hasSize(1);
        assertThat(trail.database(Party.STATISTICS).query(
                "select event_id from incident_facts where event_id = ?", eventId))
                .as("E4.4: факт происшествия один").hasSize(1);
    }

    @Test
    @Tag("debt")
    @DisplayName("E4.5 — Сторона-коннектор недостижима: команда не считается исполненной")
    void e4_5_anUnreachableConnectorLeavesTheCommandUnexecuted() {
        Database core = trail.database(Party.TRADING_CORE);
        trail.passUntil("E4.5: заявка заведена локально", () -> present(core.query("select id from orders")));
        String clientId = String.valueOf(core.query("select internal_id from orders").getFirst().get("internal_id"));
        trail.stop(Party.CONNECTOR);
        try {
            trail.forgetTraces();

            trail.orchestrate();
            trail.orchestrate();

            assertThat(core.query("select external_id from orders where internal_id = ?", clientId))
                    .as("E4.5: внешнего идентификатора у заявки нет").extracting(row -> row.get("external_id"))
                    .containsOnlyNulls();
            assertThat(trail.exchange().requests()).as("E4.5: к площадке обращений нет ни одного").isEmpty();
            trail.relayCore();
            assertThat(core.query("select event_type from outbox_events where payload ->> 'dealInternalId' = ?",
                    dealId)).as("E4.5: след ограничен классом решения о заявке — класса отправки нет")
                    .extracting(row -> row.get("event_type"))
                    .containsExactlyInAnyOrder(DealTrace.DEAL_OPENED, DealTrace.ORDER_DECIDED);
        } finally {
            trail.start(Party.CONNECTOR);
        }
        assertThat(dealRead(trail, dealId).path("status").asString())
                .as("E4.5: молчащий коннектор статуса не двигает — проход пропущен").isEqualTo("ACTIVE");

        trail.entrySubmitted();

        assertThat(Json.tree(placements().getLast().getBodyAsString()).path("clOrdId").asString())
                .as("E4.5: после подъёма коннектора команда дошла тем же клиентским идентификатором")
                .isEqualTo(clientId);
    }

    @Test
    @DisplayName("E4.6 — Отказ площадки на команде плеча: постановки за ним нет")
    void e4_6_aLeverageRejectionStopsThePlacement() {
        Database core = trail.database(Party.TRADING_CORE);
        trail.exchangeRejectsLeverage();

        trail.passUntil("E4.6: команда плеча ушла площадке", () -> present(commands()));
        for (Integer pass = 0; pass < 2; pass++) {
            trail.orchestrate();
        }

        assertThat(paths(commands())).as("E4.6: площадке ушли только команды плеча — постановки нет")
                .isNotEmpty().containsOnly("POST " + Trail.EXCHANGE_LEVERAGE);
        assertThat(core.query("select external_id from orders")).as("E4.6: у заявки внешнего идентификатора нет")
                .extracting(row -> row.get("external_id")).containsOnlyNulls();
    }

    @Test
    @Tag("debt")
    @DisplayName("E4.7 — Площадка нашла заявку по клиентскому идентификатору: второй постановки нет")
    void e4_7_aFoundOrderIsRecoveredWithoutASecondPlacement() {
        Database core = trail.database(Party.TRADING_CORE);
        trail.exchange().failsPostTransportOnce(Trail.EXCHANGE_ORDER);

        trail.passUntil("E4.7: постановка ушла площадке", () -> present(placements()));
        Map<String, Object> order = core.query("select internal_id, size from orders").getFirst();
        String clientId = String.valueOf(order.get("internal_id"));
        String size = ((BigDecimal) order.get("size")).stripTrailingZeros().toPlainString();
        trail.exchange().answers(Trail.EXCHANGE_ORDER, """
                {"code": "0", "msg": "", "data": [{"instId": "%s", "ordId": "%s", "clOrdId": "%s",
                  "ordType": "market", "side": "buy", "posSide": "net", "state": "live", "px": "",
                  "sz": "%s", "accFillSz": "0", "avgPx": "", "cTime": "1758240000000", "uTime": "1758240001000"}]}
                """.formatted(Trail.EXTERNAL_INSTRUMENT, Trail.EXTERNAL_ORDER, clientId, size));
        trail.forgetTraces();

        trail.passUntil("E4.7: повтор нашёл заявку по клиентскому идентификатору", () -> present(core.query(
                "select id from orders where external_id is not null")));

        assertThat(placements()).as("E4.7: второй постановки нет — факт отправки восстановлен").isEmpty();
        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E4.7: повтор искал заявку тем же клиентским идентификатором").isNotEmpty()
                .allSatisfy(request -> assertThat(request.getUrl()).contains("clOrdId=" + clientId));
        assertThat(core.query("select internal_id, external_id from orders"))
                .as("E4.7: зеркало несёт биржевой идентификатор найденной заявки")
                .extracting(row -> row.get("internal_id"), row -> row.get("external_id"))
                .containsExactly(tuple(clientId, Trail.EXTERNAL_ORDER));
    }

    // ---------------------------------------------------------------- чтения

    private Map<String, Object> decidedOutbox(String clientId) {
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE).query(
                "select * from outbox_events where event_type = ? and payload ->> 'orderInternalId' = ?",
                DealTrace.ORDER_DECIDED, clientId);
        assertThat(rows).as("строка решения о заявке " + clientId).hasSize(1);
        return rows.getFirst();
    }

    /** Постановки заявки у площадки, в порядке прихода. */
    private static List<LoggedRequest> placements() {
        return commands().stream()
                .filter(request -> request.getUrl().startsWith(Trail.EXCHANGE_ORDER))
                .toList();
    }

    /** Команды площадке — мутирующие обращения, в порядке прихода. */
    private static List<LoggedRequest> commands() {
        return trail.exchange().requests().stream()
                .filter(request -> Objects.equals("POST", request.getMethod().getName()))
                .toList();
    }

    /** Всё содержимое базы ядра текстом — таблица за таблицей. */
    private static String coreDump() {
        Database core = trail.database(Party.TRADING_CORE);
        StringBuilder dump = new StringBuilder();
        core.query("select table_name from information_schema.tables where table_schema = 'public'")
                .forEach(row -> core.query("select * from " + row.get("table_name"))
                        .forEach(line -> dump.append(line).append('\n')));
        return dump.toString();
    }
}
