package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Настройки расчёта stop-loss. Хранятся JSONB-полем на строке
 * действия-владельца (или внутри attachedProtection). Ссылки на
 * настройки рыночных данных — «мягкие», по ключу (резолвит приложение):
 * indicatorKey — для ATR_PERCENT, structureKey — для
 * MARKET_STRUCTURE_BUFFER_PERCENT. См.
 * docs/models/domain/aggregate/Strategy.md (§StopLossSettings).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StopLossSettings {

    /** Способ расчёта дистанции стопа. */
    private StopLossCalculationType calculationType;

    /** Дистанция, % по способу расчёта (для ATR_PERCENT: 150 = 1.5 ATR). */
    private BigDecimal distancePercents;

    /** Тип trigger-цены биржи (обязателен). */
    private AlgoOrder.TriggerPriceType triggerPriceType;

    /** Ключ настройки ATR-индикатора (только для ATR_PERCENT). */
    private String indicatorKey;

    /** Ключ настройки структуры рынка (только для MARKET_STRUCTURE_BUFFER_PERCENT). */
    private String structureKey;
}
