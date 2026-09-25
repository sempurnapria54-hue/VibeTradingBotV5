package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.IdentityStub;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.example.tests.e2e.perimeterread.StreamTrace.CONTEXT;
import static com.example.tests.e2e.perimeterread.StreamTrace.JOURNAL;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.header;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E8} тропы периметра: порядок, отсутствие следов и повтор тропы
 * (.claude/tests/cases/e2e-perimeter-read.md §«E8 — Порядок, отсутствие следов
 * и повтор тропы»).
 *
 * <p><b>Тропа у группы одна, и порядок методов несущий:</b> {@code E8.1}
 * проходит её с первого хода — контекста на пустом кэше, — через пролог до
 * факта производителя; по дороге снимаются оба снимка {@code E8.3}: после
 * пролога и после чтений всех владельцев. {@code E8.3} сверяет их и доводит
 * тропу до состояния {@code E6.3}, {@code E8.2} разбирает следы тропы целиком,
 * {@code E8.4} повторяет её ходы, {@code E8.5} читает отсутствие выходов.
 * Журналы стабов и доступа не забываются ни разу после подъёма сторон.
 *
 * <p><b>Пульс — раз в секунду:</b> ответ открытия подписки приходит с первой
 * записью (находка {@code F-11} ящика периметра), и подписке, по которой
 * факта нет, — у {@code E8.4} их две — ответить иначе нечем.
 *
 * <p><b>Отметка собственного момента снимками не сравнивается:</b> строку
 * состояния приёма тик журнала и статистики переписывает каждым тактом
 * независимо от тропы (.claude/rules/codestyle.md §Джобы), и её расхождение
 * между снимками — такт, а не ход тропы.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E8 — Порядок, отсутствие следов и повтор тропы")
class PerimeterIntegrityPathTest {

    private static final String SUBJECT = "subject-s1";

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String MEMBERSHIPS = "/api/v1/auth/memberships/self";

    private static final String ROWS = "/api/v1/statistics/aggregates/rows";

    private static final String PULSE_KEY = "perimeter.stream.pulse-interval";

    private static final String ISSUER_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    private static final String PERIMETER_GROUP = "bff-";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final String NEVER = "0 0 0 1 1 *";

    private static final String RECEPTION_STATES = "reception_states";

    private static final List<Party> KEEPERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS, Party.AUTH);

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    private static Subscription first;

    private static final List<Subscription> repeated = new ArrayList<>();

    private static String firstTicket;

    private static String orderEvent;

    private static Map<String, String> reads;

    private static Map<String, String> settledReads;

    private static Map<String, List<String>> afterPrologue;

    private static Map<String, List<String>> afterReads;

    private static final Map<Party, Integer> prologueMarks = new EnumMap<>(Party.class);

    private static final Map<Party, Integer> factMoveMarks = new EnumMap<>(Party.class);

    private static final Map<Party, Integer> afterFactMarks = new EnumMap<>(Party.class);

    private static Integer exchangeBeforeFactMove;

    private static Integer exchangeAfterFact;

    private static Integer exchangeAfterPrologue;

    @BeforeAll
    static void openTrailWithEmptyCache() {
        trail = Trail.openPerimeter("p12");
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(PULSE_KEY, "1s");
        trail.start(Party.BFF);
        token = trail.identity().browserToken(SUBJECT, "Trader One");
    }

    @AfterAll
    static void closeTrail() {
        repeated.forEach(Subscription::close);
        if (nonNull(first)) {
            first.close();
        }
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E8.1 — След стороны не появляется раньше причины на предыдущей")
    void e8_1_aTraceNeverPrecedesItsCauseOnThePreviousSide() {
        Database auth = trail.database(Party.AUTH);
        assertThat(auth.count("tenants")).as("E8.1: до запроса контекста тенантов у владельца нет").isZero();
        assertThat(auth.count("memberships")).as("E8.1: и членств нет").isZero();

        Answer context = viaPerimeter("GET", CONTEXT, null);

        assertThat(context.status()).as("E8.1: контекст выведен — " + context.body()).isEqualTo(200);
        assertThat(auth.count("tenants")).as("E8.1: после контекста тенант заведён").isEqualTo(1L);
        assertThat(auth.count("memberships")).as("E8.1: и членство").isEqualTo(1L);

        prologue = Prologue.walkToOpenDeal(trail, token);
        awaitPerimeterCaughtUp();
        mark(prologueMarks);
        exchangeAfterPrologue = trail.exchange().requests().size();
        afterPrologue = snapshot();

        Answer unticketed = viaPerimeter("GET", "/api/v1/bff/stream", null);
        assertThat(unticketed.status()).as("E8.1: до выдачи билета подписку открыть нечем — " + unticketed.body())
                .isBetween(400, 499);
        firstTicket = ticket(trail, token);
        first = Subscription.open(trail, firstTicket);
        assertThat(first.status()).as("E8.1: билетом подписка открыта").isEqualTo(200);

        reads = readAll("E8.1");
        afterReads = snapshot();
        mark(factMoveMarks);
        exchangeBeforeFactMove = trail.exchange().requests().size();

        trail.entrySubmitted();
        List<Map<String, Object>> outbox = trail.database(Party.TRADING_CORE).query(
                "select event_id, published_at from outbox_events where event_type = ?", ORDER_DECIDED);
        assertThat(outbox).as("E8.1: проход завёл строку outbox решения о заявке").hasSize(1);
        orderEvent = String.valueOf(outbox.getFirst().get("event_id"));
        assertThat(outbox.getFirst().get("published_at")).as("E8.1: до тика реле она не опубликована").isNull();
        assertThat(eventIds(Substrate.CORE_TOPIC)).as("E8.1: до тика реле записи в теме нет")
                .doesNotContain(orderEvent);
        assertThat(first.facts()).as("E8.1: до тика реле на соединении её нет").isEmpty();
        assertThat(trail.database(Party.AUDIT).query("select id from audit_records where event_id = ?", orderEvent))
                .as("E8.1: до приёма строки журнала нет").isEmpty();
        assertThat(journalRead()).as("E8.1: и чтение через периметр её не находит").doesNotContain(orderEvent);

        trail.relayCore();

        assertThat(first.awaitId(orderEvent).type()).as("E8.1: после тика реле запись пришла на соединение")
                .isEqualTo(ORDER_DECIDED);
        assertThat(eventIds(Substrate.CORE_TOPIC)).as("E8.1: и лежит в теме").contains(orderEvent);
        awaitJournal(trail, orderEvent);
        assertThat(journalRead()).as("E8.1: после приёма чтение через периметр строку находит")
                .contains(orderEvent);
        mark(afterFactMarks);
        exchangeAfterFact = trail.exchange().requests().size();
    }

    @Test
    @Order(2)
    @DisplayName("E8.3 — Тропа состояния владельцев не изменила, и исключений ровно два")
    void e8_3_theTrailLeftOwnerStatesUnchangedSaveItsOwnMoves() {
        assertThat(changed(afterPrologue, afterReads))
                .as("E8.3: ни одно чтение через периметр не изменило ни одной строки ни у одного владельца")
                .isEmpty();

        Answer command = viaPerimeter("PUT", Trail.CORE + "/risk-appetites/" + prologue.tenant(), """
                {
                  "globalSimultaneousRiskPerDealPercent": 4,
                  "globalCatastrophicRiskPerDealMultiplier": 100,
                  "globalConsecutiveLossLimit": 4
                }
                """);
        assertThat(command.status()).as("E8.3: команда пользователя принята — " + command.body()).isEqualTo(200);
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E8.3: факт принят статистикой", () -> isFalse(statistics
                .query("select event_id from incident_facts where event_id = ?", orderEvent).isEmpty()));
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        Trail.await("E8.3: такт сложил сутки с решением о заявке", () -> isFalse(statistics.query(
                "select id from incident_aggregates where tenant_id = ? and order_decisions = 1",
                prologue.tenant()).isEmpty()));
        trail.statisticsRecomputes(NEVER);
        settledReads = readAll("E8.3");

        Map<String, Set<String>> changed = changed(afterPrologue, snapshot());

        assertThat(changed.getOrDefault(Party.AUTH.module(), Set.of()))
                .as("E8.3: у владельца членств не изменилось ничего").isEmpty();
        assertThat(changed.getOrDefault(Party.STRATEGIES.module(), Set.of()))
                .as("E8.3: у владельца определений не изменилось ничего").isEmpty();
        assertThat(changed.get(Party.TRADING_CORE.module()))
                .as("E8.3: у ядра — числа риск-аппетита команды и след прохода хода E3.1: сделка и состояния её "
                        + "действий, зеркало заявки и её защиты, снимок средств, строка outbox; копии определений нет")
                .containsExactlyInAnyOrder("public.tenant_risk_appetites", "public.deals",
                        "public.deal_strategy_action_states", "public.deal_system_action_states", "public.orders",
                        "public.attached_algo_orders", "public.balances", "public.balance_containers",
                        "public.exchange_accounts", "public.outbox_events");
        assertThat(changed.get(Party.AUDIT.module())).as("E8.3: у журнала — только строка журнала того же факта")
                .containsExactly("public.audit_records");
        assertThat(changed.get(Party.STATISTICS.module()))
                .as("E8.3: у статистики — строки факта и агрегата")
                .contains("public.incident_facts", "public.incident_aggregates");
    }

    @Test
    @Order(3)
    @DisplayName("E8.2 — Сторона вне тропы следа не оставляет ни одного")
    void e8_2_noSideOffTheTrailIsTouched() {
        assertThat(trail.side(Party.CONNECTOR).accessSince(prologueMarks.get(Party.CONNECTOR)).stream()
                .limit(factMoveMarks.get(Party.CONNECTOR) - prologueMarks.get(Party.CONNECTOR)).toList())
                .as("E8.2: на отрезке подписки и чтений к коннектору не ушло ни одного вызова").isEmpty();
        assertThat(exchangeBeforeFactMove).as("E8.2: и к стабу площадки — ни одного запроса")
                .isEqualTo(exchangeAfterPrologue);
        assertThat(trail.side(Party.CONNECTOR).accessSince(afterFactMarks.get(Party.CONNECTOR)))
                .as("E8.2: после хода E3.1 к коннектору не ушло ни одного вызова").isEmpty();
        assertThat(trail.exchange().requests()).as("E8.2: и к стабу площадки — ни одного запроса")
                .hasSize(exchangeAfterFact);

        List<LoggedRequest> marketData = trail.marketData().requests();
        List<LoggedRequest> browserReads = marketData.stream().filter(PerimeterIntegrityPathTest::carriesToken)
                .toList();
        assertThat(marketData).as("E8.2: у стаба market-data каждое чтение приписано поводу — чтению браузера "
                        + "либо сборке контекста ядра")
                .isNotEmpty()
                .allMatch(request -> carriesToken(request) || coreBuildsContext(request));
        assertThat(browserReads).as("E8.2: чтений браузера у стаба ровно столько, сколько их прошло периметром")
                .hasSize((int) trail.accesses(Party.BFF).stream()
                        .filter(access -> access.under(Trail.PEER_INSTRUMENTS))
                        .count());
        assertThat(trail.side(Party.AUTH).accessSince(prologueMarks.get(Party.AUTH)))
                .as("E8.2: к владельцу членств после пролога — только такты резолва")
                .allMatch(access -> Objects.equals("POST", access.method())
                        && Objects.equals(MEMBERSHIPS, access.path()));
        assertThat(trail.side(Party.STRATEGIES).accessSince(prologueMarks.get(Party.STRATEGIES)))
                .as("E8.2: у владельца определений после пролога — только чтения, торговых ходов нет")
                .isNotEmpty()
                .allMatch(access -> Objects.equals("GET", access.method()) || access.isProbe());
        assertThat(trail.identity().requests()).as("E8.2: к провайдеру — диспетчер, ключи и выдача токена")
                .extracting(LoggedRequest::getUrl)
                .isNotEmpty()
                .allMatch(url -> Set.of("/.well-known/openid-configuration", "/jwks", "/token").contains(url));
    }

    @Test
    @Order(4)
    @DisplayName("E8.4 — Повтор тропы целиком даёт то же состояние")
    void e8_4_replayingTheWholeTrailYieldsTheSameState() {
        Map<String, List<String>> before = snapshot();
        Long outbox = trail.database(Party.TRADING_CORE).count("outbox_events");

        Answer context = viaPerimeter("GET", CONTEXT, null);
        String secondTicket = ticket(trail, token);
        Subscription second = Subscription.open(trail, secondTicket);
        repeated.add(second);
        Subscription again = Subscription.open(trail, firstTicket);
        repeated.add(again);
        Map<String, String> reread = readAll("E8.4");
        trail.relayCore();

        assertThat(context.status()).as("E8.4: контекст отвечает тем же тенантом — " + context.body())
                .isEqualTo(200);
        assertThat(Json.object(context.body()).get("tenantId")).isEqualTo(prologue.tenant());
        assertThat(trail.database(Party.AUTH).count("tenants")).as("E8.4: второго тенанта не завелось")
                .isEqualTo(1L);
        assertThat(trail.database(Party.AUTH).count("memberships")).as("E8.4: членств столько же").isEqualTo(1L);
        assertThat(second.status()).as("E8.4: вторая подписка открыта в пределах потолка").isEqualTo(200);
        assertThat(again.status()).as("E8.4: прежний билет не отменён и в пределах срока годен").isEqualTo(200);
        assertThat(first.isOpen()).as("E8.4: первая подписка жива").isTrue();
        assertThat(reread).as("E8.4: ответы чтений те же").isEqualTo(settledReads);
        assertThat(trail.database(Party.TRADING_CORE).count("outbox_events"))
                .as("E8.4: повторный тик реле новых строк outbox не завёл").isEqualTo(outbox);
        assertThat(changed(before, snapshot())).as("E8.4: у владельцев новых строк нет, повторной доставки "
                + "факта журналу и статистике не произошло").isEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("E8.5 — Отсутствие выходов у тропы целиком")
    void e8_5_theTrailAsAWholeHasNoAbsentOutputs() {
        refuseAtThePerimeter();
        refuseAtTheOwner();

        assertThat(trail.topics()).as("E8.5: тем ровно две — периметр ни одной не завёл")
                .containsExactlyInAnyOrder(Substrate.CORE_TOPIC, Substrate.STRATEGY_TOPIC);
        Set<String> published = new TreeSet<>();
        for (Party producer : List.of(Party.TRADING_CORE, Party.STRATEGIES)) {
            trail.database(producer).query("select event_id from outbox_events")
                    .forEach(row -> published.add(String.valueOf(row.get("event_id"))));
        }
        List<ConsumerRecord<String, String>> unpublished = new ArrayList<>();
        for (String topic : List.of(Substrate.CORE_TOPIC, Substrate.STRATEGY_TOPIC)) {
            trail.records(topic).stream()
                    .filter(record -> isFalse(published.contains(header(record, "eventId"))))
                    .forEach(unpublished::add);
        }
        assertThat(unpublished).as("E8.5: каждая запись тем — строка outbox производителя; сверх — только "
                        + "первый факт ряда, положенный прошлыми сутками")
                .singleElement()
                .satisfies(record -> assertThat(OffsetDateTime.parse(header(record, "occurredAt")).toLocalDate())
                        .isBefore(LocalDate.now(ZoneOffset.UTC)));
        assertThat(trail.database(Party.AUTH).query("select datname from pg_database where datname like 'p12\\_%'"))
                .as("E8.5: своей базы у периметра нет — баз ровно столько, сколько владельцев")
                .extracting(row -> String.valueOf(row.get("datname")))
                .containsExactlyInAnyOrder("p12_core", "p12_strategies", "p12_audit", "p12_statistics", "p12_auth");
        assertThat(ownerCalls()).as("E8.5: каждый вызов владельцу под токеном браузера имеет входящий вызов "
                + "периметра, его породивший").isEqualTo(perimeterForwards());
        assertThat(trail.database(Party.TRADING_CORE).hasTable("audit_records")
                || trail.database(Party.TRADING_CORE).hasTable("incident_facts")
                || trail.database(Party.TRADING_CORE).hasTable("tenants"))
                .as("E8.5: в базе ядра чужих таблиц нет").isFalse();
        for (Party party : List.of(Party.STRATEGIES, Party.AUDIT, Party.STATISTICS, Party.AUTH)) {
            assertThat(trail.database(party).hasTable("deals")).as("E8.5: в базе " + party.module()
                    + " таблиц ядра нет").isFalse();
        }
        assertThat(trail.database(Party.AUDIT).hasTable("incident_facts")
                || trail.database(Party.AUDIT).hasTable("tenants")).as("E8.5: в базе журнала чужих таблиц нет")
                .isFalse();
        assertThat(trail.database(Party.STATISTICS).hasTable("audit_records")
                || trail.database(Party.STATISTICS).hasTable("tenants"))
                .as("E8.5: в базе статистики чужих таблиц нет").isFalse();
        assertThat(trail.exchange().requests().stream()
                .filter(request -> Objects.equals("POST", request.getMethod().getName()))
                .map(LoggedRequest::getUrl)
                .toList())
                .as("E8.5: команды площадке — ровно плечо и постановка входа пролога; торговых команд тропы нет")
                .containsExactly(Trail.EXCHANGE_LEVERAGE, Trail.EXCHANGE_ORDER);
        assertThat(trail.database(Party.AUDIT).count("access_denials"))
                .as("E8.5: у журнала одна строка отказа — от E2.6").isEqualTo(1L);
        for (Party party : List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.STATISTICS, Party.AUTH)) {
            Database database = trail.database(party);
            if (database.hasTable("access_denials")) {
                assertThat(database.count("access_denials")).as("E8.5: у " + party.module()
                        + " строк отказа нет — отвергнутые периметром вызовы до владельцев не дошли").isZero();
            }
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static Answer viaPerimeter(String method, String path, String body) {
        return trail.callWith(token, Party.BFF, method, path, null, body);
    }

    /**
     * Чтения всех владельцев через периметр — по представителю на владельца;
     * пути собираются один раз, чтобы повтор читал ровно то же.
     */
    private static Map<String, String> readAll(String label) {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Map<String, String> answers = new LinkedHashMap<>();
        for (String path : List.of(journalPath(), ROWS + "?grain=INCIDENT&from=" + day + "&to=" + day,
                Trail.STRATEGIES, Trail.CORE + "/deals?exchangeAccountInternalId=" + prologue.account(),
                Trail.CORE + "/risk-appetites/" + prologue.tenant(), Trail.PEER_INSTRUMENTS)) {
            Answer answer = viaPerimeter("GET", path, null);
            assertThat(answer.status()).as(label + ": чтение " + path + " — " + answer.body()).isEqualTo(200);
            answers.put(path, Json.tree(answer.body()).toString());
        }
        return answers;
    }

    private static String journalPath() {
        OffsetDateTime day = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        return JOURNAL + "?from=" + day.minusDays(1) + "&to=" + day.plusDays(1);
    }

    /** Идентичности событий в чтении журнала через периметр. */
    private static List<String> journalRead() {
        Answer answer = viaPerimeter("GET", journalPath(), null);
        assertThat(answer.status()).as("чтение журнала — " + answer.body()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        Json.tree(answer.body()).path("records").forEach(record -> ids.add(record.path("eventId").asString()));
        return ids;
    }

    private static List<String> eventIds(String topic) {
        return trail.records(topic).stream().map(record -> header(record, "eventId")).toList();
    }

    /** Ход {@code E7.4}: три отказа периметра — без токена, чужой подписью, подделанным билетом. */
    private static void refuseAtThePerimeter() {
        IdentityStub foreign = new IdentityStub();
        try {
            assertThat(trail.callWith("", Party.BFF, "GET", journalPath(), null, null).status())
                    .as("E8.5: чтение без токена отвергнуто периметром").isEqualTo(401);
            assertThat(trail.callWith(foreign.browserToken(SUBJECT, "Trader One"), Party.BFF, "GET", journalPath(),
                    null, null).status()).as("E8.5: чтение чужой подписью отвергнуто периметром").isEqualTo(401);
            assertThat(viaPerimeter("GET", "/api/v1/bff/stream?ticket=forged-ticket", null).status())
                    .as("E8.5: подделанный билет отвергнут периметром").isEqualTo(401);
        } finally {
            foreign.stop();
        }
    }

    /** Ход {@code E2.6}: владелец, поднятый с чужим издателем, отвергает чтение своей проверкой. */
    private static void refuseAtTheOwner() {
        IdentityStub foreign = new IdentityStub();
        String ownIssuer = trail.identity().issuer();
        restartAudit(foreign.issuer());
        try {
            assertThat(viaPerimeter("GET", journalPath(), null).status())
                    .as("E8.5: владелец отверг чтение своей проверкой подписи").isEqualTo(401);
        } finally {
            restartAudit(ownIssuer);
            foreign.stop();
        }
    }

    private static void restartAudit(String issuer) {
        trail.stop(Party.AUDIT);
        trail.side(Party.AUDIT).set(ISSUER_KEY, issuer);
        trail.start(Party.AUDIT);
    }

    /** Вызовы владельцам под токеном браузера, кроме тактов резолва, — методом и строкой запроса. */
    private static List<String> ownerCalls() {
        List<String> calls = new ArrayList<>();
        for (Party owner : KEEPERS) {
            trail.accesses(owner).stream()
                    .filter(access -> Objects.equals(token, access.bearer()))
                    .filter(access -> isFalse(Objects.equals(MEMBERSHIPS, access.path())))
                    .forEach(access -> calls.add(access.method() + " " + access.uri()));
        }
        trail.marketData().requests().stream()
                .filter(PerimeterIntegrityPathTest::carriesToken)
                .forEach(request -> calls.add(request.getMethod().getName() + " " + request.getUrl()));
        return calls.stream().sorted().toList();
    }

    /** Вызовы периметра под токеном браузера на поверхность владельцев. */
    private static List<String> perimeterForwards() {
        return trail.accesses(Party.BFF).stream()
                .filter(access -> Objects.equals(token, access.bearer()))
                .filter(access -> isFalse(access.under(Party.BFF.root())))
                .map(access -> access.method() + " " + access.uri())
                .sorted()
                .toList();
    }

    private static Boolean carriesToken(LoggedRequest request) {
        return Objects.equals("Bearer " + token, request.getHeader("Authorization"));
    }

    /** Чтения сборки контекста ядра: синк проекций на прологе и раскладка фич на проходе. */
    private static Boolean coreBuildsContext(LoggedRequest request) {
        String call = request.getMethod().getName() + " " + request.getUrl();
        return Set.of("GET " + Trail.PEER_INSTRUMENTS, "GET " + Trail.PEER_INSTRUMENTS + "/" + Trail.INSTRUMENT
                + "/rules", "POST " + Trail.PEER_FEATURES).contains(call);
    }

    private static void mark(Map<Party, Integer> marks) {
        for (Party party : Party.values()) {
            marks.put(party, trail.side(party).accessMark());
        }
    }

    /**
     * Снимок строк всех владельцев: сторона → таблица → строки, упорядоченные
     * текстом. Строка состояния приёма — отметка собственного момента такта —
     * в снимок не входит.
     */
    private static Map<String, List<String>> snapshot() {
        Map<String, List<String>> tables = new TreeMap<>();
        for (Party owner : KEEPERS) {
            Database database = trail.database(owner);
            for (Map<String, Object> table : database.query("select table_schema || '.' || table_name as name "
                    + "from information_schema.tables where table_type = 'BASE TABLE' "
                    + "and table_schema not in ('pg_catalog', 'information_schema') "
                    + "and table_schema not like '\\_timescaledb%' and table_name <> ?", RECEPTION_STATES)) {
                String name = String.valueOf(table.get("name"));
                tables.put(owner.module() + "|" + name, database.query("select * from " + name).stream()
                        .map(Object::toString)
                        .sorted()
                        .toList());
            }
        }
        return tables;
    }

    /** Таблицы, чьё содержимое разошлось между снимками: сторона → таблицы. */
    private static Map<String, Set<String>> changed(Map<String, List<String>> before,
                                                    Map<String, List<String>> after) {
        Map<String, Set<String>> changed = new TreeMap<>();
        Set<String> names = new TreeSet<>(before.keySet());
        names.addAll(after.keySet());
        for (String name : names) {
            if (isFalse(Objects.equals(before.get(name), after.get(name)))) {
                String[] parts = name.split("\\|");
                changed.computeIfAbsent(parts[0], owner -> new TreeSet<>()).add(parts[1]);
            }
        }
        return changed;
    }

    /** Периметр дочитал обе темы — факт пролога не приедет на подписку, открытую после него. */
    private static void awaitPerimeterCaughtUp() {
        Trail.await("периметр дочитал обе темы", () -> {
            Map<String, Set<String>> groups = trail.consumingGroups(PERIMETER_GROUP);
            if (groups.size() != 1) {
                return Boolean.FALSE;
            }
            String group = groups.keySet().iterator().next();
            return Objects.equals(trail.committedOffset(group, Substrate.CORE_TOPIC),
                    trail.endOffset(Substrate.CORE_TOPIC))
                    && Objects.equals(trail.committedOffset(group, Substrate.STRATEGY_TOPIC),
                    trail.endOffset(Substrate.STRATEGY_TOPIC));
        });
    }
}
