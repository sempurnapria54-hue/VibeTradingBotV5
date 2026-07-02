package com.example.tradingbot.domain.model.aggregate.strategy;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import java.util.List;
import java.util.Optional;
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

    /** Шаг — точка входа: ENTRY или GRID_ENTRY. */
    public Boolean isEntryStep() {
        return StrategyStepType.ENTRY.equals(stepType)
                || StrategyStepType.GRID_ENTRY.equals(stepType);
    }

    /** Первое order-действие шага; пусто — order-действий в шаге нет. */
    public Optional<StrategyOrderAction> firstOrderAction() {
        if (isEmpty(actions)) {
            return Optional.empty();
        }
        return actions.stream()
                .filter(StrategyOrderAction.class::isInstance)
                .map(StrategyOrderAction.class::cast)
                .findFirst();
    }
}
