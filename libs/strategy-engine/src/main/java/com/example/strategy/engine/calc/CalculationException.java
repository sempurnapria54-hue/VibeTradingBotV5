package com.example.strategy.engine.calc;

import lombok.Getter;

/**
 * Внутренний механизм сигнализации контролируемой ошибки расчёта внутри
 * расчётного слоя: несёт {@link CalculationError}, перехватывается
 * оркестратором расчёта и превращается в результат-ошибку. Внешний
 * контракт слоя — <b>возвратный</b>, а не бросковый
 * (docs/components/StrategyActionCalculator.md).
 *
 * <p>Неожиданные исключения этим механизмом не оборачиваются — их ловит
 * граница исполнения прохода.
 */
@Getter
public class CalculationException extends RuntimeException {

    private final transient CalculationError error;

    public CalculationException(CalculationError error) {
        super(error.getMessage());
        this.error = error;
    }
}
