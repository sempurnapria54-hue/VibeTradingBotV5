package com.example.tradingbot.domain.model.aggregate.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Поактный потолок риска: сколько берёт ОДНО действие, % базы риска. */
    private BigDecimal riskPerActionPercent;

    /**
     * Множитель кумулятивного потолка: во сколько раз сделка за жизнь
     * вправе превысить поактный потолок. Ни снизу, ни сверху не
     * валидируется — выбор автора стратегии; при множителе меньше единицы
     * первая же нога не проходит, и отказ громкий (docs/rules/risk-policy.md).
     */
    private BigDecimal cumulativeRiskPerDealMultiplier;

    /**
     * Максимум ОДНОВРЕМЕННОГО риска сделки, % базы риска. Вкладывается в
     * конфигурационный максимум риск-аппетита: на создании проверяется
     * «стратегия ≤ конфигурация».
     */
    private BigDecimal strategySimultaneousRiskPerDealPercent;

    /**
     * Множитель катастрофического потолка сделки: во сколько раз
     * растягивается максимальный риск на сделку. Сверяется с
     * конфигурационным пределом на создании.
     */
    private BigDecimal strategyCatastrophicRiskPerDealMultiplier;

    /**
     * Допускает ли стратегия ПЕРЕОТКРЫТИЕ эпизода транша: сопровождение
     * снова отправляет вход тем же траншем. Операнд охраны переоткрытия
     * (docs/spec/deal-tranche-lifecycle.json §reopenPermitted); пусто
     * читается как «не допускает» — разрешение объявляется явно, а не
     * умолчанием.
     */
    private Boolean positionReopenAllowed;

    /** High-level ориентир risk/reward. */
    private BigDecimal targetRiskRewardRatio;

    /**
     * Шаги, сгруппированные по статусу ТРАНША: шаг принадлежит
     * траншу, кроме узкой агрегатной поверхности (её несёт тип шага,
     * не ключ группировки).
     */
    private Map<DealTranche.Status, List<StrategyStep>> stepsByStatus;

    /**
     * Entry-шаги детали: шаги PRECHECK-группы типов ENTRY/GRID_ENTRY в
     * порядке объявления; пусто — entry-шагов нет.
     */
    public List<StrategyStep> entrySteps() {
        if (isNull(stepsByStatus)) {
            return List.of();
        }
        List<StrategyStep> precheckSteps = stepsByStatus.get(DealTranche.Status.PRECHECK);
        if (isEmpty(precheckSteps)) {
            return List.of();
        }
        return precheckSteps.stream()
                .filter(step -> isTrue(step.isEntryStep()))
                .collect(Collectors.toList());
    }

    /**
     * Действие детали по стабильному ключу — резолв цели, объявленной
     * действием {@code REPLACE}/{@code CANCEL} ({@code targetActionKey}).
     * Ключ уникален в рамках детали, поэтому совпадение одно; ключа нет —
     * {@code null}.
     */
    public StrategyAction actionByKey(String key) {
        if (isBlank(key) || isNull(stepsByStatus)) {
            return null;
        }
        return stepsByStatus.values().stream()
                .flatMap(steps -> emptyIfNull(steps).stream())
                .flatMap(step -> emptyIfNull(step.getActions()).stream())
                .filter(action -> Objects.equals(key, action.getKey()))
                .findFirst()
                .orElse(null);
    }

    /** Разрешён ли вход в фазе: политика задана, не NO_TRADE и допускает фазу. */
    public Boolean allowsEntryFor(MarketPhase.Type phase) {
        return nonNull(phaseEntryPolicy)
                && isFalse(PhaseEntryPolicy.NO_TRADE.equals(phaseEntryPolicy))
                && isTrue(phaseEntryPolicy.isAllowedFor(phase));
    }
}
