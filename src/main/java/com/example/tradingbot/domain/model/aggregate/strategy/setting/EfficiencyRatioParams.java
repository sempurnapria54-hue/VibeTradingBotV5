package com.example.tradingbot.domain.model.aggregate.strategy.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Параметры Kaufman efficiency ratio (EFFICIENCY_RATIO). ER — оконный
 * индикатор: warmup по умолчанию = period. Каталожный измеритель
 * тренд/шум (fork A). См.
 * docs/decisions/efficiency-ratio-as-catalog-indicator.md,
 * docs/models/domain/aggregate/Strategy.md (§IndicatorParams).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EfficiencyRatioParams extends IndicatorParams {

    /** Окно расчёта efficiency ratio (баров). */
    private Integer period;
}
