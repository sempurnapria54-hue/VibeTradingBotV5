package com.example.tradingbot.domain.model.algo_order;

import java.math.BigDecimal;

public class PartialStopLossCondition extends Condition {

    public PartialStopLossCondition(BigDecimal stopLossTriggerPrice,
                                    TriggerPriceType triggerPriceType,
                                    BigDecimal closeFraction) {
        super(
                ConditionType.PARTIAL_STOP_LOSS,
                closeFraction,
                new Trigger(new TriggerPrice(triggerPriceType, stopLossTriggerPrice), null),
                null
        );
    }
}