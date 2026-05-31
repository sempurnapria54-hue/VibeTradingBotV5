package com.example.tradingbot.domain.service.strategy.price;

import java.math.BigDecimal;

/**
 * Результат вычисления цены attached protection.
 *
 * @param stopLossTriggerPrice trigger price для attached stop-loss
 */
public record ResolvedAttachedProtectionPrice(BigDecimal stopLossTriggerPrice) {
}
