package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение стохастического осциллятора: линии %K и %D. См.
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class StochasticValue extends IndicatorValue {

    /** Линия %K (быстрая). */
    private BigDecimal k;

    /** Линия %D (сглаженная %K). */
    private BigDecimal d;

    @Override
    public Type getType() {
        return Type.STOCHASTIC;
    }
}
