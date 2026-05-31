package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;

public class StopLossCondition extends Condition {

    public StopLossCondition(BigDecimal stopLossTriggerPrice,
                             TriggerPriceType triggerPriceType,
                             BigDecimal closeFraction) {
        super(
                ConditionType.STOP_LOSS,
                closeFraction,
                new Trigger(new TriggerPrice(triggerPriceType, stopLossTriggerPrice), null),
                null
        );
    }
}