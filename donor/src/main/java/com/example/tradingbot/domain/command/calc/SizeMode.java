package com.example.tradingbot.domain.command.calc;

/**
 * Режим рассчитанного размера действия. См.
 * docs/components/models/CalculatedSize.md (§SizeMode).
 */
public enum SizeMode {

    /** Размер для открытия или увеличения позиции. */
    OPEN_OR_INCREASE,

    /** Только для уменьшения существующей позиции. */
    REDUCE_ONLY,

    /** Для полного закрытия позиции. */
    FULL_CLOSE,

    /** Для команды размер не требуется. */
    NOT_REQUIRED
}
