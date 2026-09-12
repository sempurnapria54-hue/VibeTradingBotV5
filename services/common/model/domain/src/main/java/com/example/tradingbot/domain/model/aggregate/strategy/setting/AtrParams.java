package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора ATR (Average True Range). Рекурсивный
 * индикатор: warmup по умолчанию кратен периоду.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AtrParams extends IndicatorParams {

    /** Период усреднения true range (баров). */
    private Integer period;
}
