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

    /**
     * Отступ трейлинга в процентах. В долю площадки его переводит граница
     * коннектора, и имя доли здесь было бы ловушкой.
     */
    BigDecimal callbackPercents;

    /** Ценовая база триггера у площадки. */
    AlgoOrder.TriggerPriceType triggerPriceType;
}
