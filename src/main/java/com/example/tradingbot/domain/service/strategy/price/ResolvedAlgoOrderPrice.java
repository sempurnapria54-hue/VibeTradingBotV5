package com.example.tradingbot.domain.service.strategy.price;

import com.example.tradingbot.domain.model.core.algo_order.Condition;

/**
 * Результат вычисления условия standalone algo-order.
 *
 * @param condition условие, которое будет сохранено в локальном algo-order
 */
public record ResolvedAlgoOrderPrice(Condition condition) {
}
