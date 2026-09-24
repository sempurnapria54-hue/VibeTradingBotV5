package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.decimal;
import static com.example.strategy.engine.unit.calc.CalcFixture.liveEpisode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Тейк и трейлинг — группа `U6` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/PriceCalculator.md §Формулы;
 * docs/models/domain/aggregate/Strategy.md §«TrailingSettings»).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; якорь {@code 3000}
 * (плановая цена входной заявки); шаг цены {@code 0.1}.
 *
 * <p><b>Блок настроек трейлинга базовой сборки</b> — порог активации
 * {@code 1}, буфер {@code 0.5}, откат {@code 0.7}: полоса складывается из
 * порога и буфера, а откат переносится в результат без изменения.
 */
class TakeProfitAndTrailingTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Тейк уходит по прибыли и округляется вверх у длинной стороны. */
    @Test
    @DisplayName("U6.1 — условие TAKE_PROFIT, доля прибыли 2: тейк 3060; стоп и трейлинг пусты")
    void u6_1_theTakeProfitGoesTowardsProfit() {
        CalculatedPrice price = calculator.calculate(base(takeProfit("2")).build());

        assertThat(price.getTakeProfitPrice().getTriggerPrice()).isEqualByComparingTo("3060");
        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.TAKE_PROFIT_TRIGGER_PRICE);
        assertThat(price.getStopLossPrice()).as("стоп пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /** У короткой стороны прибыль ниже якоря, и округление идёт вниз. */
    @Test
    @DisplayName("U6.2 — то же, направление SHORT: тейк 2940, округление вниз")
    void u6_2_theShortSideTakeProfitIsBelowTheAnchor() {
        CalculationContext context = base(takeProfit("2"))
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        assertThat(calculator.calculate(context).getTakeProfitPrice().getTriggerPrice())
                .isEqualByComparingTo("2940");
    }

    /** Доля прибыли не объявлена — смещению взяться неоткуда. */
    @Test
    @DisplayName("U6.3 — доля прибыли не объявлена: отказ MISSING_DISTANCE")
    void u6_3_anAbsentProfitPercentRefuses() {
        assertRefuses(base(takeProfit(null)).build(), "MISSING_DISTANCE");
    }

    /** Частичный тейк даёт тот же уровень: доля закрытия адресует размер. */
    @Test
    @DisplayName("U6.4 — условие PARTIAL_TAKE_PROFIT, доля закрытия 40: тейк тот же 3060")
    void u6_4_thePartialTakeProfitGivesTheSameLevel() {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT);
        action.setTriggerProfitPercents(new BigDecimal("2"));
        action.setCloseFractionPercents(new BigDecimal("40"));

        CalculatedPrice price = calculator.calculate(base(action).build());

        assertThat(price.getTakeProfitPrice().getTriggerPrice()).isEqualByComparingTo("3060");
        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.TAKE_PROFIT_TRIGGER_PRICE);
    }

    /** Полоса активации складывается из порога и буфера; откат переносится как есть. */
    @Test
    @DisplayName("U6.5 — трейлинг: порог 1, буфер 0.5, откат 0.7 — активация 3045; стоп и тейк пусты")
    void u6_5_theActivationBandIsThresholdPlusBuffer() {
        CalculatedPrice price = calculator.calculate(base(trailing("1", "0.5", "0.7")).build());

        assertThat(price.getTrailingPrice().getActivationPrice()).isEqualByComparingTo("3045");
        assertThat(price.getTrailingPrice().getCallbackPercents()).isEqualByComparingTo("0.7");
        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.TRAILING_ACTIVATION_PRICE);
        assertThat(price.getStopLossPrice()).as("стоп пуст").isNull();
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
    }

    /** Буфер не объявлен — полоса складывается только из порога. */
    @Test
    @DisplayName("U6.6 — буфер не объявлен: активация 3030 — полоса только из порога")
    void u6_6_anAbsentBufferLeavesTheThresholdAlone() {
        CalculatedPrice price = calculator.calculate(base(trailing("1", null, "0.7")).build());

        assertThat(price.getTrailingPrice().getActivationPrice()).isEqualByComparingTo("3030");
    }

    /** Порога нет — трейлинг активен сразу: цена активации пуста, отказа нет. */
    @Test
    @DisplayName("U6.7 — порог активации не объявлен: активация пуста, откат заполнен, отказа нет")
    void u6_7_anAbsentThresholdMeansImmediateTrailing() {
        CalculatedPrice price = calculator.calculate(base(trailing(null, null, "0.7")).build());

        assertThat(price.getTrailingPrice().getActivationPrice()).as("активация пуста").isNull();
        assertThat(price.getTrailingPrice().getCallbackPercents()).isEqualByComparingTo("0.7");
    }

    /** Блока настроек нет вовсе — ни полосы, ни отката. */
    @Test
    @DisplayName("U6.8 — блока настроек трейлинга нет вовсе: отказ MISSING_DISTANCE")
    void u6_8_anAbsentTrailingBlockRefuses() {
        assertRefuses(base(algoAction(2L, AlgoOrder.ConditionType.TRAILING_PERCENTS)).build(),
                "MISSING_DISTANCE");
    }

    /** Якорь у активации тот же, что у прочих уровней. */
    @Test
    @DisplayName("U6.10 — живой эпизод со средней 2990: активация 3034.9, считанная от 2990")
    void u6_10_theActivationSharesTheLevelAnchor() {
        CalculationContext context = base(trailing("1", "0.5", "0.7"))
                .activePosition(liveEpisode("2990"))
                .build();

        assertThat(calculator.calculate(context).getTrailingPrice().getActivationPrice())
                .as("3045 означало бы расчёт от плановой цены")
                .isEqualByComparingTo("3034.9");
    }

    /** У короткой стороны активация ниже якоря, и округление идёт вниз. */
    @Test
    @DisplayName("U6.11 — направление SHORT, порог 1: активация 2970, округление вниз")
    void u6_11_theShortSideActivationIsBelowTheAnchor() {
        CalculationContext context = base(trailing("1", null, "0.7"))
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        assertThat(calculator.calculate(context).getTrailingPrice().getActivationPrice())
                .isEqualByComparingTo("2970");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private StrategyAlgoOrderAction takeProfit(String profitPercents) {
        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.TAKE_PROFIT);
        action.setTriggerProfitPercents(decimal(profitPercents));
        return action;
    }

    private StrategyAlgoOrderAction trailing(String activationPercents, String bufferPercents,
                                             String callbackPercents) {
        TrailingSettings settings = new TrailingSettings();
        settings.setActivationProfitPercents(decimal(activationPercents));
        settings.setActivationBufferPercents(decimal(bufferPercents));
        settings.setCallbackPercents(decimal(callbackPercents));

        StrategyAlgoOrderAction action = algoAction(2L, AlgoOrder.ConditionType.TRAILING_PERCENTS);
        action.setTrailingSettings(settings);
        return action;
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
