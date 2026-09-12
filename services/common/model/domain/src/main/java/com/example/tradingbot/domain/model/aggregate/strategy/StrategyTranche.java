package com.example.tradingbot.domain.model.aggregate.strategy;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Объявление одного транша: что он делает в каждом статусе своей
 * статусной модели. Каркасный реляционный узел дерева (строка
 * strategy_tranches); шаги транша — плоские строки strategy_steps с
 * ключом группировки {@code tranche_status}.
 *
 * <p><b>Однотипные транши объявляются шаблоном, а не копиями:</b> из
 * одного объявления с {@code levelCount = N} материализуется N траншей,
 * различающихся уровнем — цена размещения входа сдвигается на
 * {@code level × levelStep}. Копий в дереве стратегии нет.
 *
 * <p>См. docs/models/domain/aggregate/Strategy.md (§StrategyTranche).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyTranche extends Auditable {

    /** Технический ID объявления. */
    private Long id;

    /** Ключ адресации внутри детали; уникален в её пределах. */
    private String key;

    /**
     * Сколько экземпляров материализовать: {@code 1} — один транш,
     * больше — сетка. Умолчания нет: пустое место мажорировалось бы
     * единицей, то есть в разрешающую сторону неравенства
     * {@code N_overlap × riskPerActionPercent ≤ strategySimultaneousRiskPerDealPercent}
     * (docs/rules/risk-policy.md).
     */
    private Integer levelCount;

    /**
     * Смещение размещения цены входа на один уровень. Обязателен при
     * {@code levelCount > 1}, запрещён иначе.
     */
    private BigDecimal levelStep;

    /**
     * Допустимо ли переоткрытие эпизода ЭТОГО транша. Операнд охраны
     * переоткрытия (docs/spec/deal-tranche-lifecycle.json
     * §reopenPermitted); пусто читается как «не допускает» — разрешение
     * объявляется явно, а не умолчанием.
     */
    private Boolean positionReopenAllowed;

    /** Шаги транша, сгруппированные по статусу транша. */
    private Map<DealTranche.Status, List<StrategyStep>> stepsByStatus;

    /**
     * Entry-шаги объявления: шаги PRECHECK-группы типов
     * {@code ENTRY}/{@code GRID_ENTRY} в порядке объявления; пусто —
     * entry-шагов нет.
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
     * Сколько экземпляров материализуется по этому объявлению. Пустой
     * {@code levelCount} до рантайма не доезжает — его отвергает
     * создание стратегии (docs/rules/strategy-validation.md); здесь
     * пустота читается одним экземпляром, чтобы материализатор не
     * ронял проход на состоянии, которого не бывает.
     */
    public Integer materializedCount() {
        return isNull(levelCount) || levelCount < 1 ? 1 : levelCount;
    }

    /**
     * Объявление является входным: несёт хотя бы один entry-шаг.
     * Торгуемая деталь обязана объявить ровно одно такое
     * (docs/rules/strategy-validation.md).
     */
    public Boolean isEntryDeclaration() {
        return isNotEmpty(entrySteps());
    }
}
