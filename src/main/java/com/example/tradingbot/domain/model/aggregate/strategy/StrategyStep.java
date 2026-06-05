package com.example.tradingbot.domain.model.aggregate.strategy;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Шаг стратегии: одно общее условие применимости + пакет действий,
 * выполняемый целиком, если условие истинно. Каркасный реляционный
 * узел дерева (строка strategy_step); условие и политика устаревания —
 * JSONB-поля на его строке. Принадлежит StrategyDetail через ключ
 * Deal.Status и порядок в списке (stepsByStatus). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyStep).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyStep extends Auditable {

    /** Технический ID шага. */
    private Long id;

    /** Тип шага. */
    private StrategyStepType stepType;

    /** Общее условие применимости пакета действий. */
    private StrategyCondition condition;

    /** Пакет действий; выполняется целиком при истинном условии. */
    private List<StrategyAction> actions;

    /** Политика на устаревание данных, нужных этому шагу (обязательна). */
    private StrategyMarketDataExpiredSetting marketDataExpiredSetting;
}
