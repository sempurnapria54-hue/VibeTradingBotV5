package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.decimal;
import static com.example.strategy.engine.unit.calc.CalcFixture.detail;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static com.example.strategy.engine.unit.calc.CalcFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Размер защитной ступени: другой класс — группа `U10` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/order-sizing.json, величины {@code lastLadderStepSize} и
 * {@code ladderStepRejected}; docs/rules/live-risk-protection.md).
 *
 * <p><b>Базовая сборка:</b> правила инструмента — шаг лота {@code 0.1},
 * минимальный размер {@code 1}; экспозиция транша {@code 10}; шаг
 * стратегии с двумя защитными <b>создающими</b> действиями — доли
 * {@code 30} и {@code 70}, идентификаторы {@code 1} и {@code 2}; сумма
 * уже поставленных ступеней {@code 3}; рассчитывается первая ступень.
 *
 * <p><b>Признак «последняя ступень» не подменяется:</b> набор собирается
 * целиком, и селектор — защитное создающее действие шага с максимальным
 * идентификатором — считается самой доменной моделью.
 */
class LadderStepSizeTest {

    /** Правила базовой сборки: минимальный размер {@code 1}. */
    private static final InstrumentExternalRules LADDER_RULES = rules("0.1", "0.0005", "1", "0.1", "1");

    private final SizeCalculator calculator = new SizeCalculator();

    /** Не последняя ступень идёт объявленной долей; исхода выхода у ступени не бывает. */
    @Test
    @DisplayName("U10.1 — базовая сборка: размер 3.0, доля 0.3, режим только уменьшение; исход выхода пуст")
    void u10_1_aNonLastStepTakesItsDeclaredShare() {
        CalculatedSize size = calculator.calculate(ladderContext(0, "30", "70", "3", "10"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("3.0");
        assertThat(size.getCloseFraction()).isEqualByComparingTo("0.3");
        assertThat(size.getSizeMode()).isEqualTo(SizeMode.REDUCE_ONLY);
        assertThat(size.getExitOutcome()).as("у защитной ступени исхода выхода не бывает").isNull();
    }

    /** Последняя ступень берёт ОСТАТОК экспозиции, а не объявленную долю. */
    @Test
    @DisplayName("U10.2 — рассчитывается вторая ступень: размер 7.0 — остаток экспозиции")
    void u10_2_theLastStepTakesTheRemainderOfTheExposure() {
        CalculatedSize size = calculator.calculate(ladderContext(1, "30", "70", "3", "10"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("7.0");
    }

    /** Остаток округления достаётся последней ступени: покрытие равно экспозиции ровно. */
    @Test
    @DisplayName("U10.3 — экспозиция 10.05, поставлено 3.0, вторая ступень: размер 7.05 — покрытие ровно")
    void u10_3_theRoundingRemainderGoesToTheLastStep() {
        CalculatedSize size = calculator.calculate(ladderContext(1, "30", "70", "3.0", "10.05"), null);

        assertThat(size.getSizeContracts()).as("объявленная доля дала бы 7.0 и оставила бы транш недопокрытым")
                .isEqualByComparingTo("7.05");
    }

    /** У набора из одной ступени сумма поставленных — ноль по построению. */
    @Test
    @DisplayName("U10.4 — набор из одной ступени долей 100, сумма не предъявлена: размер 10")
    void u10_4_aSingleStepSetNeedsNoPlacedTotal() {
        CalculatedSize size = calculator.calculate(singleStepContext(
                AlgoOrder.ConditionType.PARTIAL_STOP_LOSS, "100", "10"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** У набора из нескольких ступеней пустота не читается нулём. */
    @Test
    @DisplayName("U10.5 — сумма не предъявлена, вторая ступень набора из двух: отказ LADDER_PREVIOUS_STEPS_UNKNOWN")
    void u10_5_anAbsentPlacedTotalOfAMultiStepSetRefuses() {
        assertRefuses(ladderContext(1, "30", "70", null, "10"), "LADDER_PREVIOUS_STEPS_UNKNOWN");
    }

    /** Объявленная доля ниже минимального размера — явный отказ шага. */
    @Test
    @DisplayName("U10.6 — доли 5 и 95, первая ступень: отказ PROTECTION_LADDER_STEP_BELOW_MIN_SIZE")
    void u10_6_aDeclaredShareBelowTheMinimumIsAnExplicitRefusal() {
        assertRefuses(ladderContext(0, "5", "95", "3", "10"), "PROTECTION_LADDER_STEP_BELOW_MIN_SIZE");
    }

    /** Шаг не предъявлен — ступень не считается последней и остатка не берёт. */
    @Test
    @DisplayName("U10.8 — детали стратегии в контексте нет: размер 3.0 — объявленная доля")
    void u10_8_withoutTheStepTheStepTakesNoRemainder() {
        CalculationContext context = ladderContextBuilder(0, "30", "70", "3", "10")
                .strategyDetail(null)
                .build();

        assertThat(calculator.calculate(context, null).getSizeContracts()).isEqualByComparingTo("3.0");
    }

    /** OCO — защитное действие: идёт ступенью, а не выходом. */
    @Test
    @DisplayName("U10.9 — условие OCO_FULL, доля 100 по имени типа: идёт ступенью — исход выхода пуст")
    void u10_9_theOcoGoesAsALadderStep() {
        CalculatedSize size = calculator.calculate(singleStepContext(
                AlgoOrder.ConditionType.OCO_FULL, null, "10"), null);

        assertThat(size.getExitOutcome()).as("у выхода исход был бы заполнен").isNull();
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** Трейлинг идёт ступенью по тому же селектору. */
    @Test
    @DisplayName("U10.10 — условие TRAILING_PERCENTS: идёт ступенью по тому же селектору")
    void u10_10_theTrailingGoesAsALadderStepToo() {
        CalculatedSize size = calculator.calculate(singleStepContext(
                AlgoOrder.ConditionType.TRAILING_PERCENTS, null, "10"), null);

        assertThat(size.getExitOutcome()).isNull();
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** Частичный стоп — тоже защита: ступень объявленной доли. */
    @Test
    @DisplayName("U10.11 — условие PARTIAL_STOP_LOSS, доля 30: ступень объявленной доли 3.0; исход пуст")
    void u10_11_thePartialStopLossGoesAsALadderStep() {
        CalculatedSize size = calculator.calculate(ladderContext(0, "30", "70", "3", "10"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("3.0");
        assertThat(size.getExitOutcome()).isNull();
    }

    /**
     * Замещающее действие последней ступенью не считается, хотя его
     * идентификатор больше обоих создающих: лестницу образуют ступени,
     * которые шаг СТАВИТ, а замещение адресует уже стоящую.
     */
    @Test
    @DisplayName("U10.12 — защитное ЗАМЕЩАЮЩЕЕ действие с бо́льшим идентификатором: размер 3.0, остатка не берёт")
    void u10_12_aReplacingActionIsNotTheLastLadderStep() {
        StrategyAlgoOrderAction first = ladderStep(1L, "30");
        StrategyAlgoOrderAction second = ladderStep(2L, "70");
        StrategyAlgoOrderAction replacing = ladderStep(3L, "30");
        replacing.setActionType(StrategyActionType.REPLACE_ACTION);
        replacing.setTargetActionKey(first.getKey());

        CalculationContext context = base((StrategyAction) replacing)
                .instrumentExternalRules(LADDER_RULES)
                .dealTranche(tranche("10"))
                .ladderPreviousStepsTotal(decimal("3"))
                .strategyDetail(detail("1", List.of(first, second, replacing)))
                .build();

        assertThat(calculator.calculate(context, null).getSizeContracts())
                .as("7.0 означало бы, что замещающее действие взяло остаток")
                .isEqualByComparingTo("3.0");
    }

    /** Тейк защитой не является: идёт выходом, и исход выхода заполнен. */
    @Test
    @DisplayName("U10.13 — условие TAKE_PROFIT: идёт выходом — исход выхода заполнен")
    void u10_13_theTakeProfitGoesAsAnExitNotALadderStep() {
        CalculatedSize size = calculator.calculate(singleStepContext(
                AlgoOrder.ConditionType.TAKE_PROFIT, null, "10"), null);

        assertThat(size.getExitOutcome()).as("у ступени исход был бы пуст").isNotNull();
    }

    // --- базовая сборка и отклонения от неё --------------------------------

    private StrategyAlgoOrderAction ladderStep(Long id, String closeFractionPercents) {
        StrategyAlgoOrderAction action = algoAction(id, AlgoOrder.ConditionType.PARTIAL_STOP_LOSS);
        action.setCloseFractionPercents(decimal(closeFractionPercents));
        return action;
    }

    private CalculationContext ladderContext(int calculatedIndex, String firstPercents, String secondPercents,
                                             String previousStepsTotal, String exposure) {
        return ladderContextBuilder(calculatedIndex, firstPercents, secondPercents, previousStepsTotal,
                exposure).build();
    }

    private CalculationContext.CalculationContextBuilder ladderContextBuilder(int calculatedIndex,
                                                                              String firstPercents,
                                                                              String secondPercents,
                                                                              String previousStepsTotal,
                                                                              String exposure) {
        List<StrategyAction> ladder = List.of(ladderStep(1L, firstPercents), ladderStep(2L, secondPercents));
        return base(ladder.get(calculatedIndex))
                .instrumentExternalRules(LADDER_RULES)
                .dealTranche(tranche(exposure))
                .ladderPreviousStepsTotal(decimal(previousStepsTotal))
                .strategyDetail(detail("1", ladder));
    }

    private CalculationContext singleStepContext(AlgoOrder.ConditionType conditionType,
                                                 String closeFractionPercents, String exposure) {
        StrategyAlgoOrderAction action = algoAction(1L, conditionType);
        action.setCloseFractionPercents(decimal(closeFractionPercents));
        return base((StrategyAction) action)
                .instrumentExternalRules(LADDER_RULES)
                .dealTranche(tranche(exposure))
                .strategyDetail(detail("1", List.of(action)))
                .build();
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context, null))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
