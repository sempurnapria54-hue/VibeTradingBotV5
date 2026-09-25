package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import static com.example.tests.e2e.perimeterread.StreamTrace.STRATEGY_DELETED;
import static com.example.tests.e2e.perimeterread.StreamTrace.TICKETS;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.header;
import static com.example.tests.e2e.perimeterread.StreamTrace.instantOf;
import static com.example.tests.e2e.perimeterread.StreamTrace.momentOf;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFact;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFactWritten;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static com.example.tests.e2e.perimeterread.StreamTrace.warmTheCache;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E3} тропы периметра: факт производителя доезжает записью в
 * браузер (.claude/tests/cases/e2e-perimeter-read.md §«E3 — Факт производителя
 * доезжает записью в браузер»).
 *
 * <p><b>Пролог здесь идёт до заведённой сделки, чья входная заявка ещё не
 * решена</b> ({@link Prologue#walkToOpenDeal}): проход, решающий заявку, есть
 * только у неё, и он — вход {@code E3.1}. Клетки, чей вход — «ход,
 * порождающий факт» без названного класса, берут факт жизненного цикла
 * определения: производитель тот же провод, и ход повторяем.
 *
 * <p><b>Клетки упорядочены, потому что состояние {@code E3.1} есть
 * предусловие {@code E3.2} и {@code E3.5}</b>, а {@code E3.7} кончается
 * остановленным брокером, после которого тропа не поднимается.
 *
 * <p><b>{@code E3.7} красна по построению</b> (находка {@code F7} документа
 * кейсов): у остановленного брокера клиент назначения партиций не теряет, и
 * пульс идёт дальше — операнд живости потребителя потери связи не видит.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E3 — Факт производителя доезжает записью в браузер")
class StreamRecordPathTest {

    private static final String SUBJECT = "subject-s1";

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String STRATEGY_DEACTIVATED = "STRATEGY_DEACTIVATED";

    private static final String PERIMETER_GROUP = "bff-";

    private static final String PULSE_KEY = "perimeter.stream.pulse-interval";

    private static final Set<String> RECORD_FIELDS = Set.of("id", "type", "occurredAt", "content");

    /** Объявленная форма периметра у решения о заявке (docs/architecture/contracts.md §«Форма на проводе к браузеру — своя, а не доменный класс»). */
    private static final Set<String> ORDER_DECIDED_FIELDS = Set.of("orderInternalId", "dealInternalId",
            "exchangeAccountInternalId", "instrumentInternalId", "orderType", "direction",
            "plannedSizeContracts", "plannedEntryPrice");

    private static final List<Party> OWNERS =
            List.of(Party.TRADING_CORE, Party.STRATEGIES, Party.AUDIT, Party.STATISTICS, Party.AUTH);

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    private static Subscription first;

    private static Subscription pulsed;

    private static String orderEvent;

    @BeforeAll
    static void walkThePrologueToAnOpenDeal() {
        trail = Trail.openPerimeter("p6");
        token = trail.identity().browserToken(SUBJECT, "Trader One");
        prologue = Prologue.walkToOpenDeal(trail, token);
    }

    @AfterAll
    static void closeTrail() {
        for (Subscription subscription : new Subscription[]{first, pulsed}) {
            if (nonNull(subscription)) {
                subscription.close();
            }
        }
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E3.1 — Билет выдан, подписка открыта, факт ядра доехал записью")
    void e3_1_theTicketOpensTheSubscriptionAndTheCoreFactArrives() {
        awaitPerimeterCaughtUp();
        warmTheCache(trail, token);
        Answer ticket = trail.callWith(token, Party.BFF, "POST", TICKETS, null, "");
        assertThat(ticket.status()).as("E3.1: билет выдан — " + ticket.body()).isEqualTo(200);
        JsonNode issued = Json.tree(ticket.body());
        assertThat(OffsetDateTime.parse(issued.path("expiresAt").asString()).toInstant())
                .as("E3.1: вместе с моментом негодности").isAfter(Instant.now());
        first = Subscription.open(trail, issued.path("ticket").asString());

        trail.entrySubmitted();
        trail.relayCore();

        Frame record = first.awaitType(ORDER_DECIDED);
        Database core = trail.database(Party.TRADING_CORE);
        List<Map<String, Object>> outbox = core.query(
                "select event_id, published_at from outbox_events where event_type = ?", ORDER_DECIDED);
        assertThat(outbox).as("E3.1: строку outbox решения о заявке завёл проход").hasSize(1);
        orderEvent = String.valueOf(outbox.getFirst().get("event_id"));
        assertThat(outbox.getFirst().get("published_at")).as("E3.1: и тик реле пометил её опубликованной")
                .isNotNull();
        assertThat(first.status()).as("E3.1: подписка открыта").isEqualTo(200);
        assertThat(first.carriesStream()).as("E3.1: провод — поток событий").isTrue();
        assertThat(record.id()).as("E3.1: на соединении — запись этого факта").isEqualTo(orderEvent);
        assertThat(core.query("select id from orders where external_id is not null"))
                .as("E3.1: зеркало заявки заведено и несёт подтверждение площадки").hasSize(1);
        assertThat(trail.marketData().requests(Trail.PEER_FEATURES))
                .as("E3.1: раскладку фич проход прочитал у стаба владельца").isNotEmpty();
        assertThat(trail.exchange().requests(Trail.EXCHANGE_ORDER))
                .as("E3.1: решённая заявка ушла площадке через коннектор")
                .anySatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("POST"));
        awaitJournal(trail, orderEvent);
        Trail.await("E3.1: факт принят статистикой", () -> isFalse(trail.database(Party.STATISTICS)
                .query("select event_id from incident_facts where event_id = ?", orderEvent).isEmpty()));
        assertThat(trail.accesses(Party.AUTH)).as("E3.1: резолва у владельца членств нет — кэш годен").isEmpty();
        assertThat(first.facts()).as("E3.1: один факт дал одну запись").extracting(Frame::id)
                .containsExactly(orderEvent);
        assertThat(first.isOpen()).as("E3.1: подписка оставлена открытой").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("E3.2 — Идентичность записи равна идентичности события у производителя и у журнала")
    void e3_2_theRecordIdentityEqualsTheEventIdentityEverywhere() {
        Frame record = first.awaitId(orderEvent);
        List<ConsumerRecord<String, String>> inTopic = trail.records(Substrate.CORE_TOPIC).stream()
                .filter(published -> Objects.equals(orderEvent, header(published, "eventId")))
                .toList();

        assertThat(inTopic).as("E3.2: в теме — одна запись с идентичностью события производителя").hasSize(1);
        assertThat(record.record().path("id").asString()).as("E3.2: поле идентичности в записи — то же")
                .isEqualTo(orderEvent);
        assertThat(String.valueOf(awaitJournal(trail, orderEvent).get("event_id")))
                .as("E3.2: идентичность строки журнала — та же").isEqualTo(record.id());
        assertThat(record.type()).as("E3.2: класс записи равен классу события заголовка")
                .isEqualTo(header(inTopic.getFirst(), "eventType"))
                .isEqualTo(record.record().path("type").asString());
    }

    @Test
    @Order(3)
    @DisplayName("E3.5 — Содержимое на проводе — форма периметра, а не доменный класс")
    void e3_5_theWireContentIsThePerimeterForm() {
        Frame record = first.awaitId(orderEvent);
        Map<String, Object> journal = awaitJournal(trail, orderEvent);
        JsonNode delivered = Json.tree(String.valueOf(journal.get("content")));
        JsonNode produced = Json.tree(String.valueOf(trail.database(Party.TRADING_CORE)
                .query("select payload from outbox_events where event_id = ?", orderEvent).getFirst().get("payload")));

        assertThat(namesOf(record.record())).as("E3.5: поля записи — объявленные формой периметра")
                .isEqualTo(new TreeSet<>(RECORD_FIELDS));
        assertThat(namesOf(record.content())).as("E3.5: поля содержимого — объявленные, и только они")
                .isEqualTo(new TreeSet<>(ORDER_DECIDED_FIELDS));
        assertThat(record.data()).as("E3.5: имени доменного класса в проводе нет")
                .doesNotContain("com.example").doesNotContain("tradingbot").doesNotContain("@class");
        assertThat(delivered).as("E3.5: у журнала содержимое лежит как доставлено").isEqualTo(produced);
        assertThat(namesOf(delivered)).as("E3.5: и форма у двух читателей одной записи разная")
                .isNotEqualTo(namesOf(record.content()));
    }

    @Test
    @Order(4)
    @DisplayName("E3.4 — Событие определений доезжает тем же проводом из второй темы")
    void e3_4_theDefinitionEventArrivesOnTheSameWire() {
        Database statistics = trail.database(Party.STATISTICS);
        List<Map<String, Object>> receptionBefore = statistics.query(
                "select topic, last_accepted_occurred_at from reception_states order by topic");
        Long incidentsBefore = statistics.count("incident_facts");
        Long dealFactsBefore = statistics.count("deal_facts");

        Answer moved = trail.moveDefinition(prologue.definition(), "INACTIVE");
        assertThat(moved.status()).as("E3.4: ход жизненного цикла определения — " + moved.body()).isEqualTo(200);
        trail.relayOwner();

        Frame record = first.awaitType(STRATEGY_DEACTIVATED);
        List<Map<String, Object>> outbox = trail.database(Party.STRATEGIES).query(
                "select event_id, published_at from outbox_events where event_type = ?", STRATEGY_DEACTIVATED);
        assertThat(outbox).as("E3.4: строка outbox владельца определений").hasSize(1);
        assertThat(outbox.getFirst().get("published_at")).as("E3.4: помечена опубликованной").isNotNull();
        assertThat(record.id()).as("E3.4: запись этого события — провод тот же")
                .isEqualTo(String.valueOf(outbox.getFirst().get("event_id")));
        assertThat(namesOf(record.record())).as("E3.4: форма записи та же").isEqualTo(new TreeSet<>(RECORD_FIELDS));
        assertThat(record.content().path("strategyInternalId").asString()).as("E3.4: о том определении")
                .isEqualTo(prologue.definition());
        assertThat(awaitJournal(trail, record.id()).get("event_type")).as("E3.4: строка журнала об этом классе")
                .isEqualTo(STRATEGY_DEACTIVATED);
        assertThat(statistics.query("select topic from reception_states where topic = ?", Substrate.STRATEGY_TOPIC))
                .as("E3.4: темы определений статистика не читает вовсе").isEmpty();
        assertThat(statistics.query("select topic, last_accepted_occurred_at from reception_states order by topic"))
                .as("E3.4: строка состояния приёма статистики не двинулась").isEqualTo(receptionBefore);
        assertThat(statistics.count("incident_facts")).as("E3.4: фактов у статистики не прибавилось")
                .isEqualTo(incidentsBefore);
        assertThat(statistics.count("deal_facts")).isEqualTo(dealFactsBefore);
        assertThat(trail.consumingGroups(PERIMETER_GROUP)).as("E3.4: обе темы читает одна эфемерная группа периметра")
                .hasSize(1)
                .allSatisfy((group, topics) -> assertThat(topics)
                        .containsExactlyInAnyOrder(Substrate.CORE_TOPIC, Substrate.STRATEGY_TOPIC));
    }

    @Test
    @Order(5)
    @DisplayName("E3.6 — Одну тему читают двое с разной судьбой: журнал добирает пропущенное, браузер — нет")
    void e3_6_theJournalCatchesUpAndTheBrowserDoesNot() {
        first.close();
        trail.stop(Party.AUDIT);
        Long mark = trail.side(Party.BFF).logMark();
        String missed = ownerFact(trail);
        trail.start(Party.AUDIT);

        assertThat(awaitJournal(trail, missed).get("event_type")).as("E3.6: журнал добрал пропущенное после подъёма")
                .isEqualTo(STRATEGY_DELETED);
        warmTheCache(trail, token);
        Subscription late = Subscription.open(trail, ticket(trail, token));
        try {
            String arriving = ownerFact(trail);
            late.awaitId(arriving);
            assertThat(late.facts()).as("E3.6: первого факта на проводе нет, второй пришёл записью")
                    .extracting(Frame::id).containsExactly(arriving);
            assertThat(trail.side(Party.BFF).logSince(mark))
                    .as("E3.6: у периметра следа потери нет — первому подключению терять нечего")
                    .doesNotContain(missed).doesNotContain("is skipped");
        } finally {
            late.close();
        }
    }

    @Test
    @Order(6)
    @DisplayName("E3.3 — Момент записи — момент происшествия из конверта, а не момент раздачи")
    void e3_3_theRecordMomentIsTheOccurrenceMoment() {
        pulseEverySecond();
        warmTheCache(trail, token);
        pulsed = Subscription.open(trail, ticket(trail, token));
        pulsed.awaitType(Subscription.PULSE);

        String fact = ownerFactWritten(trail);
        Instant occurred = instantOf(trail.database(Party.STRATEGIES)
                .query("select occurred_at from outbox_events where event_id = ?", fact).getFirst().get("occurred_at"));
        Awaitility.await("E3.3: пульс позже момента происшествия на секунду — задержка до раздачи")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> pulsed.frames().stream().anyMatch(frame -> frame.isPerimeter()
                        && momentOf(frame).isAfter(occurred.plusSeconds(1))));
        Instant distributed = Instant.now();
        trail.relayOwner();

        Frame record = pulsed.awaitId(fact);
        ConsumerRecord<String, String> published = trail.records(Substrate.STRATEGY_TOPIC).stream()
                .filter(candidate -> Objects.equals(fact, header(candidate, "eventId")))
                .findFirst().orElseThrow();
        assertThat(momentOf(record)).as("E3.3: момент записи — момент происшествия из заголовка конверта")
                .isEqualTo(OffsetDateTime.parse(header(published, "occurredAt")).toInstant())
                .isEqualTo(occurred)
                .isEqualTo(instantOf(awaitJournal(trail, fact).get("occurred_at")))
                .isBefore(distributed);
        Frame pulseAfter = awaitPulseAfter(record);
        assertThat(momentOf(pulseAfter)).as("E3.3: запись периметра несёт момент отправки")
                .isAfterOrEqualTo(distributed);
        assertThat(pulseAfter.id()).as("E3.3: различает их класс записи, а не поле момента").isNull();
        assertThat(pulsed.isOpen()).as("E3.3: подписка оставлена открытой").isTrue();
    }

    @Test
    @Order(7)
    @Tag("debt")
    @DisplayName("E3.7 — Пульс отличает живой поток от мёртвого, а факта не вытесняет")
    void e3_7_thePulseTellsALiveStreamFromADeadOne() {
        trail.forgetTraces();
        Integer seen = pulsed.frames().size();
        Awaitility.await("E3.7: несколько периодов без единого факта")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> pulsed.frames().size() >= seen + 3);
        assertThat(pulsed.frames().subList(seen, pulsed.frames().size()))
                .as("E3.7: до факта на соединении — только пульс").extracting(Frame::type)
                .containsOnly(Subscription.PULSE);
        for (Party owner : OWNERS) {
            assertThat(trail.accesses(owner)).as("E3.7: пульс ничего не читает — к " + owner.module()
                    + " обращений нет").isEmpty();
        }

        String fact = ownerFact(trail);
        Frame record = pulsed.awaitId(fact);
        Frame pulseAfter = awaitPulseAfter(record);
        assertThat(record.type()).as("E3.7: запись факта пульс не подменил").isEqualTo(STRATEGY_DELETED);
        assertThat(pulseAfter.type()).as("E3.7: и не вытеснил — пульс идёт дальше").isEqualTo(Subscription.PULSE);

        trail.stopBroker();
        Awaitility.await("E3.7: остановленный брокер гасит пульс целиком")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Duration.between(pulsed.frames().getLast().arrived(), Instant.now())
                        .compareTo(Duration.ofSeconds(5)) > 0);
        assertThat(pulsed.isOpen()).as("E3.7: подписка при этом не рвётся").isTrue();
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Периметр дочитал обе темы: его группа зафиксировала конец каждой. */
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

    /** Периметр поднят заново с пульсом раз в секунду. */
    private static void pulseEverySecond() {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(PULSE_KEY, "1s");
        trail.start(Party.BFF);
    }

    private static Frame awaitPulseAfter(Frame record) {
        Awaitility.await("пульс после записи факта")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> pulsed.frames().stream()
                        .anyMatch(frame -> frame.isPerimeter() && frame.arrived().isAfter(record.arrived())));
        return pulsed.frames().stream()
                .filter(frame -> frame.isPerimeter() && frame.arrived().isAfter(record.arrived()))
                .findFirst().orElseThrow();
    }

    private static Set<String> namesOf(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }
}
