package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Настройки attached-защиты входного ордера
 * (ENTRY_ATTACHED_STOP_LOSS). Хранятся JSONB-полем на строке
 * strategy_order_action. Attached protection не материализуется
 * автоматически в standalone AlgoOrder. См.
 * docs/models/domain/aggregate/Strategy.md
 * (§StrategyAttachedProtectionSettings).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StrategyAttachedProtectionSettings {

    /** Тип attached-защиты (фактически ATTACHED_STOP_LOSS). */
    private AttachedAlgoOrder.Type attachedType;

    /** Настройки расчёта attached stop-loss. */
    private StopLossSettings stopLossSettings;
}
