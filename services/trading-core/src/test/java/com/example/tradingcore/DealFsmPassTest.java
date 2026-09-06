package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealHandler;
import com.example.tradingcore.domain.fsm.DealStateMachine;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import com.example.tradingcore.domain.fsm.StrategyStepSelector;
import com.example.tradingcore.domain.fsm.TrancheCascade;
import com.example.tradingcore.domain.fsm.TrancheCascadeResult;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingcore.domain.fsm.deal.DealActiveHandler;
import com.example.tradingcore.domain.fsm.deal.DealExitPendingHandler;
import com.example.tradingcore.domain.fsm.deal.ErrorHandler;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.fsm.StepSelection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Проход FSM сделки: раскладка обработчиков, каскад в транши и гейт
 * терминальных контрактов.
 *
 * <p><b>Что здесь проверяется по существу.</b> Обработчик ошибочного
 * состояния, прогоняющий FSM траншей, запускает торговую машину там, где
 * риск может быть живым, а торговля заблокирована. Обработчик
 * координированного выхода, НЕ прогоняющий её, останавливает выход:
 * транши не доходят до терминала, и сделка висит с живой позицией.
 * Закрытие нетто-экспозиции раньше снятия входных ног гоняется за
 * наливом. Все три состояния собраны настоящими полями графа.
 */
class DealFsmPassTest {

    private static final Long DEAL_ID = 7L;

    private final TrancheCascade cascade = mock(TrancheCascade.class);
    private final StrategyStepSelector stepSelector = mock(StrategyStepSelector.class);
    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);
    private final DealTerminalGate terminalGate = new DealTerminalGate();
    private final DealTransitionGate transitionGate = new DealTransitionGate(terminalGate);

    // --- машина ---------------------------------------------------------

    /** Терминальные статусы обработчиков не имеют: делать по ним нечего. */
    @Test
    void terminalDealHasNoHandler() {
        DealContext context = context(Deal.Status.CLOSED, tranche(DealTranche.Status.CLOSED));
        DealStateMachine machine = new DealStateMachine(
                List.of(fixedHandler(Deal.Status.ACTIVE, DealTransition.moveTo(Deal.Status.ERROR))),
                transitionGate);

        DealTransition transition = machine.run(context);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.hasCommands()).isFalse();
    }

    /**
     * Штатный терминал без посчитанного числа матрица отвергает: контракт
     * требует числа, и его отсутствие означает, что считать ещё рано.
     */
    @Test
    void cleanTerminalWithoutTheResultIsRefused() {
        DealTranche closed = tranche(DealTranche.Status.CLOSED);
        DealContext context = context(Deal.Status.EXIT_PENDING, closed);
        DealStateMachine machine = new DealStateMachine(
                List.of(fixedHandler(Deal.Status.EXIT_PENDING, DealTransition.moveTo(Deal.Status.CLOSED))),
                transitionGate);

        assertThat(machine.run(context).movesStatus()).isFalse();

        context.getDeal().setResultProfit(new BigDecimal("12"));
        context.getDeal().setResultProfitCurrency("USDT");

        assertThat(machine.run(context).getNextStatus()).isEqualTo(Deal.Status.CLOSED);
    }

    /**
     * Аварийный терминал терминальности строк траншей НЕ требует:
     * требование одно — доказанное отсутствие живого риска. Строка транша
     * стала бы второй точкой отказа аварийного контура.
     */
    @Test
    void emergencyTerminalDoesNotRequireTerminalTranches() {
        DealTranche live = tranche(DealTranche.Status.MANAGING);
        DealContext context = context(Deal.Status.ERROR, live);
        DealStateMachine machine = new DealStateMachine(
                List.of(fixedHandler(Deal.Status.ERROR, DealTransition.moveTo(Deal.Status.EMERGENCY_CLOSED))),
                transitionGate);

        assertThat(machine.run(context).getNextStatus()).isEqualTo(Deal.Status.EMERGENCY_CLOSED);
    }

    // --- активная сделка --------------------------------------------------

    /**
     * Расхождение суммы экспозиций траншей с нетто-размером живого эпизода
     * уводит сделку КАСКАДОМ биржевой ступени 2, а не собственным решением
     * обработчика: живой риск есть, но не приписан ни одному траншу.
     */
    @Test
    void exposureMismatchRequestsTheAccountRung() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        context.getDeal().setPositions(new ArrayList<>(List.of(livePosition())));

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(transition.movesStatus()).isFalse();
        verify(cascade, never()).run(any());
    }

    /**
     * Все транши терминальны и операций по сделке не было — терминал
     * затребует ЭТОТ обработчик: у самой частой тропы сканера иного
     * затребователя нет вовсе.
     */
    @Test
    void allTranchesClosedWithoutOperationsFinalizesFromActive() {
        DealTranche closed = tranche(DealTranche.Status.CLOSED);
        closed.setCloseReason(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
        DealContext context = context(Deal.Status.ACTIVE, closed);
        when(cascade.run(context)).thenReturn(emptyCascade());
        when(stepSelector.selectDealStep(context)).thenReturn(StepSelection.none());
        when(systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_EXIT_ACTION, context, null))
                .thenReturn(Optional.of(command(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND)));

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.MARK_DEAL_CLOSED_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    /**
     * Те же терминальные транши, но при СОСТОЯВШЕМСЯ входе — сделка идёт в
     * координированный выход, и терминал затребует уже его обработчик.
     *
     * <p>Пара примеров и есть проверка разделителя: состояние траншей
     * одно, а исход разный, и различает их признак «операций по сделке не
     * было» (docs/rules/deal-without-operations.md).
     */
    @Test
    void allTranchesClosedWithOperationsGoesToCoordinatedExit() {
        DealTranche closed = tranche(DealTranche.Status.CLOSED);
        closed.setCloseReason(DealTranche.CloseReason.TAKE_PROFIT);
        closed.setEntryFilled(new BigDecimal("5"));
        closed.setReduceOnlyFilled(new BigDecimal("5"));
        DealContext context = context(Deal.Status.ACTIVE, closed);
        when(cascade.run(context)).thenReturn(emptyCascade());
        when(stepSelector.selectDealStep(context)).thenReturn(StepSelection.none());

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.TAKE_PROFIT);
    }

    /**
     * Удаление определения сворачивает живую сделку: ребро
     * {@code ACTIVE → EXIT_PENDING} с причиной {@code STRATEGY_DELETED}
     * (docs/lifecycles/Strategy.md, docs/lifecycles/Deal.md).
     *
     * <p><b>Состояние подставляется настоящее, предикат не подменяется</b>
     * (.claude/rules/codestyle.md §«Тесты доменных моделей»): статус
     * ставится на самой копии определения, а «удалена ли» считает модель.
     */
    @Test
    void aDeletedDefinitionCollapsesTheLiveDeal() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = contextWithDefinition(tranche, Strategy.Status.DELETED);
        when(cascade.run(context)).thenReturn(emptyCascade());
        when(stepSelector.selectDealStep(context)).thenReturn(StepSelection.none());

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.getNextStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(transition.getShutdownReason()).isEqualTo(Deal.ShutdownReason.STRATEGY_DELETED);
        assertThat(transition.getCloseReason()).isEqualTo(Deal.CloseReason.STRATEGY_EXIT);
    }

    /**
     * Живое определение сделку не сворачивает — иначе проверка отвечала бы
     * одинаково на обоих состояниях и не проверяла бы ничего.
     */
    @Test
    void aLiveDefinitionDoesNotCollapseTheDeal() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = contextWithDefinition(tranche, Strategy.Status.ACTIVE);
        when(cascade.run(context)).thenReturn(emptyCascade());
        when(stepSelector.selectDealStep(context)).thenReturn(StepSelection.none());

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.movesStatus()).isFalse();
    }

    /** Просьба транша увести сделку ошибочной тропой эмитит звено, а не ребро. */
    @Test
    void trancheErrorRequestEmitsTheErrorLink() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        when(cascade.run(context)).thenReturn(new TrancheCascadeResult(
                List.of(), List.of(), Boolean.TRUE, null, null));
        when(systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, context, null))
                .thenReturn(Optional.of(command(ServiceCommandType.MARK_DEAL_ERROR_COMMAND)));

        DealTransition transition = activeHandler().handle(context);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.MARK_DEAL_ERROR_COMMAND);
    }

    // --- координированный выход -------------------------------------------

    /**
     * Каскад в транши идёт и в координированном выходе: без него выходная
     * проверка «все транши терминальны» не наступает никогда.
     */
    @Test
    void coordinatedExitCascadesIntoTranches() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche);
        when(cascade.run(context)).thenReturn(new TrancheCascadeResult(
                List.of(command(ServiceCommandType.CANCEL_ORDER_COMMAND)), List.of(),
                Boolean.FALSE, null, null));

        DealTransition transition = exitHandler().handle(context);

        verify(cascade).run(context);
        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.CANCEL_ORDER_COMMAND);
    }

    /**
     * Полное закрытие нетто-экспозиции НЕ эмитится, пока у транша жива
     * входная нога: закрытие раньше её снятия гонялось бы за наливом.
     */
    @Test
    void netCloseWaitsForTheLiveEntryLegToBeCancelled() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche);
        context.getDeal().setPositions(new ArrayList<>(List.of(livePosition())));
        when(cascade.run(context)).thenReturn(emptyCascade());
        when(systemActionExecutor.next(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(exitHandler().handle(context).hasCommands()).isFalse();

        tranche.setOrders(new ArrayList<>());
        DealTransition transition = exitHandler().handle(context);

        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    // --- ошибочное состояние ----------------------------------------------

    /**
     * Обработчик ошибочного состояния FSM траншей НЕ гоняет: набор риска
     * остановлен по всем сразу, а машина транша запустила бы торговую
     * логику там, где риск может быть живым.
     */
    @Test
    void errorHandlerDoesNotRunTrancheMachines() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = context(Deal.Status.ERROR, tranche);
        context.getDeal().setPositions(new ArrayList<>(List.of(livePosition())));
        when(systemActionExecutor.next(any(), any(), any()))
                .thenReturn(Optional.of(command(ServiceCommandType.REFRESH_POSITION_COMMAND)));

        DealTransition transition = errorHandler().handle(context);

        verify(cascade, never()).run(any());
        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.REFRESH_POSITION_COMMAND);
    }

    /**
     * Доказанное отсутствие живого риска затребует аварийный терминал, и
     * причину закрытия пишет тот же обработчик той же транзакцией.
     */
    @Test
    void provenAbsenceOfRiskRequestsTheEmergencyTerminal() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        DealContext context = context(Deal.Status.ERROR, tranche);
        when(systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, context, null))
                .thenReturn(Optional.of(command(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND)));

        DealTransition transition = errorHandler().handle(context);

        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.EMERGENCY_CLOSE);
        assertThat(transition.getCommands()).singleElement().extracting(ServiceCommand::getType)
                .isEqualTo(ServiceCommandType.MARK_DEAL_EMERGENCY_CLOSED_COMMAND);
    }

    // --- сборка ------------------------------------------------------------

    private DealActiveHandler activeHandler() {
        return new DealActiveHandler(cascade, stepSelector, terminalGate, systemActionExecutor);
    }

    private DealExitPendingHandler exitHandler() {
        return new DealExitPendingHandler(cascade, transitionGate, systemActionExecutor);
    }

    private ErrorHandler errorHandler() {
        return new ErrorHandler(transitionGate, systemActionExecutor);
    }

    private DealHandler fixedHandler(Deal.Status status, DealTransition transition) {
        return new DealHandler() {

            @Override
            public Deal.Status handledStatus() {
                return status;
            }

            @Override
            public DealTransition handle(DealContext dealContext) {
                return transition;
            }
        };
    }

    private TrancheCascadeResult emptyCascade() {
        return new TrancheCascadeResult(List.of(), List.of(), Boolean.FALSE, null, null);
    }

    private ServiceCommand command(ServiceCommandType type) {
        return ServiceCommand.builder().type(type).dealId(DEAL_ID).build();
    }

    private DealTranche tranche(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        return tranche;
    }

    private Order liveEntryOrder() {
        Order order = new Order();
        order.setId(30L);
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(Boolean.FALSE);
        return order;
    }

    private Position livePosition() {
        Position position = new Position();
        position.setId(50L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal("5"));
        return position;
    }

    /** Контекст живой сделки с копией определения в названном статусе. */
    private DealContext contextWithDefinition(DealTranche tranche, Strategy.Status status) {
        Strategy definition = new Strategy();
        definition.setInternalId("st-0001");
        definition.setStatus(status);
        return context(Deal.Status.ACTIVE, tranche, definition);
    }

    private DealContext context(Deal.Status status, DealTranche tranche) {
        return context(status, tranche, null);
    }

    private DealContext context(Deal.Status status, DealTranche tranche, Strategy definition) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(status);
        deal.setInstrumentId(3L);
        deal.setExchangeAccountId(2L);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        deal.setOrders(new ArrayList<>());
        deal.setAlgoOrders(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return DealContext.builder()
                .deal(deal)
                .strategyDetail(new StrategyDetail())
                .strategy(definition)
                .actionStates(new ArrayList<>())
                .graphComplete(Boolean.TRUE)
                .build();
    }
}
