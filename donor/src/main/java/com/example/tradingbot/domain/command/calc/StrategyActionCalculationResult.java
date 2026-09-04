package com.example.tradingbot.domain.command.calc;

import lombok.Builder;
import lombok.Value;

/**
 * Внешний контракт-результат StrategyActionCalculator: отделяет успешно
 * рассчитанное действие от контролируемой ошибки расчёта. RVO, не
 * persisted. См. docs/components/models/StrategyActionCalculationResult.md.
 */
@Value
@Builder
public class StrategyActionCalculationResult {

    /** Итог расчёта. */
    Status status;

    /** Рассчитанное действие; заполнено только при SUCCESS. */
    CalculatedStrategyAction calculatedAction;

    /** Контролируемая ошибка расчёта; заполнена только при ERROR. */
    CalculationError error;

    /** Успешный результат с рассчитанным действием. */
    public static StrategyActionCalculationResult success(CalculatedStrategyAction calculatedAction) {
        return StrategyActionCalculationResult.builder()
                .status(Status.SUCCESS)
                .calculatedAction(calculatedAction)
                .build();
    }

    /** Результат-ошибка с контролируемой ошибкой расчёта. */
    public static StrategyActionCalculationResult error(CalculationError error) {
        return StrategyActionCalculationResult.builder()
                .status(Status.ERROR)
                .error(error)
                .build();
    }

    /** Итог расчёта параметров действия. */
    public enum Status {

        /** Параметры успешно рассчитаны. */
        SUCCESS,

        /** Контролируемая ошибка расчёта. */
        ERROR
    }
}
