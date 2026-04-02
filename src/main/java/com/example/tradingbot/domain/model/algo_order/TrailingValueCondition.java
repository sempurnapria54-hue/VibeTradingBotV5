package com.example.tradingbot.domain.model.algo_order;

import java.math.BigDecimal;

public class TrailingValueCondition extends Condition {

    public TrailingValueCondition(BigDecimal trailingStepValue,
                                  TriggerPrice activationPrice,
                                  BigDecimal closeFraction) {
        super(
                ConditionType.TRAILING_VALUE,
                closeFraction,
                null,
                new Trailing(null, trailingStepValue, activationPrice)
        );
    }
}