package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.exitandclose.ExitTrail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.example.tests.e2e.safetyteardown.TeardownTrail.ANOMALY_REPORTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_ORDER;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.TEARDOWN_ATTEMPTS;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.standAtObservation;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToScaledIn;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E2} тропы снятия риска: порядок и подтверждение снятия у
 * площадки (.claude/tests/cases/e2e-safety-teardown.md §«E2 — Снятие живого
 * риска у площадки: порядок и подтверждение»).
 *
 * <p><b>Вход у всех клеток один — второй тик детекции чужой заявки</b>, то
 * есть подъём жёсткой ступени счёта со снятием риска, и каждая клетка идёт на
 * ядре, поднятом заново прологом: ступень необратима. {@code E2.4} продолжает
 * ядро {@code E2.3}.
 *
 * <p><b>Клетки {@code E2.5} и {@code E2.6} здесь не написаны:</b> их
 * предусловие — две активные сделки на разных инструментах, и пролога двух
 * инструментов у набора ещё нет.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2 — Снятие живого риска у площадки: порядок и подтверждение")
class TeardownOrderPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final Integer ATTEMPTS = 3;

    private static final Set<String> TICK_CLASSES = Set.of(HOLD_RAISED, ANOMALY_REPORTED, DEAL_SHUTDOWN_INITIATED);

    private static final List<String> LIVE_ORDER = List.of("CREATED", "PENDING", "ACTIVE", "PARTIALLY_COMPLETED");

    private static Trail trail;

    private static Long closesOfTheUnconfirmedTeardown;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t2");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
        trail.side(Party.TRADING_CORE).set(TEARDOWN_ATTEMPTS, String.valueOf(ATTEMPTS));
        trail.renew(Party.TRADING_CORE);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E2.1 — Порядок снятия: входные ноги, потом позиция, защиты последними")
    void e2_1_theEntryLegsGoFirstThenThePositionAndTheProtectionsLast() {
        walkToScaledIn(trail);
        standAtObservation(trail);
        Integer outboxRows = trail.database(Party.TRADING_CORE).count("outbox_events").intValue();
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        List<LoggedRequest> journal = trail.exchange().requests();
        Integer cancelLeg = first(journal, ExitTrail.CANCEL_ORDER, ExitTrail.SECOND_ORDER);
        Integer close = first(journal, ExitTrail.CLOSE_POSITION, Trail.EXTERNAL_INSTRUMENT);
        Integer protections = first(journal, ExitTrail.CANCEL_ALGOS, "");
        assertThat(cancelLeg).as("E2.1: отмена второй входной ноги дошла до стаба — " + urls(journal))
                .isNotNegative();
        assertThat(close).as("E2.1: закрытие позиции дошло до стаба").isNotNegative();
        assertThat(protections).as("E2.1: снятие защит дошло до стаба").isNotNegative();
        assertThat(cancelLeg).as("E2.1: отмена входной ноги раньше закрытия позиции").isLessThan(close);
        assertThat(IntStream.range(close + 1, protections).mapToObj(journal::get)
                .filter(request -> Objects.equals(Trail.EXCHANGE_POSITIONS, path(request))))
                .as("E2.1: между закрытием и снятием защит — чтение позиции, подтверждающее закрытие")
                .isNotEmpty();
        assertThat(trail.accesses(Party.CONNECTOR).stream()
                .filter(access -> isFalse(Objects.equals("GET", access.method())))
                .map(Side.Access::path))
                .as("E2.1: все команды несут идентификатор счёта").isNotEmpty()
                .allSatisfy(path -> assertThat(path).contains("/accounts/" + trail.account() + "/"));
        Database core = trail.database(Party.TRADING_CORE);
        assertThat(live(core, "orders")).as("E2.1: живых заявок у траншей не осталось").isZero();
        assertThat(live(core, "algo_orders")).as("E2.1: живых отдельных защит не осталось").isZero();
        assertThat(live(core, "attached_algo_orders")).as("E2.1: живых встроенных защит не осталось").isZero();
        assertThat(core.query("select status from positions")).as("E2.1: эпизод позиции закрыт")
                .extracting(row -> row.get("status")).containsOnly("CLOSED");
        List<Map<String, Object>> tickRows = core.query("select event_id, event_type from outbox_events "
                + "order by id offset ?", outboxRows);
        assertThat(tickRows).as("E2.1: следа сверх классов тика подъёма нет").extracting(row -> row.get("event_type"))
                .isNotEmpty().allSatisfy(type -> assertThat(TICK_CLASSES).contains(String.valueOf(type)));
        tickRows.forEach(row -> awaitJournalRow(trail, row.get("event_id")));
    }

    @Test
    @Order(2)
    @DisplayName("E2.2 — Встроенная защита снимается тоже, и перечень собирается по родителям")
    void e2_2_theAttachedProtectionIsCanceledTooByItsParents() {
        walkToExposure(trail);
        standAtObservation(trail);
        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> attached = core.query("select a.internal_id, o.status as parent_status, "
                + "o.external_id as parent from attached_algo_orders a join orders o on o.id = a.order_id").getFirst();
        assertThat(attached.get("parent_status")).as("предусловие E2.2: родитель встроенной защиты терминален")
                .isEqualTo("COMPLETED");
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        List<LoggedRequest> journal = trail.exchange().requests();
        Integer cancelAttached = first(journal, ExitTrail.CANCEL_ALGOS, String.valueOf(attached.get("internal_id")));
        assertThat(cancelAttached).as("E2.2: снятие встроенной защиты терминального родителя дошло до стаба — "
                + urls(journal)).isNotNegative();
        assertThat(core.query("select internal_id from algo_orders")).as("E2.2: в перечне отдельных заявок её нет")
                .extracting(row -> row.get("internal_id")).doesNotContain(attached.get("internal_id"));
        List<LoggedRequest> parentReads = IntStream.range(cancelAttached + 1, journal.size())
                .mapToObj(journal::get)
                .filter(request -> Objects.equals(Trail.EXCHANGE_ORDER, path(request))
                        && Objects.equals("GET", request.getMethod().getName()))
                .toList();
        assertThat(parentReads).as("E2.2: судьба защиты резолвлена добычей родителя после снятия").isNotEmpty();
        assertThat(core.query("select status from attached_algo_orders").getFirst().get("status"))
                .as("E2.2: встроенная защита терминальна").isEqualTo("CANCELED");
        Map<String, Object> critical = critical(core);
        assertThat(critical.get("status")).as("E2.2: снятие подтверждено — отчёт завершён").isEqualTo("COMPLETED");
        assertThat(moment(critical.get("modified_at")))
                .as("E2.2: подтверждение выдано после добычи родителя")
                .isAfter(parentReads.getLast().getLoggedDate().toInstant());
    }

    @Test
    @Order(3)
    @DisplayName("E2.3 — Подтверждение читается фактами, а не подтверждением приёма команды")
    void e2_3_confirmationIsReadFromFactsNotFromTheCommandAck() {
        walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        standAtObservation(trail);
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        List<LoggedRequest> journal = trail.exchange().requests();
        Integer close = first(journal, ExitTrail.CLOSE_POSITION, Trail.EXTERNAL_INSTRUMENT);
        assertThat(close).as("E2.3: закрытие позиции ушло к стабу и принято — " + urls(journal)).isNotNegative();
        closesOfTheUnconfirmedTeardown = journal.stream()
                .filter(request -> Objects.equals(ExitTrail.CLOSE_POSITION, path(request))).count();
        List<String> harvest = IntStream.range(close + 1, journal.size()).mapToObj(journal::get)
                .filter(request -> Objects.equals("GET", request.getMethod().getName()))
                .map(TeardownOrderPathTest::path)
                .toList();
        assertThat(harvest).as("E2.3: после команд — чтения добычи: состояние заявок и позиция")
                .contains(Trail.EXCHANGE_POSITIONS, Trail.EXCHANGE_ORDER);
        Map<String, Object> critical = critical(trail.database(Party.TRADING_CORE));
        assertThat(critical.get("status")).as("E2.3: отчёт терминала не получил").isNotEqualTo("COMPLETED");
        assertThat(trail.database(Party.TRADING_CORE).query("select status from positions").getFirst()
                .get("status")).as("E2.3: живая позиция осталась живой").isEqualTo("ACTIVE");
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E2.3: строк о завершении отчёта нет — одна о заведении")
                .hasSize(reported + 1);
    }

    @Test
    @Order(4)
    @DisplayName("E2.4 — Неподтверждённое снятие повторяется по бюджету и кончается штатным отказом")
    void e2_4_anUnconfirmedTeardownRepeatsWithinBudgetAndEndsInARegularRefusal() {
        Map<String, Object> before = incidents(trail);
        Integer raised = outbox(trail, HOLD_RAISED).size();
        trail.forgetTraces();

        for (int tick = 0; tick <= ATTEMPTS; tick++) {
            detect(trail);
        }
        trail.relayCore();

        assertThat(closesOfTheUnconfirmedTeardown).as("E2.4: проходов снятия у стаба не больше предела")
                .isLessThanOrEqualTo(ATTEMPTS.longValue());
        assertThat(trail.exchange().requests(ExitTrail.CLOSE_POSITION))
                .as("E2.4: повторный сигнал поглощён анкером — второго снятия нет").isEmpty();
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E2.4: ступень счёта стои́т")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(critical(trail.database(Party.TRADING_CORE)).get("status"))
                .as("E2.4: отчёт в промежуточном статусе").isNotEqualTo("COMPLETED");
        assertThat(outbox(trail, HOLD_RAISED)).as("E2.4: перестановки не было").hasSize(raised);
        assertThat(counter(incidents(trail), "raised_holds")).as("E2.4: счётчик подъёмов на прежнем значении")
                .isEqualTo(counter(before, "raised_holds"));
    }

    // ---------------------------------------------------------------- чтения

    /** Позиция первого запроса по пути, чьё тело несёт значение; -1 — такого нет. */
    private static Integer first(List<LoggedRequest> journal, String path, String bodyValue) {
        for (int index = 0; index < journal.size(); index++) {
            LoggedRequest request = journal.get(index);
            if (Objects.equals(path, path(request)) && request.getBodyAsString().contains(bodyValue)) {
                return index;
            }
        }
        return -1;
    }

    private static String path(LoggedRequest request) {
        return request.getUrl().split("\\?")[0];
    }

    private static List<String> urls(List<LoggedRequest> journal) {
        List<String> urls = new ArrayList<>();
        journal.forEach(request -> urls.add(request.getMethod().getName() + " " + request.getUrl()));
        return urls;
    }

    private static Long live(Database core, String table) {
        return core.query("select id, status from " + table).stream()
                .filter(row -> LIVE_ORDER.contains(String.valueOf(row.get("status"))))
                .count();
    }

    /** Критичная строка отчёта кодом чужой заявки. */
    private static Map<String, Object> critical(Database core) {
        return core.query("select status, modified_at from anomaly_reports where code = ? and severity = 'CRITICAL'",
                FOREIGN_ORDER).getFirst();
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
