package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора MACD (Moving Average Convergence Divergence).
 * Warmup по умолчанию выводится от старшего периода.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MacdParams extends IndicatorParams {

    /** Период быстрой EMA (баров). */
    private Integer fastPeriod;

    /** Период медленной EMA (баров). */
    private Integer slowPeriod;

    /** Период сигнальной линии (баров). */
    private Integer signalPeriod;
}
