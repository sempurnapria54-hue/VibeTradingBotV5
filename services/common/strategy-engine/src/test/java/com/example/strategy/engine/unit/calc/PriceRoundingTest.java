package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryOrder;
import static com.example.strategy.engine.unit.calc.CalcFixture.placement;
import static com.example.strategy.engine.unit.calc.CalcFixture.rulesWithTick;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Округление: сторона выбирается дважды и по разным правилам — группа
 * `U7` документа `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/PriceCalculator.md §Округление).
 *
 * <p><b>Сторона у цены ЗАЯВКИ мерится стороной сделки, а у защитного
 * уровня — стороной от якоря:</b> единого направления не хватает, потому
 * что перенос в безубыток кладёт уровень по другую сторону якоря.
 *
 * <p><b>Ожидания стоя́т на якорях вне сетки шага:</b> {@code 2777.77} и
 * {@code 2999.5} выбраны затем, чтобы сдвиг был наблюдаем — на якоре,
 * кратном шагу, обе стороны дают одно и то же число.
 *
 * <p><b>Сменяет часть прежней пробы предмета</b>
 * ({@code com.example.strategy.engine.calc.PriceLevelTest}): её
 * клетка-преемница здесь — `U7.1` (защитный уровень округляется прочь от
 * якоря, на том же якоре {@code 2777.77}).
 */
class PriceRoundingTest {

    /** Якорь вне сетки шага: округление на нём наблюдаемо. */
    private static final String OFF_GRID_ANCHOR = "2777.77";

    private final PriceCalculator calculator = new PriceCalculator();

    /** Защитный уровень ниже якоря округляется вниз: стоп не ужимается. */
    @Test
    @DisplayName("U7.1 — уровень ниже якоря: 2749.9 — сырое 2749.9923 округлено вниз, прочь от якоря")
    void u7_1_aLevelBelowTheAnchorIsRoundedDown() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(entryOrder(OFF_GRID_ANCHOR, null))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2749.9");
    }

    /** Защитный уровень выше якоря округляется вверх: безубыток не опускается. */
    @Test
    @DisplayName("U7.2 — уровень выше якоря (безубыток длинной стороны): округление вверх до 3003.1")
    void u7_2_aLevelAboveTheAnchorIsRoundedUp() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.BREAKEVEN, null));

        assertThat(calculator.calculate(base(action).build()).getStopLossPrice().getTriggerPrice())
                .as("сырое 3003.0015… округлено вверх, а не вниз до 3003.0")
                .isEqualByComparingTo("3003.1");
    }

    /** Сторона мерится строгим сравнением: совпавший с якорем уровень идёт вниз. */
    @Test
    @DisplayName("U7.3 — уровень совпал с якорем: округление вниз — сторона мерится строгим сравнением")
    void u7_3_aLevelEqualToTheAnchorIsRoundedDown() {
        CalculationContext context = base(stopAction("0"))
                .entryOrder(entryOrder(OFF_GRID_ANCHOR, null))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("2777.8 означало бы нестрогое сравнение")
                .isEqualByComparingTo("2777.7");
    }

    /** Цена заявки длинной стороны округляется вниз — по стороне сделки. */
    @Test
    @DisplayName("U7.4 — цена входа, направление LONG: округление вниз до 2969.5")
    void u7_4_theLongSideOrderPriceIsRoundedDown() {
        assertThat(calculator.calculate(orderPriceContext(StrategyTradeDirection.LONG)).getRoundedPrice())
                .isEqualByComparingTo("2969.5");
    }

    /** Цена заявки короткой стороны округляется вверх. */
    @Test
    @DisplayName("U7.5 — цена входа, направление SHORT: округление вверх до 2969.6")
    void u7_5_theShortSideOrderPriceIsRoundedUp() {
        assertThat(calculator.calculate(orderPriceContext(StrategyTradeDirection.SHORT)).getRoundedPrice())
                .isEqualByComparingTo("2969.6");
    }

    /** Тейк округляется в сторону прибыли: вверх у длинной стороны, вниз у короткой. */
    @Test
    @DisplayName("U7.6 — тейк: LONG округляется вверх до 2805.6, SHORT — вниз до 2749.9")
    void u7_6_theTakeProfitIsRoundedTowardsProfit() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.TAKE_PROFIT);
        action.setTriggerProfitPercents(new BigDecimal("1"));

        CalculationContext longContext = base(action)
                .entryOrder(entryOrder(OFF_GRID_ANCHOR, null))
                .build();
        CalculationContext shortContext = base(action)
                .entryOrder(entryOrder(OFF_GRID_ANCHOR, null))
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        assertThat(calculator.calculate(longContext).getTakeProfitPrice().getTriggerPrice())
                .isEqualByComparingTo("2805.6");
        assertThat(calculator.calculate(shortContext).getTakeProfitPrice().getTriggerPrice())
                .isEqualByComparingTo("2749.9");
    }

    /** Округление идёт по шагу инструмента, а не по числу знаков. */
    @Test
    @DisplayName("U7.7 — шаг цены 0.5, сырая 2969.505: 2969.5 — округление по шагу, а не по знакам")
    void u7_7_theRoundingFollowsTheInstrumentTick() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.BEST_BID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .instrumentExternalRules(rulesWithTick("0.5"))
                .build();

        assertThat(calculator.calculate(context).getRoundedPrice()).isEqualByComparingTo("2969.5");
    }

    /** Защитный уровень после округления неположителен — отказ. */
    @Test
    @DisplayName("U7.8 — защитный уровень после округления неположителен: отказ INVALID_PRICE_AFTER_ROUNDING")
    void u7_8_aNonPositiveProtectiveLevelRefuses() {
        CalculationContext context = base(stopAction("1"))
                .entryOrder(entryOrder("0.05", null))
                .build();

        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo("INVALID_PRICE_AFTER_ROUNDING");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private CalculationContext orderPriceContext(StrategyTradeDirection direction) {
        return base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.BEST_BID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .strategyDirection(direction)
                .build();
    }
}
