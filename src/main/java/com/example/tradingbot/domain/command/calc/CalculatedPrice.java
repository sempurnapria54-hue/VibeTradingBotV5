package com.example.tradingbot.domain.command.calc;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Рассчитанная цена размещения. Минимальный контракт входа
 * ServiceCommandFactory; полный CalculatedPrice (с StrategyPricePurpose и
 * набором цен) — у калькулятора шага 5. См.
 * docs/components/models/CalculatedPrice.md.
 */
@Value
@Builder
public class CalculatedPrice {

    /** Цена размещения (для market-like — null). */
    BigDecimal price;

    /** Слать ли цену на биржу (market-like — false). */
    Boolean sendToExchange;
}
