package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.DEAL_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.StepSelection;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.fsm.deal.DealActiveHandler;
import com.example.tradingcore.domain.safety.HoldSignal;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик активной сделки со своими тремя коллабораторами
 * (`.claude/tests/cases/trading-core-fsm.md` §«Чем достаются выходы»).
 *
 * <p><b>Подменяется ровно то, у чего есть ввод-вывод либо свой
 * предмет:</b> каскад траншей — соседний уровень машины, отбор шага —
 * соседняя группа, исполнитель системных действий — строка исполнения в
 * базе. Гейт терминала настоящий: живой риск и сверка экспозиции
 * считаются по полям графа.
 *
 * <p><b>Умолчания стоя́т в конструкторе:</b> каскад молчит, агрегатного
 * шага нет, живая строка звена уже есть (исполнитель отдаёт пустоту).
 * Кейс переназначает ровно то, что составляет его вход.
 */
final class DealActiveHarness {

    private final TrancheCascade cascade = mock(TrancheCascade.class);

    private final StrategyStepSelector stepSelector = mock(StrategyStepSelector.class);

    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);

    private final DealActiveHandler handler =
            new DealActiveHandler(cascade, stepSelector, new DealTerminalGate(), systemActionExecutor);

    DealActiveHarness() {
        when(cascade.run(any())).thenReturn(silentCascade());
        when(stepSelector.selectDealStep(any())).thenReturn(StepSelection.none());
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());
    }

    /** Свод, который отдаёт каскад. */
    void givenCascade(TrancheCascadeResult result) {
        when(cascade.run(any())).thenReturn(result);
    }

    /** Исход отбора агрегатного шага. */
    void givenDealStep(StepSelection selection) {
        when(stepSelector.selectDealStep(any())).thenReturn(selection);
    }

    /** Звено названного типа отдаёт команду: живой строки у него нет. */
    void givenSystemCommand(SystemActionType type, ServiceCommandType commandType) {
        when(systemActionExecutor.next(eq(type), any(), isNull()))
                .thenReturn(Optional.of(command(commandType)));
    }

    /** Один проход обработчика. */
    DealTransition handle(DealContext dealContext) {
        return handler.handle(dealContext);
    }

    /** Каскад на этом проходе не запускался. */
    void verifyCascadeNotRun() {
        verify(cascade, never()).run(any());
    }

    /** Агрегатный шаг на этом проходе не спрашивался. */
    void verifyDealStepNotSelected() {
        verify(stepSelector, never()).selectDealStep(any());
    }

    // --- своды каскада ------------------------------------------------------

    /** Каскад молчит: ни команд, ни рёбер, ни просьб. */
    static TrancheCascadeResult silentCascade() {
        return new TrancheCascadeResult(List.of(), List.of(), Boolean.FALSE, null, null);
    }

    /** Каскад выдал названные команды. */
    static TrancheCascadeResult cascadeWithCommands(ServiceCommandType... types) {
        return new TrancheCascadeResult(List.of(types).stream().map(DealActiveHarness::command).toList(),
                List.of(), Boolean.FALSE, null, null);
    }

    /** Каскад одобрил названные рёбра траншей. */
    static TrancheCascadeResult cascadeWithEdges(List<TrancheEdge> edges) {
        return new TrancheCascadeResult(List.of(), edges, Boolean.FALSE, null, null);
    }

    /** Каскад просит увести сделку ошибочной тропой. */
    static TrancheCascadeResult cascadeAskingError(List<TrancheEdge> edges, HoldSignal rung) {
        return new TrancheCascadeResult(List.of(), edges, Boolean.TRUE, null, rung);
    }

    /** Каскад просит управляемое сворачивание с названной причиной. */
    static TrancheCascadeResult cascadeAskingShutdown(Deal.ShutdownReason reason, HoldSignal rung) {
        return new TrancheCascadeResult(List.of(), List.of(), Boolean.FALSE, reason, rung);
    }

    /** Каскад просит только ступень: работой это не считается. */
    static TrancheCascadeResult cascadeAskingRung(HoldSignal rung) {
        return new TrancheCascadeResult(List.of(), List.of(), Boolean.FALSE, null, rung);
    }

    /** Каскад выдал команду и попросил ступень. */
    static TrancheCascadeResult cascadeWithCommandAndRung(ServiceCommandType type, HoldSignal rung) {
        return new TrancheCascadeResult(List.of(command(type)), List.of(), Boolean.FALSE, null, rung);
    }

    /** Каскад просит и ошибочную тропу, и управляемое сворачивание. */
    static TrancheCascadeResult cascadeAskingBoth(Deal.ShutdownReason reason) {
        return new TrancheCascadeResult(List.of(), List.of(), Boolean.TRUE, reason, null);
    }

    /** Команда названного типа, адресованная сделке базовой сборки. */
    static ServiceCommand command(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(DEAL_ID).build();
    }
}
