package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.attachedStop;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.decimal;
import static com.example.strategy.engine.unit.calc.CalcFixture.detail;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.marketPlacement;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Размер входа: закрытая форма под поактный потолок — группа `U8`
 * документа `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/order-sizing.json; docs/components/SizeCalculator.md §«Вход:
 * сайзинг под поактный потолок»).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; правила инструмента —
 * стоимость контракта {@code 1}, шаг лота {@code 0.1}, минимальный размер
 * {@code 0.1}, шаг цены {@code 0.1}, ставка комиссии {@code 0.0005}; база
 * риска {@code 10000}; поактный потолок риска {@code 1}; действие — вход
 * лимитом с размещением {@code {рыночная, последняя, ниже, 1}} и
 * встроенной защитой долей {@code 1}, доля аллокации {@code 100}. Отсюда
 * цена входа {@code 2970}, уровень остановки убытка {@code 2940.3},
 * убыток на контракте {@code 32.65515}, бюджет риска {@code 100}.
 *
 * <p><b>Цена базовой сборки считается НАСТОЯЩИМ калькулятором цены</b> —
 * иначе сборка была бы объявлена, а не собрана. Клетки, чьё отклонение
 * называет саму цену или уровень остановки (`U8.4`-`U8.7`, `U8.10`,
 * `U8.16`), подают цену собранной: их состояние тропой расчёта цены
 * недостижимо, и в этом и состои́т их предмет.
 */
class EntrySizeTest {

    private final PriceCalculator priceCalculator = new PriceCalculator();

    private final SizeCalculator calculator = new SizeCalculator();

    /** Связывает потолок риска: меньший кандидат — по риску. */
    @Test
    @DisplayName("U8.1 — базовая сборка: размер 3.0 по потолку риска, нотинал 8910; доля и исход пусты")
    void u8_1_theRiskCeilingBindsTheEntrySize() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules(), "10000", "1");
        CalculatedPrice price = priceCalculator.calculate(context);

        assertThat(price.getRoundedPrice()).as("цена входа базовой сборки").isEqualByComparingTo("2970");
        assertThat(price.getStopLossPrice().getTriggerPrice()).as("уровень остановки базовой сборки")
                .isEqualByComparingTo("2940.3");

        CalculatedSize size = calculator.calculate(context, price);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("3.0");
        assertThat(size.getNotionalUsdt()).isEqualByComparingTo("8910");
        assertThat(size.getSizeMode()).isEqualTo(SizeMode.OPEN_OR_INCREASE);
        assertThat(size.getDescription()).as("пояснение называет потолок риска").contains("risk ceiling");
        assertThat(size.getCloseFraction()).as("доля закрытия пуста").isNull();
        assertThat(size.getExitOutcome()).as("исход выхода пуст").isNull();
    }

    /**
     * Связывает доля аллокации: фактический риск ноги выходит НИЖЕ
     * потолка, и это штатное следствие — потолок есть предел, а не цель.
     */
    @Test
    @DisplayName("U8.2 — доля аллокации 10: размер 0.3 по аллокации; фактический риск 9.79… ниже бюджета 100")
    void u8_2_theAllocationShareBindsAndLeavesTheRiskBelowTheCeiling() {
        StrategyOrderAction action = attachedEntry("10");
        CalculationContext context = entryContext(action, rules(), "10000", "1");

        CalculatedSize size = calculator.calculate(context, priceCalculator.calculate(context));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("0.3");
        assertThat(size.getDescription()).as("пояснение называет долю аллокации").contains("allocation share");
        assertThat(size.getSizeContracts().multiply(new BigDecimal("32.65515")))
                .as("фактический риск ноги ниже бюджета 100").isLessThan(new BigDecimal("100"));
    }

    /**
     * Пол минимального размера бьёт потолок риска, и это объявленное
     * следствие: округление вниз даёт ноль, пол поднимает до минимума, а
     * фактический риск выходит выше бюджета.
     */
    @Test
    @DisplayName("U8.3 — потолок риска 0.01: размер 0.1 — пол минимального; фактический риск 3.26… выше бюджета 1")
    void u8_3_theMinimumSizeFloorOutranksTheRiskCeiling() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules(), "10000", "0.01");

        CalculatedSize size = calculator.calculate(context, priceCalculator.calculate(context));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("0.1");
        assertThat(size.getSizeContracts().multiply(new BigDecimal("32.65515")))
                .as("фактический риск ноги выше бюджета 1").isGreaterThan(BigDecimal.ONE);
    }

    /** Уровень на прибыльной стороне: сайзить нечего ни по риску, ни по аллокации. */
    @Test
    @DisplayName("U8.4 — уровень 3000 выше якоря: отказ STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING")
    void u8_4_aStopOnTheProfitSideRefuses() {
        assertRefuses(entryContext(attachedEntry("100"), rules(), "10000", "1"),
                suppliedPrice("2970", "3000"), "STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING");
    }

    /** Уровень равен якорю при нулевой ставке — тот же отказ, а не деление на ноль. */
    @Test
    @DisplayName("U8.5 — уровень равен якорю 2970, ставка 0: тот же отказ, деления на ноль нет")
    void u8_5_aStopExactlyAtTheAnchorRefusesInsteadOfDividingByZero() {
        CalculationContext context = entryContext(attachedEntry("100"),
                rules("0.1", "0", "1", "0.1", "0.1"), "10000", "1");

        assertRefuses(context, suppliedPrice("2970", "2970"), "STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING");
    }

    /**
     * Знак мерится СУММОЙ с комиссией, а не чистой дистанцией: комиссия
     * обеих ног перекрывает разницу, и размер считается. Отсечение такого
     * объявления — предмет преконтроля, не сайзинга.
     */
    @Test
    @DisplayName("U8.6 — уровень 2971 при ставке 0.01: размер 1.7 — убыток на контракте 58.41 положителен")
    void u8_6_theSignIsMeasuredWithTheFeeNotByTheBareDistance() {
        CalculationContext context = entryContext(attachedEntry("100"),
                rules("0.1", "0.01", "1", "0.1", "0.1"), "10000", "1");

        CalculatedSize size = calculator.calculate(context, suppliedPrice("2970", "2971"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("1.7");
    }

    /** У короткой стороны убыточная сторона — выше якоря. */
    @Test
    @DisplayName("U8.7 — направление SHORT, якорь 2970, уровень 3000: размер 3.0 — считается")
    void u8_7_theShortSideLossSideIsAboveTheAnchor() {
        CalculationContext context = entryContextBuilder(attachedEntry("100"), rules(), "10000", "1")
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        CalculatedSize size = calculator.calculate(context, suppliedPrice("2970", "3000"));

        assertThat(size.getSizeContracts()).isEqualByComparingTo("3.0");
    }

    /** Ставка не резолвится — обе ноги издержки неизвестны. */
    @Test
    @DisplayName("U8.8 — ставка комиссии не резолвится: отказ FEE_RATE_UNAVAILABLE")
    void u8_8_anUnresolvedFeeRateRefuses() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules("0.1", null, "1", "0.1", "0.1"),
                "10000", "1");

        assertRefuses(context, priceCalculator.calculate(context), "FEE_RATE_UNAVAILABLE");
    }

    /** Вход без объявленного уровня остановки долей аллокации НЕ сайзится. */
    @Test
    @DisplayName("U8.9 — встроенной защиты нет: отказ MISSING_STOP_PRICE_FOR_SIZING, аллокацией вход не сайзится")
    void u8_9_anEntryWithoutAStopLevelIsNotSizedByAllocationAlone() {
        StrategyOrderAction action = entryAction(marketPlacement());
        CalculationContext context = entryContext(action, rules(), "10000", "1");

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_STOP_PRICE_FOR_SIZING");
    }

    /**
     * Цена входа пуста. <b>Охрана без входа через оркестратор:</b> обе
     * тропы цены заявки её заполняют, и пустой она приходит только прямым
     * вызовом расчёта размера.
     */
    @Test
    @DisplayName("U8.10 — цена входа в результате расчёта цены пуста: отказ MISSING_ENTRY_PRICE")
    void u8_10_anEmptyEntryPriceRefuses() {
        assertRefuses(entryContext(attachedEntry("100"), rules(), "10000", "1"),
                suppliedPrice(null, "2940.3"), "MISSING_ENTRY_PRICE");
    }

    /** Базы риска нет — у поактного потолка нет делителя. */
    @Test
    @DisplayName("U8.11 — базы риска в контексте нет: отказ MISSING_RISK_BASE")
    void u8_11_anAbsentRiskBaseRefuses() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules(), null, "1");

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_RISK_BASE");
    }

    /** Нулевая база риска — тот же отказ, а не деление на ноль. */
    @Test
    @DisplayName("U8.12 — база риска 0: отказ MISSING_RISK_BASE, а не деление на ноль")
    void u8_12_aZeroRiskBaseRefusesInsteadOfDividingByZero() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules(), "0", "1");

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_RISK_BASE");
    }

    /** Детали стратегии нет — потолок объявить некому. */
    @Test
    @DisplayName("U8.13 — детали стратегии в контексте нет: отказ MISSING_RISK_LIMIT")
    void u8_13_anAbsentStrategyDetailRefuses() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContextBuilder(action, rules(), "10000", "1")
                .strategyDetail(null)
                .build();

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_RISK_LIMIT");
    }

    /** Потолок в детали не объявлен — тот же отказ. */
    @Test
    @DisplayName("U8.14 — поактный потолок риска не объявлен: отказ MISSING_RISK_LIMIT")
    void u8_14_anUndeclaredRiskCeilingRefuses() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules(), "10000", null);

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_RISK_LIMIT");
    }

    /** Доля аллокации не объявлена — второго кандидата не существует. */
    @Test
    @DisplayName("U8.15 — доля аллокации не объявлена: отказ MISSING_ALLOCATION")
    void u8_15_anUndeclaredAllocationRefuses() {
        StrategyOrderAction action = attachedEntry(null);
        CalculationContext context = entryContext(action, rules(), "10000", "1");

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_ALLOCATION");
    }

    /** Правил инструмента нет вовсе — набор полей сайзинга пуст. */
    @Test
    @DisplayName("U8.16 — правил инструмента нет вовсе: отказ MISSING_SIZE_SPECS")
    void u8_16_absentInstrumentRulesRefuse() {
        CalculationContext context = entryContextBuilder(attachedEntry("100"), rules(), "10000", "1")
                .instrumentExternalRules(null)
                .build();

        assertRefuses(context, suppliedPrice("2970", "2940.3"), "MISSING_SIZE_SPECS");
    }

    /** Нулевая стоимость контракта — набор полей сайзинга неполон. */
    @Test
    @DisplayName("U8.17 — стоимость контракта 0: отказ MISSING_SIZE_SPECS")
    void u8_17_aZeroContractValueRefuses() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules("0.1", "0.0005", "0", "0.1", "0.1"),
                "10000", "1");

        assertRefuses(context, priceCalculator.calculate(context), "MISSING_SIZE_SPECS");
    }

    /** Оба кандидата и нотинал масштабированы стоимостью контракта; размер остаётся в контрактах. */
    @Test
    @DisplayName("U8.18 — стоимость контракта 0.01: размер 306.2 в контрактах, нотинал 9094.14")
    void u8_18_bothCandidatesAndTheNotionalScaleWithTheContractValue() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action,
                rules("0.1", "0.0005", "0.01", "0.1", "0.1"), "10000", "1");

        CalculatedSize size = calculator.calculate(context, priceCalculator.calculate(context));

        assertThat(size.getSizeContracts()).as("размер в контрактах, а не в валюте")
                .isEqualByComparingTo("306.2");
        assertThat(size.getNotionalUsdt()).isEqualByComparingTo("9094.14");
    }

    /** Округление вниз идёт по шагу лота инструмента. */
    @Test
    @DisplayName("U8.19 — шаг лота 1: размер округлён вниз до целого 3")
    void u8_19_theSizeIsFlooredToTheInstrumentLotSize() {
        StrategyOrderAction action = attachedEntry("100");
        CalculationContext context = entryContext(action, rules("0.1", "0.0005", "1", "1", "0.1"),
                "10000", "1");

        assertThat(calculator.calculate(context, priceCalculator.calculate(context)).getSizeContracts())
                .isEqualByComparingTo("3");
    }

    // --- базовая сборка и отклонения от неё --------------------------------

    private StrategyOrderAction attachedEntry(String allocationPercents) {
        StrategyOrderAction action = entryAction(marketPlacement());
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setAttachedProtection(attachedStop("1"));
        action.setAllocationPercents(decimal(allocationPercents));
        return action;
    }

    private CalculationContext entryContext(StrategyOrderAction action, InstrumentExternalRules rules,
                                            String riskBase, String riskPerActionPercent) {
        return entryContextBuilder(action, rules, riskBase, riskPerActionPercent).build();
    }

    private CalculationContext.CalculationContextBuilder entryContextBuilder(StrategyOrderAction action,
                                                                             InstrumentExternalRules rules,
                                                                             String riskBase,
                                                                             String riskPerActionPercent) {
        StrategyDetail strategyDetail = detail(riskPerActionPercent, List.of((StrategyAction) action));
        return base(action)
                .instrumentExternalRules(rules)
                .riskBase(decimal(riskBase))
                .strategyDetail(strategyDetail);
    }

    /** Цена, поданная собранной: её состояние тропой расчёта цены недостижимо. */
    private CalculatedPrice suppliedPrice(String entryPrice, String stopPrice) {
        return CalculatedPrice.builder()
                .roundedPrice(decimal(entryPrice))
                .stopLossPrice(ResolvedStopLossPrice.builder()
                        .triggerPrice(decimal(stopPrice))
                        .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                        .build())
                .build();
    }

    private void assertRefuses(CalculationContext context, CalculatedPrice price, String code) {
        assertThatThrownBy(() -> calculator.calculate(context, price))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
