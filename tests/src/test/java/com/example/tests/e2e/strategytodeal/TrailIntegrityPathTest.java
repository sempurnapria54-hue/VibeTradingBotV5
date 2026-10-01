package com.example.tests.e2e.strategytodeal;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.SharedStand;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.example.tests.e2e.strategytodeal.DealTrace.awaitIncident;
import static com.example.tests.e2e.strategytodeal.DealTrace.coreOutbox;
import static com.example.tests.e2e.strategytodeal.DealTrace.paths;
import static com.example.tests.e2e.strategytodeal.DealTrace.present;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E8} тропы «активация определения → сделка», кейсы
 * {@code E8.1}, {@code E8.2}, {@code E8.3}, {@code E8.5}: порядок, отсутствие
 * следов и повтор тропы
 * (.claude/tests/cases/e2e-strategy-to-deal.md §«E8 — Порядок, отсутствие следов
 * и повтор тропы»). Кейс {@code E8.4} поднимает потребителей после публикации
 * и живёт своей тропой — {@code LateConsumerPathTest}.
 *
 * <p><b>Тропа у группы одна, и порядок методов несущий:</b> {@code E8.1}
 * проходит её ход за ходом с начала, {@code E8.2} доводит до состояния
 * {@code E7.1} и читает всё, что пришло на стабы с подъёма, {@code E8.5}
 * читает отсутствие выходов на том же состоянии, {@code E8.3} подаёт тики
 * повторно. Журналы стабов не забываются ни разу после подъёма сторон —
 * {@code E8.2} утверждает о тропе целиком, включая её предусловия.
 *
 * <p><b>Такт пересчёта</b> подаётся подъёмом статистики с сокращённым
 * расписанием — до него расписание не бьёт, и состояние «фактов нет в
 * агрегате» наблюдаемо.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E8 — Порядок, отсутствие следов и повтор тропы")
class TrailIntegrityPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static Trail trail;

    private static String definition;

    private static String dealId;

    @BeforeAll
    static void openTrail() {
        trail = SharedStand.dealPath(TrailIntegrityPathTest.class);
        trail.factSeriesStartedYesterday();
        trail.commonPreconditions();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            SharedStand.release(TrailIntegrityPathTest.class);
        }
    }

    @Test
    @Order(1)
    @DisplayName("E8.1 — След стороны не появляется раньше причины на предыдущей")
    void e8_1_aTraceNeverPrecedesItsCauseOnThePreviousSide() {
        Database core = trail.database(Party.TRADING_CORE);
        Database audit = trail.database(Party.AUDIT);
        definition = trail.createDefinition();
        assertThat(trail.moveDefinition(definition, "ACTIVE").status()).isEqualTo(200);

        assertThat(trail.published(Substrate.STRATEGY_TOPIC)).as("E8.1: до тика реле владельца в тему не легло ничего")
                .isEmpty();
        assertThat(core.query("select id from strategies where internal_id = ?", definition))
                .as("E8.1: до тика реле копии у ядра нет").isEmpty();
        assertThat(audit.query("select id from audit_records where strategy_internal_id = ?", definition))
                .as("E8.1: до тика реле строк журнала нет").isEmpty();

        trail.relayOwner();
        Trail.await("E8.1: копия у ядра заведена", () -> present(core.query(
                "select id from strategies where internal_id = ? and status = 'ACTIVE'", definition)));

        assertThat(trail.deals()).as("E8.1: до тика сканера сделок нет").isEmpty();

        trail.marketFavoursEntry();
        trail.scanEntries();
        dealId = trail.deals().getFirst().path("internalId").asString();
        String opened = String.valueOf(coreOutbox(trail, dealId, DealTrace.DEAL_OPENED).get("event_id"));

        assertThat(DealTrace.coreRecordsOf(trail, opened)).as("E8.1: до тика реле ядра тема ядра события не несёт")
                .isEmpty();
        assertThat(audit.query("select id from audit_records where deal_internal_id = ?", dealId))
                .as("E8.1: до тика реле ядра строк журнала классов ядра нет").isEmpty();
        assertThat(trail.database(Party.STATISTICS).query("select event_id from incident_facts where event_id = ?",
                opened)).as("E8.1: до тика реле ядра фактов статистики нет").isEmpty();

        trail.relayCore();
        awaitIncident(trail, opened);

        assertThat(trail.rows(Party.STATISTICS, "incident_aggregates"))
                .as("E8.1: до такта пересчёта строк агрегата нет").isZero();

        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E8.1: такт пересчёта собрал строку суток тропы", () -> present(trail.database(Party.STATISTICS)
                .query("select id from incident_aggregates where "
                        + Trail.BY_TENANT + " and bucket_date = ?", trail.tenant(), today())));
    }

    @Test
    @Order(2)
    @DisplayName("E8.2 — Сторона вне тропы следа не оставляет ни одного")
    void e8_2_noSideOffTheTrailIsTouched() {
        walkToTheFilledEntry();

        assertThat(paths(trail.auth().requests())).as("E8.2: к стабу auth — только чтения реестра счетов")
                .isNotEmpty().containsOnly("GET " + Trail.PEER_ACCOUNTS);
        assertThat(paths(trail.marketData().requests()))
                .as("E8.2: к стабу market-data — каталог, правила и раскладка фич; истории и записи нет")
                .isNotEmpty()
                .containsOnly("GET " + Trail.PEER_INSTRUMENTS, "GET " + Trail.PEER_INSTRUMENTS + "/"
                        + Trail.INSTRUMENT + "/rules", "POST " + Trail.PEER_FEATURES);
        List<String> exchange = paths(trail.exchange().requests());
        assertThat(exchange).as("E8.2: у площадки — ставка комиссии, три среза детекции, момент, средства "
                        + "и конфигурация счёта, плечо, постановка, добыча заявки и её защиты; иных обращений нет")
                .containsOnly("GET " + Trail.EXCHANGE_FEE, "GET " + Trail.EXCHANGE_POSITIONS,
                        "GET " + Trail.EXCHANGE_ORDERS_PENDING, "GET " + Trail.EXCHANGE_TIME,
                        "GET " + Trail.EXCHANGE_BALANCE, "GET " + Trail.EXCHANGE_ACCOUNT_CONFIG,
                        "POST " + Trail.EXCHANGE_LEVERAGE,
                        "POST " + Trail.EXCHANGE_ORDER, "GET " + Trail.EXCHANGE_ORDER,
                        "GET " + Trail.EXCHANGE_ALGO_PENDING);
        assertThat(exchange.stream().filter(request -> request.startsWith("POST")).toList())
                .as("E8.2: команды площадке — ровно плечо и постановка; ни отмены, ни закрытия, ни условной")
                .containsExactly("POST " + Trail.EXCHANGE_LEVERAGE, "POST " + Trail.EXCHANGE_ORDER);
        assertThat(trail.identity().requests()).as("E8.2: к провайдеру — диспетчер, ключи и выдача токена; "
                        + "их самих может не быть — кэши сторон общего стенда прогреты прежними классами")
                .extracting(LoggedRequest::getUrl)
                .allMatch(url -> Set.of("/.well-known/openid-configuration", "/jwks", "/token").contains(url));
        assertThat(trail.side(Party.BFF)).as("E8.2: периметр не поднят вовсе — стороной тропы он не является")
                .isNull();
    }

    @Test
    @Order(3)
    @DisplayName("E8.5 — Отсутствие выходов у тропы целиком")
    void e8_5_theTrailAsAWholeHasNoAbsentOutputs() {
        Database core = trail.database(Party.TRADING_CORE);

        assertThat(trail.rows(Party.TRADING_CORE, "positions")).as("E8.5: позиции у сделки нет").isZero();
        assertThat(eventTypes(Substrate.CORE_TOPIC))
                .as("E8.5: в теме ядра только создание сделки и решение о заявке — терминала, ступеней и отчётов нет")
                .isNotEmpty().isSubsetOf(DealTrace.DEAL_OPENED, DealTrace.ORDER_DECIDED);
        Map<String, Object> incidents = trail.database(Party.STATISTICS).query(
                "select * from incident_aggregates where "
                        + Trail.BY_TENANT + " and bucket_date = ?", trail.tenant(), today()).getFirst();
        for (String zero : List.of("raised_holds", "hard_raised_holds", "manually_raised_holds", "anomaly_reports",
                "critical_anomaly_reports", "manual_operation_reports")) {
            assertThat(((Number) incidents.get(zero)).intValue()).as("E8.5: счётчик " + zero + " нулевой").isZero();
        }
        for (Party party : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS)) {
            Database database = trail.database(party);
            if (database.hasTable("access_denials")) {
                assertThat(database.count("access_denials"))
                        .as("E8.5: строк отказа доступа у " + party.module() + " нет").isZero();
            }
        }
        assertThat(core.hasTable("audit_records") || core.hasTable("incident_facts"))
                .as("E8.5: в базе ядра чужих таблиц нет").isFalse();
        assertThat(trail.database(Party.AUDIT).hasTable("deals")).as("E8.5: в базе журнала таблиц ядра нет")
                .isFalse();
        assertThat(trail.database(Party.STATISTICS).hasTable("deals"))
                .as("E8.5: в базе статистики таблиц ядра нет").isFalse();
        assertThat(trail.database(Party.STRATEGIES).hasTable("deals") || trail.database(Party.STRATEGIES)
                .hasTable("audit_records")).as("E8.5: в базе владельца определений чужих таблиц нет").isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("E8.3 — Повтор тропы целиком даёт то же состояние")
    void e8_3_replayingTheWholeTrailYieldsTheSameState() {
        Database core = trail.database(Party.TRADING_CORE);
        Long ownerOutbox = trail.rows(Party.STRATEGIES, "outbox_events");
        Long coreOutbox = trail.rows(Party.TRADING_CORE, "outbox_events");
        Long orders = trail.rows(Party.TRADING_CORE, "orders");
        Long journal = trail.rows(Party.AUDIT, "audit_records");
        Long facts = trail.rows(Party.STATISTICS, "incident_facts");
        Map<String, Object> before = todayIncidents();
        trail.forgetTraces();

        trail.relayOwner();
        trail.scanEntries();
        trail.orchestrate();
        trail.relayCore();
        Trail.await("E8.3: следующий такт пересчёта отработал", () -> isFalse(Objects.equals(
                before.get("assembled_at"), todayIncidents().get("assembled_at"))));

        assertThat(trail.rows(Party.STRATEGIES, "outbox_events"))
                .as("E8.3: у владельца новых строк outbox нет").isEqualTo(ownerOutbox);
        assertThat(trail.deals()).as("E8.3: сделка та же, второй на паре нет")
                .extracting(deal -> deal.path("internalId").asString()).containsExactly(dealId);
        assertThat(trail.rows(Party.TRADING_CORE, "orders")).as("E8.3: заявка та же, второй нет").isEqualTo(orders);
        assertThat(trail.rows(Party.TRADING_CORE, "outbox_events"))
                .as("E8.3: у ядра новых строк outbox нет").isEqualTo(coreOutbox);
        assertThat(paths(trail.exchange().requests())).as("E8.3: к площадке только чтения — второй постановки нет")
                .noneMatch(request -> request.startsWith("POST"));
        assertThat(trail.rows(Party.AUDIT, "audit_records")).as("E8.3: у журнала новых строк нет")
                .isEqualTo(journal);
        assertThat(trail.rows(Party.STATISTICS, "incident_facts")).as("E8.3: новых фактов нет")
                .isEqualTo(facts);
        Map<String, Object> after = todayIncidents();
        for (String counter : List.of("opened_deals", "order_decisions")) {
            assertThat(after.get(counter)).as("E8.3: число " + counter + " то же").isEqualTo(before.get(counter));
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Тропа до состояния {@code E7.1}: отправка входа, его налив, реле ядра, такт пересчёта. */
    private static void walkToTheFilledEntry() {
        Database core = trail.database(Party.TRADING_CORE);
        trail.entrySubmitted();
        trail.relayCore();
        trail.exchangeFillsEntry();
        trail.passUntil("E8.2: налив наблюдён", () -> present(core.query(
                "select id from orders where " + Trail.BY_DEAL + " and external_status = 'filled'", trail.account())));
        trail.relayCore();
        Trail.await("E8.2: строка суток собрала решение о заявке", () -> ((Number) todayIncidents()
                .get("order_decisions")).intValue() == 1);
    }

    private static Map<String, Object> todayIncidents() {
        return trail.database(Party.STATISTICS).query("select * from incident_aggregates where "
                + Trail.BY_TENANT + " and bucket_date = ?", trail.tenant(),
                today()).getFirst();
    }

    /**
     * Классы записей темы, несущих ключ тенанта тропы. Стенд общий: проход
     * детекции, которым предусловие ставит наблюдение, идёт по всем его
     * счетам, и у счетов прежних пар оставляет отчёты под их тенантами — это
     * след чужих пар, а не этой тропы.
     */
    private static List<String> eventTypes(String topic) {
        return trail.records(topic).stream()
                .filter(record -> Objects.equals(record.key(), trail.tenant()))
                .map(record -> record.headers().lastHeader("eventType"))
                .filter(Objects::nonNull)
                .map(Header::value)
                .map(value -> new String(value, StandardCharsets.UTF_8))
                .toList();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
