package com.example.tradingbot.domain.model.trade.indicator;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Значение индикатора OBV (On-Balance Volume) — кумулятивная бегущая
 * сумма знакового объёма от старта расчёта. Абсолютный уровень
 * нестабилен (зависит от глубины истории и масштаба объёма): операнд OBV
 * ограничен относительными формами (docs/models/domain/other/IndicatorValue.md).
 * См. docs/models/domain/other/IndicatorValue.md (§Наследники).
 */
@Getter
@Setter
@NoArgsConstructor
public class ObvValue extends IndicatorValue {

    /** Значение OBV (кумулятивный знаковый объём). */
    private BigDecimal obv;

    @Override
    public Type getType() {
        return Type.OBV;
    }
}
