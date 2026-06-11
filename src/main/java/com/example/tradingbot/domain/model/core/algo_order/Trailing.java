package com.example.tradingbot.domain.model.core.algo_order;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trailing-механизм algo-order: callback в процентах/значении, цена
 * активации (null — активен сразу), текущее биржевое значение trailing.
 * Раздел модели AlgoOrder. См. docs/models/domain/core/AlgoOrder.md
 * (§Condition-модель).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Trailing {

    /** Callback в процентах (OKX callbackRatio). */
    private BigDecimal trailingPercents;

    /** Callback в абсолютном значении (OKX callbackSpread). */
    private BigDecimal trailingStepValue;

    /** Цена активации trailing (null — активен сразу). */
    private TriggerPrice activationPrice;

    /** Текущее биржевое значение trailing (OKX moveTriggerPx). */
    private BigDecimal externalPrice;
}
