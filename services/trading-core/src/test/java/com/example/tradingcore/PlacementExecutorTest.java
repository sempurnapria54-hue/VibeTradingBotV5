package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.exchange.ExchangeAck;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.ActionKind;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.executor.CancelAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.CancelAttachedProtectionExecutor;
import com.example.tradingcore.domain.command.executor.CancelOrderExecutor;
import com.example.tradingcore.domain.command.executor.ClosePositionExecutor;
import com.example.tradingcore.domain.command.executor.CreateAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.CreateOrderExecutor;
import com.example.tradingcore.domain.command.executor.SubmitAlgoOrderExecutor;
import com.example.tradingcore.domain.command.executor.SubmitOrderExecutor;
import com.example.tradingcore.domain.command.payload.AttachedProtectionPayload;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelAttachedProtectionCommandPayload;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.SubmitAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.SubmitOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeIntegrationException;
import com.example.tradingcore.integration.internal.api.exchange.ExchangeOperationsClient;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.AlgoOrderDataService;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.persistence.service.OrderDataService;
import com.example.tradingcore.persistence.service.PositionDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Исполнители постановки и снятия: что каждый пишет, чего не пишет и на
 * чём стои́т его идемпотентность.
 *
 * <p><b>Предмет — контракт исполнителя, а не факт вызова шлюза.</b>
 * Проверяются свойства, объявленные домами: подтверждение приёма
 * состоянием не считается, причина закрытия write-once, окно линковки
 * открывает только вход, повтор работает с ТОЙ ЖЕ локальной сущностью.
 */
class PlacementExecutorTest {

    private static final Long DEAL = 1L;
    private static final Long TRANCHE = 10L;
    private static final String ACCOUNT = "ea-0001";
    private static final String INSTRUMENT = "ETH-USDT-SWAP";

    private final OrderDataService orderDataService = mock(OrderDataService.class);
    private final AlgoOrderDataService algoOrderDataService = mock(AlgoOrderDataService.class);
    private final PositionDataService positionDataService = mock(PositionDataService.class);
    private final DealActionStateDataService actionStateDataService = mock(DealActionStateDataService.class);
    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealRiskNumbersService riskNumbersService = mock(DealRiskNumbersService.class);
    private final ExchangeOperationsClient exchange = mock(ExchangeOperationsClient.class);
    private final AccountInstrumentStateDataService pairStateDataService =
            mock(AccountInstrumentStateDataService.class);

    /**
     * Заведение ноги замораживает базу риска сделки и её валюту — снимки
     * момента ПЕРВОГО сайзинга: значением снимок и живая база совпадают
     * ровно в момент заморозки, а потолки живой сделки считаются от
     * снимка.
     */
    @Test
    void creatingTheFirstLegFreezesTheRiskBase() {
        Deal deal = deal();
        DealContext context = context(deal);
        DealActionState row = row();
        when(orderDataService.save(any())).thenAnswer(call -> stored(call.getArgument(0), 100L));
        when(riskNumbersService.recompute(context)).thenReturn(true);

        createOrderExecutor().execute(createCommand(row), row, context);

        assertThat(deal.getPlannedRiskEquityBase()).isEqualByComparingTo("5000");
        assertThat(deal.getPlannedRiskCurrency()).isEqualTo("USDT");
        assertThat(row.getTargetEntityType()).isEqualTo(TargetEntityType.ORDER);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.CREATED);
    }

    /**
     * Свежесозданная нога входит в граф ТОЙ ЖЕ транзакцией и кладётся на
     * СВОЙ транш: контекст собран до неё, и без этого числа риска
     * считались бы по графу без только что заведённой ноги.
     */
    @Test
    void freshLegJoinsItsTrancheWithinTheSameTransaction() {
        Deal deal = deal();
        DealContext context = context(deal);
        DealActionState row = row();
        when(orderDataService.save(any())).thenAnswer(call -> stored(call.getArgument(0), 100L));
        when(riskNumbersService.recompute(context)).thenReturn(true);

        createOrderExecutor().execute(createCommand(row), row, context);

        assertThat(deal.getTranches().getFirst().getOrders()).hasSize(1);
        assertThat(deal.getTranches().getFirst().getOrders().getFirst().getId()).isEqualTo(100L);
    }

    /**
     * Два измерителя записываются рядом с шестёркой планового риска, но в
     * её инвариант не входят: пустой измеритель — самостоятельное
     * значение «не измеряли», а не нарушение «шести или ни одной».
     */
    @Test
    void placementMeasuresAreWrittenAndMayStayEmpty() {
        Deal deal = deal();
        DealContext context = context(deal);
        when(orderDataService.save(any())).thenAnswer(call -> stored(call.getArgument(0), 100L));
        when(riskNumbersService.recompute(context)).thenReturn(true);

        DealActionState measured = row();
        createOrderExecutor().execute(createCommand(measured), measured, context);
        Order withMeasures = deal.getTranches().getFirst().getOrders().getFirst();

        assertThat(withMeasures.getBookDepthAtPlacement()).isEqualByComparingTo("1200");
        assertThat(withMeasures.getLiquidationDistanceRatio()).isNull();
        assertThat(withMeasures.getPlannedRiskAmount()).isEqualByComparingTo("100");
    }

    /**
     * Неполный граф оставляет звено НЕЗАВЕРШЁННЫМ: числа не пишутся,
     * исход не «успех», и повтор идёт по бюджету строки. Заниженный
     * заявленный риск ослабил бы кумулятивный потолок.
     */
    @Test
    void incompleteGraphLeavesTheCreateLinkUnfinished() {
        Deal deal = deal();
        DealContext context = context(deal);
        DealActionState row = row();
        when(orderDataService.save(any())).thenAnswer(call -> stored(call.getArgument(0), 100L));
        when(riskNumbersService.recompute(context)).thenReturn(false);

        ServiceCommandExecutionResult result = createOrderExecutor().execute(createCommand(row), row, context);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorCode()).isNull();
    }

    /**
     * Повтор до завершения работает с ТОЙ ЖЕ локальной сущностью: анкер —
     * цель строки исполнения. Заново сгенерированный клиентский
     * идентификатор оставил бы сироту, а на площадку она уехала бы вторым
     * размером.
     */
    @Test
    void repeatedCreateReusesTheAnchoredEntity() {
        Deal deal = deal();
        DealContext context = context(deal);
        DealActionState row = row();
        row.targetAt(TargetEntityType.ORDER, 100L);
        Order existing = stored(new Order(), 100L);
        existing.setInternalId("vtbalreadyplaced");
        when(orderDataService.getRequiredById(100L)).thenReturn(existing);
        when(riskNumbersService.recompute(context)).thenReturn(true);

        createOrderExecutor().execute(createCommand(row), row, context);

        verify(orderDataService, never()).save(any());
        assertThat(row.getTargetEntityId()).isEqualTo(100L);
    }

    /**
     * Отправка входной ноги открывает окно линковки движений биржевым
     * временем приёма — единственный писатель нижней границы. Заявка,
     * только уменьшающая позицию, окна не открывает.
     */
    @Test
    void submittingTheEntryLegOpensTheLinkageWindow() {
        Order entry = stored(new Order(), 100L);
        entry.setDealId(DEAL);
        entry.setStatus(Order.Status.CREATED);
        entry.setInternalId("vtbclientid");
        OffsetDateTime accepted = OffsetDateTime.parse("2026-09-05T10:15:30Z");
        when(orderDataService.getRequiredById(100L)).thenReturn(entry);
        when(exchange.setLeverage(ACCOUNT, INSTRUMENT, 5)).thenReturn(ack(true, null, null));
        when(exchange.placeOrder(eq(ACCOUNT), any(), eq(INSTRUMENT)))
                .thenReturn(ack(true, "ext-1", accepted));

        DealActionState row = row();
        submitOrderExecutor().execute(submitCommand(row), row, context(deal()));

        verify(dealDataService).applyBillsWindowBegin(DEAL, accepted);
        assertThat(entry.getExternalId()).isEqualTo("ext-1");
        assertThat(entry.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(row.getStatus()).isEqualTo(DealActionStateStatus.SUBMITTED);
    }

    /** Заявка, только уменьшающая позицию, ни окна не открывает, ни плеча не трогает. */
    @Test
    void reducingOnlyLegTouchesNeitherWindowNorLeverage() {
        Order reducing = stored(new Order(), 101L);
        reducing.setDealId(DEAL);
        reducing.setStatus(Order.Status.CREATED);
        reducing.setPositionReducingOnly(true);
        when(orderDataService.getRequiredById(101L)).thenReturn(reducing);
        when(exchange.placeOrder(eq(ACCOUNT), any(), eq(INSTRUMENT)))
                .thenReturn(ack(true, "ext-2", OffsetDateTime.parse("2026-09-05T10:15:30Z")));

        DealActionState row = row();
        submitOrderExecutor().execute(
                command(ServiceCommandType.SUBMIT_ORDER_COMMAND, row, new SubmitOrderCommandPayload(101L)),
                row, context(deal()));

        verify(dealDataService, never()).applyBillsWindowBegin(any(), any());
        verify(exchange, never()).setLeverage(any(), any(), any());
    }

    /**
     * Перед ПОВТОРНОЙ отправкой заявка ищется по стабильному клиентскому
     * идентификатору: предыдущая постановка могла пройти, а ответ
     * потеряться — второй раз не шлём.
     */
    @Test
    void repeatedSubmitRecoversByClientIdInsteadOfPlacingTwice() {
        Order entry = stored(new Order(), 100L);
        entry.setDealId(DEAL);
        entry.setStatus(Order.Status.CREATED);
        entry.setInternalId("vtbclientid");
        Order onExchange = new Order();
        onExchange.setExternalId("ext-recovered");
        onExchange.setExternalCreatedAt(OffsetDateTime.parse("2026-09-05T10:00:00Z"));
        when(orderDataService.getRequiredById(100L)).thenReturn(entry);
        when(exchange.getOrder(ACCOUNT, INSTRUMENT, null, "vtbclientid")).thenReturn(onExchange);

        DealActionState retried = row();
        retried.setAttemptCount(1);
        submitOrderExecutor().execute(submitCommand(retried), retried, context(deal()));

        verify(exchange, never()).placeOrder(any(), any(), any());
        assertThat(entry.getExternalId()).isEqualTo("ext-recovered");
    }

    /**
     * Отказ постановки рабочего плеча останавливает вход броском, а не
     * тихой отправкой заявки без плеча: значение применяется к моменту
     * входа, и без него размер уехал бы под чужим плечом.
     */
    @Test
    void leverageRejectionStopsTheEntry() {
        Order entry = stored(new Order(), 100L);
        entry.setDealId(DEAL);
        entry.setStatus(Order.Status.CREATED);
        when(orderDataService.getRequiredById(100L)).thenReturn(entry);
        when(exchange.setLeverage(ACCOUNT, INSTRUMENT, 5)).thenReturn(ack(false, null, null));

        DealActionState row = row();
        assertThatThrownBy(() -> submitOrderExecutor().execute(submitCommand(row), row, context(deal())))
                .isInstanceOf(ExchangeIntegrationException.class);

        verify(exchange, never()).placeOrder(any(), any(), any());
    }

    /**
     * Встроенная защита уходит на площадку вместе с родителем, поэтому
     * факт «родитель отправлен» пишется и ей. Живость этим не
     * утверждается — её подтверждает добыча.
     */
    @Test
    void submittingTheParentMarksItsEmbeddedProtectionSubmittedToo() {
        Order entry = stored(new Order(), 100L);
        entry.setDealId(DEAL);
        entry.setStatus(Order.Status.CREATED);
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setStatus(AttachedAlgoOrder.Status.CREATED);
        entry.setAttachedAlgoOrders(List.of(protection));
        when(orderDataService.getRequiredById(100L)).thenReturn(entry);
        when(exchange.setLeverage(ACCOUNT, INSTRUMENT, 5)).thenReturn(ack(true, null, null));
        when(exchange.placeOrder(eq(ACCOUNT), any(), eq(INSTRUMENT)))
                .thenReturn(ack(true, "ext-1", OffsetDateTime.parse("2026-09-05T10:15:30Z")));

        DealActionState row = row();
        submitOrderExecutor().execute(submitCommand(row), row, context(deal()));

        assertThat(protection.getStatus()).isEqualTo(AttachedAlgoOrder.Status.PENDING);
    }

    /**
     * Снятие фиксирует причину write-once и НЕ переводит заявку в снятое
     * состояние: подтверждение приёма состоянием не считается, факт
     * снятия подтверждает добыча.
     */
    @Test
    void cancelWritesTheIntentOnceAndDoesNotFinalizeTheLeg() {
        Order live = stored(new Order(), 100L);
        live.setStatus(Order.Status.ACTIVE);
        live.setCloseReason(Order.CloseReason.REPLACED_BY_STRATEGY);
        when(orderDataService.getRequiredById(100L)).thenReturn(live);
        when(exchange.cancelOrder(eq(ACCOUNT), any(), eq(INSTRUMENT))).thenReturn(ack(true, null, null));

        DealActionState row = row();
        cancelOrderExecutor().execute(command(ServiceCommandType.CANCEL_ORDER_COMMAND, row,
                new CancelOrderCommandPayload(100L, Order.CloseReason.KILL_SWITCH)), row, context(deal()));

        assertThat(live.getStatus()).isEqualTo(Order.Status.ACTIVE);
        assertThat(live.getCloseReason()).isEqualTo(Order.CloseReason.REPLACED_BY_STRATEGY);
        verify(orderDataService, never()).save(any());
    }

    /**
     * Снятие встроенной защиты идёт СВОЕЙ командой со своим словарём
     * причин: адресат — раздел модели заявки, а не отдельная условная
     * заявка.
     */
    @Test
    void embeddedProtectionIsCancelledByItsOwnCommandAndVocabulary() {
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setId(70L);
        attached.setOrderId(100L);
        attached.setStatus(AttachedAlgoOrder.Status.ACTIVE);
        when(orderDataService.getRequiredAttachedById(70L)).thenReturn(attached);
        when(exchange.cancelAttachedProtection(eq(ACCOUNT), any(), eq(INSTRUMENT)))
                .thenReturn(ack(true, null, null));

        DealActionState row = row();
        cancelAttachedProtectionExecutor().execute(
                command(ServiceCommandType.CANCEL_ATTACHED_PROTECTION_COMMAND, row,
                        new CancelAttachedProtectionCommandPayload(70L,
                                AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY)),
                row, context(deal()));

        assertThat(attached.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(attached.getCloseReason()).isEqualTo(AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY);
        verify(orderDataService).saveAttached(attached);
    }

    /**
     * Закрытие позиции — всегда полное и по расчётной валюте инструмента;
     * запрошенная причина write-once, а закрытое состояние ставит добыча.
     */
    @Test
    void closingThePositionSendsFullCloseAndRecordsTheIntent() {
        Position live = new Position();
        live.setId(5L);
        live.setStatus(Position.Status.ACTIVE);
        live.setExternalSize(new BigDecimal("100"));
        when(positionDataService.getRequiredById(5L)).thenReturn(live);
        when(exchange.closePosition(ACCOUNT, INSTRUMENT, "USDT")).thenReturn(ack(true, null, null));

        DealActionState row = row();
        closePositionExecutor().execute(command(ServiceCommandType.CLOSE_POSITION_COMMAND, row,
                new ClosePositionCommandPayload(5L, Position.CloseReason.CLOSED_BY_STRATEGY)), row,
                context(deal()));

        assertThat(live.getStatus()).isEqualTo(Position.Status.ACTIVE);
        assertThat(live.getCloseReason()).isEqualTo(Position.CloseReason.CLOSED_BY_STRATEGY);
        verify(positionDataService).save(live);
    }

    /** Отклонённая площадкой команда снятия не пишет намерения вовсе. */
    @Test
    void rejectedCancelWritesNoIntent() {
        Order live = stored(new Order(), 100L);
        live.setStatus(Order.Status.ACTIVE);
        when(orderDataService.getRequiredById(100L)).thenReturn(live);
        when(exchange.cancelOrder(eq(ACCOUNT), any(), eq(INSTRUMENT))).thenReturn(ack(false, null, null));

        DealActionState row = row();
        ServiceCommandExecutionResult result = cancelOrderExecutor().execute(
                command(ServiceCommandType.CANCEL_ORDER_COMMAND, row,
                        new CancelOrderCommandPayload(100L, Order.CloseReason.KILL_SWITCH)), row, context(deal()));

        assertThat(result.getSuccess()).isFalse();
        assertThat(live.getCloseReason()).isNull();
    }

    /**
     * Постановка отдельной защиты меняет операнд четвёртого числа —
     * уровень действующей защиты, — поэтому исполнитель ЯВЛЯЕТСЯ писателем
     * четвёрки и кладёт защиту на свой транш той же транзакцией.
     */
    @Test
    void creatingAProtectionRecomputesTheFourNumbers() {
        Deal deal = deal();
        DealContext context = context(deal);
        DealActionState row = row();
        when(algoOrderDataService.save(any())).thenAnswer(call -> {
            AlgoOrder saved = call.getArgument(0);
            saved.setId(60L);
            return saved;
        });
        when(riskNumbersService.recompute(context)).thenReturn(true);

        createAlgoOrderExecutor().execute(createAlgoCommand(row), row, context);

        verify(riskNumbersService).recompute(context);
        assertThat(deal.getTranches().getFirst().getAlgoOrders()).hasSize(1);
        assertThat(row.getTargetEntityType()).isEqualTo(TargetEntityType.ALGO_ORDER);
    }

    /**
     * Денормализованная проекция рода условия обязана совпадать с самим
     * условием: разойдясь, она увела бы снятие на чужой эндпоинт — семья
     * условия резолвится именно по ней.
     */
    @Test
    void mismatchedConditionProjectionIsRejected() {
        DealContext context = context(deal());
        DealActionState row = row();
        ServiceCommand mismatched = command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND, row,
                CreateAlgoOrderCommandPayload.builder()
                        .dealTrancheId(TRANCHE)
                        .conditionType(AlgoOrder.ConditionType.STOP_LOSS)
                        .direction(AlgoOrder.Direction.SELL)
                        .positionReducingOnly(true)
                        .sizeContracts(new BigDecimal("10"))
                        .condition(new Condition(AlgoOrder.ConditionType.TAKE_PROFIT,
                                new Trigger(stopLossLeg(), null), null))
                        .build());

        assertThatThrownBy(() -> createAlgoOrderExecutor().execute(mismatched, row, context))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Повторная отправка условной заявки ищет её по стабильному
     * клиентскому идентификатору и второй раз не ставит: постановка могла
     * пройти, а ответ потеряться.
     */
    @Test
    void repeatedAlgoSubmitRecoversByClientId() {
        AlgoOrder protection = new AlgoOrder();
        protection.setId(60L);
        protection.setStatus(AlgoOrder.Status.CREATED);
        protection.setInternalId("vtbalgoclientid");
        AlgoOrder onExchange = new AlgoOrder();
        onExchange.setExternalId("algo-ext");
        when(algoOrderDataService.getRequiredById(60L)).thenReturn(protection);
        when(exchange.getAlgoOrder(ACCOUNT, INSTRUMENT, null, "vtbalgoclientid")).thenReturn(onExchange);

        DealActionState retried = row();
        retried.setAttemptCount(1);
        submitAlgoOrderExecutor().execute(command(ServiceCommandType.SUBMIT_ALGO_ORDER_COMMAND, retried,
                new SubmitAlgoOrderCommandPayload(60L)), retried, context(deal()));

        verify(exchange, never()).placeAlgoOrder(any(), any(), any());
        assertThat(protection.getExternalId()).isEqualTo("algo-ext");
        assertThat(protection.getStatus()).isEqualTo(AlgoOrder.Status.PENDING);
    }

    /**
     * Снятие отдельной защиты записывает намерение и НЕ финализирует
     * заявку: операнд «действующая защита» двигает наблюдённый факт, а не
     * наша команда.
     */
    @Test
    void cancellingAProtectionRecordsIntentWithoutFinalizing() {
        AlgoOrder protection = new AlgoOrder();
        protection.setId(60L);
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        when(algoOrderDataService.getRequiredById(60L)).thenReturn(protection);
        when(exchange.cancelAlgoOrder(eq(ACCOUNT), any(), eq(INSTRUMENT))).thenReturn(ack(true, null, null));

        DealActionState row = row();
        cancelAlgoOrderExecutor().execute(command(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND, row,
                        new CancelAlgoOrderCommandPayload(60L, AlgoOrder.CloseReason.CANCELED_BY_STRATEGY)),
                row, context(deal()));

        assertThat(protection.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
        assertThat(protection.getCloseReason()).isEqualTo(AlgoOrder.CloseReason.CANCELED_BY_STRATEGY);
    }

    private static ServiceCommand createAlgoCommand(DealActionState row) {
        return command(ServiceCommandType.CREATE_ALGO_ORDER_COMMAND, row,
                CreateAlgoOrderCommandPayload.builder()
                        .dealTrancheId(TRANCHE)
                        .conditionType(AlgoOrder.ConditionType.STOP_LOSS)
                        .direction(AlgoOrder.Direction.SELL)
                        .positionReducingOnly(true)
                        .sizeContracts(new BigDecimal("10"))
                        .condition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                                new Trigger(stopLossLeg(), null), null))
                        .build());
    }

    private static TriggerPrice stopLossLeg() {
        TriggerPrice price = new TriggerPrice();
        price.setType(AlgoOrder.TriggerPriceType.MARK);
        price.setValue(new BigDecimal("2910"));
        return price;
    }

    private static ExchangeAck ack(Boolean success, String externalId, OffsetDateTime acceptedAt) {
        return ExchangeAck.builder()
                .success(success)
                .externalId(externalId)
                .externalCreatedAt(acceptedAt)
                .message(Boolean.TRUE.equals(success) ? null : "rejected by venue")
                .build();
    }

    private CreateOrderExecutor createOrderExecutor() {
        return new CreateOrderExecutor(orderDataService, actionStateDataService, dealDataService,
                riskNumbersService, mock(CoreEventWriter.class));
    }

    /**
     * Плечо приезжает со строки ПАРЫ «счёт, инструмент»: у проекции
     * каталога колонки под него нет вовсе, и чтение оттуда молча не
     * выставляло бы плечо никогда.
     */
    private SubmitOrderExecutor submitOrderExecutor() {
        AccountInstrumentState pairState = new AccountInstrumentState();
        pairState.setLeverage(5);
        when(pairStateDataService.getRequiredByPair(any(), any())).thenReturn(pairState);
        return new SubmitOrderExecutor(orderDataService, actionStateDataService, dealDataService, exchange,
                pairStateDataService);
    }

    private CreateAlgoOrderExecutor createAlgoOrderExecutor() {
        return new CreateAlgoOrderExecutor(algoOrderDataService, actionStateDataService, riskNumbersService);
    }

    private SubmitAlgoOrderExecutor submitAlgoOrderExecutor() {
        return new SubmitAlgoOrderExecutor(algoOrderDataService, actionStateDataService, exchange);
    }

    private CancelAlgoOrderExecutor cancelAlgoOrderExecutor() {
        return new CancelAlgoOrderExecutor(algoOrderDataService, actionStateDataService, exchange);
    }

    private CancelOrderExecutor cancelOrderExecutor() {
        return new CancelOrderExecutor(orderDataService, actionStateDataService, exchange);
    }

    private CancelAttachedProtectionExecutor cancelAttachedProtectionExecutor() {
        return new CancelAttachedProtectionExecutor(orderDataService, actionStateDataService, exchange);
    }

    private ClosePositionExecutor closePositionExecutor() {
        return new ClosePositionExecutor(positionDataService, actionStateDataService, exchange);
    }

    private static ServiceCommand createCommand(DealActionState row) {
        return command(ServiceCommandType.CREATE_ORDER_COMMAND, row, CreateOrderCommandPayload.builder()
                .orderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS)
                .side(Order.Side.BUY)
                .sizeContracts(new BigDecimal("10"))
                .price(new BigDecimal("3000"))
                .sendPriceToExchange(true)
                .positionReducingOnly(false)
                .dealTrancheId(TRANCHE)
                .plannedEntryPrice(new BigDecimal("3000"))
                .plannedStopPrice(new BigDecimal("2910"))
                .plannedRiskAmount(new BigDecimal("100"))
                .plannedRiskCurrency("USDT")
                .plannedContractValue(new BigDecimal("0.1"))
                .bookDepthAtPlacement(new BigDecimal("1200"))
                .attachedProtection(AttachedProtectionPayload.builder()
                        .attachedType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS)
                        .stopLossTriggerPrice(new BigDecimal("2910"))
                        .triggerPriceType(AlgoOrder.TriggerPriceType.MARK)
                        .size(new BigDecimal("10"))
                        .build())
                .build());
    }

    private static ServiceCommand submitCommand(DealActionState row) {
        return command(ServiceCommandType.SUBMIT_ORDER_COMMAND, row, new SubmitOrderCommandPayload(100L));
    }

    private static ServiceCommand command(ServiceCommandType type, DealActionState row,
                                          ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(DEAL)
                .dealActionStateId(row.getId())
                .payload(payload)
                .build();
    }

    private static Order stored(Order order, Long id) {
        order.setId(id);
        order.setDealTrancheId(TRANCHE);
        return order;
    }

    private static DealActionState row() {
        DealActionState row = new DealActionState();
        row.setId(42L);
        row.setDealId(DEAL);
        row.setDealTrancheId(TRANCHE);
        row.setActionKind(ActionKind.STRATEGY);
        row.setStrategyActionId(11L);
        row.setStatus(DealActionStateStatus.PLANNED);
        return row;
    }

    private static Deal deal() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE);
        tranche.setStatus(DealTranche.Status.PRECHECK);
        Deal deal = new Deal();
        deal.setId(DEAL);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setTranches(List.of(tranche));
        return deal;
    }

    private static DealContext context(Deal deal) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(3L);
        account.setInternalId(ACCOUNT);
        account.setRiskBase(new BigDecimal("5000"));
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setExternalId(INSTRUMENT);
        instrument.setExternalSettlementCurrency("USDT");
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .graphComplete(true)
                .build();
    }
}
