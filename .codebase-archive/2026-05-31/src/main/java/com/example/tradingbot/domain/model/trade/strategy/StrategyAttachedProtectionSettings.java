package com.example.tradingbot.domain.model.trade.strategy;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import lombok.Getter;
import lombok.Setter;

/**
 * Настройки attached-защиты для order-action.
 */
@Getter
@Setter
public class StrategyAttachedProtectionSettings {

    /**
     * Сейчас по домену это фактически ATTACHED_STOP_LOSS.
     */
    private AttachedAlgoOrder.Type attachedType;

    /**
     * Настройки стартового stop-loss.
     */
    private StopLossSettings stopLossSettings;
}
