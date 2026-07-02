package com.example.tradingbot.domain.command.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанный размер order/algo action — результат SizeCalculator.
 * RVO, не persisted. {@code sz} в OKX API для SWAP/FUTURES — это
 * контракты, не USDT. Полного закрытия позиции как действия нет (выход —
 * условие-перехода MANAGING → EXIT_PENDING); частичное уменьшение —
 * через reduce-only Order/AlgoOrder ({@code closeFraction}).
 * См. docs/components/models/CalculatedSize.md.
 */
@Value
@Builder
public class CalculatedSize {

    /** Размер в контрактах после округления по lot size. */
    BigDecimal sizeContracts;

    /** Доля уменьшения позиции 0..1; только для reduce-only Order/AlgoOrder. */
    BigDecimal closeFraction;

    /** Номинал позиции в USDT, если рассчитывался. */
    BigDecimal notionalUsdt;

    /** Режим размера. */
    SizeMode sizeMode;

    /** Пояснение расчёта для логов/аудита. */
    String description;
}
