package com.example.tradingcore;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.config.DealContextProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.SystemActionType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.fsm.DealTrancheHandler;
import com.example.tradingcore.domain.fsm.DealTrancheStateMachine;
import com.example.tradingcore.domain.fsm.StrategyWorkRunner;
import com.example.tradingcore.domain.fsm.TrancheActionDisposition;
import com.example.tradingcore.domain.fsm.TrancheTransition;
import com.example.tradingcore.domain.fsm.TrancheTransitionGate;
import com.example.tradingcore.domain.fsm.TrancheWorkPass;
import com.example.tradingcore.domain.fsm.tranche.TrancheEntrySubmittedHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheExitPendingHandler;
import com.example.tradingcore.domain.fsm.tranche.TrancheManagingHandler;
import com.example.tradingcore.domain.fsm.tranche.TranchePrecheckHandler;
import com.example.tradingcore.domain.safety.HoldRung;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Проход FSM транша: выбор обработчика, гейт предложенного ребра и
 * поведение самих обработчиков на тропах, где ошибка направлена.
 *
 * <p><b>Что здесь проверяется по существу.</b> Транш, берущий вход
 * посреди сворачивания сделки, набирает риск, который она уже снимает.
 * Транш, не уходящий в выход при сворачивании, оставляет сделку в
 * {@code EXIT_PENDING} навсегда: её выходная проверка «все транши
 * терминальны» не сходится. Транш, закрывающий экспозицию раньше снятия
 * своей живой входной ноги, гоняется за наливом. Каждое из трёх состояний
 * собрано настоящими полями графа.
 */
class TrancheFsmPassTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 21L;
    private static final Long DECLARATION_ID = 11L;

    private final TrancheWorkPass workPass = mock(TrancheWorkPass.class);
    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);
    private final StrategyWorkRunner workRunner = mock(StrategyWorkRunner.class);
    private final TrancheActionDisposition disposition =
            new TrancheActionDisposition(systemActionExecutor, workRunner);
    private final TrancheTransitionGate transitionGate = new TrancheTransitionGate();
    private final ProtectionCoverageGate coverageGate = new ProtectionCoverageGate();
    private final DealContextProperties properties = new DealContextProperties();

    // --- машина ---------------------------------------------------------

    /** Терминальный транш обработчика не имеет: проход по нему пуст. */
    @Test
    void terminalTrancheHasNoHandler() {
        DealTranche tranche = tranche(DealTranche.Status.CLOSED);
        DealTrancheStateMachine machine = new DealTrancheStateMachine(
                List.of(fixedHandler(DealTranche.Status.MANAGING, TrancheTransition.escalate())), transitionGate);

        TrancheTransition transition = machine.run(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.hasCommands()).isFalse();
        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getDealErrorRequested()).isFalse();
    }

    /**
     * Ребро, отвергнутое матрицей, снимается — а команды прохода
     * остаются: работа сделана, и повторять её следующим проходом значило
     * бы удвоить биржевой вызов.
     */
    @Test
    void refusedEdgeDropsTheStatusAndKeepsCommands() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        TrancheTransition proposed = TrancheTransition
                .moveTo(DealTranche.Status.ENTRY_SUBMITTED)
                .withCommand(ServiceCommand.builder().type(ServiceCommandType.REFRESH_ORDER_COMMAND).build());
        DealTrancheStateMachine machine = new DealTrancheStateMachine(
                List.of(fixedHandler(DealTranche.Status.PRECHECK, proposed)), transitionGate);

        TrancheTransition applied = machine.run(context(Deal.Status.EXIT_PENDING, tranche), tranche);

        assertThat(applied.movesStatus()).isFalse();
        assertThat(applied.getCommands()).hasSize(1);
    }

    /**
     * Номер эпизода растёт на ОДОБРЕННОМ ребре переоткрытия и не растёт на
     * отвергнутом: признаки эпизода сбрасывать нечему, если эпизод не
     * начинался.
     */
    @Test
    void episodeNumberGrowsOnlyOnAnApprovedReopen() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setReduceOnlyFilled(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        DealTrancheStateMachine machine = new DealTrancheStateMachine(
                List.of(fixedHandler(DealTranche.Status.MANAGING,
                        TrancheTransition.moveTo(DealTranche.Status.ENTRY_SUBMITTED))), transitionGate);

        machine.run(context(Deal.Status.EXIT_PENDING, tranche, true), tranche);
        assertThat(tranche.getEpisodeSeq()).isEqualTo(1);

        machine.run(context(Deal.Status.ACTIVE, tranche, true), tranche);
        assertThat(tranche.getEpisodeSeq()).isEqualTo(2);
    }

    // --- предвходовая проверка ------------------------------------------

    /**
     * Транш, дошедший до предвходовой проверки при сворачивающейся сделке,
     * закрывается — и НАСЛЕДУЕТ её причину, а не пишет свою.
     *
     * <p>Ожидание конца сворачивания оставило бы нетерминальный транш, и
     * выходная проверка сделки не сошлась бы никогда.
     */
    @Test
    void precheckClosesUnderCollapseWithTheDealReason() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        DealContext context = context(Deal.Status.EXIT_PENDING, tranche);
        context.getDeal().setCloseReason(Deal.CloseReason.STRATEGY_EXIT);

        TrancheTransition transition = precheckHandler().handle(context, tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
    }

    /**
     * Устаревший снимок средств уводит проход в добычу: ни преконтроля, ни
     * создания заявки на этой итерации не запускается.
     */
    @Test
    void staleBalanceGoesToHarvestingBeforeAnyWork() {
        properties.setBalanceFreshness(Duration.ofMinutes(2));
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        context.getBalanceContainer().setExternalUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(systemActionExecutor.next(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(ServiceCommand.builder()
                        .type(ServiceCommandType.REFRESH_BALANCE_COMMAND).build()));

        TrancheTransition transition = precheckHandler().handle(context, tranche);

        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.REFRESH_BALANCE_COMMAND);
        assertThat(transition.movesStatus()).isFalse();
    }

    /**
     * Применимого шага нет, живого риска нет, вход не отправлен — условие
     * входа истекло, и закрывается ТРАНШ, а не сделка.
     */
    @Test
    void precheckClosesTheTrancheWhenTheEntryConditionExpired() {
        DealTranche tranche = tranche(DealTranche.Status.PRECHECK);
        when(workPass.run(any(), any())).thenReturn(TrancheTransition.stay());
        when(workPass.spoke(any())).thenReturn(false);

        TrancheTransition transition = precheckHandler().handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    // --- отправленный вход ----------------------------------------------

    /**
     * Под сворачиванием сделки транш с ЖИВОЙ входной ногой уходит в свой
     * выход: снимает ногу дочистка выхода, а не этот статус.
     *
     * <p>Без триггера ногу под сворачиванием не снимал бы никто: сделка
     * стои́т в выходе, а нога продолжает наливаться посреди teardown.
     */
    @Test
    void submittedEntryGoesToExitUnderCollapse() {
        DealTranche tranche = tranche(DealTranche.Status.ENTRY_SUBMITTED);
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));

        TrancheTransition transition = entrySubmittedHandler()
                .handle(context(Deal.Status.EXIT_PENDING, tranche), tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    /** Вход терминален без единой операции — транш закрывается истёкшим условием. */
    @Test
    void terminalEntryWithoutOperationsClosesTheTranche() {
        DealTranche tranche = tranche(DealTranche.Status.ENTRY_SUBMITTED);
        Order canceled = liveEntryOrder();
        canceled.setStatus(Order.Status.CANCELED);
        tranche.setOrders(new ArrayList<>(List.of(canceled)));

        TrancheTransition transition = entrySubmittedHandler()
                .handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.ENTRY_CONDITION_EXPIRED);
    }

    /**
     * Подтверждённый фактами вход эмитит команду консолидации и статуса НЕ
     * двигает: ребро пишет звено в одной транзакции со своим завершением.
     */
    @Test
    void confirmedEntryEmitsConsolidationAndDoesNotMoveTheStatus() {
        DealTranche tranche = tranche(DealTranche.Status.ENTRY_SUBMITTED);
        Order filled = liveEntryOrder();
        filled.setStatus(Order.Status.COMPLETED);
        filled.setCloseReason(Order.CloseReason.FILLED);
        filled.setAccumulatedFillSize(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(filled)));
        tranche.setEntryFilled(new BigDecimal("5"));
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        context.getDeal().setPositions(new ArrayList<>(List.of(livePosition())));
        when(systemActionExecutor.next(SystemActionType.FINALIZE_DEAL_ENTRY_ACTION, context, tranche))
                .thenReturn(Optional.of(ServiceCommand.builder()
                        .type(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND).build()));

        TrancheTransition transition = entrySubmittedHandler().handle(context, tranche);

        assertThat(transition.movesStatus()).isFalse();
        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.FINALIZE_DEAL_ENTRY_COMMAND);
    }

    // --- сопровождение ---------------------------------------------------

    /**
     * Сворачивание сделки уводит сопровождаемый транш в его выход — третий
     * триггер, разрывающий круг «нога снимается только в выходе, а выход
     * наступает только от закрытия».
     */
    @Test
    void managingGoesToExitUnderCollapse() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("5"));

        TrancheTransition transition = managingHandler()
                .handle(context(Deal.Status.EXIT_PENDING, tranche), tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    /**
     * Схлопнувшаяся экспозиция ветвится ОБЪЯВЛЕНИЕМ: разрешает — транш
     * переоткрывается, запрещает — уходит своим выходом. Пустое объявление
     * читается как запрет.
     */
    @Test
    void collapsedExposureBranchesOnTheDeclaration() {
        DealTranche reopening = tranche(DealTranche.Status.MANAGING);
        reopening.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        assertThat(managingHandler().handle(context(Deal.Status.ACTIVE, reopening, true), reopening)
                .getNextStatus()).isEqualTo(DealTranche.Status.ENTRY_SUBMITTED);

        DealTranche exiting = tranche(DealTranche.Status.MANAGING);
        exiting.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        assertThat(managingHandler().handle(context(Deal.Status.ACTIVE, exiting, false), exiting)
                .getNextStatus()).isEqualTo(DealTranche.Status.EXIT_PENDING);
    }

    /**
     * Непокрытая экспозиция без живого обязательства — нарушение
     * инварианта: затребована биржевая ступень 2, и сделка идёт ошибочной
     * тропой.
     */
    @Test
    void uncoveredExposureWithoutCommitmentRequestsTheRung() {
        DealTranche tranche = tranche(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("5"));

        TrancheTransition transition = managingHandler()
                .handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getDealErrorRequested()).isTrue();
        assertThat(transition.getHoldSignal().getRung()).isEqualTo(HoldRung.HARD);
    }

    // --- выход транша ----------------------------------------------------

    /**
     * Дочистка идёт в порядке инварианта: живая ВХОДНАЯ нога снимается
     * раньше всего остального, и команда едет БЕЗ анкера.
     */
    @Test
    void exitCancelsTheLiveEntryLegFirst() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setOrders(new ArrayList<>(List.of(liveEntryOrder())));
        tranche.setAlgoOrders(new ArrayList<>(List.of(liveStop())));

        TrancheTransition transition = exitHandler().handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getCommands()).singleElement()
                .extracting(ServiceCommand::getType).isEqualTo(ServiceCommandType.CANCEL_ORDER_COMMAND);
        assertThat(transition.getCommands().getFirst().getDealActionStateId()).isNull();
    }

    /**
     * Живого риска нет — терминал транша с причиной по инициатору; его нет
     * вовсе, значит экспозиция обнулилась вне нашего ведения.
     */
    @Test
    void exitClosesWithExternalReasonWhenNoInitiatorIsKnown() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setProtectionClosed(new BigDecimal("5"));

        TrancheTransition transition = exitHandler().handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getNextStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.EXTERNAL_CLOSE);
    }

    /** Сработавший стоп транша называет причину его закрытия сам. */
    @Test
    void triggeredStopNamesTheCloseReason() {
        DealTranche tranche = tranche(DealTranche.Status.EXIT_PENDING);
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setProtectionClosed(new BigDecimal("5"));
        AlgoOrder stop = liveStop();
        stop.setStatus(AlgoOrder.Status.COMPLETED);
        stop.setCloseReason(AlgoOrder.CloseReason.TRIGGERED);
        tranche.setAlgoOrders(new ArrayList<>(List.of(stop)));

        TrancheTransition transition = exitHandler().handle(context(Deal.Status.ACTIVE, tranche), tranche);

        assertThat(transition.getCloseReason()).isEqualTo(DealTranche.CloseReason.STOP_LOSS);
    }

    // --- сборка ----------------------------------------------------------

    private TranchePrecheckHandler precheckHandler() {
        return new TranchePrecheckHandler(workPass, disposition, properties);
    }

    private TrancheEntrySubmittedHandler entrySubmittedHandler() {
        return new TrancheEntrySubmittedHandler(workPass, disposition, systemActionExecutor);
    }

    private TrancheManagingHandler managingHandler() {
        return new TrancheManagingHandler(workPass, coverageGate);
    }

    private TrancheExitPendingHandler exitHandler() {
        return new TrancheExitPendingHandler(workPass, disposition);
    }

    private DealTrancheHandler fixedHandler(DealTranche.Status status, TrancheTransition transition) {
        return new DealTrancheHandler() {

            @Override
            public DealTranche.Status handledStatus() {
                return status;
            }

            @Override
            public TrancheTransition handle(DealContext dealContext, DealTranche tranche) {
                return transition;
            }
        };
    }

    private DealTranche tranche(DealTranche.Status status) {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(status);
        tranche.setEpisodeSeq(1);
        tranche.setStrategyTrancheId(DECLARATION_ID);
        return tranche;
    }

    private Order liveEntryOrder() {
        Order order = new Order();
        order.setId(30L);
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(Boolean.FALSE);
        order.setAccumulatedFillSize(BigDecimal.ZERO);
        return order;
    }

    private AlgoOrder liveStop() {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(40L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(new BigDecimal("5"));
        return algoOrder;
    }

    private Position livePosition() {
        Position position = new Position();
        position.setId(50L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal("5"));
        return position;
    }

    private DealContext context(Deal.Status status, DealTranche tranche) {
        return context(status, tranche, false);
    }

    private DealContext context(Deal.Status status, DealTranche tranche, boolean reopenAllowed) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setId(DECLARATION_ID);
        declaration.setPositionReopenAllowed(reopenAllowed);
        StrategyDetail detail = new StrategyDetail();
        detail.setTranches(new ArrayList<>(List.of(declaration)));

        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(status);
        deal.setInstrumentId(3L);
        deal.setExchangeAccountId(2L);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        deal.setOrders(new ArrayList<>(isNull(tranche.getOrders()) ? List.of() : tranche.getOrders()));
        deal.setAlgoOrders(new ArrayList<>(isNull(tranche.getAlgoOrders())
                ? List.of() : tranche.getAlgoOrders()));
        deal.setPositions(new ArrayList<>());

        Instrument instrument = new Instrument();
        instrument.setId(3L);
        instrument.setExternalSettlementCurrency("USDT");

        BalanceContainer balance = new BalanceContainer();
        balance.setExternalUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return DealContext.builder()
                .deal(deal)
                .strategyDetail(detail)
                .instrument(instrument)
                .balanceContainer(balance)
                .actionStates(new ArrayList<>())
                .graphComplete(Boolean.TRUE)
                .build();
    }
}
