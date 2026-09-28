package com.example.tests.e2e.safetyteardown;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.SharedStand;
import com.example.tests.e2e.Trail;
import com.example.tests.e2e.Trail.Answer;
import com.example.tests.e2e.exitandclose.ExitTrail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.safetyteardown.TeardownTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.EXTERNAL_SECOND_INSTRUMENT;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.HOLD_RAISED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MANUAL_REQUESTED;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.MIN_AGE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.SECOND_SIZE;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.TEARDOWN_ATTEMPTS;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.absent;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.awaitJournalRow;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.commandBodies;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.commands;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealOutbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.dealStatus;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.detect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.episodes;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.exchangeAcknowledgesSecondCloseWithoutEffect;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.haltFully;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.outbox;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.pairRung;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.payload;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.recoverSecondDeal;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.reports;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.safetyState;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.secondPairRung;
import static com.example.tests.e2e.safetyteardown.TeardownTrail.walkToExposure;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кейсы тропы снятия риска со второй сделкой счёта, заведённой
 * восстановлением: радиус пары, обход счёта и каскад по двум сделкам
 * (.claude/tests/cases/e2e-safety-teardown.md §«Пролог второй сделки счёта —
 * восстановлением»; пробел {@code G5}).
 *
 * <p><b>Пролог у клеток общий, а ядро у каждой своё:</b> ступень необратима,
 * и каждая клетка поднимает сделку тропы и вторую сделку заново;
 * {@code E3.7} продолжает ядро {@code E2.9} — её вход тот же ход. Вход клеток
 * групп {@code E2}-{@code E3} — ручная полная постановка: тиков детекции над
 * восстановленной сделкой он не добавляет.
 *
 * <p><b>Порядок сделок в обходе не ассертится</b> — выборки популяций без
 * упорядочения, — и площадка отражает закрытие двух позиций в любом порядке
 * ({@link TeardownTrail#recoverSecondDeal}).
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Пробел G5 — вторая сделка счёта, заведённая восстановлением")
class TeardownRecoveredDealPathTest {

    private static final Integer ATTEMPTS = 3;

    private static final String EXPOSURE_MISMATCH = "EXCHANGE_EXPOSURE_MISMATCH";

    private static Trail trail;

    private static String deal;

    private static String recovered;

    @BeforeAll
    static void openTrail() {
        trail = SharedStand.dealPath(TeardownRecoveredDealPathTest.class);
        trail.side(Party.TRADING_CORE).set(MIN_AGE, "0s");
        trail.side(Party.TRADING_CORE).set(TEARDOWN_ATTEMPTS, String.valueOf(ATTEMPTS));
        trail.renew(Party.TRADING_CORE);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            SharedStand.release(TeardownRecoveredDealPathTest.class);
        }
    }

    @Test
    @Order(1)
    @DisplayName("E2.8 — Инструментный радиус при второй, восстановленной сделке счёта: снятие идёт по сделке пары, "
            + "восстановленную не трогает")
    void e2_8_thePairRadiusTearsDownThePairDealAndLeavesTheRecoveredOne() {
        String pairDeal = walkToExposure(trail);
        String second = recoverSecondDeal(trail);
        trail.forgetTraces();

        Answer answer = haltFully(trail, Trail.INSTRUMENT);
        trail.relayCore();

        assertThat(answer.status()).as("E2.8: постановка на паре принята — " + answer.body()).isEqualTo(202);
        List<String> commands = commandLines();
        assertThat(commands).as("E2.8: команды снятия риска несут инструмент тропы").isNotEmpty()
                .allSatisfy(command -> assertThat(command).contains(Trail.EXTERNAL_INSTRUMENT));
        assertThat(commands).as("E2.8: по второму инструменту нет ни одной команды")
                .noneSatisfy(command -> assertThat(command).contains(EXTERNAL_SECOND_INSTRUMENT));
        assertThat(pairRung(trail)).as("E2.8: ступень пары тропы — сворачивание").isEqualTo("TRADE_BLOCKED");
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E2.8: ступени счёта нет")
                .isEqualTo("ACTIVE");
        assertThat(secondPairRung(trail)).as("E2.8: ступени пары второго инструмента нет")
                .satisfiesAnyOf(rung -> assertThat(rung).isNull(), rung -> assertThat(rung).isEqualTo("ACTIVE"));
        assertThat(dealStatus(trail, pairDeal)).as("E2.8: сделка тропы в ошибочном состоянии").isEqualTo("ERROR");
        assertThat(dealStatus(trail, second)).as("E2.8: восстановленная сделка осталась активной")
                .isEqualTo("ACTIVE");
        assertThat(dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, second)).as("E2.8: остановки по ней нет").isEmpty();
        assertThat(episodes(trail, second)).as("E2.8: строки эпизода у неё не заведено").isEmpty();
        assertThat(reports(trail, MANUAL_REQUESTED).getLast().get("status"))
                .as("E2.8: отчёт реакции доведён до терминала").isEqualTo("COMPLETED");
        assertThat(outbox(trail, HOLD_RAISED)).as("E2.8: эскалации нет — строка подъёма одна").hasSize(1);
        List<Map<String, Object>> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, pairDeal);
        assertThat(shutdown).as("E2.8: строка outbox остановки сделки тропы одна").hasSize(1);
        awaitJournalRow(trail, shutdown.getFirst().get("event_id"));
        assertThat(journalShutdowns()).as("E2.8: строка журнала об остановке одна — по сделке тропы")
                .containsExactly(pairDeal);
    }

    @Test
    @Order(2)
    @DisplayName("E2.9 — Счётный радиус обходит и восстановленную сделку: её эпизод заводит добыча снятия, "
            + "позиция закрывается, исход подтверждён")
    void e2_9_theAccountRadiusTearsDownTheRecoveredDealToo() {
        deal = walkToExposure(trail);
        recovered = recoverSecondDeal(trail);
        trail.forgetTraces();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("E2.9: постановка на счёте принята — " + answer.body()).isEqualTo(202);
        List<LoggedRequest> journal = trail.exchange().requests();
        Integer read = firstIndex(journal, request -> Objects.equals(Trail.EXCHANGE_POSITIONS, path(request))
                && Objects.equals(EXTERNAL_SECOND_INSTRUMENT, instrument(request)));
        List<Integer> closes = indexes(journal, request -> Objects.equals(ExitTrail.CLOSE_POSITION, path(request))
                && request.getBodyAsString().contains(EXTERNAL_SECOND_INSTRUMENT));
        assertThat(read).as("E2.9: чтение позиции по второму инструменту ушло к стабу").isNotNegative();
        assertThat(closes).as("E2.9: закрытие позиции по второму инструменту ровно одно").hasSize(1);
        assertThat(closes.getFirst()).as("E2.9: закрытие позже чтения позиции").isGreaterThan(read);
        assertThat(commandLines()).as("E2.9: отмен и снятий защит по второму инструменту нет")
                .filteredOn(command -> isFalse(command.startsWith(ExitTrail.CLOSE_POSITION)))
                .noneSatisfy(command -> assertThat(command).contains(EXTERNAL_SECOND_INSTRUMENT));
        assertThat(episodes(trail, recovered)).as("E2.9: у восстановленной сделки заведена строка эпизода")
                .hasSize(1).extracting(row -> row.get("status")).containsExactly("CLOSED");
        assertThat(reports(trail, MANUAL_REQUESTED).getLast().get("status"))
                .as("E2.9: снятие подтверждено — отчёт реакции доведён до терминала").isEqualTo("COMPLETED");
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E2.9: ступень счёта — сворачивание")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(outbox(trail, HOLD_RAISED)).as("E2.9: строка outbox класса подъёма одна").hasSize(1);
    }

    @Test
    @Order(3)
    @DisplayName("E3.7 — Каскад уводит обе сделки счёта — входную и восстановленную — по сделке за раз")
    void e3_7_theCascadeTakesBothDealsOfTheAccountOneAtATime() {
        assertThat(reports(trail, MANUAL_REQUESTED).getLast().get("status"))
                .as("предусловие E3.7: состояние E2.9 — снятие подтверждено").isEqualTo("COMPLETED");

        Instant terminal = moment(trail.database(Party.TRADING_CORE).query("select modified_at from anomaly_reports "
                + "where " + Trail.BY_ACCOUNT + " and code = ? order by id", trail.account(), MANUAL_REQUESTED)
                .getLast().get("modified_at"));
        for (String each : List.of(deal, recovered)) {
            assertThat(dealStatus(trail, each)).as("E3.7: сделка " + each + " в ошибочном состоянии")
                    .isEqualTo("ERROR");
            assertThat(trail.database(Party.TRADING_CORE).query("select shutdown_reason from deals "
                    + "where internal_id = ?", each).getFirst().get("shutdown_reason"))
                    .as("E3.7: причина у " + each + " — биржевая").isEqualTo("EXCHANGE_HOLD");
            List<Map<String, Object>> shutdown = dealOutbox(trail, DEAL_SHUTDOWN_INITIATED, each);
            assertThat(shutdown).as("E3.7: строка outbox остановки по " + each + " одна").hasSize(1);
            assertThat(moment(shutdown.getFirst().get("occurred_at")))
                    .as("E3.7: остановка " + each + " позже терминала отчёта").isAfter(terminal);
            awaitJournalRow(trail, shutdown.getFirst().get("event_id"));
        }
        assertThat(outbox(trail, DEAL_SHUTDOWN_INITIATED)).as("E3.7: строк остановки две, по одной на сделку")
                .hasSize(2);
        assertThat(journalShutdowns()).as("E3.7: две строки журнала об остановке, по одной на сделку")
                .containsExactlyInAnyOrder(deal, recovered);
        assertThat(trail.rows(Party.STATISTICS, "deal_facts")).as("E3.7: сделочных фактов нет — терминала не было")
                .isZero();
    }

    @Test
    @Order(4)
    @DisplayName("E2.10 — Счётный радиус: неподтверждённая восстановленная сделка делает исход неподтверждённым, "
            + "и обход пройден до конца")
    void e2_10_anUnconfirmedRecoveredDealMakesTheOutcomeUnconfirmedAndTheWalkGoesToTheEnd() {
        String pairDeal = walkToExposure(trail);
        recoverSecondDeal(trail);
        exchangeAcknowledgesSecondCloseWithoutEffect(trail);
        trail.forgetTraces();

        Answer answer = haltFully(trail, null);
        trail.relayCore();

        assertThat(answer.status()).as("E2.10: постановка на счёте принята — " + answer.body()).isEqualTo(202);
        List<String> closes = commandBodies(trail, ExitTrail.CLOSE_POSITION);
        assertThat(closes).as("E2.10: закрытие по инструменту тропы ушло")
                .anySatisfy(command -> assertThat(command).contains(Trail.EXTERNAL_INSTRUMENT));
        assertThat(closes.stream().filter(command -> command.contains(EXTERNAL_SECOND_INSTRUMENT)).count())
                .as("E2.10: закрытие по второму ушло, и закрытий не больше предела попыток")
                .isPositive().isLessThanOrEqualTo(ATTEMPTS.longValue());
        assertThat(reports(trail, MANUAL_REQUESTED).getLast().get("status"))
                .as("E2.10: исход не подтверждён — отчёт в промежуточном статусе").isNotEqualTo("COMPLETED");
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E2.10: ступень счёта стои́т")
                .isEqualTo("TRADE_BLOCKED");
        assertThat(outbox(trail, HOLD_RAISED)).as("E2.10: эскалации нет — строка подъёма одна").hasSize(1);
        assertThat(episodes(trail, pairDeal)).as("E2.10: эпизод сделки тропы закрыт")
                .extracting(row -> row.get("status")).containsOnly("CLOSED");
    }

    /**
     * Без строки эпизода обе стороны сверки нулевые, поэтому первый проход
     * добывает позицию до всякой работы, и расхождение наблюдаемо только
     * после неё (находка {@code F9} документа закрыта кодом).
     */
    @Test
    @Order(5)
    @DisplayName("E1.9 — Восстановленная сделка с живой позицией: первый проход добывает её эпизод, и детекция "
            + "поднимает сворачивание счёта расхождением экспозиции")
    void e1_9_theFirstPassOfTheRecoveredDealFetchesItsEpisodeAndDetectionRaisesTheAccountRung() {
        walkToExposure(trail);
        String second = recoverSecondDeal(trail);
        Database core = trail.database(Party.TRADING_CORE);
        String trancheSql = "select status from deal_tranches where deal_id = (select id from deals where internal_id = ?)";
        Object tranche = core.query(trancheSql, second).getFirst().get("status");
        trail.forgetTraces();

        trail.orchestrate();

        List<Map<String, Object>> episodes = episodes(trail, second);
        assertThat(episodes).as("E1.9: после прохода заведена строка живого эпизода").hasSize(1)
                .extracting(row -> row.get("status")).containsExactly("ACTIVE");
        assertThat(plain(episodes.getFirst().get("external_size"))).as("E1.9: размером среза").isEqualTo(SECOND_SIZE);
        assertThat(core.query(trancheSql, second).getFirst().get("status"))
                .as("E1.9: транш остался в ведении — проход при расхождении работы не делает").isEqualTo(tranche);
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E1.9: после прохода ступени нет")
                .isEqualTo("ACTIVE");
        assertThat(trail.exchange().requests(Trail.EXCHANGE_POSITIONS))
                .as("E1.9: на проходе ушло чтение позиции по второму инструменту")
                .anySatisfy(request -> assertThat(instrument(request)).isEqualTo(EXTERNAL_SECOND_INSTRUMENT));
        assertThat(commands(trail)).as("E1.9: на проходе команд нет").isEmpty();

        detect(trail);
        trail.relayCore();

        List<Map<String, Object>> rows = reports(trail, EXPOSURE_MISMATCH);
        assertThat(rows).as("E1.9: после первого тика строка отчёта расхождением экспозиции").hasSize(1);
        assertThat(rows.getFirst().get("severity")).as("E1.9: некритичная").isEqualTo("NON_CRITICAL");
        assertThat(rows.getFirst().get("scope")).as("E1.9: радиус счётный").isEqualTo("EXCHANGE_ACCOUNT");
        assertThat(safetyState(trail).path("accountSafetyRung").asString()).as("E1.9: после первого тика ступени нет")
                .isEqualTo("ACTIVE");
        assertThat(commands(trail)).as("E1.9: на первом тике команд нет").isEmpty();

        detect(trail);
        trail.relayCore();

        assertThat(safetyState(trail).path("accountSafetyRung").asString())
                .as("E1.9: после второго тика — сворачивание").isEqualTo("TRADE_BLOCKED");
        List<Map<String, Object>> raised = outbox(trail, HOLD_RAISED);
        assertThat(raised).as("E1.9: строка outbox класса подъёма одна").hasSize(1);
        JsonNode content = payload(raised.getFirst());
        assertThat(content.path("scope").asString()).as("E1.9: радиус счётный").isEqualTo("EXCHANGE_ACCOUNT");
        assertThat(content.path("code").asString()).as("E1.9: код расхождения экспозиции").isEqualTo(EXPOSURE_MISMATCH);
        assertThat(content.path("rung").asString()).as("E1.9: ступень жёсткая").isEqualTo("HARD");
        assertThat(absent(content.path("instrumentInternalId"))).as("E1.9: инструмента в содержимом нет").isTrue();
        assertThat(reports(trail, EXPOSURE_MISMATCH).getLast().get("severity"))
                .as("E1.9: критичная строка отчёта тем же кодом").isEqualTo("CRITICAL");
    }

    // ---------------------------------------------------------------- чтения

    /** Сделки, об остановке которых у журнала есть строка, — по тенанту ходов. */
    private static List<Object> journalShutdowns() {
        return trail.database(Party.AUDIT).query("select deal_internal_id from audit_records where tenant_id = ? "
                        + "and event_type = ?", trail.tenant(), DEAL_SHUTDOWN_INITIATED).stream()
                .map(row -> row.get("deal_internal_id"))
                .toList();
    }

    /** Команды площадке телами: всё, что не чтение, — адресом и телом. */
    private static List<String> commandLines() {
        return trail.exchange().requests().stream()
                .filter(request -> isFalse(Objects.equals("GET", request.getMethod().getName())))
                .map(request -> path(request) + " " + request.getBodyAsString())
                .toList();
    }

    private static Integer firstIndex(List<LoggedRequest> journal, Predicate<LoggedRequest> match) {
        List<Integer> found = indexes(journal, match);
        return found.isEmpty() ? -1 : found.getFirst();
    }

    private static List<Integer> indexes(List<LoggedRequest> journal,
                                         Predicate<LoggedRequest> match) {
        return IntStream.range(0, journal.size())
                .filter(index -> match.test(journal.get(index)))
                .boxed()
                .toList();
    }

    /** Инструмент чтения — параметром запроса; пусто — чтение не по инструменту. */
    private static String instrument(LoggedRequest request) {
        return request.queryParameter("instId").isPresent() ? request.queryParameter("instId").firstValue() : null;
    }

    private static String path(LoggedRequest request) {
        return request.getUrl().split("\\?")[0];
    }

    private static String plain(Object number) {
        return ((BigDecimal) number).stripTrailingZeros().toPlainString();
    }

    private static Instant moment(Object column) {
        return column instanceof Timestamp stamp ? stamp.toInstant() : ((OffsetDateTime) column).toInstant();
    }
}
