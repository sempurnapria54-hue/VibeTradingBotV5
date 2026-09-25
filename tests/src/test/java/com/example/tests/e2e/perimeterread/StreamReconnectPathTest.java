package com.example.tests.e2e.perimeterread;

import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Substrate;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.perimeterread.Subscription.Frame;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

import static com.example.tests.e2e.perimeterread.StreamTrace.JOURNAL;
import static com.example.tests.e2e.perimeterread.StreamTrace.TICKETS;
import static com.example.tests.e2e.perimeterread.StreamTrace.awaitJournal;
import static com.example.tests.e2e.perimeterread.StreamTrace.header;
import static com.example.tests.e2e.perimeterread.StreamTrace.momentOf;
import static com.example.tests.e2e.perimeterread.StreamTrace.ownerFact;
import static com.example.tests.e2e.perimeterread.StreamTrace.ticket;
import static com.example.tests.e2e.perimeterread.StreamTrace.warmTheCache;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E4} тропы периметра: переподключение, окно и разрыв на живой
 * тропе (.claude/tests/cases/e2e-perimeter-read.md §«E4 — Переподключение, окно
 * и разрыв на живой тропе»).
 *
 * <p><b>Состояние {@code E3.1} ставится тем же ходом, что у группы
 * {@code E3}</b>: пролог до заведённой сделки, подписка, проход входного шага и
 * тик реле. Прочие факты — удаление черновика определения у владельца
 * ({@link StreamTrace#ownerFact}).
 *
 * <p><b>Клетки упорядочены:</b> {@code E4.4} и {@code E4.5} стоят на состоянии
 * {@code E4.1}, а клетки с сокращённым окном и сроком билета поднимают периметр
 * заново со своей конфигурацией — окно и подписки перезапуск уносит.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E4 — Переподключение, окно и разрыв на живой тропе")
class StreamReconnectPathTest {

    private static final String SUBJECT = "subject-s1";

    private static final String ORDER_DECIDED = "ORDER_DECIDED";

    private static final String WINDOW_KEY = "perimeter.stream.replay-window";

    private static final String TICKET_TTL_KEY = "perimeter.ticket.ttl";

    private static final String CACHE_TTL_KEY = "perimeter.membership.cache-ttl";

    private static final String MEMBERSHIPS = "/api/v1/auth/memberships/self";

    /** Поля количества у решения о заявке: их область — объёмы и цены, и совпадение с числом смещения случайно. */
    private static final Set<String> QUANTITIES = Set.of("plannedSizeContracts", "plannedEntryPrice");

    private static Trail trail;

    private static String token;

    private static Prologue.Tenancy prologue;

    private static String firstTicket;

    private static Subscription live;

    private static Subscription resumed;

    private static String orderEvent;

    private static List<String> missed;

    @BeforeAll
    static void standInTheStateOfE31() {
        trail = Trail.openPerimeter("p8");
        token = trail.identity().browserToken(SUBJECT, "Trader One");
        prologue = Prologue.walkToOpenDeal(trail, token);
        warmTheCache(trail, token);
        firstTicket = ticket(trail, token);
        live = Subscription.open(trail, firstTicket);
        trail.entrySubmitted();
        trail.relayCore();
        orderEvent = live.awaitType(ORDER_DECIDED).id();
    }

    @AfterAll
    static void closeTrail() {
        for (Subscription subscription : new Subscription[]{live, resumed}) {
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
    @DisplayName("E4.1 — Переподключение с идентичностью последней записи продолжает поток с неё")
    void e4_1_reconnectingWithTheLastIdentityContinuesFromIt() {
        live.close();
        String second = ownerFact(trail);
        String third = ownerFact(trail);
        missed = List.of(second, third);

        resumed = Subscription.open(trail, firstTicket, orderEvent);
        resumed.awaitId(third);

        assertThat(distinctIds(resumed.frames()))
                .as("E4.1: пришли обе пропущенные записи, в порядке темы, без разрыва и без уже полученной")
                .containsExactly(second, third);
        assertThat(missed).as("E4.1: те же два факта лежат строками журнала — состав совпал с проводом")
                .allSatisfy(fact -> assertThat(awaitJournal(trail, fact).get("event_type"))
                        .isEqualTo(StreamTrace.STRATEGY_DELETED));
        assertThat(resumed.isOpen()).as("E4.1: подписка оставлена открытой").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("E4.4 — Смещение темы наружу не уходит ни одним полем")
    void e4_4_theTopicOffsetDoesNotLeaveInAnyField() {
        Map<String, Frame> distinct = new LinkedHashMap<>();
        live.facts().forEach(frame -> distinct.putIfAbsent(frame.id(), frame));
        resumed.facts().forEach(frame -> distinct.putIfAbsent(frame.id(), frame));
        List<Frame> delivered = new ArrayList<>(distinct.values());
        Map<String, Long> offsets = offsetsOf(delivered);

        assertThat(delivered).as("E4.4: ни одно поле ни одной записи смещения темы не несёт")
                .hasSize(3)
                .allSatisfy(frame -> assertThat(valuesOf(frame))
                        .doesNotContain(String.valueOf(offsets.get(frame.id()))));
        Subscription probe = Subscription.open(trail, firstTicket, String.valueOf(offsets.get(missed.getLast())));
        try {
            Frame first = probe.awaitType(Subscription.GAP);
            assertThat(probe.frames()).as("E4.4: идентичность из смещения даёт разрыв, истории темы не отдано")
                    .containsExactly(first);
        } finally {
            probe.close();
        }
    }

    @Test
    @Order(3)
    @DisplayName("E4.5 — Перезапуск реплики периметра теряет окно и подписки, а состояния владельцев не трогает")
    void e4_5_aPerimeterRestartLosesTheWindowAndLeavesTheOwners() {
        Instant now = Instant.now();
        String journal = JOURNAL + "?from=" + now.minus(Duration.ofDays(2)) + "&to=" + now.plus(Duration.ofMinutes(30));
        Map<String, JsonNode> before = ownerReads(journal);
        Long tenants = trail.database(Party.AUTH).count("tenants");
        Long memberships = trail.database(Party.AUTH).count("memberships");

        trail.stop(Party.BFF);
        trail.start(Party.BFF);
        trail.forgetTraces();

        Awaitility.await("E4.5: прежнее соединение закрыто перезапуском")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> isFalse(resumed.isOpen()));
        Subscription again = Subscription.open(trail, firstTicket, missed.getLast());
        try {
            Frame first = again.awaitType(Subscription.GAP);
            assertThat(again.status()).as("E4.5: прежний билет подписку открывает — он проверяется подписью")
                    .isEqualTo(200);
            assertThat(again.frames()).as("E4.5: окно за ним не восстановлено — разрыв").containsExactly(first);
        } finally {
            again.close();
        }
        assertThat(ownerReads(journal)).as("E4.5: все чтения группы E2 дают те же ответы, что до перезапуска")
                .isEqualTo(before);
        assertThat(trail.accesses(Party.AUTH)).as("E4.5: резолв перевыводится — кэш процесс не пережил")
                .extracting(Side.Access::path).contains(MEMBERSHIPS);
        assertThat(trail.database(Party.AUTH).count("tenants")).as("E4.5: новых строк у владельца членств нет")
                .isEqualTo(tenants);
        assertThat(trail.database(Party.AUTH).count("memberships")).isEqualTo(memberships);
    }

    @Test
    @Order(4)
    @DisplayName("E4.2 — Идентичность вне окна даёт явный разрыв, и дочитывается он чтением у владельца")
    void e4_2_anIdentityOutsideTheWindowGivesAGapReadBackAtTheOwner() {
        restartThePerimeter(WINDOW_KEY, "2");
        warmTheCache(trail, token);
        String narrowTicket = ticket(trail, token);
        Subscription narrow = Subscription.open(trail, narrowTicket);
        String shown = ownerFact(trail);
        Frame lastShown = narrow.awaitId(shown);
        narrow.close();
        List<String> passed = List.of(ownerFact(trail), ownerFact(trail), ownerFact(trail));

        Subscription reopened = Subscription.open(trail, narrowTicket, shown);
        try {
            String current = ownerFact(trail);
            reopened.awaitId(current);
            assertThat(reopened.frames()).as("E4.2: первой записью — разрыв, дальше поток с текущего момента")
                    .extracting(Frame::type, Frame::id)
                    .containsExactly(tuple(Subscription.GAP, null),
                            tuple(StreamTrace.STRATEGY_DELETED, current));
        } finally {
            reopened.close();
        }
        passed.forEach(fact -> awaitJournal(trail, fact));
        String path = JOURNAL + "?from=" + momentOf(lastShown) + "&to=" + Instant.now().plusSeconds(300);
        Answer readBack = trail.callWith(token, Party.BFF, "GET", path, null, null);
        assertThat(readBack.status()).as("E4.2: дочитывание у владельца — " + readBack.body()).isEqualTo(200);
        List<String> journal = new ArrayList<>();
        Json.tree(readBack.body()).path("records").forEach(record -> journal.add(record.path("eventId").asString()));
        assertThat(journal).as("E4.2: журнал отдаёт все пропущенные факты, включая вытесненные из окна")
                .containsAll(passed);
    }

    @Test
    @Order(5)
    @DisplayName("E4.3 — Просроченный билет подписку не открывает, а новый открывает её тем же адресом")
    void e4_3_anExpiredTicketDoesNotOpenAndANewOneDoes() {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(TICKET_TTL_KEY, "3s");
        trail.side(Party.BFF).set(CACHE_TTL_KEY, "2s");
        trail.start(Party.BFF);
        warmTheCache(trail, token);
        Answer issued = trail.callWith(token, Party.BFF, "POST", TICKETS, null, "");
        String shortTicket = Json.tree(issued.body()).path("ticket").asString();
        Instant expires = OffsetDateTime.parse(Json.tree(issued.body()).path("expiresAt").asString()).toInstant();
        Subscription before = Subscription.open(trail, shortTicket);
        String shown = ownerFact(trail);
        before.awaitId(shown);
        before.close();
        String between = ownerFact(trail);
        Awaitility.await("E4.3: срок билета истёк")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> Instant.now().isAfter(expires.plusSeconds(1)));
        trail.forgetTraces();

        Subscription expired = Subscription.open(trail, shortTicket, shown);
        try {
            assertThat(expired.status()).as("E4.3: просроченный билет отвергнут").isNotEqualTo(200);
            assertThat(expired.carriesStream()).as("E4.3: соединения потока не создано").isFalse();
            assertThat(expired.frames()).as("E4.3: записи не уехало ни одной").isEmpty();
        } finally {
            expired.close();
        }
        String renewed = ticket(trail, token);
        assertThat(trail.accesses(Party.AUTH)).as("E4.3: выдача нового билета резолв перевыводит — кэш просрочен")
                .extracting(Side.Access::path).contains(MEMBERSHIPS);
        Subscription reopened = Subscription.open(trail, renewed, shown);
        try {
            String next = ownerFact(trail);
            reopened.awaitId(next);
            assertThat(reopened.status()).as("E4.3: новый билет открывает подписку тем же адресом").isEqualTo(200);
            assertThat(distinctIds(reopened.facts()))
                    .as("E4.3: поток продолжился с переданной идентичности — пропущенная запись переиграна")
                    .containsExactly(between, next);
        } finally {
            reopened.close();
        }
    }

    // ---------------------------------------------------------------- ходы и чтения

    private static void restartThePerimeter(String key, String value) {
        trail.stop(Party.BFF);
        trail.side(Party.BFF).set(key, value);
        trail.start(Party.BFF);
    }

    /** Чтения группы E2 через периметр — ответ на путь. */
    private static Map<String, JsonNode> ownerReads(String journal) {
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        List<String> paths = List.of(
                journal,
                "/api/v1/statistics/aggregates/rows?grain=INCIDENT&from=" + day + "&to=" + day,
                Trail.STRATEGIES,
                Trail.CORE + "/deals?exchangeAccountInternalId=" + prologue.account(),
                Trail.CORE + "/risk-appetites/" + prologue.tenant());
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        for (String path : paths) {
            Answer answer = trail.callWith(token, Party.BFF, "GET", path, null, null);
            assertThat(answer.status()).as("чтение " + path + " — " + answer.body()).isEqualTo(200);
            answers.put(path, Json.tree(answer.body()));
        }
        return answers;
    }

    /**
     * Идентичности записей в порядке первого прихода.
     *
     * <p>Дедуплицирует клиент, и это часть внешнего контракта
     * (docs/architecture/contracts.md §«Живые данные в браузер»): повтор записи
     * на проводе законен, и кейс говорит о составе по идентичности.
     */
    private static List<String> distinctIds(List<Frame> frames) {
        return frames.stream().map(Frame::id).distinct().toList();
    }

    /** Смещение записи темы по идентичности события — у обеих тем тропы. */
    private static Map<String, Long> offsetsOf(List<Frame> frames) {
        Map<String, Long> offsets = new LinkedHashMap<>();
        for (String topic : List.of(Substrate.CORE_TOPIC, Substrate.STRATEGY_TOPIC)) {
            for (ConsumerRecord<String, String> record : trail.records(topic)) {
                String eventId = header(record, "eventId");
                if (frames.stream().anyMatch(frame -> Objects.equals(eventId, frame.id()))) {
                    offsets.put(eventId, record.offset());
                }
            }
        }
        assertThat(offsets).as("у каждой записи есть запись темы").hasSize(frames.size());
        return offsets;
    }

    /** Значения записи — поле идентичности, момент и содержимое, кроме полей количества. */
    private static List<String> valuesOf(Frame frame) {
        List<String> values = new ArrayList<>();
        values.add(frame.id());
        values.add(frame.record().path("id").asString());
        values.add(frame.record().path("occurredAt").asString());
        frame.content().properties().forEach(field -> {
            if (isFalse(QUANTITIES.contains(field.getKey()))) {
                values.add(field.getValue().asString());
            }
        });
        return values;
    }
}
