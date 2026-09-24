package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DECLARATION_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.action;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.declaration;
import static com.example.tradingcore.unit.fsm.FsmFixture.detail;
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
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Отбор шага: first-match, применённость, гейт повтора — группа
 * {@code U23} документа `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/strategy-walkthrough.json, величина {@code stepEligible};
 * прозой — docs/rules/strategy-step-once-per-episode.md).
 *
 * <p><b>Базовая сборка.</b> Транш в сопровождении с объявлением, несущим
 * два шага на этот статус; интерпретатор условий подменён и отвечает
 * истиной; строк исполнения нет; ступени на паре «счёт, инструмент» нет.
 *
 * <p><b>Интерпретатор подменяется не потому, что ходит в базу</b> — он не
 * ходит, — а потому, что предмет группы есть ПОРЯДОК отбора: настоящий
 * интерпретатор потребовал бы собирать грамматику условия ради ответа
 * «да/нет», и кейс мерил бы соседний предмет.
 */
class StrategyStepSelectionOrderTest {

    private final StrategyConditionEvaluator conditionEvaluator = mock(StrategyConditionEvaluator.class);

    private final AccountInstrumentStateDataService pairStateDataService =
            mock(AccountInstrumentStateDataService.class);

    private final StrategyStepSelector selector =
            new StrategyStepSelector(conditionEvaluator, pairStateDataService);

    private final StrategyCondition firstCondition = plainCondition();

    private final StrategyCondition secondCondition = plainCondition();

    StrategyStepSelectionOrderTest() {
        when(conditionEvaluator.evaluate(any(), any())).thenReturn(Boolean.TRUE);
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.ACTIVE));
    }

    @Test
    @DisplayName("U23.1 — базовая сборка: отобран ПЕРВЫЙ шаг, второй не спрашивается")
    void u23_1_theFirstEligibleStepWins() {
        DealContext context = context();

        StepSelection selection = select(context);

        assertThat(selection.getStep().getId()).isEqualTo(1L);
        verify(conditionEvaluator, never()).evaluate(eq(secondCondition), any());
    }

    @Test
    @DisplayName("U23.2 — условие первого ложно, второго истинно: отобран второй")
    void u23_2_theSecondStepWinsWhenTheFirstConditionIsFalse() {
        when(conditionEvaluator.evaluate(eq(firstCondition), any())).thenReturn(Boolean.FALSE);

        assertThat(select(context()).getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U23.3 — условия обоих ложны: ни шага, ни реакции")
    void u23_3_bothFalseConditionsSelectNothing() {
        when(conditionEvaluator.evaluate(any(), any())).thenReturn(Boolean.FALSE);

        StepSelection selection = select(context());

        assertThat(selection.hasStep()).isFalse();
        assertThat(selection.hasEscalation()).isFalse();
    }

    @Test
    @DisplayName("U23.4 — шагов на этот статус нет: отбора нет")
    void u23_4_aStatusWithoutStepsSelectsNothing() {
        DealContext context = contextOf(declaration(DECLARATION_ID, Boolean.FALSE,
                DealTranche.Status.PRECHECK, firstStep()));

        assertThat(select(context).hasStep()).isFalse();
    }

    @Test
    @DisplayName("U23.5 — объявления у транша нет: пустое объявление — прямое следствие")
    void u23_5_anAbsentDeclarationSelectsNothing() {
        DealContext context = context();
        context.getDeal().getTranches().getFirst().setStrategyTrancheId(null);

        assertThat(select(context).hasStep()).isFalse();
    }

    @Test
    @DisplayName("U23.6 — объявление есть, раскладки шагов по статусам нет: исключения нет")
    void u23_6_aDeclarationWithoutStepsByStatusSelectsNothing() {
        DealContext context = contextOf(declaration(DECLARATION_ID, Boolean.FALSE));

        assertThat(select(context).hasStep()).isFalse();
    }

    @Test
    @DisplayName("U23.7 — два действия, исполнено одно: шаг применённым не считается")
    void u23_7_aPartiallyAppliedStepStaysEligible() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.8 — два действия, исполнены оба: шаг применён, отобран второй")
    void u23_8_aFullyAppliedStepIsSkipped() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED),
                strategyRow(12L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED));

        assertThat(select(context).getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U23.9 — второе действие пропущено: пропущенное пакет исчерпывает, отобран второй шаг")
    void u23_9_aSkippedRowCountsAsApplied() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED),
                strategyRow(12L, TRANCHE_ID, 1, DealActionStateStatus.SKIPPED));

        assertThat(select(context).getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U23.10 — второе действие отказало: отказавшее в счёт не идёт")
    void u23_10_aFailedRowDoesNotCountAsApplied() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.COMPLETED),
                strategyRow(12L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.11 — у первого шага ни одного действия: охрана пустого пакета стои́т первой")
    void u23_11_anEmptyActionPackageIsNeverApplied() {
        StrategyStep empty = step(1L, StrategyStepType.EXIT);
        empty.setCondition(firstCondition);
        DealContext context = contextOf(declaration(DECLARATION_ID, Boolean.FALSE,
                DealTranche.Status.MANAGING, empty, secondStep()));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.12 — строки принадлежат ПРОШЛОМУ эпизоду транша: в счёт не идут")
    void u23_12_rowsOfAPreviousEpisodeDoNotCount() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 0, DealActionStateStatus.COMPLETED),
                strategyRow(12L, TRANCHE_ID, 0, DealActionStateStatus.COMPLETED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.13 — строки принадлежат СОСЕДНЕМУ траншу: в счёт не идут")
    void u23_13_rowsOfANeighbourTrancheDoNotCount() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, SECOND_TRANCHE_ID, 1, DealActionStateStatus.COMPLETED),
                strategyRow(12L, SECOND_TRANCHE_ID, 1, DealActionStateStatus.COMPLETED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.14 — отказавшая строка, ступени на паре нет: гейт повтора не срабатывает")
    void u23_14_aFailedRowWithoutAStandingRungKeepsTheStepEligible() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.15 — отказавшая строка и стоящая ступень: первый шаг пропущен")
    void u23_15_aFailedRowUnderAStandingRungSkipsTheStep() {
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.TRADE_BLOCKED));
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U23.16 — ступень стои́т, отказавших строк нет: гейт повтора не срабатывает")
    void u23_16_aStandingRungWithoutFailedRowsKeepsTheStepEligible() {
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.TRADE_BLOCKED));

        assertThat(select(context()).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.17 — отказавшая строка у ВТОРОГО шага: пропущен второй, отобран первый")
    void u23_17_theRetryGateIsPerStep() {
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.TRADE_BLOCKED));
        StrategyStep second = secondStep();
        second.setActions(List.of(action(21L)));
        DealContext context = contextOf(declaration(DECLARATION_ID, Boolean.FALSE,
                DealTranche.Status.MANAGING, firstStep(), second),
                strategyRow(21L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U23.18 — ступень поднята ЧУЖОЙ надобностью: связь «строка → причина» не хранится")
    void u23_18_aForeignRungGatesTheRetryJustTheSame() {
        when(pairStateDataService.getRequiredByPair(any(), any()))
                .thenReturn(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("U23.19 — биржевая ступень при чистой паре: биржевой радиус в область не входит")
    void u23_19_theExchangeRungIsOutsideTheRetryGateScope() {
        DealContext context = contextWithRows(twoActionFirstStep(),
                strategyRow(11L, TRANCHE_ID, 1, DealActionStateStatus.FAILED));

        assertThat(select(context).getStep().getId()).isEqualTo(1L);
    }

    // --- сборка ------------------------------------------------------------

    private StepSelection select(DealContext context) {
        return selector.selectTrancheStep(context, context.getDeal().getTranches().getFirst());
    }

    private DealContext context() {
        return contextOf(declaration(DECLARATION_ID, Boolean.FALSE, DealTranche.Status.MANAGING,
                firstStep(), secondStep()));
    }

    private DealContext contextWithRows(StrategyStep first, DealActionState... rows) {
        return contextOf(declaration(DECLARATION_ID, Boolean.FALSE, DealTranche.Status.MANAGING,
                first, secondStep()), rows);
    }

    private DealContext contextOf(StrategyTranche declaration, DealActionState... rows) {
        return contextBuilder(deal(Deal.Status.ACTIVE, tranche(TRANCHE_ID, DealTranche.Status.MANAGING)))
                .strategyDetail(detail(declaration))
                .actionStates(List.of(rows))
                .build();
    }

    /** Первый шаг объявленного порядка: одно действие. */
    private StrategyStep firstStep() {
        StrategyStep first = step(1L, StrategyStepType.PROTECTION_ADJUSTMENT, action(11L));
        first.setCondition(firstCondition);
        return first;
    }

    /** Первый шаг с пакетом из двух действий. */
    private StrategyStep twoActionFirstStep() {
        StrategyStep first = step(1L, StrategyStepType.PROTECTION_ADJUSTMENT, action(11L), action(12L));
        first.setCondition(firstCondition);
        return first;
    }

    /** Второй шаг объявленного порядка. */
    private StrategyStep secondStep() {
        StrategyStep second = step(2L, StrategyStepType.PROTECTION_ADJUSTMENT, action(21L));
        second.setCondition(secondCondition);
        return second;
    }
}
