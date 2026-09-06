package com.example.tradingcore;

import static com.example.strategy.engine.calc.util.CalculationErrorCodes.FEE_RATE_UNAVAILABLE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.PROTECTION_LADDER_STEP_BELOW_MIN_SIZE;
import static com.example.strategy.engine.calc.util.CalculationErrorCodes.STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.strategy.engine.calc.CalculationError;
import com.example.tradingcore.domain.command.RetryPolicyService;
import com.example.tradingcore.domain.command.strategy.StrategyActionOrchestrator;
import com.example.tradingcore.domain.fsm.StrategyWorkRunner;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import org.junit.jupiter.api.Test;

/**
 * Карв-аут аварийной тропы: какие контролируемые ошибки расчёта уводят
 * сделку в аварийный контур, а какие — нет.
 *
 * <p><b>Что здесь проверяется по существу.</b> Увод в аварию означает
 * kill-switch: живая позиция закрывается по рынку. Ошибка расчёта, у
 * которой прежняя защита осталась жива, такого исхода не заслуживает —
 * система закрыла бы по рынку сделку, риск которой под контролем, из-за
 * того, что очередная ступень защитной лестницы не влезла в минимальный
 * лот. Дом членства — `docs/processes/risk-evaluation.md` §«Карв-аут
 * исчерпанного бюджета сделки»; здесь проверяется, что исполнение
 * членству соответствует.
 *
 * <p><b>Два карв-аута РАЗНЫЕ, и тест разводит их явно.</b> Отказ по
 * стороне уровня не попадает даже в учёт — строка остаётся
 * запланированной; ступень ниже минимального размера строку роняет
 * (отказ обязан быть видимым), но аварии не вызывает.
 */
class CalculationFailureCarveOutTest {

    private final StrategyWorkRunner runner = new StrategyWorkRunner(
            mock(StrategyActionOrchestrator.class),
            mock(RetryPolicyService.class),
            mock(DealActionStateDataService.class));

    /**
     * Ступень лестницы ниже минимального размера сделку в аварию НЕ
     * уводит: прежняя защита жива.
     */
    @Test
    void aLadderStepBelowMinimumSizeIsNotFatalForTheDeal() {
        CalculationError error = CalculationError.permanent(
                PROTECTION_LADDER_STEP_BELOW_MIN_SIZE, "ladder step is below minimum size");

        assertThat(runner.calculationFailureIsFatal(error)).isFalse();
    }

    /** Отказ по стороне уровня остановки — тоже не авария. */
    @Test
    void aStopLevelOnTheWrongSideIsNotFatalForTheDeal() {
        CalculationError error = CalculationError.permanent(
                STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING, "stop level is not on the loss side");

        assertThat(runner.calculationFailureIsFatal(error)).isFalse();
    }

    /**
     * Прочая постоянная ошибка расчёта аварийна: операнд неверен, и
     * доиграть надобность больше некому.
     */
    @Test
    void anyOtherPermanentCalculationFailureIsFatal() {
        CalculationError error = CalculationError.permanent(
                FEE_RATE_UNAVAILABLE, "taker fee rate is not resolved");

        assertThat(runner.calculationFailureIsFatal(error)).isTrue();
    }

    /** Временная ошибка аварийной не бывает: она ждёт отката по бюджету. */
    @Test
    void aTemporaryCalculationFailureIsNeverFatal() {
        CalculationError error = CalculationError.temporary("MISSING_ATR", "atr is not computed yet");

        assertThat(runner.calculationFailureIsFatal(error)).isFalse();
    }

    /** Пустая ошибка означает «отказа не было». */
    @Test
    void anAbsentFailureIsNotFatal() {
        assertThat(runner.calculationFailureIsFatal(null)).isFalse();
    }
}
