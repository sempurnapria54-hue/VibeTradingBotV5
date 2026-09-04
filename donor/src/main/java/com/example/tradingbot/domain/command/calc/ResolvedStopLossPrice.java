package com.example.tradingbot.domain.command.calc;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Резолв конкретной защитной цены stop-loss (раздел CalculatedPrice, без
 * него смысла не имеет). На первом этапе нога исполнения — market
 * ({@code orderPrice = null} → биржевой market-flag). См.
 * docs/components/models/CalculatedPrice.md.
 */
@Value
@Builder
public class ResolvedStopLossPrice {

    /** Trigger-цена срабатывания стопа. */
    BigDecimal triggerPrice;

    /** Order-цена ноги после срабатывания; null — рыночное исполнение. */
    BigDecimal orderPrice;

    /** Тип trigger-цены биржи (last/index/mark). */
    AlgoOrder.TriggerPriceType triggerPriceType;
}
