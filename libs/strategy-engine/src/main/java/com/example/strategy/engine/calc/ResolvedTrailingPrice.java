package com.example.strategy.engine.calc;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Резолв параметров трейлинга — раздел {@link CalculatedPrice}, без него
 * смысла не имеющий (docs/components/models/CalculatedPrice.md).
 */
@Value
@Builder
public class ResolvedTrailingPrice {

    /** Цена активации; пусто — трейлинг активен сразу. */
    BigDecimal activationPrice;

    /** Отступ трейлинга в процентах. */
    BigDecimal callbackRatio;

    /** Ценовая база триггера у площадки. */
    AlgoOrder.TriggerPriceType triggerPriceType;
}
