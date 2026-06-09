package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора RSI (Relative Strength Index, осциллятор). См.
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class RsiValue extends IndicatorValue {

    /** Значение RSI (0..100). */
    private BigDecimal rsi;

    @Override
    public Type getType() {
        return Type.RSI;
    }
}
