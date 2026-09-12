package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора EMA (экспоненциальная скользящая средняя). См.
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class EmaValue extends IndicatorValue {

    /** Значение EMA. */
    private BigDecimal ema;

    @Override
    public Type getType() {
        return Type.EMA;
    }
}
