package com.example.strategy.engine.calc;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Резолв уровня остановки убытка — раздел {@link CalculatedPrice}, без
 * него смысла не имеющий (docs/components/models/CalculatedPrice.md).
 */
@Value
@Builder
public class ResolvedStopLossPrice {

    /** Триггерная цена срабатывания. */
    BigDecimal triggerPrice;

    /** Цена ноги после срабатывания; пусто — рыночное исполнение. */
    BigDecimal orderPrice;

    /** Ценовая база триггера у площадки. */
    AlgoOrder.TriggerPriceType triggerPriceType;
}
