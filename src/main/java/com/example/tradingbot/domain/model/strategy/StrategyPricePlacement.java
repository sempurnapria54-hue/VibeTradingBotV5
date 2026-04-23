package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.algo_order.TriggerPriceType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Параметры позиционирования цены действия стратегии.
 */
@Getter
@Setter
public class StrategyPricePlacement {

    /**
     * От какой базы считаем цену.
     */
    private StrategyPriceBaseType baseType;

    /**
     * Если baseType = MARKET_PRICE,
     * то указываем, какую именно рыночную цену брать: LAST / INDEX / MARK.
     * <p>
     * Для RANGE_LOW / RANGE_HIGH / ENTRY_PRICE = null.
     */
    private TriggerPriceType marketPriceType;

    /**
     * Куда смещаемся относительно базы.
     */
    private StrategyPriceOffsetSide offsetSide;

    /**
     * Процент смещения от базы.
     * <p>
     * Пример:
     * 10 = смещение на 10% от выбранной базы/диапазона.
     */
    private BigDecimal percents;
}
