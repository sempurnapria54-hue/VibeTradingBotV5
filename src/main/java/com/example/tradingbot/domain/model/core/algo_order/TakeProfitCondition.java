package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;

public class TakeProfitCondition extends Condition {

    public TakeProfitCondition(BigDecimal takeProfitTriggerPrice,
                               TriggerPriceType triggerPriceType,
                               BigDecimal closeFraction) {
        super(
                ConditionType.TAKE_PROFIT,
                closeFraction,
                new Trigger(null, new TriggerPrice(triggerPriceType, takeProfitTriggerPrice)),
                null
        );
    }
}