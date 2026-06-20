package com.example.tradingbot.domain.command.risk;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Результат одной конкретной risk-проверки внутри RiskValidationResult.
 * RVO, не persisted. {@code limitValue} отдельным полем не вводится — при
 * необходимости лимит кладётся в {@code details}. См.
 * docs/components/models/RiskCheckResult.md.
 */
@Value
@Builder
public class RiskCheckResult {

    /** Машинный код проверки. */
    RiskCheckCode code;

    /** Результат конкретной проверки. */
    RiskCheckStatus status;

    /** Фактическое значение, если проверка числовая. */
    BigDecimal actualValue;

    /** Короткое пояснение. */
    String comment;

    /** Дополнительные детали для диагностики. */
    Map<String, Object> details;

    /** Блокирующая проверка с пояснением и фактическим значением. */
    public static RiskCheckResult blocked(RiskCheckCode code, String comment, BigDecimal actualValue) {
        return RiskCheckResult.builder()
                .code(code)
                .status(RiskCheckStatus.BLOCKED)
                .comment(comment)
                .actualValue(actualValue)
                .build();
    }

    /** Результат конкретной risk-проверки. */
    public enum RiskCheckStatus {

        /** Проверка пройдена. */
        PASSED,

        /** Предупреждение — действие не блокируется. */
        WARNING,

        /** Действие заблокировано этой проверкой. */
        BLOCKED
    }

    /**
     * Машинный код risk-проверки. Часть кодов (PARTIAL_EXIT_*,
     * DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN) — safety/invariant violation
     * exit-flow (reduce-only), не risk-policy проверки RiskValidator.
     */
    public enum RiskCheckCode {

        /** Убыток на стопе как % от свободного депозита превышает лимит сделки. */
        RISK_PER_TRADE_EXCEEDED,

        /** Плечо превышает биржевой максимум инструмента. */
        EXCHANGE_MAX_LEVERAGE_EXCEEDED,

        /** Режим маржи не isolated. */
        MARGIN_MODE_NOT_ISOLATED,

        /** Обнаружен borrow/debt (торгуем только своими средствами). */
        BORROW_OR_DEBT_DETECTED,

        /** Недостаточно средств для действия. */
        BALANCE_NOT_ENOUGH,

        /** Баланс не свежий. */
        BALANCE_NOT_FRESH,

        /** Баланс отсутствует/невалиден. */
        BALANCE_INVALID,

        /** Размер ниже минимального размера инструмента. */
        SIZE_BELOW_MIN,

        /** Размер не кратен шагу лота. */
        SIZE_LOT_STEP_INVALID,

        /** Размер выше per-order лимита инструмента. */
        SIZE_ABOVE_LIMIT,

        /** Стоп-лосс на неверной стороне относительно входа. */
        STOP_LOSS_INVALID_SIDE,

        /** Тейк-профит на неверной стороне относительно входа. */
        TAKE_PROFIT_INVALID_SIDE,

        /** Стоп-лосс слишком близко к цене ликвидации. */
        STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION,

        /** Частичный выход не reduce-only (safety/invariant). */
        PARTIAL_EXIT_NOT_REDUCE_ONLY,

        /** Частичный выход увеличивает позицию (safety/invariant). */
        PARTIAL_EXIT_INCREASES_POSITION,

        /** Прямое частичное закрытие позиции запрещено (safety/invariant). */
        DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN,

        /** Обнаружено более одной позиции по инструменту. */
        MULTIPLE_POSITIONS_DETECTED,

        /** Состояние позиции неизвестно. */
        POSITION_STATE_UNKNOWN,

        /** Инструмент не торгуется (status != LIVE). */
        INSTRUMENT_NOT_LIVE,

        /** Внешние правила инструмента не материализованы. */
        INSTRUMENT_RULES_MISSING,

        /** Рассчитанное действие невалидно (нет размера/цены). */
        CALCULATED_ACTION_INVALID
    }
}
