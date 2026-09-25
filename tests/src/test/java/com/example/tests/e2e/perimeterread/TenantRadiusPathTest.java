package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.perimeterread.StreamTrace.CONTEXT;
import static com.example.tests.e2e.perimeterread.StreamTrace.JOURNAL;
import static com.example.tests.e2e.perimeterread.StreamTrace.TICKETS;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFact;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static com.example.tests.e2e.perimeterread.StreamTrace.warmTheCache;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E5} тропы периметра: радиус тенанта на обеих тропах — чтении
 * и потоке (.claude/tests/cases/e2e-perimeter-read.md §«E5 — Радиус тенанта на
 * обеих тропах»). Клетка {@code E5.5} — свой класс
 * {@code SubscriptionCeilingPathTest}: она пролога не читает.
 *
 * <p><b>Факт чужого тенанта кладётся в тему напрямую</b> — так, как положил бы
 * производитель: всякий эмитент ядра работает внутри прохода по сделке, а у
 * чужого тенанта сделки нет. Счёт у факта — счёт тропы: иначе раздельность
 * строк у статистики держалась бы зерном счёта, а не тенантом.
 *
 * <p><b>Клетки упорядочены:</b> {@code E5.1} стоит на состоянии {@code E3.1},
 * {@code E5.3} — на состоянии {@code E5.2}, а {@code E5.4} поднимает периметр
 * заново со своими сроками.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E5 — Радиус тенанта на обеих тропах")
class TenantRadiusPathTest {

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String FOREIGN_TENANT = "tenant-t2-radius";

    private static final String MEMBERSHIPS = "/api/v1/auth/memberships/self";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static Trail trail;

    private static String token;

    private static String secondToken;

    private static String secondTenant;

    private static Prologue.Tenancy prologue;

    private static Subscription first;

    @BeforeAll
    static void standInTheStateOfE31() {
        trail = Trail.openPerimeter("p9");
        token = trail.identity().browserToken("subject-s1", "Trader One");
        secondToken = trail.identity().browserToken("subject-s2", "Trader Two");
        prologue = Prologue.walkToOpenDeal(trail, token);
        warmTheCache(trail, token);
        first = Subscription.open(trail, ticket(trail, token));
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
    @DisplayName("E5.1 — Факт чужого тенанта в провод не идёт, а в журнал ложится")
    void e5_1_aForeignTenantFactStaysOffTheWireAndLandsInTheJournal() {
        String foreign = produceFact(Substrate.CORE_TOPIC, FOREIGN_TENANT, "DEAL_OPENED", """
                {"dealInternalId": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s",
                 "strategyInternalId": "%s", "entryReason": "STRATEGY", "direction": "LONG",
                 "entryMarketPhase": "BULL_TREND"}
                """.formatted(UUID.randomUUID(), prologue.account(), Trail.INSTRUMENT, UUID.randomUUID()));

        trail.entrySubmitted();
        trail.relayCore();

        Frame own = first.awaitType(ORDER_DECIDED);
        assertThat(first.facts()).as("E5.1: запись факта T1 пришла, записи факта T2 на соединении нет")
                .extracting(Frame::id).containsExactly(own.id());
        assertThat(awaitJournal(trail, foreign).get("tenant_id")).as("E5.1: строка факта T2 в журнале — со своим тенантом")
                .isEqualTo(FOREIGN_TENANT);
        assertThat(awaitJournal(trail, own.id()).get("tenant_id")).as("E5.1: строка факта T1 — со своим")
                .isEqualTo(prologue.tenant());
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E5.1: факт T2 принят статистикой", () -> isFalse(statistics
                .query("select event_id from incident_facts where event_id = ?", foreign).isEmpty()));
        assertThat(statistics.query("select tenant_id from incident_facts where event_id = ?", foreign))
                .as("E5.1: строка факта T2 у статистики легла под своим тенантом")
                .extracting(row -> row.get("tenant_id")).containsExactly(FOREIGN_TENANT);
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Trail.await("E5.1: сутки T1 сложены с решением о заявке", () -> isFalse(statistics.query(
                "select id from incident_aggregates where tenant_id = ? and bucket_date = ? and order_decisions = 1",
                prologue.tenant(), today).isEmpty()));
        assertThat(statistics.query("select opened_deals from incident_aggregates where tenant_id = ?"
                        + " and exchange_account_internal_id = ? and bucket_date = ?",
                prologue.tenant(), prologue.account(), today))
                .as("E5.1: число агрегата T1 чужим фактом на том же счёте не выросло")
                .extracting(row -> row.get("opened_deals")).containsExactly(1);
        assertThat(first.isOpen()).as("E5.1: раздача жива").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("E5.2 — Второй субъект получает своего тенанта, и чтения радиусов не пересекаются")
    void e5_2_theSecondSubjectGetsItsOwnTenant() {
        Database auth = trail.database(Party.AUTH);
        List<Map<String, Object>> firstMemberships = auth.query("select * from memberships order by id");

        Answer context = trail.callWith(secondToken, Party.BFF, "GET", CONTEXT, null, null);

        assertThat(context.status()).as("E5.2: контекст второго субъекта — " + context.body()).isEqualTo(200);
        secondTenant = String.valueOf(Json.object(context.body()).get("tenantId"));
        assertThat(secondTenant).as("E5.2: субъекту S2 заведён свой тенант").isNotEqualTo(prologue.tenant());
        assertThat(auth.query("select role from memberships where user_id = ?", "subject-s2"))
                .as("E5.2: и своё членство OWNER").extracting(row -> row.get("role")).containsExactly("OWNER");
        assertThat(auth.query("select * from memberships where user_id = ? order by id", "subject-s1"))
                .as("E5.2: строки S1 не тронуты").isEqualTo(firstMemberships);
        Map<String, JsonNode> own = reads(token);
        Map<String, JsonNode> other = reads(secondToken);
        assertThat(own.get(JOURNAL).path("records")).as("E5.2: S1 видит строки пролога в журнале").isNotEmpty();
        assertThat(other.get(JOURNAL).path("records")).as("E5.2: выборка журнала S2 пуста").isEmpty();
        assertThat(own.get(Trail.STRATEGIES)).as("E5.2: S1 видит определение пролога").isNotEmpty();
        assertThat(other.get(Trail.STRATEGIES)).as("E5.2: выборка определений S2 пуста").isEmpty();
        assertThat(own.get(rowsPath()).path("incidentRows")).as("E5.2: S1 видит строку суток").isNotEmpty();
        assertThat(other.get(rowsPath()).path("incidentRows")).as("E5.2: выборка агрегатов S2 пуста").isEmpty();
        assertThat(other.get(dealsPath())).as("E5.2: сделки счёта T1 по операнду вызова читаются обоими — названная цена")
                .isEqualTo(own.get(dealsPath()));
    }

    @Test
    @Order(3)
    @DisplayName("E5.3 — Тенант билета выведен из членств, а не прислан и не выбран")
    void e5_3_theTicketTenantIsDerivedNotSent() {
        first.close();
        warmTheCache(trail, token);
        Answer issued = trail.callWith(token, Party.BFF, "POST", TICKETS, secondTenant, "");
        assertThat(issued.status()).as("E5.3: билет выдан — " + issued.body()).isEqualTo(200);
        String ownTicket = Json.tree(issued.body()).path("ticket").asString();
        List<String> fields = ticketFields(ownTicket);
        String otherTicket = ticket(trail, secondToken);

        assertThat(fields).as("E5.3: билет несёт субъекта, тенанта и срок — роли в нём нет")
                .hasSize(3).containsSequence("subject-s1", prologue.tenant());
        Subscription own = Subscription.open(trail, ownTicket);
        Subscription other = Subscription.open(trail, otherTicket);
        try {
            String ownFact = ownerFact(trail);
            String otherFact = produceFact(Substrate.STRATEGY_TOPIC, secondTenant, "STRATEGY_DELETED", """
                    {"strategyInternalId": "%s", "exchangeAccountInternalId": "%s", "instrumentInternalId": "%s",
                     "actor": "subject-s2"}
                    """.formatted(UUID.randomUUID(), prologue.account(), Trail.INSTRUMENT));
            own.awaitId(ownFact);
            other.awaitId(otherFact);
            assertThat(own.facts()).as("E5.3: подписка S1 несёт записи только его тенанта — присланное не прочитано")
                    .extracting(Frame::id).containsExactly(ownFact);
            assertThat(other.facts()).as("E5.3: билет S2 открывает подписку его радиуса")
                    .extracting(Frame::id).containsExactly(otherFact);
        } finally {
            own.close();
            other.close();
        }
    }

    @Test
    @Order(4)
    @DisplayName("E5.4 — Радиус потока держится билетом и переживает срок кэша членств")
    void e5_4_theStreamRadiusIsHeldByTheTicketAndOutlivesTheCache() {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set("perimeter.membership.cache-ttl", "2s");
        trail.side(Party.BFF).set("perimeter.ticket.ttl", "4s");
        trail.start(Party.BFF);
        warmTheCache(trail, token);
        Answer issued = trail.callWith(token, Party.BFF, "POST", TICKETS, null, "");
        Instant expires = OffsetDateTime.parse(Json.tree(issued.body()).path("expiresAt").asString()).toInstant();
        Subscription held = Subscription.open(trail, Json.tree(issued.body()).path("ticket").asString());
        try {
            String before = ownerFact(trail);
            held.awaitId(before);
            trail.forgetTraces();
            Awaitility.await("E5.4: сроки кэша и билета истекли")
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> Instant.now().isAfter(expires.plusSeconds(1)));

            String journal = journalPath();
            Answer firstRead = trail.callWith(token, Party.BFF, "GET", journal, null, null);
            Instant firstReadAt = Instant.now();
            Awaitility.await("E5.4: срок кэша истёк после первого чтения")
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> Instant.now().isAfter(firstReadAt.plusSeconds(3)));
            Answer secondRead = trail.callWith(token, Party.BFF, "GET", journal, null, null);
            String after = ownerFact(trail);
            held.awaitId(after);

            assertThat(List.of(firstRead.status(), secondRead.status())).as("E5.4: чтения отвечены")
                    .containsOnly(200);
            assertThat(trail.accesses(Party.AUTH)).as("E5.4: резолв уходит на каждом чтении сверх срока кэша, "
                            + "а поток владельца членств не спрашивает ни разу")
                    .extracting(Side.Access::path).containsExactly(MEMBERSHIPS, MEMBERSHIPS);
            assertThat(held.isOpen()).as("E5.4: открытая подписка по истечении срока билета не рвётся").isTrue();
            assertThat(held.facts()).as("E5.4: записи по ней идут и после негодности билета для открытия")
                    .extracting(Frame::id).containsExactly(before, after);
        } finally {
            held.close();
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Запись в тему так, как её положил бы производитель: полный конверт. */
    private static String produceFact(String topic, String tenant, String eventType, String payload) {
        String eventId = UUID.randomUUID().toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("eventId", eventId);
        headers.put("eventType", eventType);
        headers.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        headers.put("version", "1");
        trail.produce(topic, tenant, payload, headers);
        return eventId;
    }

    /** Чтения группы E2 предъявителем — ответ на путь. */
    private static Map<String, JsonNode> reads(String bearer) {
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        for (String path : List.of(JOURNAL, Trail.STRATEGIES, rowsPath(), dealsPath())) {
            String sent = JOURNAL.equals(path) ? journalPath() : path;
            Answer answer = trail.callWith(bearer, Party.BFF, "GET", sent, null, null);
            assertThat(answer.status()).as("чтение " + sent + " — " + answer.body()).isEqualTo(200);
            answers.put(path, Json.tree(answer.body()));
        }
        return answers;
    }

    private static String journalPath() {
        Instant now = Instant.now();
        return JOURNAL + "?from=" + now.minus(Duration.ofDays(2)) + "&to=" + now.plus(Duration.ofMinutes(30));
    }

    private static String rowsPath() {
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        return "/api/v1/statistics/aggregates/rows?grain=INCIDENT&from=" + day + "&to=" + day;
    }

    private static String dealsPath() {
        return Trail.CORE + "/deals?exchangeAccountInternalId=" + prologue.account();
    }

    /** Поля билета: значение — base64 полей через черту, подпись — после точки. */
    private static List<String> ticketFields(String ticket) {
        String value = new String(Base64.getUrlDecoder().decode(ticket.substring(0, ticket.indexOf('.'))),
                StandardCharsets.UTF_8);
        return List.of(value.split("\\|"));
    }
}
