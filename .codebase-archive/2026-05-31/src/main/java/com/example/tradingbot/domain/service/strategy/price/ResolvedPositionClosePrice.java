package com.example.tradingbot.domain.service.strategy.price;

import java.math.BigDecimal;

/**
 * Результат вычисления цены закрытия позиции.
 *
 * @param price цена закрытия; null допустим для рыночного закрытия
 */
public record ResolvedPositionClosePrice(BigDecimal price) {
}
