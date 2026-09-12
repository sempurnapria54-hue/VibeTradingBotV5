package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение Kaufman efficiency ratio: скаляр ∈ [0,1]
 * (|чистый ход| / Σ|побарных ходов| по окну). ER→1 — тренд, ER→0 —
 * шум/боковик; нормирован по определению. Единый каталожный источник ER,
 * потребляемый условиями (фаза/вход) и MarketStructureResolver. См.
 * docs/models/domain/other/IndicatorValue.md,
 * docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class EfficiencyRatioValue extends IndicatorValue {

    /** Значение efficiency ratio (0..1). */
    private BigDecimal efficiencyRatio;

    @Override
    public Type getType() {
        return Type.EFFICIENCY_RATIO;
    }
}
