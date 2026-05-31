package com.example.tradingbot.domain.service.strategy.price;

import java.math.BigDecimal;

/**
 * Результат вычисления цены обычного order.
 *
 * @param price цена order; null допустим для market-like сценариев
 */
public record ResolvedOrderPrice(BigDecimal price) {
}
