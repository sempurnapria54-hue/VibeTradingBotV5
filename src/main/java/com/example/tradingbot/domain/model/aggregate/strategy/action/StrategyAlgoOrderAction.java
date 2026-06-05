package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ожидаемое действие над standalone algo-order (SL/TP/OCO/trailing/
 * partial). На первом этапе positionReducingOnly выводится из
 * назначения (защитные/закрывающие — не открывают позицию) и полем не
 * хранится. Для OCO_FULL: SL — из stopLossSettings, TP — из
 * triggerProfitPercents + triggerPriceType. См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyAlgoOrderAction).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyAlgoOrderAction extends Auditable implements StrategyAction {

    /** Технический ID действия. */
    private Long id;

    /** Стабильный ключ действия в рамках StrategyDetail. */
    private String key;

    /** Ключ target-действия для AMEND/CANCEL; для CREATE null. */
    private String targetActionKey;

    /** Тип действия: CREATE/AMEND/CANCEL. */
    private StrategyActionType actionType;

    /** Тип условия срабатывания algo-order. */
    private AlgoOrder.ConditionType conditionType;

    /** Уровень действия внутри стратегии. */
    private Integer level;

    /** Настройки расчёта stop-loss (для SL/OCO-ноги). */
    private StopLossSettings stopLossSettings;

    /** Настройки трейлинга (для TRAILING_*). */
    private TrailingSettings trailingSettings;

    /** Доля закрытия позиции, % (в runtime → fraction 0..1). */
    private BigDecimal closeFractionPercents;

    /** Порог прибыли trigger'а TP, % (для TP/OCO-ноги). */
    private BigDecimal triggerProfitPercents;

    /** Тип trigger-цены биржи. */
    private AlgoOrder.TriggerPriceType triggerPriceType;
}
