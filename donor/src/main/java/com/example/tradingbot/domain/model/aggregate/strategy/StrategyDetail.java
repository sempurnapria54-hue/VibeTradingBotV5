package com.example.tradingbot.domain.model.aggregate.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
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
 * реляционный узел дерева (строка strategy_detail).
 *
 * <p><b>Уровней объявления два.</b> Поведение транша объявляют строки
 * {@link StrategyTranche}; на самой детали живёт только УЗКАЯ агрегатная
 * поверхность — шаги уровня сделки ({@code EXIT}, {@code FAIL_SAFE}),
 * сгруппированные статусом агрегата. Шаги обоих уровней — плоские строки
 * strategy_step; уровень читается по тому, на кого строка ссылается.
 *
 * <p>Индикаторы/структуры, нужные детали, объявлены на уровне стратегии
 * (strategy-scope) и адресуются по {@code key} из условий и листьев
 * действий. Ровно одна detail на один MarketPhase.Type (инвариант). См.
 * docs/models/domain/aggregate/Strategy.md (§StrategyDetail).
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

    /** High-level ориентир risk/reward. */
    private BigDecimal targetRiskRewardRatio;

    /**
     * Объявленные транши: что заводится и как ведётся каждый вход.
     * Торгуемая деталь обязана объявить хотя бы один — деталь без
     * траншей не имеет входа и торговать не может.
     */
    private List<StrategyTranche> tranches;

    /**
     * Шаги уровня СДЕЛКИ, сгруппированные по статусу агрегата.
     * Поверхность узкая — только {@code EXIT} и {@code FAIL_SAFE}:
     * выход из сделки есть утверждение обо всех траншах сразу, и
     * размноженный по N объявлениям он был бы совпадением N деклараций,
     * а не обеспеченным фактом. Всё остальное объявляется на транше.
     */
    private Map<Deal.Status, List<StrategyStep>> stepsByStatus;

    /**
     * Шаги уровня сделки для текущего статуса агрегата (упорядочены =
     * приоритет); пусто — агрегатных шагов у детали на этом статусе нет.
     */
    public List<StrategyStep> dealLevelSteps(Deal.Status status) {
        if (isNull(stepsByStatus)) {
            return List.of();
        }
        List<StrategyStep> steps = stepsByStatus.get(status);
        return isEmpty(steps) ? List.of() : steps;
    }

    /**
     * Единственное входное объявление детали — то, чьи PRECHECK-шаги
     * несут вход. Их у торгуемой детали ровно одно
     * (docs/rules/strategy-validation.md); пусто — детали входа нет.
     */
    public StrategyTranche entryTranche() {
        return emptyIfNull(tranches).stream()
                .filter(tranche -> isTrue(tranche.isEntryDeclaration()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Объявление по его идентификатору — резолв «транш сделки → его
     * объявление». Пусто, если объявления нет: так у восстановленного
     * транша, и это факт его тропы, а не недогруженное дерево.
     */
    public StrategyTranche declarationById(Long strategyTrancheId) {
        if (isNull(strategyTrancheId)) {
            return null;
        }
        return emptyIfNull(tranches).stream()
                .filter(tranche -> Objects.equals(strategyTrancheId, tranche.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Entry-шаги детали — шаги входного объявления; пусто, если входного
     * объявления нет.
     */
    public List<StrategyStep> entrySteps() {
        StrategyTranche entry = entryTranche();
        return isNull(entry) ? List.of() : entry.entrySteps();
    }

    /**
     * Действие детали по стабильному ключу — резолв цели, объявленной
     * действием {@code REPLACE_ACTION}/{@code CANCEL_ACTION}
     * ({@code targetActionKey}). Ключ уникален в рамках детали, поэтому
     * совпадение одно; ключа нет — {@code null}.
     *
     * <p>Область поиска — оба уровня объявления: шаги траншей и шаги
     * узкой агрегатной поверхности. Сузить её до одного уровня значило
     * бы сделать цель нерезолвимой ровно там, где она объявлена
     * соседним уровнем.
     */
    public StrategyAction actionByKey(String key) {
        if (isBlank(key)) {
            return null;
        }
        return allSteps().stream()
                .flatMap(step -> emptyIfNull(step.getActions()).stream())
                .filter(action -> Objects.equals(key, action.getKey()))
                .findFirst()
                .orElse(null);
    }

    /** Все шаги детали — траншевые и агрегатные, в порядке объявления. */
    public List<StrategyStep> allSteps() {
        List<StrategyStep> steps = emptyIfNull(tranches).stream()
                .map(StrategyTranche::getStepsByStatus)
                .filter(Objects::nonNull)
                .flatMap(byStatus -> byStatus.values().stream())
                .flatMap(list -> emptyIfNull(list).stream())
                .collect(Collectors.toList());
        if (nonNull(stepsByStatus)) {
            stepsByStatus.values().forEach(list -> steps.addAll(emptyIfNull(list)));
        }
        return steps;
    }

    /** Разрешён ли вход в фазе: политика задана, не NO_TRADE и допускает фазу. */
    public Boolean allowsEntryFor(MarketPhase.Type phase) {
        return nonNull(phaseEntryPolicy)
                && isFalse(PhaseEntryPolicy.NO_TRADE.equals(phaseEntryPolicy))
                && isTrue(phaseEntryPolicy.isAllowedFor(phase));
    }
}
