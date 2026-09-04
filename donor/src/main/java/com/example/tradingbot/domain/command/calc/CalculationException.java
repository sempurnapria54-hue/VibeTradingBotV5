package com.example.tradingbot.domain.command.calc;

import lombok.Getter;

/**
 * Внутренний механизм сигнализации контролируемой ошибки расчёта внутри
 * калькуляторного слоя (CalculationContextFactory / PriceCalculator /
 * SizeCalculator): несёт {@link CalculationError}, перехватывается
 * StrategyActionCalculator и превращается в ERROR-результат
 * (StrategyActionCalculationResult). Unexpected exceptions этим
 * механизмом не оборачиваются — они ловятся на границе FSM
 * (RuntimeErrorCode). См. docs/components/models/CalculationError.md.
 */
@Getter
public class CalculationException extends RuntimeException {

    private final transient CalculationError error;

    public CalculationException(CalculationError error) {
        super(error.getMessage());
        this.error = error;
    }
}
