package com.example.tradingbot.domain.command.calc;

/**
 * Тип контролируемой ошибки расчёта параметров действия. См.
 * docs/components/models/CalculationError.md (§CalculationErrorType).
 */
public enum CalculationErrorType {

    /** Данные задержались / сервис временно недоступен (повторяемо). */
    TEMPORARY,

    /** Action невозможно корректно рассчитать в текущей конфигурации. */
    PERMANENT
}
