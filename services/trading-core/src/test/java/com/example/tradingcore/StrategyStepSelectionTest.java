package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.trade.indicator.EmaValue;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Отбор применимого шага — исполнимая форма
 * docs/spec/strategy-walkthrough.json (величина {@code stepEligible}) и
 * реакции на устаревание данных
 * (docs/spec/market-data-freshness.json).
 *
 * <p><b>Предмет — направление ошибки.</b> Шаг, не выпадающий из набора по
 * исчерпанному пакету, перевыставляет нижнюю ступень лестницы бессрочно, и
 * верхняя не исполняется ни разу. Шаг, выпадающий по строке ПЕРВОГО
 * действия, не доигрывает двухдейственную ступень: снятие OCO не
 * исполняется никогда. Оба состояния собираются здесь настоящими строками
 * исполнения.
 */
class StrategyStepSelectionTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 21L;
    private static final Long DECLARATION_ID = 11L;
    private static final String INDICATOR_KEY = "ema_fast";

    private final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);
    private final StrategyStepSelector selector =
            new StrategyStepSelector(new StrategyConditionEvaluator(), pairStates);

    /** Пакет не начат и условие истинно — шаг применим. */
    @Test
    void freshStepWithTrueConditionIsSelected() {
        StrategyStep step = step(1L, action(101L), action(102L));

        StepSelection selection = selector.selectTrancheStep(context(step), tranche());

        assertThat(selection.hasStep()).isTrue();
        assertThat(selection.getStep().getId()).isEqualTo(1L);
    }

    /**
     * Пакет исполнен ЧАСТИЧНО — шаг остаётся применимым.
     *
     * <p>Выпади он по строке первого действия, двухдейственная ступень не
     * доигрывалась бы: снятие прежней защиты не исполнялось бы никогда.
     */
    @Test
    void partiallyExecutedPackageKeepsTheStepEligible() {
        StrategyStep step = step(1L, action(101L), action(102L));
        DealContext context = context(step, row(101L, DealActionStateStatus.COMPLETED));

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isTrue();
    }

    /** Пакет исчерпан — шаг выпадает из набора до переоткрытия эпизода. */
    @Test
    void exhaustedPackageDropsTheStep() {
        StrategyStep step = step(1L, action(101L), action(102L));
        DealContext context = context(step,
                row(101L, DealActionStateStatus.COMPLETED),
                row(102L, DealActionStateStatus.COMPLETED));

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isFalse();
    }

    /**
     * Строки ПРОШЛОГО эпизода в отбор не попадают: переоткрытие идёт тем
     * же траншем, и без номера эпизода строки неотличимы.
     */
    @Test
    void rowsOfAPreviousEpisodeDoNotCount() {
        StrategyStep step = step(1L, action(101L));
        DealActionState old = row(101L, DealActionStateStatus.COMPLETED);
        old.setTrancheEpisodeSeq(0);

        assertThat(selector.selectTrancheStep(context(step, old), tranche()).hasStep()).isTrue();
    }

    /**
     * Пустой пакет шага применённости не даёт: шаг полного выхода несёт
     * только условие, и без охраны он не был бы допустим ни разу.
     */
    @Test
    void emptyPackageNeverCountsAsApplied() {
        StrategyStep step = step(1L);

        assertThat(selector.selectTrancheStep(context(step), tranche()).hasStep()).isTrue();
    }

    /** Отказавшая строка при стоящей ступени пары замораживает повтор. */
    @Test
    void failedRowUnderStandingRungGatesTheRetry() {
        StrategyStep step = step(1L, action(101L));
        DealContext context = context(step, row(101L, DealActionStateStatus.FAILED));
        when(pairStates.getRequiredByPair(any(), any())).thenReturn(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isFalse();
    }

    /**
     * Снятие ступени возобновляет надобность: отказавшая строка сама по
     * себе шага не гасит, иначе исчерпанный бюджет выключал бы ступень до
     * конца эпизода молча.
     */
    @Test
    void failedRowWithoutARungDoesNotGateTheRetry() {
        StrategyStep step = step(1L, action(101L));
        DealContext context = context(step, row(101L, DealActionStateStatus.FAILED));
        when(pairStates.getRequiredByPair(any(), any())).thenReturn(pairState(Instrument.SafetyRung.ACTIVE));

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isTrue();
    }

    /**
     * Операнд шага недоступен, а его реакция — блокировка: шаг не
     * исполняется и эскалации не порождает.
     */
    @Test
    void blockedReactionSkipsTheStepSilently() {
        StrategyStep step = step(1L, action(101L));
        step.setCondition(indicatorCondition());
        step.setMarketDataExpiredSetting(new StrategyMarketDataExpiredSetting(
                MarketDataExpiredAction.BLOCK_STEP, MarketDataExpiredAction.GRACEFUL_CLOSE));
        DealContext context = context(step, emptyFeatures());

        StepSelection selection = selector.selectTrancheStep(context, tranche());

        assertThat(selection.hasStep()).isFalse();
        assertThat(selection.hasEscalation()).isFalse();
    }

    /**
     * Тот же шаг на ТОЙ ЖЕ пустоте, но при непокрытом живом риске, выбирает
     * ВТОРУЮ ветвь пары — управляемое сворачивание.
     *
     * <p>Пара примеров и есть проверка дискриминатора: состояние данных
     * одно, а реакция разная, и различает их живой риск транша с его
     * покрытием (docs/rules/market-data-freshness.md §«Оси дискриминатора
     * ветви»).
     */
    @Test
    void unprotectedBranchEscalatesOnTheSameEmptiness() {
        StrategyStep step = step(1L, action(101L));
        step.setCondition(indicatorCondition());
        step.setMarketDataExpiredSetting(new StrategyMarketDataExpiredSetting(
                MarketDataExpiredAction.BLOCK_STEP, MarketDataExpiredAction.GRACEFUL_CLOSE));
        DealTranche uncovered = tranche();
        uncovered.setEntryFilled(new BigDecimal("5"));
        DealContext context = context(step, emptyFeatures(), uncovered);

        StepSelection selection = selector.selectTrancheStep(context, uncovered);

        assertThat(selection.hasEscalation()).isTrue();
        assertThat(selection.getEscalation()).isEqualTo(MarketDataExpiredAction.GRACEFUL_CLOSE);
    }

    /** Свежий операнд шага гейт свежести не поднимает вовсе. */
    @Test
    void presentOperandPassesTheFreshnessGate() {
        StrategyStep step = step(1L, action(101L));
        step.setCondition(indicatorCondition());
        step.setMarketDataExpiredSetting(new StrategyMarketDataExpiredSetting(
                MarketDataExpiredAction.BLOCK_STEP, MarketDataExpiredAction.GRACEFUL_CLOSE));
        DealContext context = context(step, featuresWith(INDICATOR_KEY));

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isTrue();
    }

    /**
     * Условие, читающее только факты сделки, гейту свежести не подлежит:
     * поставленный на него, гейт останавливал бы выход ровно тогда, когда
     * данные пропали.
     */
    @Test
    void dealFactConditionIsNotGatedByFreshness() {
        StrategyStep step = step(1L, action(101L));
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.NO_OPEN_POSITION);
        step.setCondition(new StrategyCondition(new ArrayList<>(List.of(rule))));
        DealContext context = context(step, emptyFeatures());

        assertThat(selector.selectTrancheStep(context, tranche()).hasStep()).isTrue();
    }

    // --- сборка состояния -----------------------------------------------

    private StrategyCondition indicatorCondition() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey(INDICATOR_KEY);
        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setOperator(StrategyConditionOperator.GT);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return new StrategyCondition(new ArrayList<>(List.of(rule)));
    }

    private MarketFeatures emptyFeatures() {
        return MarketFeatures.builder().latestIndicators(Map.of()).structures(Map.of()).build();
    }

    private MarketFeatures featuresWith(String key) {
        EmaValue value = new EmaValue();
        value.setEma(new BigDecimal("1"));
        return MarketFeatures.builder()
                .latestIndicators(Map.of(key, value))
                .structures(Map.of())
                .build();
    }

    private StrategyStep step(Long id, StrategyAction... actions) {
        StrategyStep step = new StrategyStep();
        step.setId(id);
        step.setStepType(StrategyStepType.PROTECTION_ADJUSTMENT);
        step.setActions(new ArrayList<>(List.of(actions)));
        return step;
    }

    private StrategyAction action(Long id) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(id);
        action.setKey("a" + id);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        return action;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEpisodeSeq(1);
        tranche.setStrategyTrancheId(DECLARATION_ID);
        return tranche;
    }

    private DealActionState row(Long actionId, DealActionStateStatus status) {
        DealActionState state = new DealActionState();
        state.setId(actionId);
        state.setDealId(DEAL_ID);
        state.setActionKind(ActionKind.STRATEGY);
        state.setStrategyActionId(actionId);
        state.setDealTrancheId(TRANCHE_ID);
        state.setTrancheEpisodeSeq(1);
        state.setStatus(status);
        return state;
    }

    private AccountInstrumentState pairState(Instrument.SafetyRung rung) {
        AccountInstrumentState state = new AccountInstrumentState();
        state.setSafetyRung(rung);
        return state;
    }

    private DealContext context(StrategyStep step, DealActionState... rows) {
        return context(step, null, tranche(), rows);
    }

    private DealContext context(StrategyStep step, MarketFeatures features, DealActionState... rows) {
        return context(step, features, tranche(), rows);
    }

    private DealContext context(StrategyStep step, MarketFeatures features, DealTranche tranche,
                                DealActionState... rows) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(DECLARATION_ID);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(DealTranche.Status.MANAGING, new ArrayList<>(List.of(step)));
        declaration.setStepsByStatus(byStatus);
        StrategyDetail detail = new StrategyDetail();
        detail.setTranches(new ArrayList<>(List.of(declaration)));

        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setInstrumentId(3L);
        deal.setExchangeAccountId(2L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        return DealContext.builder()
                .deal(deal)
                .strategyDetail(detail)
                .marketFeatures(features)
                .actionStates(new ArrayList<>(List.of(rows)))
                .graphComplete(Boolean.TRUE)
                .build();
    }
}
