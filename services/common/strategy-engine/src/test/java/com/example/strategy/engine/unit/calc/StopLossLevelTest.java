package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.INDICATOR_KEY;
import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.atr;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.levels;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopSettings;
import static com.example.strategy.engine.unit.calc.CalcFixture.structures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Уровень остановки убытка: четыре способа расчёта — группа `U4`
 * документа `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/stop-distance.json, docs/components/PriceCalculator.md
 * §Формулы).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; якорь {@code 3000}
 * (плановая цена входной заявки, живого эпизода нет); шаг цены
 * {@code 0.1}; ставка комиссии {@code 0.0005}; действие — условная заявка
 * `STOP_LOSS`.
 */
class StopLossLevelTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Доля от цены входа — линейное смещение от якоря. */
    @Test
    @DisplayName("U4.1 — способ «доля от цены входа», доля 1: стоп 2970; тейк и трейлинг пусты")
    void u4_1_theEntryPricePercentGivesALinearOffset() {
        CalculatedPrice price = calculator.calculate(base(stopAction("1")).build());

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2970");
        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.STOP_LOSS_TRIGGER_PRICE);
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /** Доля не объявлена — смещению взяться неоткуда. */
    @Test
    @DisplayName("U4.2 — доля не объявлена: отказ MISSING_DISTANCE")
    void u4_2_anAbsentDistanceRefuses() {
        assertRefuses(base(stopAction(null)).build(), "MISSING_DISTANCE");
    }

    /** Доля от волатильности берётся от значения индикатора, а не от цены. */
    @Test
    @DisplayName("U4.3 — способ «доля от волатильности», ATR 50, доля 20: стоп 2990 — доля от ATR")
    void u4_3_theAtrPercentIsTakenFromTheIndicatorValue() {
        CalculationContext context = atrContext("20")
                .indicatorValues(atr("50"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("600 означало бы долю от цены, а не от значения волатильности")
                .isEqualByComparingTo("2990");
    }

    /** Значения по ключу нет — считать не из чего. */
    @Test
    @DisplayName("U4.4 — значения по ключу нет: отказ MISSING_ATR")
    void u4_4_anAbsentIndicatorValueRefuses() {
        assertRefuses(atrContext("20").build(), "MISSING_ATR");
    }

    /** По ключу лежит другой индикатор — отказ, а не чтение чужого поля. */
    @Test
    @DisplayName("U4.5 — по ключу лежит другой индикатор: отказ MISSING_ATR, а не чтение чужого поля")
    void u4_5_aForeignIndicatorRefusesInsteadOfBeingRead() {
        EmaValue ema = new EmaValue();
        ema.setId(52L);
        ema.setEma(new BigDecimal("50"));
        Map<String, IndicatorValue> values = Map.of(INDICATOR_KEY, ema);

        assertRefuses(atrContext("20").indicatorValues(values).build(), "MISSING_ATR");
    }

    /** Значение есть, само число пусто — тот же отказ. */
    @Test
    @DisplayName("U4.6 — значение волатильности есть, число пусто: отказ MISSING_ATR")
    void u4_6_anIndicatorValueWithoutANumberRefuses() {
        assertRefuses(atrContext("20").indicatorValues(atr(null)).build(), "MISSING_ATR");
    }

    /** Пустой ключ не резолвится ни во что. */
    @Test
    @DisplayName("U4.7 — ключ индикатора не объявлен: отказ MISSING_ATR")
    void u4_7_anAbsentIndicatorKeyResolvesToNothing() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        StopLossSettings settings = stopSettings(StopLossCalculationType.ATR_PERCENT, "20");
        settings.setIndicatorKey(null);
        action.setStopLossSettings(settings);

        assertRefuses(base(action).indicatorValues(atr("50")).build(), "MISSING_ATR");
    }

    /** Буфер от структуры: база смещения — свинг, а не якорь. */
    @Test
    @DisplayName("U4.8 — способ «буфер от структуры», свинг 2900, доля 1: стоп 2871 — база смещения свинг")
    void u4_8_theStructureBufferIsOffsetFromTheSwing() {
        CalculationContext context = structureContext("1")
                .marketStructures(structures(levels(MarketPriceLevel.Type.SWING_LOW, "2900")))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2871");
    }

    /**
     * Фолбэк на границу диапазона есть у уровня остановки убытка — и
     * только у него: у базы размещения его нет (`U1.16`).
     */
    @Test
    @DisplayName("U4.9 — свинга нет, низ диапазона 2890: стоп 2861.1 — фолбэк на границу диапазона")
    void u4_9_theStopLevelFallsBackToTheRangeBoundary() {
        CalculationContext context = structureContext("1")
                .marketStructures(structures(levels(MarketPriceLevel.Type.RANGE_LOW, "2890")))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2861.1");
    }

    /** У короткой стороны берётся верхняя пара уровней. */
    @Test
    @DisplayName("U4.10 — направление SHORT, максимум свинга 3100: стоп 3131 — верхняя пара уровней")
    void u4_10_theShortSideTakesTheUpperLevelPair() {
        CalculationContext context = structureContext("1")
                .strategyDirection(StrategyTradeDirection.SHORT)
                .marketStructures(structures(levels(MarketPriceLevel.Type.SWING_HIGH, "3100")))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("3131");
    }

    /** Структуры по ключу нет — отказ. */
    @Test
    @DisplayName("U4.11 — структуры по ключу нет: отказ MISSING_STRUCTURE")
    void u4_11_anAbsentStructureRefuses() {
        assertRefuses(structureContext("1").build(), "MISSING_STRUCTURE");
    }

    /** Уровень найден, цены у него нет — тот же отказ. */
    @Test
    @DisplayName("U4.12 — уровень найден, цены у него нет: отказ MISSING_STRUCTURE")
    void u4_12_aLevelWithoutAPriceRefuses() {
        CalculationContext context = structureContext("1")
                .marketStructures(structures(levels(MarketPriceLevel.Type.SWING_LOW, null)))
                .build();

        assertRefuses(context, "MISSING_STRUCTURE");
    }

    /** Настроек стопа у действия нет вовсе — считать нечего. */
    @Test
    @DisplayName("U4.13 — настроек стопа у действия нет: отказ MISSING_STOP_LOSS_SETTINGS")
    void u4_13_absentStopSettingsRefuse() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);

        assertRefuses(base(action).build(), "MISSING_STOP_LOSS_SETTINGS");
    }

    /** Доля закрытия адресует размер, а не цену: частичный стоп даёт тот же уровень. */
    @Test
    @DisplayName("U4.14 — условие PARTIAL_STOP_LOSS: стоп тот же 2970 — доля закрытия на цену не влияет")
    void u4_14_thePartialStopLossGivesTheSameLevel() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.PARTIAL_STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1"));
        action.setCloseFractionPercents(new BigDecimal("40"));

        assertThat(calculator.calculate(base(action).build()).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2970");
    }

    /** База триггера переносится в результат без изменения: калькулятор её не резолвит. */
    @Test
    @DisplayName("U4.15 — база триггера MARK: переносится в результат без изменения")
    void u4_15_theTriggerPriceTypeIsCarriedUnchanged() {
        CalculatedPrice price = calculator.calculate(base(stopAction("1")).build());

        assertThat(price.getStopLossPrice().getTriggerPriceType()).isEqualTo(AlgoOrder.TriggerPriceType.MARK);
    }

    /** OCO заполняет обе половины; трейлинг при этом пуст. */
    @Test
    @DisplayName("U4.16 — условие OCO_FULL: стоп 2970 и тейк 3060 заполнены обе; трейлинг пуст")
    void u4_16_theOcoFillsBothHalves() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.OCO_FULL);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.ENTRY_PRICE_PERCENT, "1"));
        action.setTriggerProfitPercents(new BigDecimal("2"));

        CalculatedPrice price = calculator.calculate(base(action).build());

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("2970");
        assertThat(price.getTakeProfitPrice().getTriggerPrice()).isEqualByComparingTo("3060");
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /**
     * У OCO стоп считается первым, и тейк не считается вовсе. Наблюдаемо
     * это тем, что доля прибыли тоже не объявлена: посчитай калькулятор
     * тейк раньше, отказ пришёл бы кодом {@code MISSING_DISTANCE}.
     */
    @Test
    @DisplayName("U4.17 — OCO_FULL без настроек стопа: отказ MISSING_STOP_LOSS_SETTINGS — тейк не считается")
    void u4_17_theOcoRefusesOnTheStopBeforeComputingTheTakeProfit() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.OCO_FULL);

        assertRefuses(base(action).build(), "MISSING_STOP_LOSS_SETTINGS");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private CalculationContext.CalculationContextBuilder atrContext(String distancePercents) {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.ATR_PERCENT, distancePercents));
        return base(action);
    }

    private CalculationContext.CalculationContextBuilder structureContext(String distancePercents) {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(
                stopSettings(StopLossCalculationType.MARKET_STRUCTURE_BUFFER_PERCENT, distancePercents));
        return base(action);
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
