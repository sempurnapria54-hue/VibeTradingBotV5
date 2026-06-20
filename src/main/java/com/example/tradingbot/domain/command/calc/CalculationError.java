package com.example.tradingbot.domain.command.calc;

import lombok.Builder;
import lombok.Value;

/**
 * Контролируемая ошибка расчёта параметров действия (возвращается в
 * StrategyActionCalculationResult при ERROR). RVO, не persisted. Только
 * контролируемые случаи: нет актуальной цены / entry price / ATR /
 * market structure, нельзя округлить по tick, нельзя посчитать размер из-за
 * minSz/lotSz, не хватает обязательных input data. Unexpected exceptions
 * сюда не превращаются (для них RuntimeErrorCode). См.
 * docs/components/models/CalculationError.md.
 */
@Value
@Builder
public class CalculationError {

    /** Машинный код ошибки расчёта. */
    String code;

    /** Тип ошибки расчёта. */
    CalculationErrorType type;

    /** Человекочитаемое описание. */
    String message;

    /** Можно ли повторить расчёт позже без изменения стратегии. */
    Boolean retryable;

    /** Временная ошибка (данные задержались / сервис недоступен) — повторяема. */
    public static CalculationError temporary(String code, String message) {
        return CalculationError.builder()
                .code(code)
                .type(CalculationErrorType.TEMPORARY)
                .message(message)
                .retryable(Boolean.TRUE)
                .build();
    }

    /** Постоянная ошибка (action не рассчитать в текущей конфигурации) — не повторяема. */
    public static CalculationError permanent(String code, String message) {
        return CalculationError.builder()
                .code(code)
                .type(CalculationErrorType.PERMANENT)
                .message(message)
                .retryable(Boolean.FALSE)
                .build();
    }
}
