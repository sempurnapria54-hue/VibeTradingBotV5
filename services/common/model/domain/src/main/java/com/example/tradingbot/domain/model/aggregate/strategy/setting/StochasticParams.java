package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры стохастического осциллятора. Оконный индикатор: warmup по
 * умолчанию равен наибольшему окну расчёта.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StochasticParams extends IndicatorParams {

    /** Период линии %K (баров). */
    private Integer kPeriod;

    /** Период линии %D (баров). */
    private Integer dPeriod;

    /** Период дополнительного сглаживания (баров). */
    private Integer smoothPeriod;
}
