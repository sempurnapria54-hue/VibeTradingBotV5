package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора RSI (Relative Strength Index). Рекурсивный
 * индикатор: warmup по умолчанию кратен периоду.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RsiParams extends IndicatorParams {

    /** Период расчёта RSI (баров). */
    private Integer period;
}
