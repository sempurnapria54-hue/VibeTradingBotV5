package com.example.tradingbot.domain.model.aggregate.strategy.action;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Правило расчёта цены размещения ордера: база (структурный уровень /
 * цена входа / рыночная цена) + геометрическое смещение. Для
 * market-like входа placement = null. Хранится JSONB-полем на строке
 * strategy_order_action; ссылка на настройку структуры — «мягкая», по
 * ключу (резолвит приложение). Якорь процент-смещения («N% относительно
 * чего») — открытый вопрос STRAT-Q4; интерпретация — деталь
 * PriceCalculator. См. docs/models/domain/aggregate/Strategy.md
 * (§StrategyPricePlacement).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyPricePlacement {

    /** База расчёта цены. */
    private StrategyPriceBaseType baseType;

    /** Источник рыночной цены (только для baseType = MARKET_PRICE). */
    private StrategyPriceSource priceSource;

    /**
     * Ключ настройки структуры рынка (для baseType
     * RANGE_LOW/RANGE_HIGH/SWING_LOW/SWING_HIGH/SUPPORT/RESISTANCE).
     */
    private String structureKey;

    /** Сторона смещения от базы. */
    private StrategyPriceOffsetSide offsetSide;

    /** Величина смещения, % (якорь процента — STRAT-Q4). */
    private BigDecimal percents;
}
