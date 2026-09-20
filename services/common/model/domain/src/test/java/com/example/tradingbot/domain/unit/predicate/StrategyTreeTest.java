package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Дерево стратегии: административные рёбра и резолв внутри детали —
 * группа `U15` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/lifecycles/Strategy.md §«Допустимые переходы»,
 * docs/rules/strategy-validation.md,
 * docs/models/domain/aggregate/Strategy.md).
 *
 * <p><b>Базовая сборка:</b> определение стратегии со статусом и
 * коллекцией деталей; деталь — с типом фазы, коллекцией объявлений и
 * узкой агрегатной поверхностью шагов. Все связи — настоящими объектами.
 */
class StrategyTreeTest {

    @Test
    @DisplayName("U15.1 — созданный статус, перевод в активный")
    void u15_1_createdMayBecomeActive() {
        assertThat(strategy(Strategy.Status.CREATED).canTransitionTo(Strategy.Status.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("U15.2 — активный, перевод в неактивный, и обратно")
    void u15_2_activeAndInactiveAreMutuallyReachable() {
        assertThat(strategy(Strategy.Status.ACTIVE).canTransitionTo(Strategy.Status.INACTIVE)).isTrue();
        assertThat(strategy(Strategy.Status.INACTIVE).canTransitionTo(Strategy.Status.ACTIVE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Strategy.Status.class, names = "DELETED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U15.3 — любой нетерминальный, перевод в удалённый")
    void u15_3_anyNonTerminalMayBeDeleted(Strategy.Status status) {
        assertThat(strategy(status).canTransitionTo(Strategy.Status.DELETED)).isTrue();
    }

    /** Терминал необратим, и ответ один на все четыре цели. */
    @ParameterizedTest
    @EnumSource(Strategy.Status.class)
    @DisplayName("U15.4 — удалённый, перевод в любой")
    void u15_4_theTerminalIsIrreversible(Strategy.Status target) {
        assertThat(strategy(Strategy.Status.DELETED).canTransitionTo(target)).isFalse();
    }

    /** Вернуть в исходный нельзя. */
    @ParameterizedTest
    @EnumSource(Strategy.Status.class)
    @DisplayName("U15.5 — любой статус, перевод в созданный")
    void u15_5_nothingReturnsToCreated(Strategy.Status from) {
        assertThat(strategy(from).canTransitionTo(Strategy.Status.CREATED)).isFalse();
    }

    @Test
    @DisplayName("U15.6 — созданный, перевод в неактивный")
    void u15_6_createdMayNotBecomeInactive() {
        assertThat(strategy(Strategy.Status.CREATED).canTransitionTo(Strategy.Status.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("U15.7 — активный статус")
    void u15_7_activeIsActiveAndNotDeleted() {
        Strategy subject = strategy(Strategy.Status.ACTIVE);

        assertThat(subject.isActive()).isTrue();
        assertThat(subject.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("U15.8 — деталь под запрошенный тип фазы есть")
    void u15_8_theDetailForThePhaseIsReturned() {
        StrategyDetail bull = detailForPhase(MarketPhase.Type.BULL_TREND);
        Strategy subject = strategyWithDetails(bull, detailForPhase(MarketPhase.Type.RANGE));

        assertThat(subject.detailForPhase(MarketPhase.Type.BULL_TREND)).containsSame(bull);
    }

    /** Первая попавшаяся не отдаётся. */
    @Test
    @DisplayName("U15.9 — детали под тип фазы нет")
    void u15_9_anAbsentPhaseGivesEmptiness() {
        Strategy subject = strategyWithDetails(detailForPhase(MarketPhase.Type.RANGE));

        assertThat(subject.detailForPhase(MarketPhase.Type.BULL_TREND)).isEmpty();
    }

    @Test
    @DisplayName("U15.10 — коллекция деталей пуста")
    void u15_10_anEmptyDetailListGivesEmptiness() {
        assertThat(strategy(Strategy.Status.ACTIVE).detailForPhase(MarketPhase.Type.RANGE)).isEmpty();
    }

    /** Вопрос задаётся до выбора детали. */
    @Test
    @DisplayName("U15.11 — хоть одна деталь читает цену")
    void u15_11_priceReadingIsAskedAtTheStrategyLevel() {
        Strategy subject = strategyWithDetails(detailForPhase(MarketPhase.Type.RANGE),
                detailReadingPrice());

        assertThat(subject.readsPrice()).isTrue();
    }

    /** Round-trip к площадке на проходе не ставится. */
    @Test
    @DisplayName("U15.12 — ни одна деталь цены не читает")
    void u15_12_noDetailReadsPrice() {
        assertThat(strategyWithDetails(detailForPhase(MarketPhase.Type.RANGE)).readsPrice()).isFalse();
    }

    @Test
    @DisplayName("U15.13 — у детали ровно одно объявление с входными шагами")
    void u15_13_theSingleEntryDeclarationIsNamed() {
        StrategyStep entry = step(StrategyStepType.ENTRY);
        StrategyTranche declaration = declaration(1L, "d1", Map.of(DealTranche.Status.PRECHECK, List.of(entry)));
        StrategyDetail subject = detailWith(List.of(declaration, declaration(2L, "d2", Map.of())));

        assertThat(subject.entryTranche()).isSameAs(declaration);
        assertThat(subject.entrySteps()).containsExactly(entry);
    }

    /** Пустой перечень, а не отказ. */
    @Test
    @DisplayName("U15.14 — входного объявления у детали нет")
    void u15_14_noEntryDeclarationGivesEmptyLists() {
        StrategyDetail subject = detailWith(List.of(declaration(1L, "d1", Map.of())));

        assertThat(subject.entryTranche()).isNull();
        assertThat(subject.entrySteps()).isEmpty();
    }

    /** Охрана второго рубежа: такая конфигурация до рантайма не доезжает. */
    @Test
    @DisplayName("U15.15 — два объявления с входными шагами")
    void u15_15_theFirstEntryDeclarationWins() {
        StrategyTranche first = declaration(1L, "d1",
                Map.of(DealTranche.Status.PRECHECK, List.of(step(StrategyStepType.ENTRY))));
        StrategyTranche second = declaration(2L, "d2",
                Map.of(DealTranche.Status.PRECHECK, List.of(step(StrategyStepType.GRID_ENTRY))));
        StrategyDetail subject = detailWith(List.of(first, second));

        assertThat(subject.entryTranche()).isSameAs(first);
    }

    @Test
    @DisplayName("U15.16 — объявление по известному ключу строки")
    void u15_16_theDeclarationIsResolvedByRowKey() {
        StrategyTranche declaration = declaration(7L, "d7", Map.of());
        StrategyDetail subject = detailWith(List.of(declaration));

        assertThat(subject.declarationById(7L)).isSameAs(declaration);
    }

    /** Так у восстановленного транша — факт его тропы, а не недогруженное дерево. */
    @Test
    @DisplayName("U15.17 — ключ объявления пуст")
    void u15_17_anAbsentDeclarationKeyGivesEmptiness() {
        StrategyDetail subject = detailWith(List.of(declaration(7L, "d7", Map.of())));

        assertThat(subject.declarationById(null)).isNull();
    }

    @Test
    @DisplayName("U15.18 — действие по стабильному ключу, объявленное траншевым шагом")
    void u15_18_theStableKeyResolvesInsideADeclaration() {
        StrategyPositionAction action = action(1L, "exit-1");
        StrategyDetail subject = detailWith(List.of(declaration(1L, "d1",
                Map.of(DealTranche.Status.MANAGING, List.of(stepWithActions(action))))));

        assertThat(subject.actionByKey("exit-1")).isSameAs(action);
    }

    /** Область поиска — оба уровня объявления. */
    @Test
    @DisplayName("U15.19 — действие по тому же ключу, объявленное агрегатным шагом")
    void u15_19_theStableKeyResolvesAtTheDealLevelToo() {
        StrategyPositionAction action = action(1L, "exit-1");
        StrategyDetail subject = detailWith(List.of());
        subject.setStepsByStatus(Map.of(Deal.Status.ACTIVE, List.of(stepWithActions(action))));

        assertThat(subject.actionByKey("exit-1")).isSameAs(action);
    }

    /** Обхода дерева не происходит. */
    @Test
    @DisplayName("U15.20 — ключ пуст либо состоит из пробелов")
    void u15_20_aBlankStableKeyGivesEmptiness() {
        StrategyDetail subject = detailWithAction(action(1L, "exit-1"));

        assertThat(subject.actionByKey(null)).isNull();
        assertThat(subject.actionByKey("   ")).isNull();
    }

    /** Ключ строки и стабильный ключ — разные оси резолва. */
    @Test
    @DisplayName("U15.21 — действие по ключу строки")
    void u15_21_theRowKeyIsASecondAxis() {
        StrategyPositionAction action = action(3L, "exit-1");
        StrategyDetail subject = detailWithAction(action);

        assertThat(subject.actionById(3L)).isSameAs(action);
    }

    @Test
    @DisplayName("U15.22 — ключ строки, которого в дереве нет")
    void u15_22_anUnknownRowKeyGivesEmptiness() {
        StrategyDetail subject = detailWithAction(action(3L, "exit-1"));

        assertThat(subject.actionById(4L)).isNull();
        assertThat(subject.actionById(null)).isNull();
    }

    /** Отбор по тождеству, не по ключу. */
    @Test
    @DisplayName("U15.23 — шаг по поданному объекту действия")
    void u15_23_theStepIsFoundByObjectIdentity() {
        StrategyPositionAction action = action(3L, "exit-1");
        StrategyStep owner = stepWithActions(action);
        StrategyDetail subject = detailWith(List.of(declaration(1L, "d1",
                Map.of(DealTranche.Status.MANAGING, List.of(owner, stepWithActions(action(4L, "exit-2")))))));

        assertThat(subject.stepOf(action)).isSameAs(owner);
    }

    /** Тождество объекта строже равенства ключа. */
    @Test
    @DisplayName("U15.24 — объект действия, равный по ключу, но не тот же объект")
    void u15_24_anEqualButDistinctActionIsNotFound() {
        StrategyDetail subject = detailWithAction(action(3L, "exit-1"));

        assertThat(subject.stepOf(action(3L, "exit-1"))).isNull();
        assertThat(subject.stepOf(null)).isNull();
    }

    /**
     * Обход идёт сперва по объявлениям, затем по раскладке уровня сделки.
     * Порядок ВНУТРИ уровня задан обходом раскладки по статусам, и
     * носителя, объявляющего его, нет ни одного (звено `Z5`) — ожидания о
     * нём здесь поэтому нет.
     */
    @Test
    @DisplayName("U15.25 — перечень всех шагов детали")
    void u15_25_declarationStepsComeBeforeDealLevelSteps() {
        StrategyStep ofDeclaration = step(StrategyStepType.MAIN_PROTECTION);
        StrategyStep ofDeal = step(StrategyStepType.FAIL_SAFE);
        StrategyDetail subject = detailWith(List.of(declaration(1L, "d1",
                Map.of(DealTranche.Status.MANAGING, List.of(ofDeclaration)))));
        subject.setStepsByStatus(Map.of(Deal.Status.ACTIVE, List.of(ofDeal)));

        assertThat(subject.allSteps()).containsExactly(ofDeclaration, ofDeal);
    }

    @Test
    @DisplayName("U15.26 — политика входа задана и допускает фазу")
    void u15_26_anAllowingPolicyOpensTheEntry() {
        assertThat(detailWithPolicy(PhaseEntryPolicy.FOLLOW_PHASE)
                .allowsEntryFor(MarketPhase.Type.BULL_TREND)).isTrue();
    }

    /** «Не торгуем» допустима в любой фазе — и всё равно вход не открывает. */
    @Test
    @DisplayName("U15.27 — политика входа — «не торгуем»")
    void u15_27_theNoTradePolicyNeverOpensTheEntry() {
        assertThat(detailWithPolicy(PhaseEntryPolicy.NO_TRADE)
                .allowsEntryFor(MarketPhase.Type.BULL_TREND)).isFalse();
        assertThat(PhaseEntryPolicy.NO_TRADE.isAllowedFor(MarketPhase.Type.BULL_TREND)).isTrue();
    }

    @Test
    @DisplayName("U15.28 — политика входа не задана")
    void u15_28_anAbsentPolicyDoesNotOpenTheEntry() {
        assertThat(detailWithPolicy(null).allowsEntryFor(MarketPhase.Type.BULL_TREND)).isFalse();
    }

    private static Strategy strategy(Strategy.Status status) {
        Strategy strategy = new Strategy();
        strategy.setStatus(status);
        return strategy;
    }

    private static Strategy strategyWithDetails(StrategyDetail... details) {
        Strategy strategy = strategy(Strategy.Status.ACTIVE);
        strategy.setDetails(List.of(details));
        return strategy;
    }

    private static StrategyDetail detailForPhase(MarketPhase.Type phaseType) {
        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(phaseType);
        return detail;
    }

    private static StrategyDetail detailReadingPrice() {
        StrategyStep priceStep = step(StrategyStepType.ENTRY);
        priceStep.setCondition(new StrategyCondition(List.of(priceRule())));
        StrategyDetail detail = detailForPhase(MarketPhase.Type.BULL_TREND);
        detail.setTranches(List.of(declaration(1L, "d1",
                Map.of(DealTranche.Status.PRECHECK, List.of(priceStep)))));
        return detail;
    }

    private static StrategyConditionRule priceRule() {
        StrategyConditionOperand price = new StrategyConditionOperand();
        price.setSourceType(StrategyConditionSourceType.PRICE);
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setLeftOperand(price);
        return rule;
    }

    private static StrategyDetail detailWith(List<StrategyTranche> declarations) {
        StrategyDetail detail = detailForPhase(MarketPhase.Type.BULL_TREND);
        detail.setTranches(declarations);
        return detail;
    }

    private static StrategyDetail detailWithAction(StrategyAction action) {
        return detailWith(List.of(declaration(1L, "d1",
                Map.of(DealTranche.Status.MANAGING, List.of(stepWithActions(action))))));
    }

    private static StrategyDetail detailWithPolicy(PhaseEntryPolicy policy) {
        StrategyDetail detail = detailForPhase(MarketPhase.Type.BULL_TREND);
        detail.setPhaseEntryPolicy(policy);
        return detail;
    }

    private static StrategyTranche declaration(Long id, String key,
                                               Map<DealTranche.Status, List<StrategyStep>> stepsByStatus) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(id);
        declaration.setKey(key);
        declaration.setStepsByStatus(stepsByStatus);
        return declaration;
    }

    private static StrategyStep step(StrategyStepType stepType) {
        StrategyStep step = new StrategyStep();
        step.setStepType(stepType);
        return step;
    }

    private static StrategyStep stepWithActions(StrategyAction... actions) {
        StrategyStep step = step(StrategyStepType.EXIT);
        step.setActions(List.of(actions));
        return step;
    }

    private static StrategyPositionAction action(Long id, String key) {
        StrategyPositionAction action = new StrategyPositionAction();
        action.setId(id);
        action.setKey(key);
        action.setActionType(StrategyActionType.EXIT_ACTION);
        return action;
    }
}
