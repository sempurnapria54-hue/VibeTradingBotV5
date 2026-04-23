package com.example.tradingbot.domain.model.strategy;

import com.example.tradingbot.domain.model.algo_order.TriggerPriceType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Настройки расчёта stop-loss.
 */
@Getter
@Setter
public class StopLossSettings {

    /**
     * ENTRY_PRICE_PERCENT / ATR_PERCENT / MARKET_STRUCTURE_BUFFER_PERCENT
     */
    private String calculationType;

    /**
     * Универсальное процентное расстояние.
     */
    private BigDecimal distancePercents;

    /**
     * LAST / INDEX / MARK.
     */
    private TriggerPriceType triggerPriceType;
}
