package com.example.tradingbot.domain.model.algo_order;

import java.math.BigDecimal;

public class OcoFullCondition extends Condition {

    public OcoFullCondition(BigDecimal stopLossTriggerPrice,
                            TriggerPriceType stopLossTriggerPriceType,
                            BigDecimal takeProfitTriggerPrice,
                            TriggerPriceType takeProfitTriggerPriceType,
                            BigDecimal closeFraction) {
        super(
                ConditionType.OCO_FULL,
                closeFraction,
                new Trigger(
                        new TriggerPrice(stopLossTriggerPriceType, stopLossTriggerPrice),
                        new TriggerPrice(takeProfitTriggerPriceType, takeProfitTriggerPrice)
                ),
                null
        );
    }
}