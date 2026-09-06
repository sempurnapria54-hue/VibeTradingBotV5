package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.ResolvedStopLossPrice;
import com.example.strategy.engine.calc.ResolvedTakeProfitPrice;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.ActionReadiness;
import com.example.tradingcore.domain.command.strategy.ActionRiskGate;
import com.example.tradingcore.domain.command.strategy.CancelAlgoOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.CreateAlgoOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.CreateOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.ExitActionExecutor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Команды, которые выдают исполнители типов действия.
 *
 * <p><b>Предмет — состав команды и порядок стадий, а не факт вызова.</b>
 * Защитная заявка без триггерной цены уходит на площадку неисполнимой;
 * закрытие позиции раньше отмены живой входной ноги перестаёт быть
 * финальным, потому что нога наливается после него и открывает позицию
 * заново (docs/rules/exit-teardown-order.md).
 */
class ActionCommandPlanTest {

    private static final Long DEAL_ID = 7L;
    private static final Long TRANCHE_ID = 21L;
    private static final Long STATE_ID = 55L;

    private final CalculationContextFactory contextFactory = mock(CalculationContextFactory.class);
    private final StrategyActionCalculator calculator = mock(StrategyActionCalculator.class);
    private final ActionRiskGate riskGate = mock(ActionRiskGate.class);

    private final CreateOrderActionExecutor orderExecutor =
            new CreateOrderActionExecutor(contextFactory, calculator, riskGate);
    private final CreateAlgoOrderActionExecutor algoExecutor =
            new CreateAlgoOrderActionExecutor(contextFactory, calculator, riskGate);
    private final CancelAlgoOrderActionExecutor cancelExecutor = new CancelAlgoOrderActionExecutor(riskGate);
    private final ExitActionExecutor exitExecutor = new ExitActionExecutor();

    @BeforeEach
    void setUp() {
        when(contextFactory.build(any(), any(), any())).thenReturn(context());
        when(riskGate.gate(any(), any(), any())).thenReturn(Optional.empty());
        when(riskGate.gateProtectionRemoval(any(), any(), any())).thenReturn(Optional.empty());
    }

    /**
     * Вход: шесть чисел планового риска едут вместе с командой заведения.
     *
     * <p>Инвариант «шесть или ни одного» стои́т на том, что их производит
     * один преконтроль и пишет одна транзакция
     * (docs/models/domain/core/Order.md).
     */
    @Test
    void entryCarriesTheSixPlannedNumbers() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(calculatedEntry()));

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entryAction(), planned(),
                dealContext(), tranche());

        assertThat(plan.hasCommand()).isTrue();
        CreateOrderCommandPayload payload = (CreateOrderCommandPayload) plan.getCommand().getPayload();
        assertThat(payload.getSide()).isEqualTo(Order.Side.BUY);
        assertThat(payload.getPlannedEntryPrice()).isEqualByComparingTo("100");
        assertThat(payload.getPlannedStopPrice()).isEqualByComparingTo("90");
        assertThat(payload.getPlannedContractValue()).isEqualByComparingTo("1");
        assertThat(payload.getPlannedRiskCurrency()).isEqualTo("USDT");
        assertThat(payload.getPlannedRiskAmount()).isNotNull();
        assertThat(payload.getBookDepthAtPlacement()).isEqualByComparingTo("40");
    }

    /**
     * Reduce-only нога плановых чисел не несёт и преконтроля не проходит:
     * она риск снимает, а не создаёт.
     */
    @Test
    void reduceOnlyLegCarriesNoPlannedNumbers() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(calculatedEntry()));
        StrategyOrderAction action = entryAction();
        action.setPositionReducingOnly(true);

        ActionPlan plan = orderExecutor.next(new StrategyStep(), action, planned(), dealContext(), tranche());

        CreateOrderCommandPayload payload = (CreateOrderCommandPayload) plan.getCommand().getPayload();
        assertThat(payload.getSide()).isEqualTo(Order.Side.SELL);
        assertThat(payload.getPlannedRiskAmount()).isNull();
        assertThat(payload.getPlannedStopPrice()).isNull();
    }

    /** Отказ расчёта приходит планом-ошибкой, а не исключением наружу. */
    @Test
    void calculationRefusalBecomesAPlan() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.error(
                CalculationError.temporary("NO_REFERENCE_PRICE", "no price")));

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entryAction(), planned(),
                dealContext(), tranche());

        assertThat(plan.hasCalculationError()).isTrue();
        assertThat(plan.hasCommand()).isFalse();
    }

    /** Блокирующая реакция преконтроля отменяет команду. */
    @Test
    void blockingPrecheckReplacesTheCommand() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(calculatedEntry()));
        when(riskGate.gate(any(), any(), any())).thenReturn(Optional.of(ActionPlan.blocked(
                RiskBlockAction.builder().type(RiskBlockAction.Type.SKIP_ACTION).build())));

        ActionPlan plan = orderExecutor.next(new StrategyStep(), entryAction(), planned(),
                dealContext(), tranche());

        assertThat(plan.isBlocked()).isTrue();
        assertThat(plan.hasCommand()).isFalse();
    }

    /** Стадии ноги идут по подтверждённым фактам: завести, отправить, добыть. */
    @Test
    void legAdvancesByConfirmedFacts() {
        DealActionState created = planned();
        created.setStatus(DealActionStateStatus.CREATED);
        created.targetAt(TargetEntityType.ORDER, 900L);

        assertThat(orderExecutor.next(new StrategyStep(), entryAction(), created, dealContext(), tranche())
                .getCommand().getType()).isEqualTo(ServiceCommandType.SUBMIT_ORDER_COMMAND);

        created.setStatus(DealActionStateStatus.SUBMITTED);

        assertThat(orderExecutor.next(new StrategyStep(), entryAction(), created, dealContext(), tranche())
                .getCommand().getType()).isEqualTo(ServiceCommandType.REFRESH_ORDER_COMMAND);
    }

    /**
     * Полный OCO заполняет ОБЕ ноги условия, стоп — только свою.
     *
     * <p>Нога, которой тип не несёт, остаётся пустой: подставить туда чужую
     * цену значило бы выпустить защиту на уровень, которого стратегия не
     * объявляла.
     */
    @Test
    void conditionTreeFollowsTheDeclaredType() {
        when(calculator.calculate(any())).thenReturn(StrategyActionCalculationResult.success(calculatedProtection()));

        CreateAlgoOrderCommandPayload oco = payloadOf(algoExecutor.next(new StrategyStep(),
                protectionAction(AlgoOrder.ConditionType.OCO_FULL), planned(), dealContext(), tranche()));
        assertThat(oco.getCondition().getTrigger().getStopLoss()).isNotNull();
        assertThat(oco.getCondition().getTrigger().getTakeProfit()).isNotNull();
        assertThat(oco.getDirection()).isEqualTo(AlgoOrder.Direction.SELL);
        assertThat(oco.getPositionReducingOnly()).isTrue();

        CreateAlgoOrderCommandPayload stop = payloadOf(algoExecutor.next(new StrategyStep(),
                protectionAction(AlgoOrder.ConditionType.STOP_LOSS), planned(), dealContext(), tranche()));
        assertThat(stop.getCondition().getTrigger().getStopLoss()).isNotNull();
        assertThat(stop.getCondition().getTrigger().getTakeProfit()).isNull();
    }

    /**
     * Снятие без живой цели — НЕАКТУАЛЬНО, а не отложено: ждать нечего, и
     * отложение остановило бы пакет навсегда.
     */
    @Test
    void cancelWithoutALiveTargetIsIrrelevant() {
        StrategyAlgoOrderAction cancel = protectionAction(AlgoOrder.ConditionType.STOP_LOSS);
        cancel.setActionType(StrategyActionType.CANCEL_ACTION);
        cancel.setTargetActionKey("nobody");

        assertThat(cancelExecutor.readiness(cancel, dealContext(), tranche()))
                .isEqualTo(ActionReadiness.IRRELEVANT);
        assertThat(cancelExecutor.next(new StrategyStep(), cancel, planned(), dealContext(), tranche()).isEmpty())
                .isTrue();
    }

    /**
     * Выход отменяет живую входную ногу РАНЬШЕ закрытия экспозиции.
     *
     * <p>Обратный порядок оставляет не-reduce-only ногу живой: наливившись
     * после закрытия, она открывает позицию заново — полное закрытие
     * перестаёт быть финальным.
     */
    @Test
    void exitCancelsLiveEntryLegBeforeClosing() {
        DealTranche tranche = tranche();
        tranche.setOrders(List.of(liveEntryLeg()));
        DealContext context = dealContext(tranche);

        ActionPlan first = exitExecutor.next(new StrategyStep(), exitAction(), planned(), context, tranche);
        assertThat(first.getCommand().getType()).isEqualTo(ServiceCommandType.CANCEL_ORDER_COMMAND);
        assertThat(((CancelOrderCommandPayload) first.getCommand().getPayload()).getOrderId()).isEqualTo(800L);

        tranche.setOrders(List.of());

        ActionPlan second = exitExecutor.next(new StrategyStep(), exitAction(), planned(), context, tranche);
        assertThat(second.getCommand().getType()).isEqualTo(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    /** Reduce-only нога под отмену выхода не идёт: она риск снимает. */
    @Test
    void exitDoesNotCancelReduceOnlyLegs() {
        DealTranche tranche = tranche();
        Order reducing = liveEntryLeg();
        reducing.setPositionReducingOnly(true);
        tranche.setOrders(List.of(reducing));

        ActionPlan plan = exitExecutor.next(new StrategyStep(), exitAction(), planned(), dealContext(tranche),
                tranche);

        assertThat(plan.getCommand().getType()).isEqualTo(ServiceCommandType.CLOSE_POSITION_COMMAND);
    }

    private CreateAlgoOrderCommandPayload payloadOf(ActionPlan plan) {
        return (CreateAlgoOrderCommandPayload) plan.getCommand().getPayload();
    }

    private CalculationContext context() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalContractValue("1");
        rules.setExternalTakerFeeRate("0.0005");
        Instrument instrument = new Instrument();
        instrument.setId(5L);
        instrument.setExternalSettlementCurrency("USDT");
        MarketPriceData prices = new MarketPriceData();
        prices.setExternalAskSize(new BigDecimal("40"));
        prices.setExternalBidSize(new BigDecimal("30"));
        return CalculationContext.builder()
                .instrument(instrument)
                .instrumentExternalRules(rules)
                .marketPriceData(prices)
                .strategyDirection(StrategyTradeDirection.LONG)
                .build();
    }

    private CalculatedStrategyAction calculatedEntry() {
        return CalculatedStrategyAction.builder()
                .sourceAction(entryAction())
                .calculatedPrice(CalculatedPrice.builder()
                        .roundedPrice(new BigDecimal("100"))
                        .sendPriceToExchange(true)
                        .stopLossPrice(ResolvedStopLossPrice.builder()
                                .triggerPrice(new BigDecimal("90"))
                                .triggerPriceType(AlgoOrder.TriggerPriceType.LAST)
                                .build())
                        .build())
                .calculatedSize(CalculatedSize.builder().sizeContracts(new BigDecimal("3")).build())
                .build();
    }

    private CalculatedStrategyAction calculatedProtection() {
        return CalculatedStrategyAction.builder()
                .sourceAction(protectionAction(AlgoOrder.ConditionType.OCO_FULL))
                .calculatedPrice(CalculatedPrice.builder()
                        .stopLossPrice(ResolvedStopLossPrice.builder()
                                .triggerPrice(new BigDecimal("90"))
                                .triggerPriceType(AlgoOrder.TriggerPriceType.LAST)
                                .build())
                        .takeProfitPrice(ResolvedTakeProfitPrice.builder()
                                .triggerPrice(new BigDecimal("120"))
                                .triggerPriceType(AlgoOrder.TriggerPriceType.LAST)
                                .build())
                        .build())
                .calculatedSize(CalculatedSize.builder().sizeContracts(new BigDecimal("3")).build())
                .build();
    }

    private StrategyOrderAction entryAction() {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(103L);
        action.setKey("entry");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setDirection(StrategyTradeDirection.LONG);
        return action;
    }

    private StrategyAlgoOrderAction protectionAction(AlgoOrder.ConditionType type) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setId(101L);
        action.setKey("protection");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setConditionType(type);
        return action;
    }

    private StrategyPositionAction exitAction() {
        StrategyPositionAction action = new StrategyPositionAction();
        action.setId(105L);
        action.setKey("exit");
        action.setActionType(StrategyActionType.EXIT_ACTION);
        return action;
    }

    private Order liveEntryLeg() {
        Order order = new Order();
        order.setId(800L);
        order.setStatus(Order.Status.ACTIVE);
        order.setPositionReducingOnly(false);
        return order;
    }

    private DealActionState planned() {
        DealActionState state = new DealActionState();
        state.setId(STATE_ID);
        state.setDealId(DEAL_ID);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }

    private DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(TRANCHE_ID);
        tranche.setEpisodeSeq(1);
        tranche.setOrders(List.of());
        tranche.setAlgoOrders(List.of());
        return tranche;
    }

    private DealContext dealContext() {
        return dealContext(tranche());
    }

    private DealContext dealContext(DealTranche tranche) {
        Position live = new Position();
        live.setId(700L);
        live.setStatus(Position.Status.ACTIVE);
        live.setExternalSize(new BigDecimal("3"));
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setTranches(List.of(tranche));
        deal.setPositions(List.of(live));
        return DealContext.builder()
                .deal(deal)
                .actionStates(new ArrayList<>())
                .build();
    }
}
