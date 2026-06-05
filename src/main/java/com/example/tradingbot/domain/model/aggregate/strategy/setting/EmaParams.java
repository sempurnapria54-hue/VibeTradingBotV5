package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора EMA (экспоненциальная скользящая средняя).
 * Рекурсивный индикатор: warmup по умолчанию кратен периоду.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmaParams extends IndicatorParams {

    /** Период сглаживания (баров). */
    private Integer period;
}
