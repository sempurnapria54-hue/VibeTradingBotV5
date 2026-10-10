package com.example.tests.e2e.exitandclose;

import com.example.tests.e2e.Database;
import com.example.tests.e2e.Json;
import com.example.tests.e2e.Party;
import com.example.tests.e2e.SharedStand;
import com.example.tests.e2e.Trail;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ALGOS;
import static com.example.tests.e2e.exitandclose.ExitTrail.CANCEL_ORDER;
import static com.example.tests.e2e.exitandclose.ExitTrail.CLOSE_POSITION;
import static com.example.tests.e2e.exitandclose.ExitTrail.DEAL_SHUTDOWN_INITIATED;
import static com.example.tests.e2e.exitandclose.ExitTrail.HOLD_RAISED;
import static com.example.tests.e2e.exitandclose.ExitTrail.PARTIAL_EXIT_KEY;
import static com.example.tests.e2e.exitandclose.ExitTrail.attachedStopOnlyDefinition;
import static com.example.tests.e2e.exitandclose.ExitTrail.bill;
import static com.example.tests.e2e.exitandclose.ExitTrail.closeRecord;
import static com.example.tests.e2e.exitandclose.ExitTrail.conditionOnlyExit;
import static com.example.tests.e2e.exitandclose.ExitTrail.coreOutbox;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealFactSeriesStartedYesterday;
import static com.example.tests.e2e.exitandclose.ExitTrail.dealRead;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeFillsEntryWithUnplacedStop;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeKeepsBills;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeKeepsCloseRecords;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeTriggersAttachedStop;
import static com.example.tests.e2e.exitandclose.ExitTrail.exchangeTriggersStopAfterLeg;
import static com.example.tests.e2e.exitandclose.ExitTrail.journalOf;
import static com.example.tests.e2e.exitandclose.ExitTrail.plain;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToExposure;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToPartialExit;
import static com.example.tests.e2e.exitandclose.ExitTrail.walkToSubmittedEntry;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Группа {@code E8} тропы выхода: торгово-критичные исходы — гэп через стоп,
 * проскок рыночного выхода, отказ постановки встроенного стопа и частичный
 * выход до стопа (.claude/tests/cases/e2e-exit-and-close.md §«E8 —
 * Торгово-критичные исходы: гэп, проскок, отказ стопа, финансирование»).
 *
 * <p><b>Клеток у класса четыре, и каждая — своя сделка на свежей паре
 * «тенант, счёт»:</b> {@code E8.1} — стоп сработал у площадки, а запись
 * закрытия несёт выход сквозь уровень; {@code E8.2} — шаг выхода уровня
 * сделки решён в прибыли, а закрытие исполнилось в убыток; {@code E8.6} —
 * reduce-only нога частичного выхода налилась выше уровня, остаток добрал
 * стоп; {@code E8.3} — встроенная защита налитого входа не встала. Пара
 * «тенант, счёт» у каждой своя, поэтому строка сделочного зерна суток
 * читается суммой по паре: сделка в ней одна. Клетки {@code E8.4} и
 * {@code E8.5} — детекция ручным фасадом — здесь не написаны (документ
 * кейсов, §«Кейсы, не прогоняемые сегодня»).
 *
 * <p><b>{@code E8.3} идёт последней:</b> она поднимает жёсткую ступень счёта,
 * и хотя следующая клетка взяла бы свежую пару, отпускание стенда с ней
 * последней не оставляет ни одной клетки на счёте под ступенью.
 *
 * <p><b>Пересчёт агрегатов статистики — расписанием, сокращённым на класс:</b>
 * фасада у него нет намеренно, а пересчёт есть проекция, и такт, бьющий во
 * время хода, ничего не портит (.claude/skills/test-code.md §«Уровень 3 —
 * сквозной набор»). Ряд сделочных фактов каждой пары начат прошлыми сутками —
 * строка общих предусловий тропы о клетках, читающих строку сделочного зерна.
 *
 * <p><b>Время площадки на добыче движений — сутки после начала окна</b>, тем
 * же доводом, что у групп {@code E4} и {@code E5}.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E8 — Торгово-критичные исходы: гэп, проскок, отказ стопа, финансирование")
class ExitAdverseOutcomePathTest {

    private static final String DEAL_CLOSED = "DEAL_CLOSED";

    private static final String ANOMALY_REPORTED = "ANOMALY_REPORTED";

    private static final String UNCOVERED = "EXCHANGE_LIVE_RISK_UNCOVERED";

    private static final String RECOMPUTE_EVERY_TWO_SECONDS = "*/2 * * * * *";

    private static final List<String> TERMINAL = List.of("CLOSED", "EMERGENCY_CLOSED");

    private static final Long OPENED = 1758240000000L;

    private static final Long CLOSED = 1758240005000L;

    private static final Long SOURCE_TIME = OPENED + Duration.ofDays(1).toMillis();

    private static final String LAST_BILL = "9003";

    private static final String FEE = "-0.2";

    private static final String NO_FUNDING = "0";

    private static final BigDecimal ENTRY_PRICE = new BigDecimal(Trail.ENTRY_PRICE);

    /** Последняя цена раскладки на проходе выхода {@code E8.2}: выше входа на 0,5 % — ниже порога подстройки защиты. */
    private static final String DECIDED_IN_PROFIT = "2010";

    /** Средняя цена исполнения закрытия {@code E8.2}: ниже входа. */
    private static final String EXECUTED_IN_LOSS = "1990";

    /** Доля частичного выхода {@code E8.6}: остаток при наливе тропы не ниже минимального размера. */
    private static final String PARTIAL_EXIT_PERCENTS = "50";

    /** Цена исполнения reduce-only ноги {@code E8.6}: выше входа, то есть выше уровня стопа. */
    private static final String LEG_PRICE = "2010";

    /** Исполнение стопа, добравшего остаток {@code E8.6}, — на столько хуже уровня. */
    private static final BigDecimal STOP_EXECUTION_GAP = new BigDecimal("5");

    private static final String CLOSED_DEALS = "closed_deals";

    private static final String LOSING_DEALS = "losing_deals";

    private static final String R_SUM = "r_sum";

    private static final String STOP_EXIT_DEALS = "stop_exit_deals";

    private static final String STOP_EXIT_SLIPPAGE_DEALS = "stop_exit_slippage_deals";

    private static final String STOP_EXIT_SLIPPAGE_R_SUM = "stop_exit_slippage_r_sum";

    private static final Offset<BigDecimal> R_PRECISION = Offset.offset(new BigDecimal("0.000001"));

    private static Trail trail;

    private static String deal;

    private static Object dealId;

    @BeforeAll
    static void openTrail() {
        trail = SharedStand.dealPath(ExitAdverseOutcomePathTest.class);
        dealFactSeriesStartedYesterday(trail);
        trail.statisticsRecomputes(RECOMPUTE_EVERY_TWO_SECONDS);
    }

    @AfterAll
    static void closeTrail() {
        if (nonNull(trail)) {
            SharedStand.release(ExitAdverseOutcomePathTest.class);
        }
    }

    /**
     * Стоп сработал у площадки, а исполнение ушло сквозь уровень: запись
     * закрытия эпизода несёт среднюю цену выхода на две дистанции стопа ниже
     * уровня, и ценовой убыток эпизода по модулю больше планового риска.
     * Транш покрыт одной встроенной защитой — отдельной у него нет, и
     * снимать после срабатывания нечего.
     */
    @Test
    @Order(1)
    @DisplayName("E8.1 — Гэп через стоп: убыток сверх заявленного риска едет числом площадки, и реакции на превышение нет")
    void e8_1_aGapThroughTheStopCarriesThePlatformLossAndNoReaction() {
        deal = walkToExposure(trail, attachedStopOnlyDefinition());
        dealId = dealIdOf(deal);
        trail.passUntil("предусловие E8.1: транш в сопровождении", () -> Objects.equals("MANAGING",
                trancheRow().get("status")));
        BigDecimal stop = stopLevel();
        BigDecimal planned = plannedRisk();
        BigDecimal exposure = entryExposure();
        BigDecimal exitPrice = stop.subtract(ENTRY_PRICE.subtract(stop).multiply(BigDecimal.TWO));
        BigDecimal pnl = exitPrice.subtract(ENTRY_PRICE).multiply(exposure);
        BigDecimal realized = pnl.add(new BigDecimal(FEE));
        assertThat(pnl.abs()).as("предусловие E8.1: ценовой убыток эпизода по модулю больше планового риска")
                .isGreaterThan(planned);
        assertThat(lossStreak()).as("предусловие E8.1: счётчик серии нулевой").isEqualTo(0);
        exchangeTriggersAttachedStop(trail, closeRecord(OPENED, CLOSED, plain(exitPrice), plain(pnl), FEE,
                NO_FUNDING, plain(realized)));
        exchangeKeepsBills(trail, SOURCE_TIME, LAST_BILL, bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT",
                plain(realized), FEE, CLOSED));
        Integer reportsBefore = reports().size();
        Integer holdsBefore = outbox(HOLD_RAISED).size();
        Integer reportedBefore = outbox(ANOMALY_REPORTED).size();
        Map<String, BigDecimal> grain = grain();
        trail.forgetTraces();

        passUntilTerminal("E8.1");
        trail.relayCore();

        Map<String, Object> row = dealRow();
        assertThat(dealRead(trail, deal).path("status").asString()).as("E8.1: сделка — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(trancheRow().get("close_reason")).as("E8.1: транш закрыт причиной «стоп»")
                .isEqualTo("STOP_LOSS");
        assertThat(row.get("close_reason"))
                .as("E8.1: причина сделки — «стоп» маршрутом «все транши терминальны при состоявшемся входе»")
                .isEqualTo("STOP_LOSS");
        assertThat((BigDecimal) row.get("result_profit"))
                .as("E8.1: итог — net эпизода из записи площадки, а не пересчёт от цены срабатывания")
                .isEqualByComparingTo(realized);
        assertThat(((BigDecimal) row.get("result_profit")).abs())
                .as("E8.1: итог по модулю больше планового риска — до него не обрезан").isGreaterThan(planned);
        assertThat((BigDecimal) episode().get("external_close_average_price"))
                .as("E8.1: зеркало эпизода несёт среднюю цену выхода записи").isEqualByComparingTo(exitPrice);
        assertThat(row.get("reconciliation_status")).as("E8.1: сверка — «сошлось»").isEqualTo("MATCHED");
        assertThat(lossStreak()).as("E8.1: счётчик серии вырос на единицу").isEqualTo(1);
        assertThat(accountRung()).as("E8.1: ступени нет — предел серии не достигнут").isEqualTo("ACTIVE");
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E8.1: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        BigDecimal slippage = stop.subtract(exitPrice).multiply(exposure);
        assertThat(content.path("result").decimalValue()).as("E8.1: событие закрытия несёт тот же итог")
                .isEqualByComparingTo(realized);
        assertThat(content.path("plannedRisk").decimalValue()).as("E8.1: тот же плановый риск")
                .isEqualByComparingTo(planned);
        assertThat(content.path("closeReason").asString()).as("E8.1: причину «стоп»").isEqualTo("STOP_LOSS");
        assertThat(content.path("stopExitSlippage").isNumber()).as("E8.1: проскок выхода по стопу — числом: "
                + content).isTrue();
        assertThat(content.path("stopExitSlippage").decimalValue())
                .as("E8.1: проскок = (уровень срабатывания − средняя цена выхода) × налив × стоимость контракта, "
                        + "положительный, нулём и плановым риском не обрезан")
                .isPositive()
                .isEqualByComparingTo(slippage);
        assertThat(reports()).as("E8.1: отчёта о происшествии не прибавилось — превышение заявленного риска "
                + "реакцией дома не является").hasSize(reportsBefore);
        assertThat(outbox(HOLD_RAISED)).as("E8.1: подъёма ступени не прибавилось").hasSize(holdsBefore);
        assertThat(outbox(ANOMALY_REPORTED)).as("E8.1: события отчёта не прибавилось").hasSize(reportedBefore);
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E8.1: закрытий позиции у стаба не прибавилось — "
                + "позицию закрыл стоп").isEmpty();
        assertThat(trail.exchange().requests(CANCEL_ALGOS)).as("E8.1: снятий защиты у стаба не прибавилось")
                .isEmpty();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("result").decimalValue()).as("E8.1: строка журнала о терминале с тем же итогом")
                .isEqualByComparingTo(realized);
        assertThat(journal(HOLD_RAISED)).as("E8.1: строк подъёма ступени у журнала не прибавилось")
                .hasSize(holdsBefore);
        assertThat(journal(ANOMALY_REPORTED)).as("E8.1: строк отчёта у журнала не прибавилось")
                .hasSize(reportedBefore);
        Map<String, Object> fact = awaitDealFact(terminal);
        assertThat((BigDecimal) fact.get("net_result")).as("E8.1: сделочный факт — убыточный").isNegative();
        assertThat((BigDecimal) fact.get("stop_exit_slippage")).as("E8.1: колонка проскока несёт число содержимого")
                .isEqualByComparingTo(content.path("stopExitSlippage").decimalValue());
        Map<String, BigDecimal> folded = awaitGrain(grain, "E8.1");
        assertThat(moved(folded, grain, LOSING_DEALS)).as("E8.1: сделка — в убыточных строки зерна")
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(moved(folded, grain, STOP_EXIT_DEALS)).as("E8.1: stopExitDeals вырос на единицу")
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_DEALS)).as("E8.1: stopExitSlippageDeals вырос на единицу")
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_R_SUM))
                .as("E8.1: stopExitSlippageRSum вырос на проскок, делённый на плановый риск")
                .isCloseTo(slippage.divide(planned, MathContext.DECIMAL64), R_PRECISION);
        assertThat(moved(folded, grain, R_SUM))
                .as("E8.1: слагаемое суммы R у сделки меньше минус единицы — в нём весь ценовой результат")
                .isLessThan(BigDecimal.ONE.negate());
    }

    /**
     * Выход решён шагом {@code EXIT} уровня сделки на раскладке, чья
     * последняя цена выше средней цены входа, а площадка исполнила закрытие
     * ниже входа: ценовой результат эпизода отрицателен. Движения окна несут
     * те же числа, что запись, — проскок исполнения расхождением двух
     * источников не является.
     */
    @Test
    @Order(2)
    @DisplayName("E8.2 — Проскок рыночного выхода, обративший прибыль решения в убыток, серию двигает и сверку не ломает")
    void e8_2_aMarketExitSlippageTurningProfitIntoLossMovesTheStreakAndKeepsTheReconciliation() {
        deal = walkToExposure(trail, conditionOnlyExit());
        dealId = dealIdOf(deal);
        BigDecimal exitPrice = new BigDecimal(EXECUTED_IN_LOSS);
        BigDecimal pnl = exitPrice.subtract(ENTRY_PRICE).multiply(entryExposure());
        BigDecimal realized = pnl.add(new BigDecimal(FEE));
        assertThat(lossStreak()).as("предусловие E8.2: счётчик серии нулевой").isEqualTo(0);
        exchangeKeepsCloseRecords(trail, closeRecord(OPENED, CLOSED, plain(exitPrice), plain(pnl), FEE, NO_FUNDING,
                plain(realized)));
        exchangeKeepsBills(trail, SOURCE_TIME, LAST_BILL, bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT",
                plain(realized), FEE, CLOSED));
        Integer reportsBefore = reports().size();
        Map<String, BigDecimal> grain = grain();
        trail.forgetTraces();

        trail.marketPhaseIs("BEAR_TREND", DECIDED_IN_PROFIT);
        passUntilTerminal("E8.2");
        trail.relayCore();

        Map<String, Object> row = dealRow();
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E8.2: к стабу площадки ушло одно закрытие позиции")
                .hasSize(1);
        assertThat(dealRead(trail, deal).path("status").asString()).as("E8.2: сделка — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(row.get("close_reason")).as("E8.2: причина — плановый выход").isEqualTo("STRATEGY_EXIT");
        assertThat((BigDecimal) row.get("result_profit"))
                .as("E8.2: итог — net записи площадки, а не оценка от цены решения").isEqualByComparingTo(realized)
                .isNegative();
        assertThat((BigDecimal) episode().get("external_close_average_price"))
                .as("E8.2: зеркало эпизода несёт среднюю цену выхода записи").isEqualByComparingTo(exitPrice);
        assertThat(row.get("reconciliation_status"))
                .as("E8.2: сверка — «сошлось»: оба источника несут одно исполнение").isEqualTo("MATCHED");
        assertThat(lossStreak()).as("E8.2: счётчик серии вырос на единицу — ценовой результат отрицателен")
                .isEqualTo(1);
        assertThat(accountRung()).as("E8.2: ступени нет").isEqualTo("ACTIVE");
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E8.2: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("closeReason").asString()).as("E8.2: событие несёт причину «плановый выход»")
                .isEqualTo("STRATEGY_EXIT");
        assertThat(isAbsent(content.path("stopExitSlippage")))
                .as("E8.2: проскок выхода по стопу пуст — стопа не было: " + content).isTrue();
        assertThat(reports()).as("E8.2: отчёта о происшествии не прибавилось").hasSize(reportsBefore);
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("result").decimalValue()).as("E8.2: строка журнала о терминале с тем же итогом")
                .isEqualByComparingTo(realized);
        Map<String, Object> fact = awaitDealFact(terminal);
        assertThat((BigDecimal) fact.get("net_result")).as("E8.2: сделочный факт — убыточный").isNegative();
        assertThat(fact.get("stop_exit_slippage")).as("E8.2: колонка проскока пуста, а не ноль").isNull();
        Map<String, BigDecimal> folded = awaitGrain(grain, "E8.2");
        assertThat(moved(folded, grain, LOSING_DEALS)).as("E8.2: сделка — в убыточных строки зерна")
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(moved(folded, grain, STOP_EXIT_DEALS)).as("E8.2: stopExitDeals сделкой не сдвинулся")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_DEALS)).as("E8.2: stopExitSlippageDeals не сдвинулся")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_R_SUM)).as("E8.2: stopExitSlippageRSum не сдвинулся")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Шаг частичного выхода в ведении транша отправил reduce-only ногу
     * половиной экспозиции, площадка налила её выше уровня стопа, а остаток
     * добрала сработавшая встроенная защита. Запись закрытия эпизода несёт
     * смешанную среднюю цену — ноги и стопа; стоп исполнился хуже своего
     * уровня, и по смешанной цене мера вышла бы благоприятным проскоком.
     */
    @Test
    @Order(3)
    @DisplayName("E8.6 — Частичный выход reduce-only ногой до стопа: сделка закрыта стопом, а проскок пуст и виден "
            + "разностью пары счётчиков")
    void e8_6_aReduceOnlyPartialExitBeforeTheStopLeavesTheSlippageEmptyAndSeenByThePairOfCounters() {
        deal = walkToPartialExit(trail, PARTIAL_EXIT_PERCENTS, LEG_PRICE);
        dealId = dealIdOf(deal);
        trail.passUntil("предусловие E8.6: строка исполнения частичного выхода завершена",
                () -> Objects.equals("COMPLETED", partialExitState()));
        trail.relayCore();
        Map<String, Object> entry = entryOrder();
        Map<String, Object> leg = single(reduceOnlyLegs(), "предусловие E8.6: reduce-only нога транша");
        BigDecimal entryFill = (BigDecimal) entry.get("accumulated_fill_size");
        BigDecimal legFill = (BigDecimal) leg.get("accumulated_fill_size");
        BigDecimal remainder = entryFill.subtract(legFill);
        assertThat(legFill).as("предусловие E8.6: reduce-only нога налита").isPositive();
        assertThat(remainder).as("предусловие E8.6: доля оставляет жизнеспособный остаток").isPositive();
        BigDecimal contractValue = (BigDecimal) entry.get("planned_contract_value");
        BigDecimal legPrice = new BigDecimal(LEG_PRICE);
        BigDecimal stopExecution = stopLevel().subtract(STOP_EXECUTION_GAP);
        assertThat(legPrice).as("предусловие E8.6: нога налита выше уровня стопа").isGreaterThan(stopLevel());
        BigDecimal pnl = legPrice.subtract(ENTRY_PRICE).multiply(legFill)
                .add(stopExecution.subtract(ENTRY_PRICE).multiply(remainder))
                .multiply(contractValue);
        BigDecimal mixedPrice = legPrice.multiply(legFill).add(stopExecution.multiply(remainder))
                .divide(entryFill, 8, RoundingMode.HALF_UP);
        BigDecimal realized = pnl.add(new BigDecimal(FEE));
        List<LoggedRequest> legs = reduceOnlyPlacements();
        exchangeTriggersStopAfterLeg(trail, closeRecord(OPENED, CLOSED, plain(mixedPrice), plain(pnl), FEE,
                NO_FUNDING, plain(realized)));
        exchangeKeepsBills(trail, SOURCE_TIME, LAST_BILL, bill(LAST_BILL, Trail.EXTERNAL_INSTRUMENT, "2", "1", "USDT",
                plain(realized), FEE, CLOSED));
        Map<String, BigDecimal> grain = grain();
        trail.forgetTraces();

        passUntilTerminal("E8.6");
        trail.relayCore();

        assertThat(legs).as("E8.6: к стабу площадки ушла одна reduce-only заявка транша").hasSize(1);
        assertThat(reduceOnlyPlacements()).as("E8.6: и после её налива — ни одной").isEmpty();
        assertThat(partialExitState()).as("E8.6: строка исполнения частичного выхода завершена")
                .isEqualTo("COMPLETED");
        assertThat(single(reduceOnlyLegs(), "E8.6: reduce-only нога").get("external_status"))
                .as("E8.6: reduce-only нога налита").isEqualTo("filled");
        assertThat(trancheRow().get("close_reason"))
                .as("E8.6: транш закрыт причиной «стоп» — сработавшая защита старше налитой reduce-only ноги")
                .isEqualTo("STOP_LOSS");
        assertThat(dealRead(trail, deal).path("status").asString()).as("E8.6: сделка — штатный терминал")
                .isEqualTo("CLOSED");
        assertThat(dealRow().get("close_reason")).as("E8.6: причина сделки — «стоп»").isEqualTo("STOP_LOSS");
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E8.6: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("closeReason").asString()).as("E8.6: событие несёт причину «стоп»")
                .isEqualTo("STOP_LOSS");
        assertThat(isAbsent(content.path("stopExitSlippage")))
                .as("E8.6: проскок пуст — не ноль и не число по смешанной цене: " + content).isTrue();
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E8.6: закрытий позиции не прибавилось — остаток "
                + "добрал стоп").isEmpty();
        assertThat(reports()).as("E8.6: отчёта о происшествии по сделке нет").isEmpty();
        assertThat(outbox(HOLD_RAISED)).as("E8.6: подъёма ступени нет").isEmpty();
        JsonNode recorded = Json.tree(String.valueOf(awaitJournal(terminal).get("content")));
        assertThat(recorded.path("closeReason").asString()).as("E8.6: строка журнала о терминале")
                .isEqualTo("STOP_LOSS");
        Map<String, Object> fact = awaitDealFact(terminal);
        assertThat(fact.get("close_reason")).as("E8.6: сделочный факт с причиной «стоп»").isEqualTo("STOP_LOSS");
        assertThat(fact.get("stop_exit_slippage")).as("E8.6: колонка проскока пуста").isNull();
        Map<String, BigDecimal> folded = awaitGrain(grain, "E8.6");
        assertThat(moved(folded, grain, STOP_EXIT_DEALS)).as("E8.6: stopExitDeals вырос на единицу")
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_DEALS))
                .as("E8.6: stopExitSlippageDeals не сдвинулся — неизмеренная сделка видна разностью пары")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_R_SUM)).as("E8.6: stopExitSlippageRSum не сдвинулся")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Ребро в ошибочное состояние и аварийный терминал сходятся проходами
     * сопровождения, и ошибочное состояние поверхностью может не
     * наблюдаться; прохождение его пинит пустая причина остановки — ребро
     * здесь решение обработчика, а не ступени.
     */
    @Test
    @Order(4)
    @DisplayName("E8.3 — Отказ постановки встроенного стопа на налитом входе: биржевая ступень 2 и снятие риска у "
            + "площадки")
    void e8_3_anUnplacedAttachedStopOnAFilledEntryRaisesExchangeRungTwoAndTearsTheRiskDown() {
        deal = walkToSubmittedEntry(trail);
        dealId = dealIdOf(deal);
        exchangeFillsEntryWithUnplacedStop(trail);
        Long facts = trail.rows(Party.STATISTICS, "deal_facts");
        Map<String, BigDecimal> grain = grain();
        trail.forgetTraces();

        trail.passUntil("E8.3: аварийный терминал", () -> Objects.equals("EMERGENCY_CLOSED",
                dealRead(trail, deal).path("status").asString()));
        trail.relayCore();

        Database core = trail.database(Party.TRADING_CORE);
        Map<String, Object> attached = core.query("select a.status, a.close_reason from attached_algo_orders a "
                + "join orders o on o.id = a.order_id where o.deal_id = ?", dealId).getFirst();
        assertThat(attached.get("status")).as("E8.3: встроенная защита — ERROR").isEqualTo("ERROR");
        assertThat(attached.get("close_reason")).as("E8.3: с причиной «отказ постановки»")
                .isEqualTo("PROTECTION_PLACEMENT_FAILED");
        assertThat(accountRung()).as("E8.3: счёт — в TRADE_BLOCKED").isEqualTo("TRADE_BLOCKED");
        assertThat(reports()).as("E8.3: заведён отчёт кодом непокрытого риска")
                .extracting(report -> report.get("code")).contains(UNCOVERED);
        Map<String, Object> row = core.query("select shutdown_reason, close_reason from deals where id = ?", dealId)
                .getFirst();
        assertThat(row.get("shutdown_reason")).as("E8.3: в ошибочное состояние без причины остановки — ребро "
                + "решением обработчика").isNull();
        assertThat(row.get("close_reason")).as("E8.3: причина закрытия — аварийная").isEqualTo("EMERGENCY_CLOSE");
        assertThat(coreOutbox(trail, DEAL_SHUTDOWN_INITIATED, deal)).as("E8.3: события остановки сделки нет")
                .isEmpty();
        Map<String, Object> terminal = single(coreOutbox(trail, DEAL_CLOSED, deal), "E8.3: терминал");
        JsonNode content = Json.tree(String.valueOf(terminal.get("payload")));
        assertThat(content.path("status").asString()).as("E8.3: событие закрытия — аварийный терминал")
                .isEqualTo("EMERGENCY_CLOSED");
        assertThat(content.path("closeReason").asString()).as("E8.3: с причиной «аварийное закрытие»")
                .isEqualTo("EMERGENCY_CLOSE");
        assertThat(isAbsent(content.path("stopExitSlippage")))
                .as("E8.3: проскок выхода по стопу пуст: " + content).isTrue();
        assertThat(trail.exchange().requests(CLOSE_POSITION)).as("E8.3: к стабу площадки ушло закрытие позиции")
                .isNotEmpty();
        assertThat(trail.exchange().requests(CANCEL_ALGOS)).as("E8.3: снятий условной заявки не прибавилось — "
                + "отказавшая защита на площадке не стояла").isEmpty();
        assertThat(trail.exchange().requests(CANCEL_ORDER)).as("E8.3: отмен заявки не прибавилось — живой ноги "
                + "у налитого входа нет").isEmpty();
        Map<String, Object> hold = carrying(outbox(HOLD_RAISED), UNCOVERED, "E8.3: подъём ступени");
        Map<String, Object> reported = carrying(outbox(ANOMALY_REPORTED), UNCOVERED, "E8.3: событие отчёта");
        JsonNode raised = Json.tree(String.valueOf(awaitJournal(hold).get("content")));
        assertThat(raised.path("scope").asString()).as("E8.3: строка подъёма ступени — радиус счёта")
                .isEqualTo("EXCHANGE_ACCOUNT");
        assertThat(raised.path("code").asString()).as("E8.3: с кодом непокрытого риска").isEqualTo(UNCOVERED);
        assertThat(Json.tree(String.valueOf(awaitJournal(reported).get("content"))).path("code").asString())
                .as("E8.3: строка отчёта с тем же кодом").isEqualTo(UNCOVERED);
        assertThat(Json.tree(String.valueOf(awaitJournal(terminal).get("content"))).path("status").asString())
                .as("E8.3: строка аварийного терминала").isEqualTo("EMERGENCY_CLOSED");
        assertThat(journalOf(trail, deal)).as("E8.3: строки остановки сделки у журнала нет")
                .noneSatisfy(record -> assertThat(record.get("event_type")).isEqualTo(DEAL_SHUTDOWN_INITIATED));
        Database statistics = trail.database(Party.STATISTICS);
        Trail.await("E8.3: факты отчёта и подъёма приняты — счётчики отчётов и ступеней выросли", () -> statistics
                .query("select event_id from incident_facts where event_id in (?, ?)",
                        String.valueOf(reported.get("event_id")), String.valueOf(hold.get("event_id"))).size() == 2);
        Map<String, Object> fact = awaitDealFact(terminal);
        assertThat(trail.rows(Party.STATISTICS, "deal_facts")).as("E8.3: сделочный факт один").isEqualTo(facts + 1);
        assertThat(fact.get("close_reason")).as("E8.3: сделочный факт с причиной аварийного закрытия")
                .isEqualTo("EMERGENCY_CLOSE");
        assertThat(fact.get("stop_exit_slippage")).as("E8.3: и пустой колонкой проскока").isNull();
        Map<String, BigDecimal> folded = awaitGrain(grain, "E8.3");
        assertThat(moved(folded, grain, STOP_EXIT_DEALS))
                .as("E8.3: stopExitDeals не сдвинулся — аварийный терминал из популяции закрытых стопом выпадает")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(moved(folded, grain, STOP_EXIT_SLIPPAGE_DEALS)).as("E8.3: stopExitSlippageDeals не сдвинулся")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- ходы и чтения

    /** Проходы сопровождения, пока сделка не дойдёт до терминала — штатного либо аварийного. */
    private static void passUntilTerminal(String label) {
        trail.passUntil(label + ": сделка дошла до терминала", () -> TERMINAL
                .contains(dealRead(trail, deal).path("status").asString()));
    }

    private static Object dealIdOf(String dealInternalId) {
        return trail.database(Party.TRADING_CORE).query("select id from deals where internal_id = ?", dealInternalId)
                .getFirst().get("id");
    }

    private static Map<String, Object> dealRow() {
        return trail.database(Party.TRADING_CORE).query("select close_reason, result_profit, reconciliation_status "
                + "from deals where id = ?", dealId).getFirst();
    }

    /** Транш сделки — у клеток группы он один. */
    private static Map<String, Object> trancheRow() {
        return trail.database(Party.TRADING_CORE).query("select status, close_reason from deal_tranches "
                + "where deal_id = ? order by id", dealId).getFirst();
    }

    /** Эпизод позиции сделки — у клеток группы он один. */
    private static Map<String, Object> episode() {
        return trail.database(Party.TRADING_CORE).query("select external_close_average_price from positions "
                + "where deal_id = ? order by id", dealId).getFirst();
    }

    /** Входная нога сделки: налив и стоимость контракта, под которую она сайзилась. */
    private static Map<String, Object> entryOrder() {
        return trail.database(Party.TRADING_CORE).query("select accumulated_fill_size, planned_contract_value "
                + "from orders where deal_id = ? and external_id = ?", dealId, Trail.EXTERNAL_ORDER).getFirst();
    }

    /** Вышедшая экспозиция входной ноги в единицах базового актива: налив × стоимость контракта. */
    private static BigDecimal entryExposure() {
        Map<String, Object> entry = entryOrder();
        return ((BigDecimal) entry.get("accumulated_fill_size"))
                .multiply((BigDecimal) entry.get("planned_contract_value"));
    }

    private static List<Map<String, Object>> reduceOnlyLegs() {
        return trail.database(Party.TRADING_CORE).query("select accumulated_fill_size, external_status from orders "
                + "where deal_id = ? and position_reducing_only", dealId);
    }

    /** Объявленный уровень остановки убытка встроенной защиты входной ноги. */
    private static BigDecimal stopLevel() {
        return (BigDecimal) trail.database(Party.TRADING_CORE).query("select a.stop_loss_trigger_price "
                + "from attached_algo_orders a join orders o on o.id = a.order_id where o.deal_id = ?", dealId)
                .getFirst().get("stop_loss_trigger_price");
    }

    private static BigDecimal plannedRisk() {
        return (BigDecimal) trail.database(Party.TRADING_CORE).query("select planned_risk_amount from deals "
                + "where id = ?", dealId).getFirst().get("planned_risk_amount");
    }

    /** Статус последней строки исполнения действия частичного выхода; пусто — строки нет. */
    private static Object partialExitState() {
        List<Map<String, Object>> rows = trail.database(Party.TRADING_CORE).query("select s.status "
                + "from deal_strategy_action_states s join strategy_actions a on a.id = s.strategy_action_id "
                + "where s.deal_id = ? and a.key = ? order by s.id desc", dealId, PARTIAL_EXIT_KEY);
        return rows.isEmpty() ? null : rows.getFirst().get("status");
    }

    /** Постановки reduce-only заявок у стаба площадки после последнего забывания следов. */
    private static List<LoggedRequest> reduceOnlyPlacements() {
        return trail.exchange().requests(Trail.EXCHANGE_ORDER).stream()
                .filter(request -> Objects.equals("POST", request.getMethod().getName()))
                .filter(request -> isTrue(Json.tree(request.getBodyAsString()).path("reduceOnly").asBoolean()))
                .toList();
    }

    private static Object lossStreak() {
        return trail.database(Party.TRADING_CORE).query("select consecutive_loss_count from exchange_accounts"
                + " where internal_id = ?", trail.account()).getFirst().get("consecutive_loss_count");
    }

    private static Object accountRung() {
        return trail.database(Party.TRADING_CORE).query("select safety_rung from exchange_accounts"
                + " where internal_id = ?", trail.account()).getFirst().get("safety_rung");
    }

    private static List<Map<String, Object>> reports() {
        return trail.database(Party.TRADING_CORE).query("select code from anomaly_reports where "
                + Trail.BY_ACCOUNT, trail.account());
    }

    private static List<Map<String, Object>> outbox(String eventType) {
        return trail.database(Party.TRADING_CORE).query("select event_id, payload::text as payload "
                + "from outbox_events where " + Trail.BY_TENANT + " and event_type = ? order by id",
                trail.tenant(), eventType);
    }

    private static List<Map<String, Object>> journal(String eventType) {
        return trail.database(Party.AUDIT).query("select event_id from audit_records where "
                + Trail.BY_TENANT + " and event_type = ?", trail.tenant(), eventType);
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows, String label) {
        assertThat(rows).as(label + " — одна строка").hasSize(1);
        return rows.getFirst();
    }

    /** Строка outbox, чьё содержимое несёт названный код. */
    private static Map<String, Object> carrying(List<Map<String, Object>> rows, String code, String label) {
        return rows.stream()
                .filter(row -> row.get("payload").toString().contains(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(label + " с кодом " + code + " нет: " + rows));
    }

    private static Map<String, Object> awaitJournal(Map<String, Object> event) {
        Database audit = trail.database(Party.AUDIT);
        String eventId = String.valueOf(event.get("event_id"));
        Trail.await("строка журнала события " + eventId,
                () -> audit.query("select id from audit_records where event_id = ?", eventId).size() == 1);
        return audit.query("select event_type, content::text as content from audit_records where event_id = ?",
                eventId).getFirst();
    }

    private static Map<String, Object> awaitDealFact(Map<String, Object> event) {
        Database statistics = trail.database(Party.STATISTICS);
        String eventId = String.valueOf(event.get("event_id"));
        Trail.await("сделочный факт " + eventId, () -> statistics
                .query("select event_id from deal_facts where event_id = ?", eventId).size() == 1);
        return statistics.query("select net_result, close_reason, stop_exit_slippage from deal_facts "
                + "where event_id = ?", eventId).getFirst();
    }

    /**
     * Строка сделочного зерна суток пары ходов — суммой по паре: сделка у
     * пары одна, а ключ зерна несёт ещё определение и валюту, которых у
     * аварийного терминала может не быть. Строки нет — нули.
     */
    private static Map<String, BigDecimal> grain() {
        Map<String, Object> row = trail.database(Party.STATISTICS).query("select "
                + "coalesce(sum(closed_deals), 0) as " + CLOSED_DEALS + ", "
                + "coalesce(sum(losing_deals), 0) as " + LOSING_DEALS + ", "
                + "coalesce(sum(r_sum), 0) as " + R_SUM + ", "
                + "coalesce(sum(stop_exit_deals), 0) as " + STOP_EXIT_DEALS + ", "
                + "coalesce(sum(stop_exit_slippage_deals), 0) as " + STOP_EXIT_SLIPPAGE_DEALS + ", "
                + "coalesce(sum(stop_exit_slippage_r_sum), 0) as " + STOP_EXIT_SLIPPAGE_R_SUM + " "
                + "from deal_aggregates where tenant_id = ? and exchange_account_internal_id = ? and bucket_date = ?",
                trail.tenant(), trail.account(), LocalDate.now(ZoneOffset.UTC)).getFirst();
        Map<String, BigDecimal> grain = new HashMap<>();
        row.forEach((column, value) -> grain.put(column, new BigDecimal(String.valueOf(value))));
        return grain;
    }

    /** Ждёт, пока пересчёт сложит сделку в строку зерна, и отдаёт строку. */
    private static Map<String, BigDecimal> awaitGrain(Map<String, BigDecimal> before, String label) {
        Trail.await(label + ": пересчёт сложил сделку в строку сделочного зерна", () -> grain().get(CLOSED_DEALS)
                .compareTo(before.get(CLOSED_DEALS).add(BigDecimal.ONE)) == 0);
        return grain();
    }

    private static BigDecimal moved(Map<String, BigDecimal> after, Map<String, BigDecimal> before, String column) {
        return after.get(column).subtract(before.get(column));
    }

    /** Число едет отсутствующим — не нулём и не строкой. */
    private static Boolean isAbsent(JsonNode value) {
        return value.isMissingNode() || value.isNull();
    }
}
