package com.example.tradingbot.domain.model.aggregate.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Набор торговых правил для конкретной фазы рынка. Каркасный
 * реляционный узел дерева (строка strategy_detail); шаги — плоские строки
 * strategy_step (deal_status + step_index). Индикаторы/структуры, нужные
 * детали, объявлены на уровне стратегии (strategy-scope) и адресуются по
 * {@code key} из условий и листьев действий (трек D — настройки в
 * собственные строки). Ровно одна detail на один MarketPhase.Type
 * (инвариант). См. docs/models/domain/aggregate/Strategy.md (§StrategyDetail).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyDetail extends Auditable {

    /** Технический ID детали. */
    private Long id;

    /** Фаза рынка, в которой работает деталь. */
    private MarketPhase.Type marketPhaseType;

    /** Политика торговли в этой фазе. */
    private PhaseEntryPolicy phaseEntryPolicy;

    /** Риск на сделку, % от капитала (percent-risk модель сайзинга). */
    private BigDecimal riskPerTradePercent;

    /** High-level ориентир risk/reward. */
    private BigDecimal targetRiskRewardRatio;

    /** Шаги, сгруппированные по статусу сделки. */
    private Map<Deal.Status, List<StrategyStep>> stepsByStatus;

    /**
     * Entry-шаги детали: шаги PRECHECK-группы типов ENTRY/GRID_ENTRY в
     * порядке объявления; пусто — entry-шагов нет.
     */
    public List<StrategyStep> entrySteps() {
        if (isNull(stepsByStatus)) {
            return List.of();
        }
        List<StrategyStep> precheckSteps = stepsByStatus.get(Deal.Status.PRECHECK);
        if (isEmpty(precheckSteps)) {
            return List.of();
        }
        return precheckSteps.stream()
                .filter(step -> isTrue(step.isEntryStep()))
                .collect(Collectors.toList());
    }

    /** Разрешён ли вход в фазе: политика задана, не NO_TRADE и допускает фазу. */
    public Boolean allowsEntryFor(MarketPhase.Type phase) {
        return nonNull(phaseEntryPolicy)
                && isFalse(PhaseEntryPolicy.NO_TRADE.equals(phaseEntryPolicy))
                && isTrue(phaseEntryPolicy.isAllowedFor(phase));
    }
}
