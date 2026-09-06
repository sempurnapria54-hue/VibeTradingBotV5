package com.example.tradingbot.domain.model.aggregate.strategy;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /**
     * Защитные создающие действия шага — набор одной защитной лестницы.
     * Замещающие сюда не входят: лестницу образуют ступени, которые шаг
     * СТАВИТ, а замещение адресует уже стоящую
     * (docs/rules/live-risk-protection.md §«Размер ступени лестницы»).
     */
    public List<StrategyAlgoOrderAction> protectionLadderSteps() {
        return emptyIfNull(actions).stream()
                .filter(StrategyAlgoOrderAction.class::isInstance)
                .map(StrategyAlgoOrderAction.class::cast)
                .filter(action -> isTrue(action.isProtective()))
                .filter(action -> StrategyActionType.CREATE_ACTION.equals(action.getActionType()))
                .collect(Collectors.toList());
    }

    /**
     * Последняя ступень защитного набора — та, что получает остаток
     * экспозиции после округления предыдущих вниз. Селектор объявлен домом
     * правила: защитное создающее действие шага с максимальным
     * {@code id} (docs/rules/live-risk-protection.md). Пусто — защитных
     * создающих действий у шага нет.
     */
    public StrategyAlgoOrderAction lastProtectionLadderStep() {
        return protectionLadderSteps().stream()
                .filter(action -> nonNull(action.getId()))
                .max(Comparator.comparing(StrategyAlgoOrderAction::getId))
                .orElse(null);
    }
}
