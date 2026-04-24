package com.example.tradingbot.domain.model.trade.strategy;

import com.example.tradingbot.domain.model.core.order.Order;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Обычный ордер.
 * <p>
 * Attached protection встроена внутрь order-action.
 */
@Getter
@Setter
public class StrategyOrderAction implements StrategyAction {

    private Long id;

    /**
     * CREATE / AMEND / CANCEL / CLOSE_FULL / CLOSE_PARTIAL
     * <p>
     * Для обычного ордера в большинстве случаев:
     * CREATE / AMEND / CANCEL.
     */
    private StrategyActionType actionType;

    /**
     * Реальный доменный тип order.
     * <p>
     * Сейчас это:
     * ENTRY
     * ENTRY_ATTACHED_STOP_LOSS
     */
    private Order.Type orderType;

    /**
     * Нормализованное направление стратегии.
     * <p>
     * LONG -> resolver маппит в buy для entry order
     * SHORT -> resolver маппит в sell для entry order
     */
    private StrategyTradeDirection direction;

    /**
     * Доля расчётного объёма сценария.
     * <p>
     * Пример:
     * 25 = 25% от объёма, который уже посчитал PositionCalculator.
     */
    private BigDecimal allocationPercents;

    /**
     * Уровень действия.
     * <p>
     * Примеры:
     * - grid entry #1..#4
     * - серия входов в одном шаге
     */
    private Integer level;

    /**
     * Как вычислить цену ордера.
     * <p>
     * Для market-like входа может быть null.
     */
    private StrategyPricePlacement placement;

    /**
     * Attached-защита, если order создаётся вместе с attached SL.
     * <p>
     * Для ENTRY = null.
     * Для ENTRY_ATTACHED_STOP_LOSS = обязательно заполнена.
     */
    private StrategyAttachedProtectionSettings attachedProtection;
}
