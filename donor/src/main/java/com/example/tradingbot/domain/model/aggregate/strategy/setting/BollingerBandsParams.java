package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры индикатора полос Боллинджера. Оконный индикатор:
 * warmup по умолчанию равен периоду. Волатильность отдельной сущностью
 * не моделируется — через ATR / Bollinger bandwidth.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BollingerBandsParams extends IndicatorParams {

    /** Период скользящей средней (баров). */
    private Integer period;

    /** Множитель стандартного отклонения для границ полос. */
    private BigDecimal deviationMultiplier;
}
