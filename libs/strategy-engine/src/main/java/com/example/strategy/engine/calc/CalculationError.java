package com.example.strategy.engine.calc;

import lombok.Builder;
import lombok.Value;

/**
 * Контролируемая ошибка расчёта параметров действия
 * (docs/components/models/CalculationError.md). Неизменяемый
 * runtime-объект прохода: провод не пересекает и в навес не ложится,
 * поэтому форма — {@code @Value}
 * (.claude/rules/codestyle.md §«Неизменяемое значение, пересекающее
 * сериализацию»).
 *
 * <p><b>Только контролируемые случаи.</b> Неожиданные исключения в неё не
 * превращаются — у них своя классификация
 * (docs/rules/runtime-error-classification.md).
 */
@Value
@Builder
public class CalculationError {

    /** Машинный код: что именно сработало и на каком операнде. */
    String code;

    /** Тип ошибки — временная либо постоянная. */
    CalculationErrorType type;

    /** Человекочитаемое пояснение. */
    String message;

    /** Можно ли повторить расчёт позже без правки стратегии. */
    Boolean retryable;

    /** Временная нехватка данных — повторяется бюджетом действия. */
    public static CalculationError temporary(String code, String message) {
        return CalculationError.builder()
                .code(code)
                .type(CalculationErrorType.TEMPORARY)
                .message(message)
                .retryable(Boolean.TRUE)
                .build();
    }

    /** Постоянная невыразимость — действие исполнения не получает. */
    public static CalculationError permanent(String code, String message) {
        return CalculationError.builder()
                .code(code)
                .type(CalculationErrorType.PERMANENT)
                .message(message)
                .retryable(Boolean.FALSE)
                .build();
    }
}
