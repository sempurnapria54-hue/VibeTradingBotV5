package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора ATR (Average True Range) — мера волатильности.
 * См. docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class AtrValue extends IndicatorValue {

    /** Значение ATR. */
    private BigDecimal atr;

    @Override
    public Type getType() {
        return Type.ATR;
    }
}
