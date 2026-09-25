package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
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
import static com.example.tests.e2e.perimeterread.StreamTrace.TICKETS;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFact;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFactWritten;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static com.example.tests.e2e.perimeterread.StreamTrace.warmTheCache;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E7} тропы периметра, клетки {@code E7.1}-{@code E7.3}: отказ
 * стороны на тропе (.claude/tests/cases/e2e-perimeter-read.md §«E7 — Отказ
 * стороны на тропе»). Клетка {@code E7.4} пролога не читает и живёт в
 * {@code PerimeterRefusalPathTest}.
 *
 * <p><b>Пролог — до заведённой сделки, пульс — раз в секунду:</b> ход,
 * порождающий факт ядра, у тропы один — проход входного шага, — и он отдан
 * {@code E7.1}, где факт обязан дойти и до статистики; прочим клеткам хватает
 * факта владельца определений.
 *
 * <p><b>{@code E7.2} последняя и красна по построению</b> (находка {@code F7}):
 * остановленный брокер пульса не гасит, а поднять брокер обратно нечем.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E7 — Отказ стороны на тропе")
class SideOutagePathTest {

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String PULSE_KEY = "perimeter.stream.pulse-interval";

    private static final String CACHE_TTL_KEY = "perimeter.membership.cache-ttl";

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    private static Subscription first;

    @BeforeAll
    static void walkThePrologueToAnOpenDeal() {
        trail = Trail.openPerimeter("p11");
        token = trail.identity().browserToken("subject-s1", "Trader One");
        prologue = Prologue.walkToOpenDeal(trail, token);
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(PULSE_KEY, "1s");
        trail.start(Party.BFF);
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
    @DisplayName("E7.1 — Владелец остановлен: чтение отвечает отказом, подписка жива, пульс идёт")
    void e7_1_anOwnerDownFailsTheReadAndKeepsTheStream() {
        warmTheCache(trail, token);
        first = Subscription.open(trail, ticket(trail, token));
        first.awaitType(Subscription.PULSE);
        trail.stop(Party.AUDIT);

        Answer read = trail.callWith(token, Party.BFF, "GET", journalPath(), null, null);
        Answer otherOwner = trail.callWith(token, Party.BFF, "GET", Trail.STRATEGIES, null, null);
        trail.entrySubmitted();
        trail.relayCore();
        Frame fact = first.awaitType(ORDER_DECIDED);
        Frame pulse = pulseAfter(first, fact);

        assertThat(read.status()).as("E7.1: чтение отвечает недоступностью владельца — " + read.body())
                .isEqualTo(503);
        assertThat(Json.object(read.body()).get("code")).isEqualTo("PEER_UNAVAILABLE");
        assertThat(otherOwner.status()).as("E7.1: чтения прочих владельцев проходят").isEqualTo(200);
        assertThat(first.isOpen()).as("E7.1: подписка не рвётся").isTrue();
        assertThat(pulse.type()).as("E7.1: пульс на ней идёт").isEqualTo(Subscription.PULSE);
        Trail.await("E7.1: тот же факт принят статистикой штатно", () -> isFalse(trail.database(Party.STATISTICS)
                .query("select event_id from incident_facts where event_id = ?", fact.id()).isEmpty()));

        trail.start(Party.AUDIT);
        assertThat(awaitJournal(trail, fact.id())).as("E7.1: после подъёма журнал добрал пропущенное").isNotEmpty();
        Answer again = trail.callWith(token, Party.BFF, "GET", journalPath(), null, null);
        assertThat(again.status()).as("E7.1: и чтение отвечает штатно — " + again.body()).isEqualTo(200);
    }

    @Test
    @Order(2)
    @DisplayName("E7.3 — Владелец членств остановлен при годном кэше: обе точки держатся кэшем")
    void e7_3_theMembershipOwnerDownWithAValidCache() {
        first.close();
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(CACHE_TTL_KEY, CACHE_TTL.toSeconds() + "s");
        trail.start(Party.BFF);
        Database auth = trail.database(Party.AUTH);
        Long tenants = auth.count("tenants");
        Long memberships = auth.count("memberships");
        warmTheCache(trail, token);
        Instant warmed = Instant.now();
        trail.stop(Party.AUTH);

        Answer context = trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
        String held = ticket(trail, token);
        Subscription open = Subscription.open(trail, held);
        try {
            Answer read = trail.callWith(token, Party.BFF, "GET", Trail.STRATEGIES, null, null);
            open.awaitType(Subscription.PULSE);
            assertThat(Instant.now()).as("E7.3: всё это — в пределах срока кэша").isBefore(warmed.plus(CACHE_TTL));
            assertThat(context.status()).as("E7.3: в пределах срока контекст отвечает").isEqualTo(200);
            assertThat(read.status()).as("E7.3: чтение уходит владельцу с выведенным тенантом").isEqualTo(200);
            assertThat(trail.accesses(Party.STRATEGIES)).as("E7.3: тенант выведен кэшем")
                    .allSatisfy(access -> assertThat(access.tenant()).isEqualTo(prologue.tenant()));

            Awaitility.await("E7.3: срок кэша истёк")
                    .atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> Instant.now().isAfter(warmed.plus(CACHE_TTL).plusSeconds(1)));
            Answer contextAfter = trail.callWith(token, Party.BFF, "GET", CONTEXT, null, null);
            Answer ticketAfter = trail.callWith(token, Party.BFF, "POST", TICKETS, null, "");
            String fact = ownerFact(trail);
            open.awaitId(fact);

            assertThat(List.of(contextAfter.status(), ticketAfter.status()))
                    .as("E7.3: после истечения контекст и выдача билета отвечают недоступностью — "
                            + contextAfter.body() + " " + ticketAfter.body())
                    .allSatisfy(status -> assertThat(status).isGreaterThanOrEqualTo(500));
            assertThat(open.isOpen()).as("E7.3: уже открытая подписка не рвётся").isTrue();
        } finally {
            open.close();
        }
        trail.start(Party.AUTH);
        assertThat(List.of(auth.count("tenants"), auth.count("memberships")))
                .as("E7.3: после подъёма строк у владельца членств столько же").containsExactly(tenants, memberships);
    }

    @Test
    @Order(3)
    @Tag("debt")
    @DisplayName("E7.2 — Брокер остановлен: чтение идёт, поток молчит, и молчание наблюдаемо")
    void e7_2_theBrokerDownKeepsReadsAndSilencesTheStream() {
        warmTheCache(trail, token);
        Subscription open = Subscription.open(trail, ticket(trail, token));
        try {
            open.awaitType(Subscription.PULSE);
            trail.stopBroker();

            Map<String, Integer> reads = Map.of(
                    Trail.STRATEGIES, trail.callWith(token, Party.BFF, "GET", Trail.STRATEGIES, null, null).status(),
                    journalPath(), trail.callWith(token, Party.BFF, "GET", journalPath(), null, null).status());
            assertThat(reads.values()).as("E7.2: чтения проходят — синхронный канал от брокера не зависит")
                    .containsOnly(200);
            String unpublished = ownerFactWritten(trail);
            trail.relayOwner();
            assertThat(trail.database(Party.STRATEGIES).query(
                            "select published_at from outbox_events where event_id = ?", unpublished))
                    .as("E7.2: строка outbox производителя остаётся неопубликованной — реле не подтвердило публикацию")
                    .extracting(row -> row.get("published_at")).containsOnlyNulls();
            Awaitility.await("E7.2: пульс прекращается")
                    .atMost(Duration.ofSeconds(120))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> Duration.between(open.frames().getLast().arrived(), Instant.now())
                            .compareTo(Duration.ofSeconds(5)) > 0);
            assertThat(open.isOpen()).as("E7.2: подписка при этом не рвётся").isTrue();
        } finally {
            open.close();
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static String journalPath() {
        Instant now = Instant.now();
        return JOURNAL + "?from=" + now.minus(Duration.ofDays(2)) + "&to=" + now.plus(Duration.ofMinutes(30));
    }

    private static Frame pulseAfter(Subscription subscription, Frame record) {
        Awaitility.await("пульс после записи факта")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> subscription.frames().stream()
                        .anyMatch(frame -> frame.isPerimeter() && frame.arrived().isAfter(record.arrived())));
        return subscription.frames().stream()
                .filter(frame -> frame.isPerimeter() && frame.arrived().isAfter(record.arrived()))
                .findFirst().orElseThrow();
    }
}
