package com.example.tradingbot.domain.model.algo_order;

import java.math.BigDecimal;

public class PartialTakeProfitCondition extends Condition {

    public PartialTakeProfitCondition(BigDecimal takeProfitTriggerPrice,
                                      TriggerPriceType triggerPriceType,
                                      BigDecimal closeFraction) {
        super(
                ConditionType.PARTIAL_TAKE_PROFIT,
                closeFraction,
                new Trigger(null, new TriggerPrice(triggerPriceType, takeProfitTriggerPrice)),
                null
        );
    }
}