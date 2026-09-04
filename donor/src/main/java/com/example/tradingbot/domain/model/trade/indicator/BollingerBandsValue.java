package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора Bollinger Bands: три полосы, ширина и %B. См.
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class BollingerBandsValue extends IndicatorValue {

    /** Верхняя полоса (middle + k·σ). */
    private BigDecimal upperBand;

    /** Средняя полоса (SMA периода). */
    private BigDecimal middleBand;

    /** Нижняя полоса (middle − k·σ). */
    private BigDecimal lowerBand;

    /** Ширина полос ((upper − lower) / middle) — нормированная волатильность. */
    private BigDecimal bandwidth;

    /** Положение цены в полосах ((price − lower) / (upper − lower)). */
    private BigDecimal percentB;

    @Override
    public Type getType() {
        return Type.BOLLINGER_BANDS;
    }
}
