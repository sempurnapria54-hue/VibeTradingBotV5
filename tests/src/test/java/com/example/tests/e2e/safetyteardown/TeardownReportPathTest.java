package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.exitandclose.ExitTrail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.safetyteardown.TeardownTrail.ANOMALY_REPORTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_INSTRUMENT;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_INSTRUMENT_RISK;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.FOREIGN_ORDER;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.counter;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeHoldsOnlyForeignPosition;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.incidents;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.standAtObservation;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E4} тропы снятия риска: отчёт аномалии — снимки, дедуп и
 * терминал (.claude/tests/cases/e2e-safety-teardown.md §«E4 — Отчёт аномалии:
 * снимки, дедуп и терминал»).
 *
 * <p><b>{@code E4.4}, {@code E4.1} и {@code E4.2} читают ОДИН тик</b> — второй
 * тик детекции чужой заявки, у которого эскалация критичности, снимок «до» и
 * снимок «после» наблюдаются вместе; {@code E4.4} подаёт оба тика, потому что
 * её числа считаются за оба. {@code E4.3} продолжает то же ядро, {@code E4.5}
 * и {@code E4.7} идут на своих.
 *
 * <p><b>{@code E4.6} идёт на своём ядре и раньше {@code E4.7}:</b> отказ
 * вставки критичной строки отчёта ставится неисправностью базы ядра
 * ({@link Database#refuseInserts}), а пересоздание ядра прологом {@code E4.7}
 * уносит неисправную базу от соседей; ответ среза позиций, который заводит
 * {@code E4.7}, пролога этой клетки не пережил бы.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E4 — Отчёт аномалии: снимки, дедуп и терминал")
class TeardownReportPathTest {

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final List<String> INTERMEDIATE = List.of("CREATED", "IN_PROGRESS", "KILL_SWITCH_EXECUTED");

    private static final Integer ATTEMPTS = 3;

    private static final String RUNG_NOT_ENFORCED = "SAFETY_RUNG_NOT_ENFORCED";

    private static Trail trail;

    private static List<LoggedRequest> escalationTick;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("t4");
        trail.factSeriesStartedYesterday();
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
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
    @DisplayName("E4.4 — Эскалация критичности заводит свою строку: запуск снятия риска виден в журнале")
    void e4_4_theSeverityEscalationGetsItsOwnRow() {
        walkToExposure(trail);
        trail.relayCore();
        Map<String, Object> before = incidents(trail);
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        standAtObservation(trail);
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();
        escalationTick = trail.exchange().requests();

        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE)
                .query("select severity from anomaly_reports where code = ? order by id", FOREIGN_ORDER);
        assertThat(rows).as("E4.4: строк отчёта по коду две — некритичная и критичная")
                .extracting(row -> row.get("severity")).containsExactly("NON_CRITICAL", "CRITICAL");
        List<Map<String, Object>> events = outbox(trail, ANOMALY_REPORTED).subList(reported, reported + 2);
        assertThat(events).as("E4.4: две строки журнала об отчётах с разной критичностью")
                .extracting(event -> awaitJournalRow(trail, event.get("event_id")).get("content").toString())
                .satisfiesExactly(first -> assertThat(first).contains("NON_CRITICAL"),
                        second -> assertThat(second).contains("\"CRITICAL\""));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "anomaly_reports")).as("E4.4: отчётов на два больше за оба тика")
                .isEqualTo(counter(before, "anomaly_reports") + 2);
        assertThat(counter(after, "critical_anomaly_reports")).as("E4.4: критичных — на один")
                .isEqualTo(counter(before, "critical_anomaly_reports") + 1);
    }

    @Test
    @Order(2)
    @DisplayName("E4.1 — Отчёт открыт до первой команды снятия риска, и снимок «до» несёт живой риск")
    void e4_1_theReportOpensBeforeTheFirstCommand() {
        Map<String, Object> report = critical(FOREIGN_ORDER);
        Instant opened = moment(report.get("created_at"));
        Instant firstCommand = escalationTick.stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(request -> request.getLoggedDate().toInstant())
                .findFirst().orElseThrow();

        assertThat(opened).as("E4.1: критичная строка заведена раньше первой команды у стаба").isBefore(firstCommand);
        JsonNode local = Json.tree(String.valueOf(report.get("internal_before")));
        assertThat(local.path("exchangeAccountInternalId").asString()).as("E4.1: снимок «до» несёт счёт")
                .isEqualTo(trail.account());
        assertThat(local.has("exchangeAccountSafetyRung")).as("E4.1: и стоящую ступень").isTrue();
        assertThat(local.has("dealInternalId")).as("E4.1: идентичности сделки нет").isFalse();
        assertThat(local.has("orderIds")).as("E4.1: ног траншей нет").isFalse();
        assertThat(local.has("instrumentId")).as("E4.1: полей инструмента нет").isFalse();
        assertThat(report.get("external_before")).as("E4.1: внешнего снимка нет вовсе").isNull();
        assertThat(escalationTick.stream().map(request -> request.getLoggedDate().toInstant())
                .filter(at -> at.isAfter(opened) && at.isBefore(firstCommand)))
                .as("E4.1: перед первой командой к стабу не ушло чтения состояния").isEmpty();
        Map<String, Object> event = outbox(trail, ANOMALY_REPORTED).getLast();
        assertThat(moment(awaitJournalRow(trail, event.get("event_id")).get("occurred_at")))
                .as("E4.1: строка журнала несёт момент заведения").isEqualTo(moment(event.get("occurred_at")));
    }

    @Test
    @Order(3)
    @DisplayName("E4.2 — Снимок «после» читается после снятия риска, и остаточный риск виден в нём")
    void e4_2_theAfterSnapshotIsReadAfterTheTeardown() {
        Map<String, Object> report = critical(FOREIGN_ORDER);
        List<LoggedRequest> commands = escalationTick.stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .toList();

        assertThat(report.get("status")).as("E4.2: отчёт завершён").isEqualTo("COMPLETED");
        assertThat(moment(report.get("modified_at"))).as("E4.2: терминал позже последней команды снятия")
                .isAfter(commands.getLast().getLoggedDate().toInstant());
        JsonNode before = Json.tree(String.valueOf(report.get("internal_before")));
        JsonNode after = Json.tree(String.valueOf(report.get("internal_after")));
        assertThat(after.propertyNames()).as("E4.2: снимок «после» несёт те же поля счёта, что и «до»")
                .containsExactlyInAnyOrderElementsOf(before.propertyNames());
        assertThat(report.get("external_after")).as("E4.2: внешнего снимка нет").isNull();
        Instant firstCommand = commands.getFirst().getLoggedDate().toInstant();
        assertThat(escalationTick.stream()
                .filter(request -> Objects.equals("GET", request.getMethod().getName()))
                .filter(request -> request.getLoggedDate().toInstant().isAfter(firstCommand))
                .filter(request -> Objects.equals(TeardownTrail.ORDERS_PENDING, path(request))
                        || (Objects.equals(Trail.EXCHANGE_ALGO_PENDING, path(request))
                        && isFalse(request.queryParameter("instId").isPresent())))
                .map(LoggedRequest::getUrl))
                .as("E4.2: после команд — только чтения подтверждения, срезов заявок счёта нет")
                .isEmpty();
        assertThat(trail.database(Party.TRADING_CORE).query("select status from positions").getFirst()
                .get("status")).as("E4.2: отсутствие живой позиции читается срезом — эпизод закрыт")
                .isEqualTo("CLOSED");
        assertThat(outbox(trail, ANOMALY_REPORTED).stream()
                .filter(event -> String.valueOf(event.get("payload")).contains("\"CRITICAL\"")))
                .as("E4.2: строка журнала об отчёте по-прежнему одна — у терминала своего класса нет").hasSize(1);
    }

    @Test
    @Order(4)
    @DisplayName("E4.3 — Дедуп по стоящей строке: повтор того же основания второй строки не даёт и события не рождает")
    void e4_3_theStandingRowDeduplicatesARepeatOfTheSameGround() {
        Map<String, Object> before = incidents(trail);
        Integer rows = trail.database(Party.TRADING_CORE)
                .query("select id from anomaly_reports where code = ?", FOREIGN_ORDER).size();
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        Integer raised = outbox(trail, HOLD_RAISED).size();
        trail.forgetTraces();

        detect(trail);
        detect(trail);
        trail.relayCore();
        List<Map<String, Object>> fresh = outbox(trail, ANOMALY_REPORTED).subList(reported,
                outbox(trail, ANOMALY_REPORTED).size());

        assertThat(trail.database(Party.TRADING_CORE).query("select id from anomaly_reports where code = ?",
                FOREIGN_ORDER)).as("E4.3: строк отчёта по ключу столько же").hasSize(rows);
        assertThat(fresh).as("E4.3: строк outbox класса отчёта по этому ключу не прибавилось — прибавилась одна, "
                        + "детектора непроэнфорсенной ступени").singleElement()
                .satisfies(row -> assertThat(String.valueOf(row.get("payload")))
                        .contains(RUNG_NOT_ENFORCED).contains("NON_CRITICAL"));
        assertThat(outbox(trail, HOLD_RAISED)).as("E4.3: перестановки ступени не было").hasSize(raised);
        assertThat(trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName()))))
                .as("E4.3: команд снятия риска нет ни одной").isEmpty();
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "anomaly_reports")).as("E4.3: счётчик отчётов вырос ровно на строку A6")
                .isEqualTo(counter(before, "anomaly_reports") + 1);
        assertThat(counter(after, "critical_anomaly_reports")).as("E4.3: критичных столько же")
                .isEqualTo(counter(before, "critical_anomaly_reports"));
        assertThat(counter(after, "raised_holds")).as("E4.3: счётчик подъёмов стои́т")
                .isEqualTo(counter(before, "raised_holds"));
    }

    @Test
    @Order(5)
    @DisplayName("E4.5 — Незакрытый отчёт при неподтверждённом снятии — объявленный исход, а не зависание")
    void e4_5_anUnclosedReportOnAnUnconfirmedTeardownIsADeclaredOutcome() {
        walkToExposure(trail);
        exchangeAcknowledgesCloseWithoutEffect(trail);
        standAtObservation(trail);
        detect(trail);
        trail.relayCore();

        for (int tick = 0; tick <= ATTEMPTS; tick++) {
            detect(trail);
        }
        trail.relayCore();

        Map<String, Object> report = critical(FOREIGN_ORDER);
        assertThat(report.get("status")).as("E4.5: отчёт в промежуточном статусе — не завершён и не ошибочен")
                .isIn(INTERMEDIATE.toArray());
        assertThat(report.get("internal_after")).as("E4.5: снимка «после» нет").isNull();
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E4.5: ступень счёта стои́т")
                .isEqualTo("TRADE_BLOCKED");
        Map<String, Object> event = outbox(trail, ANOMALY_REPORTED).stream()
                .filter(row -> String.valueOf(row.get("payload")).contains("\"CRITICAL\""))
                .findFirst().orElseThrow();
        awaitJournalRow(trail, event.get("event_id"));
    }

    @Test
    @Order(7)
    @DisplayName("E4.7 — Счёт-широкая тропа: локальный снимок несёт только поля счёта")
    void e4_7_theAccountWidePathSnapshotCarriesOnlyTheAccountFields() {
        trail.withoutDeals();
        exchangeHoldsOnlyForeignPosition(trail);
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        Map<String, Object> report = critical(FOREIGN_INSTRUMENT_RISK);
        JsonNode local = Json.tree(String.valueOf(report.get("internal_before")));
        assertThat(local.path("exchangeAccountInternalId").asString()).as("E4.7: снимок несёт поля счёта")
                .isEqualTo(trail.account());
        assertThat(local.has("dealInternalId")).as("E4.7: идентичности сделки нет").isFalse();
        assertThat(local.has("instrumentId")).as("E4.7: инструмента нет").isFalse();
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E4.7: ступень счёта поднята")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(report.get("status")).as("E4.7: снятие подтверждено срезом — отчёт завершён")
                .isEqualTo("COMPLETED");
        assertThat(trail.exchange().requests(Trail.EXCHANGE_POSITIONS)).as("E4.7: к стабу ушли чтения среза позиций")
                .anySatisfy(request -> assertThat(request.queryParameter("instType").isPresent()).isTrue());
        List<LoggedRequest> closes = trail.exchange().requests(ExitTrail.CLOSE_POSITION);
        assertThat(closes).as("E4.7: одно закрытие чужой позиции").singleElement().satisfies(request -> {
            JsonNode body = Json.tree(request.getBodyAsString());
            assertThat(body.path("instId").asString()).isEqualTo(FOREIGN_INSTRUMENT);
            assertThat(body.has("ccy")).as("E4.7: без валюты расчёта").isFalse();
        });
        Map<String, Object> event = outbox(trail, ANOMALY_REPORTED).stream()
                .filter(row -> String.valueOf(row.get("payload")).contains(FOREIGN_INSTRUMENT_RISK))
                .findFirst().orElseThrow();
        assertThat(awaitJournalRow(trail, event.get("event_id")).get("event_type"))
                .as("E4.7: строка журнала об отчёте есть").isEqualTo(ANOMALY_REPORTED);
    }

    @Test
    @Order(6)
    @DisplayName("E4.6 — Отказ записи отчёта реакцию не гейтит, и число статистики его не досчитывается")
    void e4_6_aRefusedReportWriteDoesNotGateTheReaction() {
        walkToExposure(trail);
        standAtObservation(trail);
        Map<String, Object> before = incidents(trail);
        Integer reported = outbox(trail, ANOMALY_REPORTED).size();
        trail.database(Party.TRADING_CORE).refuseInserts("anomaly_reports", "new.severity = 'CRITICAL'");
        trail.forgetTraces();

        detect(trail);
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E4.6: ступень счёта поднята")
                .isEqualTo("TRADE_BLOCKED");
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E4.6: строка outbox класса подъёма есть").hasSize(1);
        assertThat(trail.database(Party.TRADING_CORE).query("select id from anomaly_reports where severity = "
                + "'CRITICAL'")).as("E4.6: строки отчёта нет").isEmpty();
        assertThat(outbox(trail, ANOMALY_REPORTED)).as("E4.6: строки outbox класса отчёта нет").hasSize(reported);
        assertThat(trail.exchange().requests(ExitTrail.CLOSE_POSITION))
                .as("E4.6: команды снятия риска ушли — реакция журналом не гейтится").isNotEmpty();
        awaitJournalRow(trail, raised.getFirst().get("event_id"));
        Map<String, Object> after = incidents(trail);
        assertThat(counter(after, "raised_holds")).as("E4.6: счётчик подъёмов вырос")
                .isEqualTo(counter(before, "raised_holds") + 1);
        assertThat(counter(after, "anomaly_reports")).as("E4.6: счётчик отчётов — нет")
                .isEqualTo(counter(before, "anomaly_reports"));
    }

    /** Критичная строка отчёта по коду. */
    private static Map<String, Object> critical(String code) {
        Database core = trail.database(Party.TRADING_CORE);
        return core.query("select status, created_at, modified_at, internal_before::text as internal_before, "
                + "external_before::text as external_before, internal_after::text as internal_after, "
                + "external_after::text as external_after from anomaly_reports "
                + "where code = ? and severity = 'CRITICAL' order by id", code).getLast();
    }

    private static String path(LoggedRequest request) {
        return request.getUrl().split("\\?")[0];
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
