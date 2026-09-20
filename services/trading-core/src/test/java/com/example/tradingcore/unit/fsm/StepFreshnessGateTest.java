package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DECLARATION_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.action;
import static com.example.tradingcore.unit.fsm.FsmFixture.attachedProtection;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.declaration;
import static com.example.tradingcore.unit.fsm.FsmFixture.detail;
import static com.example.tradingcore.unit.fsm.FsmFixture.emptyFeatures;
import static com.example.tradingcore.unit.fsm.FsmFixture.expiredSetting;
import static com.example.tradingcore.unit.fsm.FsmFixture.exposed;
import static com.example.tradingcore.unit.fsm.FsmFixture.featuresWith;
import static com.example.tradingcore.unit.fsm.FsmFixture.filledEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.indicatorCondition;
import static com.example.tradingcore.unit.fsm.FsmFixture.pairState;
import static com.example.tradingcore.unit.fsm.FsmFixture.plainCondition;
import static com.example.tradingcore.unit.fsm.FsmFixture.step;
import static com.example.tradingcore.unit.fsm.FsmFixture.strategyRow;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Гейт свежести данных шага и его эскалация — группа {@code U24}
 * документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/market-data-freshness.json, величина {@code reaction};
 * правило — docs/rules/market-data-freshness.md).
 *
 * <p><b>Базовая сборка.</b> Та же, что у {@code U23}; первый шаг читает
 * рыночные данные, покрытия операндов у контекста нет, настройка реакции
 * объявлена: защищённая ветвь — «ждать», незащищённая — названа входом
 * кейса.
 *
 * <p><b>Ветвь дискриминатора базовая сборка ПИНИТ, и это не деталь:</b>
 * транш несёт непокрытую экспозицию, то есть ветвь незащищённая. На
 * тривиально покрытом транше резолв всегда давал бы защищённую половину
 * настройки, и ни одна из четырёх реакций входа не была бы достижима
 * вовсе.
 *
 * <p><b>Клетки {@code U24.15} и {@code U24.16} добраны этим заходом</b> —
 * они закрывают пробел {@code G1} документа: третья ось дискриминатора
 * ветви, разрешимость уровня защиты сделки, до сих пор не была предъявлена
 * ни одной стороной.
 */
class StepFreshnessGateTest {

    private static final String INDICATOR_KEY = "ema_fast";

    private final StrategyConditionEvaluator conditionEvaluator = mock(StrategyConditionEvaluator.class);

    private final AccountInstrumentStateDataService pairStateDataService =
            mock(AccountInstrumentStateDataService.class);

    private final StrategyStepSelector selector =
            new StrategyStepSelector(conditionEvaluator, pairStateDataService);

    private final StrategyCondition secondCondition = plainCondition();

    StepFreshnessGateTest() {
        when(conditionEvaluator.evaluate(any(), any())).thenReturn(Boolean.TRUE);
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.ACTIVE));
    }

    @Test
    @DisplayName("U24.1 — шаг рыночных данных не читает: гейт свежести не применяется")
    void u24_1_aStepThatReadsNoMarketDataSkipsTheGate() {
        StrategyStep first = dataStep(plainCondition(),
                expiredSetting(MarketDataExpiredAction.GRACEFUL_CLOSE, MarketDataExpiredAction.KILL_SWITCH));

        assertThat(selectTranche(contextOf(first, emptyFeatures())).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U24.2 — покрытие операндов контекста их накрывает: гейт не применяется")
    void u24_2_coveredOperandsSkipTheGate() {
        StrategyStep first = dataStep(indicatorCondition(INDICATOR_KEY),
                expiredSetting(MarketDataExpiredAction.GRACEFUL_CLOSE, MarketDataExpiredAction.KILL_SWITCH));

        assertThat(selectTranche(contextOf(first, featuresWith(INDICATOR_KEY))).getStep().getId())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("U24.3 — реакция «ждать»: шаг пропущен, отбор идёт к следующему")
    void u24_3_theWaitReactionSkipsTheStep() {
        StepSelection selection = selectTranche(contextOf(expiredStep(MarketDataExpiredAction.WAIT),
                emptyFeatures()));

        assertThat(selection.getStep().getId()).isEqualTo(2L);
        assertThat(selection.hasEscalation()).isFalse();
    }

    @Test
    @DisplayName("U24.4 — реакция «блокировать шаг»: то же")
    void u24_4_theBlockStepReactionSkipsTheStep() {
        StepSelection selection = selectTranche(contextOf(expiredStep(MarketDataExpiredAction.BLOCK_STEP),
                emptyFeatures()));

        assertThat(selection.getStep().getId()).isEqualTo(2L);
        assertThat(selection.hasEscalation()).isFalse();
    }

    @Test
    @DisplayName("U24.5 — реакция «управляемое сворачивание»: эскалация, второй шаг не спрашивается")
    void u24_5_theGracefulCloseReactionEscalates() {
        StepSelection selection =
                selectTranche(contextOf(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE), emptyFeatures()));

        assertThat(selection.getEscalation()).isEqualTo(MarketDataExpiredAction.GRACEFUL_CLOSE);
        assertThat(selection.hasStep()).isFalse();
        verify(conditionEvaluator, never()).evaluate(eq(secondCondition), any());
    }

    @Test
    @DisplayName("U24.6 — реакция «аварийное снятие риска»: та же форма с этим значением")
    void u24_6_theKillSwitchReactionEscalates() {
        StepSelection selection =
                selectTranche(contextOf(expiredStep(MarketDataExpiredAction.KILL_SWITCH), emptyFeatures()));

        assertThat(selection.getEscalation()).isEqualTo(MarketDataExpiredAction.KILL_SWITCH);
    }

    @Test
    @DisplayName("U24.7 — настройки реакции у шага нет вовсе: шаг блокирован с предупреждением")
    void u24_7_anAbsentSettingBlocksTheStepWithAWarning() {
        StrategyStep first = dataStep(indicatorCondition(INDICATOR_KEY), null);
        DealContext context = contextOf(first, emptyFeatures());

        StepSelection selection;
        List<String> messages;
        try (FsmLogCapture capture = FsmLogCapture.attach(StrategyStepSelector.class)) {
            selection = selectTranche(context);
            messages = capture.messages();
        }

        assertThat(selection.getStep().getId()).isEqualTo(2L);
        assertThat(messages).containsExactly(
                "Step declares no market-data-expired setting, step blocked stepId=1");
    }

    @Test
    @DisplayName("U24.8 — шаг потраншевый: живой риск и покрытие читаются у ТРАНША")
    void u24_8_aTrancheStepReadsItsOwnBranchOperands() {
        DealContext context = twoTrancheContext(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE));

        StepSelection selection = selectTranche(context);

        assertThat(selection.hasEscalation()).isFalse();
        assertThat(selection.getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U24.9 — шаг агрегатный: живой риск и покрытие читаются у СДЕЛКИ")
    void u24_9_aDealStepReadsTheAggregateBranchOperands() {
        DealContext context = twoTrancheContext(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE));
        StrategyDetail detail = context.getStrategyDetail();
        detail.setStepsByStatus(dealSteps(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE)));

        StepSelection selection = selector.selectDealStep(context);

        assertThat(selection.getEscalation()).isEqualTo(MarketDataExpiredAction.GRACEFUL_CLOSE);
    }

    @Test
    @DisplayName("U24.10 — гейт свежести истинен, а шаг уже применён: порядок охран наблюдаем")
    void u24_10_theAppliedGuardRunsBeforeTheFreshnessGate() {
        DealContext context = contextOf(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE),
                emptyFeatures(), strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED));

        StepSelection selection = selectTranche(context);

        assertThat(selection.hasEscalation()).isFalse();
        assertThat(selection.getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U24.11 — эскалация у второго шага при применённом первом: применённость её не глушит")
    void u24_11_anEscalationOnTheSecondStepStillTravels() {
        StrategyStep first = step(1L, StrategyStepType.PROTECTION_ADJUSTMENT, action(11L));
        first.setCondition(plainCondition());
        StrategyStep second = dataStep(indicatorCondition(INDICATOR_KEY),
                expiredSetting(MarketDataExpiredAction.GRACEFUL_CLOSE,
                        MarketDataExpiredAction.GRACEFUL_CLOSE));
        second.setId(2L);
        second.setActions(List.of(action(21L)));
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.MANAGING)))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.MANAGING, first, second)))
                .marketFeatures(emptyFeatures())
                .actionStates(List.of(strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED)))
                .build();

        assertThat(selectTranche(context).getEscalation())
                .isEqualTo(MarketDataExpiredAction.GRACEFUL_CLOSE);
    }

    @Test
    @DisplayName("U24.12 — отбор агрегатного шага, детали у сделки нет: ни отбора, ни эскалации")
    void u24_12_anAbsentDetailSelectsNothingAtTheDealLevel() {
        DealContext context = contextBuilder(deal(Deal.Status.ACTIVE,
                tranche(TRANCHE_ID, DealTranche.Status.MANAGING)))
                .strategyDetail(null)
                .build();

        StepSelection selection = selector.selectDealStep(context);

        assertThat(selection.hasStep()).isFalse();
        assertThat(selection.hasEscalation()).isFalse();
    }

    @Test
    @DisplayName("U24.13 — деталь есть, шагов на статус сделки нет: отбора нет")
    void u24_13_aDetailWithoutDealStepsSelectsNothing() {
        DealContext context = contextOf(expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE),
                emptyFeatures());

        assertThat(selector.selectDealStep(context).hasStep()).isFalse();
    }

    @Test
    @DisplayName("U24.14 — форма исхода отбора: три взаимоисключающих состояния")
    void u24_14_theSelectionFormHasThreeExclusiveStates() {
        StepSelection none = StepSelection.none();
        StepSelection picked = StepSelection.of(step(9L, StrategyStepType.EXIT));
        StepSelection escalated = StepSelection.escalated(MarketDataExpiredAction.KILL_SWITCH);

        assertThat(none.hasStep()).isFalse();
        assertThat(none.hasEscalation()).isFalse();
        assertThat(picked.hasStep()).isTrue();
        assertThat(picked.hasEscalation()).isFalse();
        assertThat(escalated.hasEscalation()).isTrue();
        assertThat(escalated.hasStep()).isFalse();
    }

    @Test
    @DisplayName("U24.15 — риск живой, покрытие есть, уровень НЕ резолвится: незащищённая ветвь")
    void u24_15_anUnresolvedStopLevelPicksTheUnprotectedBranch() {
        DealContext context = coveredContext(attachedProtection(60L, "5"));

        assertThat(context.getDeal().stopUnresolved()).isTrue();
        assertThat(selectTranche(context).getEscalation())
                .isEqualTo(MarketDataExpiredAction.GRACEFUL_CLOSE);
    }

    @Test
    @DisplayName("U24.16 — риск живой, покрытие есть, уровень резолвится: защищённая ветвь")
    void u24_16_aResolvedStopLevelPicksTheProtectedBranch() {
        DealContext context = coveredContext(attachedProtection(60L, "5", "2910"));

        assertThat(context.getDeal().stopUnresolved()).isFalse();
        StepSelection selection = selectTranche(context);

        assertThat(selection.hasEscalation()).isFalse();
        assertThat(selection.getStep().getId()).isEqualTo(2L);
    }

    // --- сборка ------------------------------------------------------------

    private StepSelection selectTranche(DealContext context) {
        return selector.selectTrancheStep(context, context.getDeal().getTranches().getFirst());
    }

    /** Шаг, читающий рыночные данные, с названными условием и настройкой реакции. */
    private StrategyStep dataStep(StrategyCondition condition, StrategyMarketDataExpiredSetting setting) {
        StrategyStep first = step(1L, StrategyStepType.PROTECTION_ADJUSTMENT, action(11L));
        first.setCondition(condition);
        first.setMarketDataExpiredSetting(setting);
        return first;
    }

    /** Шаг с недоступными данными: защищённая ветвь ждёт, незащищённая — по входу кейса. */
    private StrategyStep expiredStep(MarketDataExpiredAction whenUnprotected) {
        return dataStep(indicatorCondition(INDICATOR_KEY),
                expiredSetting(MarketDataExpiredAction.WAIT, whenUnprotected));
    }

    /** Второй шаг объявленного порядка: рыночных данных он не читает. */
    private StrategyStep secondStep() {
        StrategyStep second = step(2L, StrategyStepType.PROTECTION_ADJUSTMENT, action(21L));
        second.setCondition(secondCondition);
        return second;
    }

    /**
     * Сделка с двумя траншами: свой без риска и покрытый тривиально,
     * соседний — с непокрытой экспозицией. Ветвь транша выходит
     * защищённой, ветвь сделки — нет.
     */
    private DealContext twoTrancheContext(StrategyStep first) {
        DealTranche subject = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        DealTranche neighbour = exposed(tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING), "5");
        return contextBuilder(deal(Deal.Status.ACTIVE, subject, neighbour))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.MANAGING, first, secondStep())))
                .marketFeatures(emptyFeatures())
                .build();
    }

    /** Транш с экспозицией, покрытой названной встроенной защитой. */
    private DealContext coveredContext(AttachedAlgoOrder protection) {
        DealTranche subject = fills(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "5", "0");
        Order entry = filledEntryLeg(30L, TRANCHE_ID, "5");
        entry.getAttachedAlgoOrders().add(protection);
        subject.getOrders().add(entry);
        return contextBuilder(deal(Deal.Status.ACTIVE, subject))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.MANAGING,
                        expiredStep(MarketDataExpiredAction.GRACEFUL_CLOSE), secondStep())))
                .marketFeatures(emptyFeatures())
                .build();
    }

    private DealContext contextOf(StrategyStep first, MarketFeatures features, DealActionState... rows) {
        return contextBuilder(deal(Deal.Status.ACTIVE,
                exposed(tranche(TRANCHE_ID, DealTranche.Status.MANAGING), "5")))
                .strategyDetail(detail(declaration(DECLARATION_ID, Boolean.FALSE,
                        DealTranche.Status.MANAGING, first, secondStep())))
                .marketFeatures(features)
                .actionStates(List.of(rows))
                .build();
    }

    /** Раскладка агрегатных шагов детали по статусу сделки. */
    private Map<Deal.Status, List<StrategyStep>> dealSteps(StrategyStep first) {
        Map<Deal.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(Deal.Status.ACTIVE, List.of(first));
        return byStatus;
    }
}
