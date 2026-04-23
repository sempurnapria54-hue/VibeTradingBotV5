package com.example.tradingbot.domain.model.trade.strategy;

import com.example.tradingbot.domain.model.core.algo_order.ConditionType;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPriceType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Standalone algo-order.
 */
@Getter
@Setter
public class StrategyAlgoOrderAction implements StrategyAction {

    /**
     * CREATE / AMEND / CANCEL / CLOSE_FULL / CLOSE_PARTIAL
     * <p>
     * Для algo-ордера в большинстве случаев:
     * CREATE / AMEND / CANCEL.
     */
    private StrategyActionType actionType;

    /**
     * Реальный доменный тип algo condition.
     */
    private ConditionType conditionType;

    /**
     * Уровень действия.
     * <p>
     * Примеры:
     * - TP1 / TP2 / TP3
     * - несколько защит в одном step
     */
    private Integer level;

    /**
     * Настройки stop-loss.
     * <p>
     * Используется для:
     * - STOP_LOSS
     * - OCO_FULL
     * - PARTIAL_STOP_LOSS
     */
    private StopLossSettings stopLossSettings;

    /**
     * Настройки trailing.
     * <p>
     * Используется для TRAILING_PERCENTS.
     */
    private TrailingSettings trailingSettings;

    /**
     * Доля закрываемой позиции в процентах.
     * <p>
     * Пример:
     * 25 = закрыть 25% позиции.
     * <p>
     * В runtime это потом конвертируется в fraction 0..1.
     */
    private BigDecimal closeFractionPercents;

    /**
     * При каком профите срабатывает действие.
     * <p>
     * Примеры:
     * - TAKE_PROFIT
     * - PARTIAL_TAKE_PROFIT
     * - OCO_FULL (если нужен TP-компонент)
     */
    private BigDecimal triggerProfitPercents;

    /**
     * MARK / LAST / INDEX.
     */
    private TriggerPriceType triggerPriceType;
}
