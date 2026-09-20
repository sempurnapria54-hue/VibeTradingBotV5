package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.DealActiveHarness.command;
import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.deal.ErrorHandler;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ошибочное состояние — группа {@code U11} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/components/ErrorHandler.md).
 *
 * <p><b>Базовая сборка.</b> Сделка {@code ERROR}; исполнитель звеньев
 * подменён и отдаёт объявленную входом команду.
 *
 * <p><b>{@code U11.6} читается по коллабораторам, а не по прогону:</b>
 * «FSM траншей не запускается» есть утверждение об отсутствии — и
 * наблюдается оно перечнем полей класса, а не одним проходом, на котором
 * вызова не случилось.
 */
class DealErrorStateTest {

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final ErrorHandler handler =
            new ErrorHandler(new DealTransitionGate(new DealTerminalGate()), systemActionExecutor);

    DealErrorStateTest() {
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("U11.1 — живой риск не доказанно отсутствует: команда звена добычи фактов")
    void u11_1_liveRiskRequestsTheContextHarvest() {
        DealContext context = riskBearingContext();
        givenCommand(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION,
                ServiceCommandType.REFRESH_POSITION_COMMAND);

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition)).containsExactly(ServiceCommandType.REFRESH_POSITION_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(context.getDeal().getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U11.2 — живая строка добычи уже есть: переход пустой")
    void u11_2_aLiveHarvestRowLeavesThePassEmpty() {
        DealTransition transition = handler.handle(riskBearingContext());

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U11.3 — риск отсутствует, причина пуста: выставлено аварийное закрытие")
    void u11_3_provenAbsenceWritesTheEmergencyCloseReason() {
        DealContext context = cleanContext();
        givenCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);

        DealTransition transition = handler.handle(context);

        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.EMERGENCY_CLOSE);
        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U11.4 — причина закрытия уже стои́т: прежнее значение не переписывается")
    void u11_4_anExistingCloseReasonIsNotOverwritten() {
        DealContext context = cleanContext();
        context.getDeal().setCloseReason(Deal.CloseReason.RISK_CONTROL);
        givenCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);

        DealTransition transition = handler.handle(context);

        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);
    }

    @Test
    @DisplayName("U11.5 — сделка уже терминальна: звено не эмитится повторно")
    void u11_5_aTerminalDealEmitsNothing() {
        DealContext context = contextBuilder(
                deal(Deal.Status.EMERGENCY_CLOSED, tranche(TRANCHE_ID, DealTranche.Status.CLOSED))).build();
        givenCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);

        DealTransition transition = handler.handle(context);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
    }

    @Test
    @DisplayName("U11.6 — любой вход: коллаборатора-каскада у обработчика нет вовсе")
    void u11_6_theErrorHandlerCarriesNoTrancheMachinery() {
        List<Class<?>> fieldTypes = List.of(ErrorHandler.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .toList();

        assertThat(fieldTypes).doesNotContain(TrancheCascade.class, DealTrancheStateMachine.class);
    }

    @Test
    @DisplayName("U11.7 — один транш не терминален: терминальность траншей эту тропу не гейтит")
    void u11_7_aLiveTrancheRowDoesNotHoldTheEmergencyFinalization() {
        DealContext context = contextBuilder(deal(Deal.Status.ERROR,
                tranche(TRANCHE_ID, DealTranche.Status.CLOSED),
                tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING))).build();
        givenCommand(SystemActionType.FINALIZE_DEAL_ERROR_ACTION,
                ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);

        DealTransition transition = handler.handle(context);

        assertThat(commandTypes(transition))
                .containsExactly(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    private void givenCommand(SystemActionType type, ServiceCommandType commandType) {
        when(systemActionExecutor.next(eq(type), any(), isNull()))
                .thenReturn(Optional.of(command(commandType)));
    }

    /** Сделка в ошибочном состоянии с живой входной ногой транша. */
    private DealContext riskBearingContext() {
        DealTranche managed = tranche(TRANCHE_ID, DealTranche.Status.MANAGING);
        managed.getOrders().add(liveEntryLeg(30L, TRANCHE_ID));
        return contextBuilder(deal(Deal.Status.ERROR, managed)).build();
    }

    /** Сделка в ошибочном состоянии без живого риска. */
    private DealContext cleanContext() {
        return contextBuilder(deal(Deal.Status.ERROR, tranche(TRANCHE_ID, DealTranche.Status.CLOSED))).build();
    }

    private List<ServiceCommandType> commandTypes(DealTransition transition) {
        return transition.getCommands().stream().map(ServiceCommand::getType).toList();
    }
}
