package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.ActionRiskGate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Разрешающее множество реакций и план действия — группа {@code U26}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/ActionRiskGate.md §«Почему узел отдельный»).
 *
 * <p><b>Резолвер здесь ПОДМЕНЁН, и довод у подмены свой:</b> предмет
 * группы — разрешающее множество, а не выбор реакции. Настоящий резолвер
 * потребовал бы собирать состояние сделки ради ответа «блокирует или
 * нет», и клетка мерила бы соседнюю группу.
 *
 * <p><b>Валидатор подменён тоже</b> — по тому же доводу: клетки U26.8 и
 * U26.9 спрашивают, КАКАЯ точка входа зовётся, а не что она возвращает.
 */
class ActionRiskGateTest {

    private final RiskValidator validator = mock(RiskValidator.class);

    private final RiskBlockResolver resolver = mock(RiskBlockResolver.class);

    private final ActionRiskGate gate = new ActionRiskGate(validator, resolver);

    @Test
    @DisplayName("U26.1 — реакция «продолжить»: плана нет, записи в лог нет")
    void u26_1_theContinueReactionYieldsNoPlanAndNoRecord() {
        try (RiskLogCapture log = RiskLogCapture.attach(ActionRiskGate.class)) {
            assertThat(gateWith(RiskBlockAction.Type.CONTINUE)).isEmpty();
            assertThat(log.messages()).isEmpty();
        }
    }

    @Test
    @DisplayName("U26.2 — реакция «продолжить с предупреждением»: запись предупреждения — весь эффект")
    void u26_2_theWarningReactionOnlyLeavesARecord() {
        try (RiskLogCapture log = RiskLogCapture.attach(ActionRiskGate.class)) {
            assertThat(gateWith(RiskBlockAction.Type.CONTINUE_WITH_WARNING)).isEmpty();
            assertThat(log.messages())
                    .as("дома у этого эффекта в корпусе нет — находка R-7")
                    .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("risk comment");
        }
    }

    @Test
    @DisplayName("U26.3 — реакция «пропустить действие»: план с реакцией, команды в нём нет")
    void u26_3_theSkipReactionYieldsABlockingPlan() {
        assertBlockingPlan(RiskBlockAction.Type.SKIP_ACTION);
    }

    @Test
    @DisplayName("U26.4 — реакция «ошибочная тропа сделки»: то же")
    void u26_4_theDealErrorReactionYieldsABlockingPlan() {
        assertBlockingPlan(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U26.5 — реакция «закрыть кандидатную сделку»: причина закрытия доносится реакцией")
    void u26_5_theCloseCandidateReactionCarriesItsCloseReasonInTheReaction() {
        RiskBlockAction reaction = RiskBlockAction.builder()
                .type(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL)
                .closeReason(Deal.CloseReason.RISK_CONTROL)
                .comment("risk comment")
                .build();
        when(resolver.resolve(any(), any(), any())).thenReturn(reaction);

        Optional<ActionPlan> plan = gate.gate(entryAction(), workingContext(), trancheOf());

        assertThat(plan).isPresent();
        assertThat(plan.orElseThrow().getBlocked().getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
        assertThat(plan.orElseThrow().hasCommand()).isFalse();
    }

    @Test
    @DisplayName("U26.6 — реакция «запросить добычу»: то же")
    void u26_6_theRefreshRequestYieldsABlockingPlan() {
        assertBlockingPlan(RiskBlockAction.Type.REQUEST_REFRESH);
    }

    @Test
    @DisplayName("U26.7 — транш не передан: стадия уезжает в карту пустой, исключения нет")
    void u26_7_anAbsentTrancheSendsAnEmptyStageToTheMap() {
        when(resolver.resolve(any(), any(), any())).thenReturn(reactionOf(RiskBlockAction.Type.CONTINUE));

        assertThat(gate.gate(entryAction(), workingContext(), null)).isEmpty();
        verify(resolver).resolve(any(DealContext.class), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    @DisplayName("U26.8 — ветвь рассчитанного действия: зовётся первая точка входа")
    void u26_8_theCalculatedActionBranchCallsTheFirstEntryPoint() {
        when(resolver.resolve(any(), any(), any())).thenReturn(reactionOf(RiskBlockAction.Type.CONTINUE));

        gate.gate(entryAction(), workingContext(), trancheOf());

        verify(validator).validate(any(), any());
        verify(validator, never()).validateProtectionRemoval(any(), any(), any());
    }

    @Test
    @DisplayName("U26.9 — ветвь снятия защиты: зовётся точка входа снятия")
    void u26_9_theProtectionRemovalBranchCallsItsOwnEntryPoint() {
        when(resolver.resolve(any(), any(), any())).thenReturn(reactionOf(RiskBlockAction.Type.CONTINUE));

        gate.gateProtectionRemoval(workingContext(), trancheOf(), 70L);

        verify(validator).validateProtectionRemoval(any(), any(), any());
        verify(validator, never()).validate(any(), any());
    }

    @Test
    @DisplayName("U26.10 — любая ветвь: статусов узел не пишет и команд не создаёт")
    void u26_10_theGateWritesNoStatusAndCreatesNoCommand() {
        DealTranche tranche = trancheOf();
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche));
        when(resolver.resolve(any(), any(), any()))
                .thenReturn(reactionOf(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR));

        Optional<ActionPlan> plan = gate.gate(entryAction(), RiskFixture.context(deal), tranche);

        assertThat(plan.orElseThrow().hasCommand()).isFalse();
        assertThat(plan.orElseThrow().hasCalculationError()).isFalse();
        assertThat(deal.getStatus()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.MANAGING);
    }

    private Optional<ActionPlan> gateWith(RiskBlockAction.Type type) {
        when(resolver.resolve(any(), any(), any())).thenReturn(reactionOf(type));
        return gate.gate(entryAction(), workingContext(), trancheOf());
    }

    private void assertBlockingPlan(RiskBlockAction.Type type) {
        Optional<ActionPlan> plan = gateWith(type);

        assertThat(plan).isPresent();
        assertThat(plan.orElseThrow().getBlocked().getType()).isEqualTo(type);
        assertThat(plan.orElseThrow().hasCommand()).isFalse();
        assertThat(plan.orElseThrow().hasCalculationError()).isFalse();
    }

    private static RiskBlockAction reactionOf(RiskBlockAction.Type type) {
        return RiskBlockAction.builder().type(type).comment("risk comment").build();
    }

    private static DealTranche trancheOf() {
        return tranche(TRANCHE_ID, List.of(), List.of());
    }
}
