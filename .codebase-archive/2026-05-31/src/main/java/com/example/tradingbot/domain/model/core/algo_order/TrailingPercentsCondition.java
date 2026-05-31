package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;

public class TrailingPercentsCondition extends Condition {

    public TrailingPercentsCondition(BigDecimal trailingPercents,
                                     TriggerPrice activationPrice,
                                     BigDecimal closeFraction) {
        super(
                ConditionType.TRAILING_PERCENTS,
                closeFraction,
                null,
                new Trailing(trailingPercents, null, activationPrice)
        );
    }
}