package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.config.DealOrchestratorProperties;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.RetryBudgetExhaustedException;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.action.SystemActionExecutor;
import com.example.tradingcore.domain.command.executor.ServiceCommandExecutor;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealShutdownEdgeException;
import com.example.tradingcore.domain.deal.DealStatusEdgeService;
import com.example.tradingcore.domain.fsm.DealStateMachine;
import com.example.tradingcore.domain.fsm.DealTransition;
import com.example.tradingcore.domain.fsm.TrancheEdge;
import com.example.tradingcore.domain.jobs.DealOrchestratorJob;
import com.example.tradingcore.domain.jobs.JobExecutionGuard;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HardRungShutdownReasonResolver;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.exchange.CredentialsRejectedException;
import com.example.tradingcore.integration.exchange.ExternalInvariantViolationException;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.DealTrancheDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Петля прохода: энфорсмент жёсткой ступени, диспетчеризация с тремя
 * выделенными перехватчиками, применение перехода и подъём затребованной
 * ступени.
 *
 * <p><b>Что здесь проверяется по существу.</b> Энфорсмент, поставленный
 * после сборки контекста, съедался бы отказом добычи ровно на той тропе,
 * где он единственный элемент реакции, отрабатывающий целиком. Ступень
 * исчерпанного бюджета, выведенная траншевым признаком безусловно,
 * сворачивала бы весь счёт по отказу строки уровня сделки. Ступень,
 * затребованная каскадом, потерянная из-за неуспеха соседней команды,
 * оставляла бы непокрытый живой риск без реакции до следующего прохода.
 * Статус транша, записанный до диспетчера, пережил бы отказ собственной
 * команды.
 *
 * <p>Состояния покрытия собраны настоящими полями графа — наливом транша
 * и живой защитой, — а не подменёнными предикатами
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class DealOrchestratorPassTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 21L;
    private static final Long ACCOUNT_ID = 2L;
    private static final Long INSTRUMENT_ID = 3L;

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealTrancheDataService dealTrancheDataService = mock(DealTrancheDataService.class);
    private final DealContextService dealContextService = mock(DealContextService.class);
    private final DealStatusEdgeService dealStatusEdgeService = mock(DealStatusEdgeService.class);
    private final SystemActionExecutor systemActionExecutor = mock(SystemActionExecutor.class);
    private final DealStateMachine dealStateMachine = mock(DealStateMachine.class);
    private final ServiceCommandExecutor serviceCommandExecutor = mock(ServiceCommandExecutor.class);
    private final HoldService holdService = mock(HoldService.class);

    // --- энфорсмент жёсткой ступени ---------------------------------------

    /**
     * Энфорсмент идёт ДО сборки контекста. На тропе отвергнутых кредов
     * добыча отказывает целиком, и шаг, стоящий после неё, не отработал бы
     * — то есть каскад активных сделок радиуса не состоялся бы ровно
     * тогда, когда он единственный элемент реакции, исполняющийся целиком.
     */
    @Test
    void theHardRungIsEnforcedBeforeTheContextIsBuilt() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.stay());
        when(dealDataService.findIdsUnderAccountRung(List.of(DEAL_ID))).thenReturn(List.of(DEAL_ID));
        when(dealStatusEdgeService.enforceHardRung(any(), any())).thenReturn(Boolean.TRUE);

        job().tick();

        InOrder order = inOrder(dealStatusEdgeService, dealContextService);
        order.verify(dealStatusEdgeService).enforceHardRung(context.getDeal(),
                Deal.ShutdownReason.EXCHANGE_HOLD);
        order.verify(dealContextService).build(context.getDeal());
    }

    /**
     * При обоих стоящих радиусах пишется биржевая причина: биржевой радиус
     * старше (docs/lifecycles/Deal.md §«Причина выхода из штатного
     * ведения»). Порядок чтения свойством этого шага не является — правило
     * исполняет читатель, общий у обоих затребователей ребра. Клетка
     * достижима: восстановительная тропа заводит сделку при уже стоящей
     * ступени любого радиуса.
     */
    @Test
    void theExchangeReasonWinsWhenBothRadiiStand() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.stay());
        when(dealDataService.findIdsUnderAccountRung(List.of(DEAL_ID))).thenReturn(List.of(DEAL_ID));
        when(dealDataService.findIdsUnderInstrumentRung(List.of(DEAL_ID))).thenReturn(List.of(DEAL_ID));

        job().tick();

        verify(dealStatusEdgeService).enforceHardRung(context.getDeal(),
                Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** Жёсткая ступень одного лишь инструмента пишет риск-политику. */
    @Test
    void theInstrumentRungWritesTheRiskPolicyReason() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.stay());
        when(dealDataService.findIdsUnderInstrumentRung(List.of(DEAL_ID))).thenReturn(List.of(DEAL_ID));

        job().tick();

        verify(dealStatusEdgeService).enforceHardRung(context.getDeal(),
                Deal.ShutdownReason.RISK_POLICY);
    }

    /** Ступени нет ни на одном радиусе — ребра тоже нет. */
    @Test
    void aDealWithoutAStandingRungIsNotEnforced() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.stay());

        job().tick();

        verify(dealStatusEdgeService, never()).enforceHardRung(any(), any());
    }

    // --- выделенные перехватчики ------------------------------------------

    /**
     * Контролируемый отказ площадки: сперва безусловная жёсткая ступень
     * СЧЁТА, затем ошибочная тропа сделки. Признак покрытия здесь не
     * читается — он сам считается по фактам площадки, которой доверять
     * уже нельзя.
     */
    @Test
    void aControlledFailureRaisesTheAccountRungAndThenMovesTheDealToError() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenThrow(new ExternalInvariantViolationException("margin mode is not isolated"));

        job().tick();

        InOrder order = inOrder(holdService, dealDataService);
        order.verify(holdService).raise(signal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.HARD,
                Constants.Hold.EXCHANGE_CONTROLLED_FAILURE), context);
        order.verify(dealDataService).applyErrorEdge(DEAL_ID);
        verify(dealStatusEdgeService, never()).applyPassEdge(any(), any(), any());
    }

    /**
     * Отказ кредов: порядок обратный — сперва ошибочная тропа сделки,
     * затем биржевая ступень 2. Каскад ступени всё равно уводит активные
     * сделки радиуса каждым проходом, поэтому увод собственной первым
     * ничего не отменяет.
     */
    @Test
    void rejectedCredentialsMoveTheDealToErrorBeforeRaisingTheRung() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenThrow(new CredentialsRejectedException("signature is not accepted"));

        job().tick();

        InOrder order = inOrder(dealDataService, holdService);
        order.verify(dealDataService).applyErrorEdge(DEAL_ID);
        order.verify(holdService).raise(signal(HoldScope.EXCHANGE_ACCOUNT, HoldRung.HARD,
                Constants.Hold.EXCHANGE_CREDENTIALS_REJECTED), context);
    }

    /**
     * Исчерпание бюджета на СТРАТЕГИЙНОЙ строке при покрытом риске:
     * сделка остаётся в своём статусе, ступень мягкая и инструментная —
     * рвать покрытый риск нечем.
     */
    @Test
    void anExhaustedBudgetOnACoveredStrategyRowRaisesTheSoftInstrumentRung() {
        DealTranche tranche = coveredTranche();
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenThrow(exhausted(trancheRow(tranche), Boolean.TRUE));

        job().tick();

        verify(holdService).raise(signal(HoldScope.INSTRUMENT, HoldRung.SOFT,
                Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED), context);
        verify(dealDataService, never()).applyErrorEdge(any());
        assertThat(context.getDeal().getStatus()).isEqualTo(Deal.Status.ACTIVE);
    }

    /**
     * Тот же класс на НЕПОКРЫТОМ риске ступени не назначает вовсе: живой
     * риск без покрытия — нарушение инварианта системы, его реакцию
     * поднимает свой триггер, и радиус там шире инструментного. Одно
     * состояние не получает двух ответов.
     */
    @Test
    void anExhaustedBudgetOnUncoveredRiskAssignsNoRung() {
        DealTranche tranche = uncoveredTranche();
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenThrow(exhausted(trancheRow(tranche), Boolean.TRUE));

        job().tick();

        verify(holdService, never()).raise(any(), any());
    }

    /**
     * Операнд покрытия выбирается УРОВНЕМ отказавшей строки. У строки
     * уровня сделки транша нет вовсе, и траншевый признак безусловно дал
     * бы «не резолвится» — то есть жёсткую счётную ступень там, где исход
     * объявлен мягким.
     */
    @Test
    void aDealLevelRowReadsTheAggregateCoverage() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenThrow(exhausted(dealLevelRow(), Boolean.FALSE));

        job().tick();

        verify(holdService).raise(signal(HoldScope.INSTRUMENT, HoldRung.SOFT,
                Constants.Hold.INSTRUMENT_RETRY_BUDGET_EXHAUSTED), context);
        verify(dealDataService).applyErrorEdge(DEAL_ID);
    }

    /**
     * Общий перехватчик уводит сделку в ошибку БЕЗ радиусной реакции: что
     * именно сломалось, здесь неизвестно, и блокировать по этому радиус
     * означало бы соразмерять реакцию с собственным багом.
     */
    @Test
    void theGeneralInterceptorReactsWithoutARadiusReaction() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        when(dealDataService.findActive(any())).thenReturn(new ArrayList<>(List.of(context.getDeal())));
        when(dealContextService.build(context.getDeal())).thenThrow(new IllegalStateException("graph is torn"));

        job().tick();

        verify(dealDataService).applyErrorEdge(DEAL_ID);
        verify(holdService, never()).raise(any(), any());
    }

    /**
     * <b>Отказ ребра, присваивавшего причину остановки, в ошибку НЕ
     * перехватывается.</b> Ребро и его факт идут одной транзакцией, и её
     * откат вернул сделку в состояние, из которого ход повторим;
     * перехваченная же сделка стои́т в {@code ERROR}, а рёбра присвоения
     * причины применяются только из {@code ACTIVE} и {@code EXIT_PENDING} —
     * причина, уже вычисленная, не записалась бы НИКОГДА.
     *
     * <p><b>Проба нужна потому, что потеря молчалива:</b> сделка выглядела
     * бы ошибочной штатно, а вопрос «почему она перестала вестись» остался
     * бы без единственного своего носителя.
     *
     * <p><b>Локус здесь ПЕРВЫЙ</b> — ребро энфорсмента ступени, стоящее
     * первым шагом прохода. На нём откат возвращает состояние целиком,
     * потому что до него проход ничего не коммитил; у второго локуса довод
     * другой, и его мерит соседняя проба.
     */
    @Test
    void aFailedShutdownEdgeLeavesTheDealToTheNextPass() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        when(dealDataService.findActive(any())).thenReturn(new ArrayList<>(List.of(context.getDeal())));
        when(dealDataService.findIdsUnderAccountRung(any())).thenReturn(List.of(DEAL_ID));
        when(dealStatusEdgeService.enforceHardRung(any(), any()))
                .thenThrow(new DealShutdownEdgeException(DEAL_ID, new IllegalStateException("outbox")));

        job().tick();

        verify(dealDataService, never()).applyErrorEdge(any());
        verify(dealContextService, never()).build(any());
    }

    // --- применение перехода ----------------------------------------------

    /**
     * Неуспешная команда откладывает применение перехода — статус,
     * записанный поверх отказа собственной команды, разошёлся бы с
     * фактами площадки, — но затребованную ступень НЕ теряет: реакция на
     * непокрытый живой риск не ждёт следующего прохода из-за неудачи
     * соседней команды.
     */
    @Test
    void anUnsuccessfulCommandDefersTheEdgeButNotTheRequestedRung() {
        DealTranche tranche = coveredTranche();
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        HoldSignal requested = HoldSignal.exchangeAccount(Constants.Hold.EXCHANGE_LIVE_RISK_UNCOVERED);
        stubPass(context, DealTransition.commands(List.of(command()))
                .withTrancheEdges(List.of(new TrancheEdge(tranche, DealTranche.Status.CLOSED, null)))
                .withRung(requested));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenReturn(ServiceCommandExecutionResult.notCompleted("the fact is not harvested yet"));

        job().tick();

        verify(dealTrancheDataService, never()).save(any());
        verify(holdService).raise(requested, context);
    }

    /**
     * Рёбра траншей применяются ПОСЛЕ диспетчеризации: записанный до неё
     * статус транша пережил бы отказ собственной команды.
     */
    @Test
    void trancheEdgesAreAppliedAfterTheCommandsAreDispatched() {
        DealTranche tranche = coveredTranche();
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        stubPass(context, DealTransition.commands(List.of(command()))
                .withTrancheEdges(List.of(new TrancheEdge(tranche, DealTranche.Status.CLOSED,
                        DealTranche.CloseReason.STRATEGY_EXIT))));
        when(serviceCommandExecutor.execute(any(), any())).thenReturn(ServiceCommandExecutionResult.ok());

        job().tick();

        InOrder order = inOrder(serviceCommandExecutor, dealTrancheDataService);
        order.verify(serviceCommandExecutor).execute(any(), any());
        order.verify(dealTrancheDataService).save(tranche);
        assertThat(tranche.getStatus()).isEqualTo(DealTranche.Status.CLOSED);
        assertThat(tranche.getCloseReason()).isEqualTo(DealTranche.CloseReason.STRATEGY_EXIT);
    }

    /**
     * Статусное ребро сделки применяется точечной записью и гардируется
     * ИСХОДНЫМ статусом: пока шли команды, терминальное звено могло
     * применить своё ребро, и запись поверх него вернула бы сделку из
     * терминала. Обе причины едут вместе со статусом.
     */
    @Test
    void theDealEdgeIsAppliedGuardedByTheStatusItWasReadIn() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        stubPass(context, DealTransition.collapse(Deal.ShutdownReason.MARKET_DATA_EXPIRED,
                Deal.CloseReason.RISK_CONTROL));

        job().tick();

        ArgumentCaptor<Deal.Status> from = ArgumentCaptor.forClass(Deal.Status.class);
        verify(dealStatusEdgeService).applyPassEdge(eq(context), from.capture(),
                eq(Deal.ShutdownReason.MARKET_DATA_EXPIRED));
        assertThat(from.getValue()).isEqualTo(Deal.Status.ACTIVE);
        assertThat(context.getDeal().getStatus()).isEqualTo(Deal.Status.EXIT_PENDING);
        assertThat(context.getDeal().getShutdownReason()).isEqualTo(Deal.ShutdownReason.MARKET_DATA_EXPIRED);
        assertThat(context.getDeal().getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    /**
     * <b>Отказ статусного ребра — ВТОРОЙ локус, и он оставляет рёбра
     * траншей применёнными.</b> Сделка так же не уводится в {@code ERROR},
     * но откат возвращает только её строку: рёбра траншей закоммичены
     * раньше и своей транзакцией, а команды перехода уже исполнены.
     *
     * <p><b>Проба закрепляет ЧАСТИЧНОЕ ПРИМЕНЕНИЕ как названную цену, а не
     * как незамеченный исход</b> (docs/components/DealOrchestratorJob.md
     * §«Цикл прохода», шаг «применение перехода»). Без неё клейм
     * повторимости читался бы безусловным — тем же, что у первого локуса,
     * — а он там держится на другом доводе: на природе причин, доезжающих
     * до этого ребра.
     */
    @Test
    void aFailedPassEdgeLeavesTheTrancheEdgesApplied() {
        DealTranche tranche = coveredTranche();
        DealContext context = context(Deal.Status.ACTIVE, tranche);
        stubPass(context, DealTransition.collapse(Deal.ShutdownReason.MARKET_DATA_EXPIRED,
                        Deal.CloseReason.RISK_CONTROL)
                .withTrancheEdges(List.of(new TrancheEdge(tranche, DealTranche.Status.CLOSED,
                        DealTranche.CloseReason.STRATEGY_EXIT))));
        when(dealStatusEdgeService.applyPassEdge(any(), any(), any()))
                .thenThrow(new DealShutdownEdgeException(DEAL_ID, new IllegalStateException("outbox")));

        job().tick();

        verify(dealDataService, never()).applyErrorEdge(any());
        verify(dealTrancheDataService).save(tranche);
        assertThat(tranche.getStatus())
                .as("ребро транша закоммичено раньше и откатом статусного ребра не снимается")
                .isEqualTo(DealTranche.Status.CLOSED);
    }

    /**
     * Ступень, затребованная ЗВЕНОМ, поднимается проходом — после
     * возврата исполнителя, то есть после коммита его транзакции: исход
     * звена есть намерение, а не право.
     */
    @Test
    void aRungRequestedByALinkIsRaisedByThePass() {
        DealContext context = context(Deal.Status.ACTIVE, coveredTranche());
        HoldSignal requested = HoldSignal.exchangeAccountSoft(Constants.Hold.PNL_RECONCILIATION_MISMATCH);
        stubPass(context, DealTransition.commands(List.of(command())));
        when(serviceCommandExecutor.execute(any(), any()))
                .thenReturn(ServiceCommandExecutionResult.okWithHold(requested));

        job().tick();

        verify(holdService).raise(requested, context);
    }

    /** Выключенный проход не делает ничего — ни выборки, ни прохода. */
    @Test
    void aDisabledPassDoesNothing() {
        DealOrchestratorProperties disabled = new DealOrchestratorProperties();
        disabled.setEnabled(Boolean.FALSE);

        job(disabled).tick();

        verify(dealDataService, never()).findActive(any());
    }

    // --- сборка ------------------------------------------------------------

    private DealOrchestratorJob job() {
        return job(new DealOrchestratorProperties());
    }

    private DealOrchestratorJob job(DealOrchestratorProperties properties) {
        return new DealOrchestratorJob(properties, new JobExecutionGuard(), dealDataService,
                dealTrancheDataService, dealContextService, dealStatusEdgeService, systemActionExecutor,
                dealStateMachine, serviceCommandExecutor, holdService,
                new HardRungShutdownReasonResolver(dealDataService));
    }

    /** Выборка из одной сделки, её контекст и заданный исход машины. */
    private void stubPass(DealContext context, DealTransition transition) {
        when(dealDataService.findActive(any())).thenReturn(new ArrayList<>(List.of(context.getDeal())));
        when(dealContextService.build(context.getDeal())).thenReturn(context);
        when(dealStateMachine.run(context)).thenReturn(transition);
    }

    private RetryBudgetExhaustedException exhausted(DealActionState state, Boolean strategyLevel) {
        return new RetryBudgetExhaustedException("budget is exhausted", state, strategyLevel);
    }

    private HoldSignal signal(HoldScope scope, HoldRung rung, String code) {
        return new HoldSignal(scope, rung, code);
    }

    private ServiceCommand command() {
        return ServiceCommand.builder()
                .type(ServiceCommandType.SUBMIT_ORDER_COMMAND)
                .dealId(DEAL_ID)
                .build();
    }

    /** Строка исполнения уровня транша: транш назван. */
    private DealActionState trancheRow(DealTranche tranche) {
        DealActionState state = new DealActionState();
        state.setId(90L);
        state.setDealId(DEAL_ID);
        state.setDealTrancheId(tranche.getId());
        state.setActionKind(ActionKind.STRATEGY);
        return state;
    }

    /** Строка исполнения уровня сделки: транша у неё нет ни одного. */
    private DealActionState dealLevelRow() {
        DealActionState state = new DealActionState();
        state.setId(91L);
        state.setDealId(DEAL_ID);
        state.setActionKind(ActionKind.SYSTEM);
        return state;
    }

    /** Транш с наливом и живой защитой на весь налив — покрытие выполнено. */
    private DealTranche coveredTranche() {
        DealTranche tranche = tranche();
        tranche.setEntryFilled(new BigDecimal("5"));
        tranche.setAlgoOrders(new ArrayList<>(List.of(liveStop(new BigDecimal("5")))));
        return tranche;
    }

    /** Транш с наливом и без единой защиты — покрытие нарушено. */
    private DealTranche uncoveredTranche() {
        DealTranche tranche = tranche();
        tranche.setEntryFilled(new BigDecimal("5"));
        return tranche;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setDealId(DEAL_ID);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEpisodeSeq(1);
        tranche.setOrders(new ArrayList<>());
        tranche.setAlgoOrders(new ArrayList<>());
        return tranche;
    }

    private AlgoOrder liveStop(BigDecimal size) {
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(60L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        algoOrder.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        algoOrder.setSize(size);
        return algoOrder;
    }

    private DealContext context(Deal.Status status, DealTranche tranche) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setStatus(status);
        deal.setExchangeAccountId(ACCOUNT_ID);
        deal.setInstrumentId(INSTRUMENT_ID);
        deal.setTranches(new ArrayList<>(List.of(tranche)));
        deal.setOrders(new ArrayList<>());
        deal.setAlgoOrders(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        ExchangeAccount account = new ExchangeAccount();
        account.setId(ACCOUNT_ID);
        Instrument instrument = new Instrument();
        instrument.setId(INSTRUMENT_ID);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .actionStates(new ArrayList<>())
                .graphComplete(Boolean.TRUE)
                .build();
    }
}
