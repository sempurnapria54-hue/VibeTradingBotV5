package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryOrder;
import static com.example.strategy.engine.unit.calc.CalcFixture.liveEpisode;
import static com.example.strategy.engine.unit.calc.CalcFixture.rulesWithFee;
import static com.example.strategy.engine.unit.calc.CalcFixture.stopSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Безубыток: точная форма и ставка комиссии — группа `U5` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/stop-distance.json, величина {@code breakevenLevel};
 * docs/components/PriceCalculator.md §«Безубыток считается с комиссией»).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; якорь {@code 3000}; шаг
 * цены {@code 0.1}; ставка комиссии {@code 0.0005}; действие — условная
 * заявка `STOP_LOSS` со способом расчёта «безубыток».
 *
 * <p><b>Точная форма разводится с линейным приближением числом</b>
 * (`U5.3`): на ставке {@code 0.01} и якоре {@code 1000} приближение дало
 * бы ровно {@code 1020.0}, а точная форма — {@code 1020.3}.
 *
 * <p><b>Сменяет часть прежней пробы предмета</b>
 * ({@code com.example.strategy.engine.calc.PriceLevelTest}): её
 * клетки-преемницы здесь — `U5.1` (точная форма, а не приближение) и
 * `U5.4` (нерезолвленная ставка отказывает, а не подставляет цену входа).
 */
class BreakevenLevelTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Точная форма плюс округление прочь от якоря; назначение — цена безубытка. */
    @Test
    @DisplayName("U5.1 — базовая сборка: уровень 3003.1, округление вверх; назначение — цена безубытка")
    void u5_1_theBreakevenIsSolvedExactlyAndRoundedAwayFromTheAnchor() {
        CalculatedPrice price = calculator.calculate(base(breakevenAction()).build());

        assertThat(price.getStopLossPrice().getTriggerPrice()).isEqualByComparingTo("3003.1");
        assertThat(price.getPurpose())
                .as("назначение разведено с триггером остановки убытка: имя обещает ноль")
                .isEqualTo(StrategyPricePurpose.BREAKEVEN_PRICE);
    }

    /** У короткой стороны уровень ниже якоря, и округление идёт вниз. */
    @Test
    @DisplayName("U5.2 — направление SHORT: уровень 2997.0, округление вниз")
    void u5_2_theShortSideBreakevenIsRoundedDown() {
        CalculationContext context = base(breakevenAction())
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("2997.0");
    }

    /** Точная форма отличима от линейного приближения на крупной ставке. */
    @Test
    @DisplayName("U5.3 — ставка 0.01, якорь 1000: уровень 1020.3, а приближение дало бы 1020.0")
    void u5_3_theExactFormIsDistinguishableFromTheLinearApproximation() {
        CalculationContext context = base(breakevenAction())
                .instrumentExternalRules(rulesWithFee("0.01"))
                .entryOrder(entryOrder("1000", null))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("1020.0 — линейное приближение; точная форма даёт 1020.2020…")
                .isEqualByComparingTo("1020.3");
    }

    /** Ставка не резолвится — отказ, а не подстановка цены входа. */
    @Test
    @DisplayName("U5.4 — ставка комиссии не резолвится: отказ FEE_RATE_UNAVAILABLE, подстановки нет")
    void u5_4_anUnresolvedFeeRateRefusesInsteadOfUsingTheEntryPrice() {
        CalculationContext context = base(breakevenAction())
                .instrumentExternalRules(rulesWithFee(null))
                .build();

        assertRefuses(context, "FEE_RATE_UNAVAILABLE");
    }

    /** Правил инструмента нет вовсе — тот же отказ. */
    @Test
    @DisplayName("U5.5 — правил инструмента нет вовсе: отказ FEE_RATE_UNAVAILABLE")
    void u5_5_absentInstrumentRulesRefuseOnTheFeeRate() {
        CalculationContext context = base(breakevenAction())
                .instrumentExternalRules(null)
                .build();

        assertRefuses(context, "FEE_RATE_UNAVAILABLE");
    }

    /**
     * Ставка, равная единице, вырождает знаменатель. <b>Охрана без входа
     * из прода:</b> такой ставки источник не отдаёт, и красный прогон
     * означал бы снятую охрану, а не сломанную тропу.
     */
    @Test
    @DisplayName("U5.6 — ставка 1: отказ BREAKEVEN_LEVEL_UNAVAILABLE, а не деление на ноль")
    void u5_6_aDegenerateDenominatorRefusesInsteadOfDividingByZero() {
        CalculationContext context = base(breakevenAction())
                .instrumentExternalRules(rulesWithFee("1"))
                .build();

        assertRefuses(context, "BREAKEVEN_LEVEL_UNAVAILABLE");
    }

    /**
     * Доля дистанции со способом «безубыток» не читается. <b>Охрана
     * второго рубежа чужая:</b> реджект такого объявления живёт у
     * создания стратегии.
     */
    @Test
    @DisplayName("U5.7 — доля дистанции объявлена вместе с безубытком: уровень тот же 3003.1")
    void u5_7_theDistanceIsNotReadByTheBreakeven() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.BREAKEVEN, "5"));

        assertThat(calculator.calculate(base(action).build()).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo("3003.1");
    }

    /**
     * Роли объявления калькулятор не знает и по ней не отказывает:
     * первичная постановка считается так же, как перенос. <b>Охрана
     * второго рубежа чужая</b> — запрет живёт у создания стратегии.
     */
    @Test
    @DisplayName("U5.8 — роль объявления «первичная защита»: уровень считается так же")
    void u5_8_thePlacementRoleIsUnknownToTheCalculator() {
        StrategyAlgoOrderAction primary = breakevenAction();
        primary.setActionType(StrategyActionType.CREATE_ACTION);
        primary.setTargetActionKey(null);

        StrategyAlgoOrderAction transfer = breakevenAction();
        transfer.setActionType(StrategyActionType.REPLACE_ACTION);
        transfer.setTargetActionKey("entry");

        assertThat(calculator.calculate(base(primary).build()).getStopLossPrice().getTriggerPrice())
                .isEqualByComparingTo(calculator.calculate(base(transfer).build())
                        .getStopLossPrice().getTriggerPrice());
    }

    /** Якорь у безубытка тот же, что у прочих уровней: факт старше плана. */
    @Test
    @DisplayName("U5.9 — живой эпизод со средней 2990: уровень 2993.0, считанный от 2990")
    void u5_9_theBreakevenSharesTheLevelAnchor() {
        CalculationContext context = base(breakevenAction())
                .activePosition(liveEpisode("2990"))
                .build();

        assertThat(calculator.calculate(context).getStopLossPrice().getTriggerPrice())
                .as("3003.1 означало бы расчёт от плановой цены")
                .isEqualByComparingTo("2993.0");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private StrategyAlgoOrderAction breakevenAction() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopSettings(StopLossCalculationType.BREAKEVEN, null));
        return action;
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
