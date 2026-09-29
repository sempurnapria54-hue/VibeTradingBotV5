package com.example.strategy.engine.calc;

/**
 * Режим рассчитанного размера — объявленное намерение действия на входе
 * расчёта (docs/components/models/CalculatedSize.md §SizeMode).
 */
public enum SizeMode {

    /** Открытие либо увеличение позиции. */
    OPEN_OR_INCREASE,

    /** Только уменьшение существующей позиции. */
    REDUCE_ONLY,

    /** Размер действию не требуется. */
    NOT_REQUIRED
}
