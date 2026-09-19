package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.algoAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.decimal;
import static com.example.strategy.engine.unit.calc.CalcFixture.reduceOnlyAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static com.example.strategy.engine.unit.calc.CalcFixture.tranche;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.ExitOutcome;
import com.example.strategy.engine.calc.SizeCalculator;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Размер reduce-only выхода: четыре исхода — группа `U9` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/spec/order-sizing.json, величина {@code exitOutcome};
 * docs/components/SizeCalculator.md §«Reduce-only выход: пола
 * минимального размера нет»).
 *
 * <p><b>Базовая сборка:</b> правила инструмента — стоимость контракта
 * {@code 1}, шаг лота {@code 0.1}, минимальный размер {@code 1};
 * экспозиция транша {@code 10}; действие — reduce-only обычная заявка с
 * объявленной долей {@code 50}.
 *
 * <p><b>Ветвление идёт по ОСТАТКУ, а не по размеру выхода:</b> признак
 * полного выхода по доле — нулевой остаток, и он не зависит от того,
 * выразим ли сам размер минимальным торговым.
 */
class ExitSizeTest {

    /** Правила базовой сборки: минимальный размер {@code 1}. */
    private static final InstrumentExternalRules EXIT_RULES = rules("0.1", "0.0005", "1", "0.1", "1");

    private final SizeCalculator calculator = new SizeCalculator();

    /** Штатный частичный выход: остаток жизнеспособен. */
    @Test
    @DisplayName("U9.1 — базовая сборка: размер 5, исход частичный, доля 0.5; нотинал пуст")
    void u9_1_aDeclaredPartialExitGoesThroughAsPartial() {
        CalculatedSize size = calculator.calculate(exitContext("50", "10", EXIT_RULES), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("5");
        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.PARTIAL);
        assertThat(size.getCloseFraction()).isEqualByComparingTo("0.5");
        assertThat(size.getSizeMode()).isEqualTo(SizeMode.REDUCE_ONLY);
        assertThat(size.getNotionalUsdt()).as("нотинал у выхода пуст").isNull();
    }

    /** Объявленная доля забрала экспозицию целиком — полный по доле. */
    @Test
    @DisplayName("U9.2 — доля 100: остаток 0, исход полный по доле; в заявку уезжает 10")
    void u9_2_theWholeExposureTakenByTheFractionIsFullByFraction() {
        CalculatedSize size = calculator.calculate(exitContext("100", "10", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL_BY_FRACTION);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** Остаток ниже минимального — выходим целиком, а не объявленной долей. */
    @Test
    @DisplayName("U9.3 — доля 95: размер доли 9.5, остаток 0.5 ниже минимума — исход полный, в заявку 10")
    void u9_3_aDeadRemainderTurnsTheExitFull() {
        CalculatedSize size = calculator.calculate(exitContext("95", "10", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL);
        assertThat(size.getSizeContracts()).as("в заявку уезжает экспозиция целиком, а не 9.5")
                .isEqualByComparingTo("10");
    }

    /** Размер ниже минимального при жизнеспособном остатке — действие не исполняется. */
    @Test
    @DisplayName("U9.4 — доля 5: размер доли 0.5 ниже минимума, остаток 9.5 жив — исход пропуск, размер 0")
    void u9_4_anUnviableShareWithAViableRemainderIsSkipped() {
        CalculatedSize size = calculator.calculate(exitContext("5", "10", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.SKIPPED);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("0");
    }

    /** Обе величины ниже минимального — «частично» невыразимо, и выход идёт целиком. */
    @Test
    @DisplayName("U9.5 — экспозиция 1.5, доля 50: размер 0.7 и остаток 0.8 ниже минимума — исход полный")
    void u9_5_bothShareAndRemainderBelowTheMinimumExitInFull() {
        CalculatedSize size = calculator.calculate(exitContext("50", "1.5", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("1.5");
    }

    /** Признак полного по доле от выразимости размера не зависит: ветвление идёт по остатку. */
    @Test
    @DisplayName("U9.6 — экспозиция 0.5, доля 100: исход полный по доле, а не полный")
    void u9_6_theFullByFractionDoesNotDependOnExpressibility() {
        CalculatedSize size = calculator.calculate(exitContext("100", "0.5", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).as("округление ни при чём, хотя экспозиция ниже минимума")
                .isEqualTo(ExitOutcome.FULL_BY_FRACTION);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("0.5");
    }

    /** Сырой размер режется вниз по шагу лота. */
    @Test
    @DisplayName("U9.7 — шаг лота 0.5, доля 33: сырой 3.3 округлён вниз до 3.0, остаток 7.0, исход частичный")
    void u9_7_theRawExitSizeIsFlooredToTheLotSize() {
        CalculatedSize size = calculator.calculate(
                exitContext("33", "10", rules("0.1", "0.0005", "1", "0.5", "1")), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("3.0");
        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.PARTIAL);
    }

    /** Пола минимального размера у выхода нет: доля не поднимается, действие пропускается. */
    @Test
    @DisplayName("U9.8 — доля 9: размер 0.9 до минимума не поднимается — исход пропуск, размер 0")
    void u9_8_theExitSizeIsNeverRaisedToTheMinimum() {
        CalculatedSize size = calculator.calculate(exitContext("9", "10", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.SKIPPED);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("0");
        assertThat(size.getSizeContracts()).as("подъёма до минимального размера нет")
                .isNotEqualByComparingTo("1");
    }

    /** Доля у reduce-only заявки не объявлена — размеру взяться неоткуда. */
    @Test
    @DisplayName("U9.9 — доля у reduce-only заявки не объявлена: отказ MISSING_CLOSE_FRACTION")
    void u9_9_anUndeclaredFractionOfAReduceOnlyOrderRefuses() {
        assertRefuses(exitContext(null, "10", EXIT_RULES), "MISSING_CLOSE_FRACTION");
    }

    /** У частичного типа доля берётся из своего поля. */
    @Test
    @DisplayName("U9.10 — PARTIAL_TAKE_PROFIT, доля закрытия 40: размер 4, исход частичный")
    void u9_10_thePartialTypeTakesItsOwnFractionField() {
        CalculatedSize size = calculator.calculate(algoExitContext(
                AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT, "40", "10"), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("4");
        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.PARTIAL);
    }

    /** Пустая доля частичного типа не читается нулём. */
    @Test
    @DisplayName("U9.11 — PARTIAL_TAKE_PROFIT без объявленной доли: отказ MISSING_CLOSE_FRACTION")
    void u9_11_anEmptyPartialFractionIsNotReadAsZero() {
        assertRefuses(algoExitContext(AlgoOrder.ConditionType.PARTIAL_TAKE_PROFIT, null, "10"),
                "MISSING_CLOSE_FRACTION");
    }

    /** У полного типа доля есть сто процентов по имени; поле частичной доли не читается. */
    @Test
    @DisplayName("U9.12 — TAKE_PROFIT с объявленной долей 40: доля 100 по имени типа, размер 10")
    void u9_12_theFullTypeTakesOneHundredPercentByItsName() {
        CalculatedSize size = calculator.calculate(algoExitContext(
                AlgoOrder.ConditionType.TAKE_PROFIT, "40", "10"), null);

        assertThat(size.getCloseFraction()).as("поле частичной доли не читается").isEqualByComparingTo("1");
        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL_BY_FRACTION);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    /** Транша нет — у экспозиции нет владельца. */
    @Test
    @DisplayName("U9.13 — транша в контексте нет: отказ MISSING_TRANCHE_EXPOSURE")
    void u9_13_anAbsentTrancheRefuses() {
        assertRefuses(exitContext("50", null, EXIT_RULES), "MISSING_TRANCHE_EXPOSURE");
    }

    /** Правила инструмента читаются раньше экспозиции: транша нет, а отказ всё равно о полях сайзинга. */
    @Test
    @DisplayName("U9.14 — правил инструмента нет: отказ MISSING_SIZE_SPECS — раньше, чем читается экспозиция")
    void u9_14_theSizeSpecsAreReadBeforeTheExposure() {
        assertRefuses(exitContext("50", null, null), "MISSING_SIZE_SPECS");
    }

    /** Нулевая экспозиция: предикат остатка срабатывает первым. */
    @Test
    @DisplayName("U9.15 — экспозиция транша 0: размер 0, исход полный по доле — предикат остатка первым")
    void u9_15_aZeroExposureIsFullByFraction() {
        CalculatedSize size = calculator.calculate(exitContext("50", "0", EXIT_RULES), null);

        assertThat(size.getSizeContracts()).isEqualByComparingTo("0");
        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL_BY_FRACTION);
    }

    /**
     * Доля вне объявленной области. <b>Охрана второго рубежа чужая:</b>
     * спека объявляет {@code exit.fraction} в {@code (0, 1]}, а отсечение
     * живёт у создания стратегии
     * (docs/rules/strategy-validation.md, `STRATEGY_ACTION_FRACTION_NOT_POSITIVE`
     * и диапазон {@code (0, 100]}). У калькулятора своей охраны нет: доля
     * {@code 150} даёт отрицательный остаток, тот меньше минимального, и
     * исход выходит полным. Строка добрана под-шагом 3 по пробелу `G4`.
     */
    @Test
    @DisplayName("U9.16 — доля 150: своей охраны области у калькулятора нет — остаток отрицателен, исход полный")
    void u9_16_aFractionOutsideTheDeclaredDomainHasNoGuardHere() {
        CalculatedSize size = calculator.calculate(exitContext("150", "10", EXIT_RULES), null);

        assertThat(size.getExitOutcome()).isEqualTo(ExitOutcome.FULL);
        assertThat(size.getSizeContracts()).isEqualByComparingTo("10");
    }

    // --- базовая сборка и отклонения от неё --------------------------------

    private CalculationContext exitContext(String closeFractionPercents, String exposure,
                                           InstrumentExternalRules instrumentRules) {
        return base(reduceOnlyAction(closeFractionPercents))
                .instrumentExternalRules(instrumentRules)
                .dealTranche(isNull(exposure) ? null : tranche(exposure))
                .build();
    }

    private CalculationContext algoExitContext(AlgoOrder.ConditionType conditionType,
                                               String closeFractionPercents, String exposure) {
        StrategyAlgoOrderAction action = algoAction(2L, conditionType);
        action.setCloseFractionPercents(decimal(closeFractionPercents));
        return base((StrategyAction) action)
                .instrumentExternalRules(EXIT_RULES)
                .dealTranche(tranche(exposure))
                .build();
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context, null))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
