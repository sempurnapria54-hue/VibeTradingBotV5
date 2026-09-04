package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора MACD (Moving Average Convergence Divergence):
 * линия MACD, сигнальная линия и гистограмма. См.
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class MacdValue extends IndicatorValue {

    /** Линия MACD (fastEMA − slowEMA). */
    private BigDecimal macdLine;

    /** Сигнальная линия (EMA от линии MACD). */
    private BigDecimal signalLine;

    /** Гистограмма (macdLine − signalLine). */
    private BigDecimal histogram;

    @Override
    public Type getType() {
        return Type.MACD;
    }
}
