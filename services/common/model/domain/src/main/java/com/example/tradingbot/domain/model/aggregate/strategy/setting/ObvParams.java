package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора OBV (On-Balance Volume). Периода не имеет —
 * накопительный объёмный индикатор.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObvParams extends IndicatorParams {

    /** Включён ли расчёт OBV. */
    private Boolean enabled;
}
