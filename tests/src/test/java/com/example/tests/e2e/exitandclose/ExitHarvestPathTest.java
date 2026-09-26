package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.Side;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.time.Duration;
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

import static com.example.tests.e2e.exitandclose.ExitTrail.BILLS;
import static com.example.tests.e2e.exitandclose.ExitTrail.BILLS_ARCHIVE;
import static com.example.tests.e2e.exitandclose.ExitTrail.INDEX_CANDLES;
import static com.example.tests.e2e.exitandclose.ExitTrail.POSITIONS_HISTORY;
import static com.example.tests.e2e.exitandclose.ExitTrail.bill;
import static com.example.tests.e2e.exitandclose.ExitTrail.closeRecord;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeKeepsCloseRecords;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeQuotesIndex;
import static com.example.tests.e2e.exitandclose.ExitTrail.journalOf;
import static com.example.tests.e2e.exitandclose.ExitTrail.plain;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToTerminalTranches;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Группа {@code E3} тропы выхода: добыча фактов закрытия через коннектор
 * (.claude/tests/cases/e2e-exit-and-close.md §«E3 — Добыча фактов закрытия
 * через коннектор»).
 *
 * <p><b>Шесть клеток идут одной сделкой, от плоской позиции к записанному
 * итогу:</b> {@code E3.1} — история закрытых эпизодов пуста;
 * {@code E3.3} — записи закрытия обоих эпизодов добыты (вход {@code E3.2}),
 * затем движения окна; {@code E3.5} — движения спрошены повторно, потому что
 * свечи индекса ещё нет; {@code E3.6} — свеча пришла, курс применён;
 * {@code E3.2} и {@code E3.4} — итог записан. Итог пишет финализация, звено
 * которой идёт после добычи движений, поэтому клетки, называющие итог,
 * стоят последними и читают след добычи, снятый в момент их входа, — как
 * {@code E2.5} читала журнал, накопленный ходами {@code E2.1}.
 *
 * <p><b>Бюджет повторов звена ядра поднят, а пауза сокращена конфигурацией
 * процесса:</b> пустая история закрытых эпизодов тратит бюджет звена
 * добычи позиции с первого наблюдения плоской позиции, то есть ещё во время
 * каскада, и предусловие {@code E3.1} — транши терминальны, запись не
 * добыта — при бюджете в три попытки гонится с ним. Исчерпание, которое
 * называет {@code E3.7}, от этого не исчезает, а наступает позже.
 *
 * <p>{@code E3.7} идёт второй сделкой: курс там не приходит никогда.
 * {@code E3.8} — третьей: площадка подтверждает её входную ногу вчерашним
 * моментом, и нижняя граница окна моложе глубины свежего эндпоинта.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E3 — Добыча фактов закрытия через коннектор")
class ExitHarvestPathTest {

    private static final String NEIGHBOUR_INSTRUMENT = "BTC-USDT-SWAP";

    private static final String INDEX = "USDC-USDT";

    private static final String RATE = "0.9998";

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long REOPENED = 1758240100000L;

    private static final Long RECLOSED = 1758240200000L;

    private static final Long FUNDED = 1758240150000L;

    private static final String LAST_BILL = "9001";

    private static final String ACCESS_KEY = "OK-ACCESS-KEY";

    private static final String EMERGENCY_CLOSED = "EMERGENCY_CLOSED";

    private static Trail trail;

    private static String deal;

    private static Object dealId;

    private static Long sourceTime;

    private static Integer journalMark;

    private static Long dealFacts;

    private static Long incidentFacts;

    private static List<LoggedRequest> recordsTrace;

    private static List<Side.Access> recordsAccesses;

    private static List<LoggedRequest> billsTrace;

    @BeforeAll
    static void openTrail() {
        trail = Trail.open("x3");
        Side core = trail.side(Party.TRADING_CORE);
        core.set("service-command-retry.default-policy.max-attempts", "10");
        core.set("service-command-retry.default-policy.initial-delay", "1s");
        core.set("service-command-retry.default-policy.max-delay", "2s");
        trail.renew(Party.TRADING_CORE);
        sourceTime = System.currentTimeMillis();
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            trail.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("E3.1 — Подтверждение отсутствия живого риска добывается ПО ОДНОЙ сущности за проход, "
            + "а не срезом счёта")
    void e3_1_theAbsenceOfLiveRiskIsFetchedPerEntityNotBySnapshot() {
        walkToHarvest(null);
        journalMark = journalOf(trail, deal).size();
        trail.forgetTraces();

        trail.passUntil("E3.1: история закрытых эпизодов спрошена",
                () -> isFalse(trail.exchange().requests(POSITIONS_HISTORY).isEmpty()));
        trail.relayCore();

        List<LoggedRequest> positions = trail.exchange().requests(Trail.EXCHANGE_POSITIONS);
        List<LoggedRequest> history = trail.exchange().requests(POSITIONS_HISTORY);
        assertThat(positions).as("E3.1: позиция спрошена по инструменту, срезов счёта нет").isNotEmpty()
                .allSatisfy(request -> assertThat(param(request, "instId")).isEqualTo(Trail.EXTERNAL_INSTRUMENT));
        assertThat(history).as("E3.1: история закрытых спрошена одна, по инструменту").hasSize(1)
                .allSatisfy(request -> assertThat(param(request, "instId")).isEqualTo(Trail.EXTERNAL_INSTRUMENT));
        List<String> paths = paths(trail.exchange().requests());
        assertThat(paths.indexOf(Trail.EXCHANGE_POSITIONS)).as("E3.1: живой эпизод, затем история закрытых")
                .isLessThan(paths.indexOf(POSITIONS_HISTORY));
        assertThat(trail.accesses(Party.CONNECTOR)).as("E3.1: звено добычи позиции у коннектора — по счёту")
                .extracting(Side.Access::path)
                .contains(accountPath("/positions/instrument"), accountPath("/positions/closed"))
                .doesNotContain(accountPath("/positions"));
        assertThat(trail.exchange().requests(BILLS)).as("E3.1: звена добычи движений этот проход не эмитит")
                .isEmpty();
        assertThat(trail.database(Party.TRADING_CORE).query("select status from positions where deal_id = ?",
                dealId)).as("E3.1: гейт отсутствия живого риска закрыт на плоском ответе")
                .allSatisfy(row -> assertThat(row.get("status")).isEqualTo("CLOSED"));
        assertThat(dealRead(trail, deal).path("status").asString())
                .as("E3.1: сделка осталась в координированном выходе").isEqualTo("EXIT_PENDING");
        assertThat(dealRow().get("result_profit")).as("E3.1: итога нет").isNull();
        assertThat(trail.database(Party.TRADING_CORE).query("select id from deal_system_action_states where "
                        + "deal_id = ? and system_action_type = 'FINALIZE_DEAL_EXIT_ACTION'", dealId))
                .as("E3.1: финализации нет").isEmpty();
        assertThat(journalOf(trail, deal)).as("E3.1: у журнала следа нет").hasSize(journalMark);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E3.1: у статистики следа нет")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.1: и фактов происшествий")
                .isEqualTo(incidentFacts);
    }

    @Test
    @Order(2)
    @DisplayName("E3.3 — Движения добываются окном: нижняя граница биржевая, верхняя — время площадки "
            + "того же прохода")
    void e3_3_cashFlowsAreFetchedByWindow() {
        closeRecordsLanded();
        trail.forgetTraces();

        trail.passUntil("E3.3: движения добыты", () -> nonNull(dealRow().get("bills_fetched_through")));
        trail.relayCore();
        billsTrace = trail.exchange().requests();

        Long windowBegin = windowBegin();
        assertThat(windowBegin).as("предусловие E3.3: нижняя граница старше глубины свежего эндпоинта")
                .isLessThan(sourceTime - Duration.ofDays(7).toMillis());
        List<String> paths = paths(billsTrace);
        assertThat(paths.indexOf(Trail.EXCHANGE_TIME)).as("E3.3: время площадки спрошено раньше движений")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(paths.indexOf(BILLS));
        assertThat(paths.indexOf(BILLS)).as("E3.3: свежий эндпоинт спрошен раньше архивного")
                .isLessThan(paths.indexOf(BILLS_ARCHIVE));
        LoggedRequest first = requestsOf(billsTrace, BILLS).getFirst();
        assertThat(param(first, "begin")).as("E3.3: нижняя граница — биржевая метка сделки")
                .isEqualTo(String.valueOf(windowBegin));
        assertThat(param(first, "end")).as("E3.3: верхняя — момент, который отдала площадка")
                .isEqualTo(String.valueOf(sourceTime));
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE).query("select external_bill_id, "
                + "category, amount, external_fee, ccy from deal_cash_flows where deal_id = ? "
                + "order by external_bill_id", dealId);
        assertThat(rows).as("E3.3: строки разбивки заведены, ссылка на сделку проставлена тем же проходом")
                .extracting(row -> row.get("external_bill_id"), row -> row.get("category"),
                        row -> plain(row.get("amount")), row -> plain(row.get("external_fee")), row -> row.get("ccy"))
                .containsExactly(
                        tuple(LAST_BILL, "FUNDING", "-0.05", "0", "USDC"),
                        tuple("9003", "REALIZED_PNL", "0.4", "-0.1", "USDT"),
                        tuple("9004", "REALIZED_PNL", "0.8", "-0.2", "USDT"));
        assertThat(journalOf(trail, deal)).as("E3.3: у журнала следа нет").hasSize(journalMark);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E3.3: у статистики следа нет")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.3: и фактов происшествий")
                .isEqualTo(incidentFacts);
    }

    @Test
    @Order(3)
    @DisplayName("E3.5 — Повторная добыча тех же движений второй строки не заводит")
    void e3_5_aRepeatedFetchOfTheSameFlowsAddsNoRow() {
        Long rows = trail.database(Party.TRADING_CORE).count("deal_cash_flows");
        trail.forgetTraces();

        trail.passUntil("E3.5: движения спрошены повторно",
                () -> isFalse(trail.exchange().requests(BILLS).isEmpty()));

        assertThat(trail.database(Party.TRADING_CORE).count("deal_cash_flows"))
                .as("E3.5: число строк разбивки не изменилось").isEqualTo(rows);
        assertThat(trail.database(Party.TRADING_CORE).query("select external_bill_id from deal_cash_flows "
                        + "group by exchange_account_id, external_bill_id having count(*) > 1"))
                .as("E3.5: ключ «счёт плюс идентификатор записи» дубля не пропустил").isEmpty();
        assertThat(dealRow().get("result_profit")).as("E3.5: итог тот же — недоступен").isNull();
    }

    @Test
    @Order(4)
    @DisplayName("E3.6 — Курс чужой валюты идёт публичным чтением коннектора")
    void e3_6_aForeignCurrencyRateGoesThroughAPublicConnectorRead() {
        exchangeQuotesIndex(trail, FUNDED, RATE);
        trail.forgetTraces();

        trail.passUntil("E3.6: курс применён", () -> Objects.equals("APPLIED", foreignRow().get("rate_status")));
        trail.relayCore();

        List<LoggedRequest> candles = trail.exchange().requests(INDEX_CANDLES);
        assertThat(candles).as("E3.6: свеча индекса спрошена публично — без ключа счёта").isNotEmpty()
                .allSatisfy(request -> assertThat(request.containsHeader(ACCESS_KEY)).isFalse());
        assertThat(candles).as("E3.6: имя индекса — валюта движения и расчётная валюта, на момент операции")
                .anySatisfy(request -> {
                    assertThat(param(request, "instId")).isEqualTo(INDEX);
                    assertThat(param(request, "bar")).isEqualTo("1s");
                    assertThat(param(request, "after")).isEqualTo(String.valueOf(FUNDED + 1));
                });
        assertThat(trail.accesses(Party.CONNECTOR)).as("E3.6: у коннектора — публичная поверхность, не счёт")
                .extracting(Side.Access::path)
                .contains("/api/v1/market/candles/index");
        Map<String, Object> foreign = foreignRow();
        assertThat(foreign.get("applied_rate")).as("E3.6: курс записан")
                .satisfies(rate -> assertThat((BigDecimal) rate).isEqualByComparingTo(RATE));
        assertThat(foreign.get("applied_rate_candle_instrument")).as("E3.6: координата свечи — индекс")
                .isEqualTo(INDEX);
        assertThat(foreign.get("applied_rate_candle_timeframe")).as("E3.6: фактическое разрешение — секунда")
                .isEqualTo("ONE_SECOND");
        assertThat(instant(foreign.get("applied_rate_candle_open_time")))
                .as("E3.6: момент открытия свечи").isEqualTo(Instant.ofEpochMilli(FUNDED));
        assertThat(trail.database(Party.TRADING_CORE).query("select rate_status from deal_cash_flows "
                        + "where deal_id = ? and ccy = 'USDT'", dealId))
                .as("E3.6: у строк расчётной валюты курс не нужен").isNotEmpty()
                .allSatisfy(row -> assertThat(row.get("rate_status")).isEqualTo("NOT_REQUIRED"));
        assertThat(trail.exchange().requests()).as("E3.6: команд площадке нет")
                .allSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("GET"));
        assertThat(trail.exchange().requests()).as("E3.6: приватные чтения — только страницы движений звена")
                .filteredOn(request -> request.containsHeader(ACCESS_KEY))
                .extracting(request -> request.getUrl().split("\\?")[0])
                .allSatisfy(path -> assertThat(path).isIn(BILLS, BILLS_ARCHIVE));
        assertThat(journalOf(trail, deal)).as("E3.6: у журнала следа нет").hasSize(journalMark);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E3.6: у статистики следа нет")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.6: и фактов происшествий")
                .isEqualTo(incidentFacts);
    }

    @Test
    @Order(5)
    @DisplayName("E3.2 — Запись закрытия эпизода доезжает до зеркала, и числа складываются по эпизодам")
    void e3_2_theEpisodeCloseRecordReachesTheMirrorAndNumbersAddUpByEpisode() {
        trail.passUntil("итог сделки записан", () -> nonNull(dealRow().get("result_profit")));
        trail.relayCore();

        assertThat(recordsTrace).as("E3.2: к стабу ушло чтение закрытых эпизодов с инструментом и началом окна")
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(param(request, "instId")).isEqualTo(Trail.EXTERNAL_INSTRUMENT);
                    assertThat(param(request, "before")).isEqualTo(String.valueOf(windowBegin()));
                });
        assertThat(recordsAccesses).as("E3.2: у коннектора — по счёту").extracting(Side.Access::path)
                .contains(accountPath("/positions/closed"));
        List<Map<String, Object>> episodes = trail.database(Party.TRADING_CORE).query("select status, "
                + "external_realized_profit, external_realized_profit_gross, external_fee, external_result_currency "
                + "from positions where deal_id = ? order by external_realized_profit desc", dealId);
        assertThat(episodes).as("E3.2: в зеркале обе записи закрытия со своими числами")
                .extracting(row -> row.get("status"), row -> plain(row.get("external_realized_profit")),
                        row -> plain(row.get("external_realized_profit_gross")), row -> plain(row.get("external_fee")),
                        row -> row.get("external_result_currency"))
                .containsExactly(
                        tuple("CLOSED", "0.8", "1", "-0.2", "USDT"),
                        tuple("CLOSED", "0.4", "0.5", "-0.1", "USDT"));
        BigDecimal crossTerm = new BigDecimal("-0.05").multiply(new BigDecimal(RATE));
        Map<String, Object> row = dealRow();
        assertThat((BigDecimal) row.get("result_profit")).as("E3.2: итог — сумма по обоим эпизодам, а не по "
                        + "последнему (плюс слагаемое чужой валюты E3.6)")
                .isEqualByComparingTo(new BigDecimal("0.8").add(new BigDecimal("0.4")).add(crossTerm));
        assertThat(row.get("result_profit_currency")).as("E3.2: валюта результата — расчётная валюта инструмента")
                .isEqualTo("USDT");
        List<Map<String, Object>> journal = journalOf(trail, deal);
        assertThat(journal.subList(journalMark, journal.size()))
                .as("E3.2: классов этого отрезка у журнала нет — только терминал")
                .allSatisfy(entry -> assertThat(entry.get("event_type")).isEqualTo("DEAL_CLOSED"));
    }

    @Test
    @Order(6)
    @DisplayName("E3.4 — Выдача движений аккаунт-широкая, и чужой инструмент отсекает предикат линковки")
    void e3_4_theCashFlowFeedIsAccountWideAndTheLinkagePredicateCutsTheNeighbour() {
        assertThat(requestsOf(billsTrace, BILLS)).as("E3.4: запрос движений несёт только счёт и окно")
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.queryParameter("instId").isPresent()).isFalse();
                    assertThat(param(request, "instType")).isEqualTo("SWAP");
                    assertThat(param(request, "begin")).isNotBlank();
                    assertThat(param(request, "end")).isNotBlank();
                });
        assertThat(trail.database(Party.TRADING_CORE).query("select reconciliation_status from deals where id = ?",
                dealId).getFirst().get("reconciliation_status"))
                .as("E3.4: строки соседа в область сверки не попали — сверка сошлась").isEqualTo("MATCHED");
        Database core = trail.database(Party.TRADING_CORE);
        assertThat(core.query("select deal_id from deal_cash_flows where external_instrument_id = ?",
                NEIGHBOUR_INSTRUMENT)).as("E3.4: строка соседнего инструмента сохранена с пустой ссылкой")
                .hasSize(1)
                .allSatisfy(flow -> assertThat(flow.get("deal_id")).isNull());
        assertThat(core.query("select deal_id from deal_cash_flows where external_instrument_id = ?",
                Trail.EXTERNAL_INSTRUMENT)).as("E3.4: ссылку получили только строки нашего инструмента")
                .hasSize(3)
                .allSatisfy(flow -> assertThat(flow.get("deal_id")).isEqualTo(dealId));
        BigDecimal crossTerm = new BigDecimal("-0.05").multiply(new BigDecimal(RATE));
        assertThat((BigDecimal) dealRow().get("result_profit")).as("E3.4: итог строк соседа не содержит")
                .isEqualByComparingTo(new BigDecimal("1.2").add(crossTerm));
    }

    @Test
    @Order(7)
    @DisplayName("E3.7 — Курс не получен: итог недоступен, терминала нет, звено повторяется по бюджету")
    void e3_7_noRateMeansNoResultNoTerminalAndTheLinkRepeatsByBudget() {
        walkToHarvest(closeRecord(OPENED, CLOSED, "1.0", "-0.2", "0", "0.8"));
        trail.forgetTraces();

        trail.passUntil("E3.7: строка чужой валюты ждёт курса", () -> nonNull(foreignRow())
                && Objects.equals("RATE_UNAVAILABLE", foreignRow().get("rate_status")));
        assertThat(dealRow().get("result_profit")).as("E3.7: итог недоступен, а не ноль").isNull();
        assertThat(dealRead(trail, deal).path("status").asString()).as("E3.7: терминала нет, пока бюджет жив")
                .isEqualTo("EXIT_PENDING");

        trail.passUntil("E3.7: сделка дошла до аварийного терминала",
                () -> Objects.equals(EMERGENCY_CLOSED, dealRead(trail, deal).path("status").asString()));
        trail.relayCore();

        Database core = trail.database(Party.TRADING_CORE);
        assertThat(foreignRow().get("rate_status")).as("E3.7: курс требовался и не получен")
                .isEqualTo("RATE_UNAVAILABLE");
        assertThat(dealRow().get("result_profit")).as("E3.7: аварийный терминал с пустым числом").isNull();
        assertThat(core.query("select id from deal_system_action_states where deal_id = ? and "
                        + "system_action_type = 'REFRESH_DEAL_CONTEXT_ACTION' and status = 'FAILED'", dealId))
                .as("E3.7: по исчерпании строка исполнения в отказе").isNotEmpty();
        assertThat(core.query("select safety_rung from account_instrument_states"))
                .as("E3.7: поднята ступень блокировки входа по инструменту")
                .extracting(state -> state.get("safety_rung")).contains("ENTRY_BLOCKED");
        assertThat(trail.exchange().requests(INDEX_CANDLES))
                .as("E3.7: чтение свечи повторялось каждым тиком")
                .filteredOn(request -> Objects.equals("1s", param(request, "bar")))
                .hasSizeGreaterThanOrEqualTo(2);
        List<Map<String, Object>> reports = outbox("ANOMALY_REPORTED");
        List<Map<String, Object>> holds = outbox("HOLD_RAISED");
        List<Map<String, Object>> closed = outbox("DEAL_CLOSED");
        assertThat(reports).as("E3.7: отчёт о происшествии заведён").isNotEmpty();
        assertThat(holds).as("E3.7: подъём ступени опубликован").isNotEmpty();
        assertThat(closed).as("E3.7: терминал один").hasSize(1);
        Database audit = trail.database(Party.AUDIT);
        for (Map<String, Object> event : List.of(reports.getFirst(), holds.getFirst(), closed.getFirst())) {
            Trail.await("E3.7: строка журнала " + event.get("event_type"), () -> audit
                    .query("select id from audit_records where event_id = ?", String.valueOf(event.get("event_id")))
                    .size() == 1);
        }
        assertThat(audit.query("select content::text as content from audit_records where event_id = ?",
                        String.valueOf(closed.getFirst().get("event_id"))).getFirst().get("content").toString())
                .as("E3.7: строка терминала — с аварийным исходом").contains("EMERGENCY");
        Database statistics = trail.database(Party.STATISTICS);
        String closedEvent = String.valueOf(closed.getFirst().get("event_id"));
        Trail.await("E3.7: сделочный факт принят", () -> statistics
                .query("select event_id from deal_facts where event_id = ?", closedEvent).size() == 1);
        assertThat(statistics.query("select net_result from deal_facts where event_id = ?", closedEvent)
                .getFirst().get("net_result")).as("E3.7: сделочный факт с пустым результатом").isNull();
        String reportEvent = String.valueOf(reports.getFirst().get("event_id"));
        Trail.await("E3.7: счётчик отчётов вырос — факт происшествия принят", () -> statistics
                .query("select event_id from incident_facts where event_id = ?", reportEvent).size() == 1);
    }

    @Test
    @Order(8)
    @DisplayName("E3.8 — Граница окна моложе глубины свежего эндпоинта: архив движений не спрашивается")
    void e3_8_aYoungWindowBoundaryLeavesTheBillsArchiveUnasked() {
        Long young = sourceTime - Duration.ofDays(1).toMillis();
        Long closedAt = young + 5_000;
        trail.exchangeAcknowledgesAt(young);
        exchangeQuotesIndex(trail, FUNDED, null);
        deal = walkToTerminalTranches(trail, closeRecord(OPENED, closedAt, "1.0", "-0.2", "0", "0.8"), sourceTime,
                LAST_BILL, bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", closedAt));
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        incidentFacts = trail.database(Party.STATISTICS).count("incident_facts");
        journalMark = journalOf(trail, deal).size();
        trail.forgetTraces();

        trail.passUntil("E3.8: движения добыты", () -> nonNull(dealRow().get("bills_fetched_through")));
        trail.relayCore();

        assertThat(trail.exchange().requests(BILLS_ARCHIVE)).as("E3.8: архивный эндпоинт не спрошен ни разу")
                .isEmpty();
        assertThat(windowBegin()).as("предусловие E3.8: нижняя граница — момент подтверждения входной ноги, "
                        + "моложе глубины свежего эндпоинта")
                .isEqualTo(young)
                .isGreaterThan(sourceTime - Duration.ofDays(7).toMillis());
        List<LoggedRequest> fresh = trail.exchange().requests(BILLS);
        assertThat(fresh).as("E3.8: свежий эндпоинт спрошен окном сделки").isNotEmpty();
        assertThat(param(fresh.getFirst(), "begin")).as("E3.8: нижняя граница — биржевая метка сделки")
                .isEqualTo(String.valueOf(young));
        assertThat(param(fresh.getFirst(), "end")).as("E3.8: верхняя — момент, который отдала площадка")
                .isEqualTo(String.valueOf(sourceTime));
        assertThat(trail.database(Party.TRADING_CORE).query("select external_bill_id, deal_id from deal_cash_flows "
                        + "where external_bill_id = ?", LAST_BILL))
                .as("E3.8: строка разбивки заведена и получила ссылку на сделку тем же проходом")
                .extracting(row -> row.get("deal_id")).containsExactly(dealId);
        assertThat(journalOf(trail, deal)).as("E3.8: у журнала следа нет").hasSize(journalMark);
        assertThat(trail.database(Party.STATISTICS).count("deal_facts")).as("E3.8: у статистики следа нет")
                .isEqualTo(dealFacts);
        assertThat(trail.database(Party.STATISTICS).count("incident_facts")).as("E3.8: и фактов происшествий")
                .isEqualTo(incidentFacts);
    }

    // ---------------------------------------------------------------- ходы и чтения

    /**
     * Пролог тропы до терминальности траншей с движениями группы: два наших
     * торговых движения, движение соседнего инструмента и финансирование в
     * чужой валюте; секундной свечи индекса нет.
     *
     * @param records записи закрытия; пусто — истории нет
     */
    private static void walkToHarvest(String records) {
        exchangeQuotesIndex(trail, FUNDED, null);
        deal = walkToTerminalTranches(trail, Objects.toString(records, ""), sourceTime, LAST_BILL, String.join(", ",
                bill("9004", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.8", "-0.2", CLOSED),
                bill("9003", Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT", "0.4", "-0.1", RECLOSED),
                bill("9002", NEIGHBOUR_INSTRUMENT, "2", "1", "USDT", "3", "-0.5", FUNDED),
                bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "8", "173", "USDC", "-0.05", "0", FUNDED)));
        dealId = trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", deal)
                .getFirst().get("id");
        dealFacts = trail.database(Party.STATISTICS).count("deal_facts");
        incidentFacts = trail.database(Party.STATISTICS).count("incident_facts");
    }

    /**
     * Состояние {@code E3.2}: площадка отдаёт две записи закрытия — эпизод
     * схлопывался и открывался заново под тем же идентификатором, — и
     * проходы идут, пока обе не лягут в зеркало. След чтения снимается
     * здесь: его читает {@code E3.2}, стоящая последней.
     */
    private static void closeRecordsLanded() {
        exchangeKeepsCloseRecords(trail, String.join(", ",
                closeRecord(OPENED, CLOSED, "1.0", "-0.2", "0", "0.8"),
                closeRecord(REOPENED, RECLOSED, "0.5", "-0.1", "0", "0.4")));
        trail.forgetTraces();
        trail.passUntil("записи закрытия обоих эпизодов в зеркале", () -> trail.database(Party.TRADING_CORE)
                .query("select id from positions where deal_id = ? and external_realized_profit is not null",
                        dealId).size() == 2);
        recordsTrace = trail.exchange().requests(POSITIONS_HISTORY);
        recordsAccesses = trail.accesses(Party.CONNECTOR);
        trail.relayCore();
    }

    private static Map<String, Object> dealRow() {
        return trail.database(Party.TRADING_CORE).query("select result_profit, result_profit_currency, "
                + "bills_window_begin, bills_fetched_through from deals where id = ?", dealId).getFirst();
    }

    /** Строка движения чужой валюты сделки; пусто — движения ещё не добыты. */
    private static Map<String, Object> foreignRow() {
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE).query("select rate_status, "
                + "applied_rate, applied_rate_candle_instrument, applied_rate_candle_timeframe, "
                + "applied_rate_candle_open_time from deal_cash_flows where deal_id = ? and ccy = 'USDC'", dealId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Нижняя граница окна сделки — в миллисекундах эпохи. */
    private static Long windowBegin() {
        return instant(dealRow().get("bills_window_begin")).toEpochMilli();
    }

    private static List<Map<String, Object>> outbox(String eventType) {
        return trail.database(Party.TRADING_CORE).query("select event_id, event_type from outbox_events "
                + "where event_type = ? order by id", eventType);
    }

    private static String accountPath(String suffix) {
        return "/api/v1/accounts/" + trail.account() + suffix;
    }

    private static List<LoggedRequest> requestsOf(List<LoggedRequest> requests, String path) {
        return requests.stream()
                .filter(request -> Objects.equals(path, request.getUrl().split("\\?")[0]))
                .toList();
    }

    private static List<String> paths(List<LoggedRequest> requests) {
        return requests.stream().map(request -> request.getUrl().split("\\?")[0]).toList();
    }

    /** Значение параметра запроса; пусто — параметра нет. */
    private static String param(LoggedRequest request, String name) {
        return request.queryParameter(name).isPresent() ? request.queryParameter(name).firstValue() : null;
    }

    private static Instant instant(Object column) {
        return column instanceof java.sql.Timestamp moment ? moment.toInstant()
                : ((OffsetDateTime) column).toInstant();
    }
}
