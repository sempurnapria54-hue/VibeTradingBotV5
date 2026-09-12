package com.example.strategy.engine.calc;

import java.util.Objects;
import lombok.Value;

/**
 * Внешний контракт-результат расчёта параметров действия
 * (docs/components/models/StrategyActionCalculationResult.md): успешно
 * рассчитанное действие либо контролируемая ошибка расчёта.
 *
 * <p><b>Контракт слоя возвратный, а не бросковый.</b> Субкалькуляторы
 * сигнализируют контролируемую ошибку броском внутреннего исключения;
 * оркестратор его перехватывает и возвращает вот это значение. Неожиданные
 * исключения так не оборачиваются — их ловит граница исполнения прохода
 * (docs/rules/runtime-error-classification.md).
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией у значения
 * нет (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 */
@Value
public class StrategyActionCalculationResult {

    /** Итог расчёта. */
    Status status;

    /** Рассчитанное действие; заполнено только при {@code SUCCESS}. */
    CalculatedStrategyAction calculatedAction;

    /** Контролируемая ошибка расчёта; заполнена только при {@code ERROR}. */
    CalculationError error;

    private StrategyActionCalculationResult(Status status, CalculatedStrategyAction calculatedAction,
                                            CalculationError error) {
        this.status = status;
        this.calculatedAction = calculatedAction;
        this.error = error;
    }

    /** Успех: действие рассчитано целиком. */
    public static StrategyActionCalculationResult success(CalculatedStrategyAction calculatedAction) {
        return new StrategyActionCalculationResult(Status.SUCCESS, calculatedAction, null);
    }

    /** Контролируемая ошибка: параметров действия не существует. */
    public static StrategyActionCalculationResult error(CalculationError error) {
        return new StrategyActionCalculationResult(Status.ERROR, null, error);
    }

    /** Расчёт удался. */
    public Boolean isSuccess() {
        return Objects.equals(status, Status.SUCCESS);
    }

    /** Итог расчёта. */
    public enum Status {

        /** Параметры посчитаны, действие готово к исполнению. */
        SUCCESS,

        /** Сработала контролируемая ошибка расчёта. */
        ERROR
    }
}
