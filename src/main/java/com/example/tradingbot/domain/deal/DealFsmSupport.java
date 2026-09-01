package com.example.tradingbot.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.DealFinalizationCommandFactory;
import com.example.tradingbot.domain.command.DealFinalizationStateStatus;
import com.example.tradingbot.domain.command.DealFinalizationType;
import com.example.tradingbot.domain.command.Retryable;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.calc.CalculationError;
import com.example.tradingbot.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import com.example.tradingbot.domain.command.risk.RiskCheckResult;
import com.example.tradingbot.domain.deal.action.StrategyStepEligibility;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.safety.HoldSignal;
import com.example.tradingbot.domain.service.market.condition.ConditionEvaluationContext;
import com.example.tradingbot.domain.service.market.condition.StrategyConditionEvaluator;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.util.Constants;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Общие операции FSM handler'ов: выбор допустимых StrategyStep по статусу
 * сделки, оценка StrategyCondition, find-or-create DealActionState под
 * action (идемпотентность по UNIQUE(deal_id, strategy_action_id)), сборка
 * технических команд (REFRESH_BALANCE) и эмиссия финализационных команд
 * через фабрику. Оркестрацию последовательности держит сам handler.
 */
@Component
@RequiredArgsConstructor
public class DealFsmSupport {

    private final DealActionStateDataService dealActionStateDataService;
    private final StrategyConditionEvaluator conditionEvaluator;
    private final MarketConditionContextFactory conditionContextFactory;
    private final DealFinalizationCommandFactory finalizationFactory;
    private final IntegrationService integrationService;
    private final StrategyStepEligibility stepEligibility;

    /** Допустимые шаги стратегии для текущего статуса ТРАНША (упорядочены = приоритет). */
    public List<StrategyStep> stepsFor(DealContext dealContext, DealTranche.Status status) {
        List<StrategyStep> steps = dealContext.getStrategyDetail().getStepsByStatus().get(status);
        return nonNull(steps) ? steps : List.of();
    }

    /** Шаги указанных типов, в порядке объявления. */
    public List<StrategyStep> stepsOfType(List<StrategyStep> steps, StrategyStepType... types) {
        List<StrategyStepType> wanted = List.of(types);
        return steps.stream()
                .filter(step -> wanted.contains(step.getStepType()))
                .collect(Collectors.toList());
    }

    /** Условие шага выполнено по текущим рыночным данным. */
    public Boolean conditionMet(StrategyStep step, ConditionEvaluationContext context) {
        return conditionEvaluator.evaluate(step.getCondition(), context);
    }

    /**
     * Шаг допустим к применению: условие истинно, на текущем эпизоде
     * объекта шага он ещё не применён и повтор не гейтится стоящей
     * ступенью. Прежде проход проверял ОДНО условие — порог нижней
     * ступени остаётся истинным и после применения, поэтому first-match
     * выбирал бы её каждым проходом, а ступень выше не исполнялась бы ни
     * разу. Дом правила — docs/rules/strategy-step-once-per-episode.md.
     */
    public Boolean stepEligible(StrategyStep step, DealContext dealContext, DealTranche tranche,
                                ConditionEvaluationContext conditionContext) {
        return stepEligibility.eligible(step, tranche, dealContext.getActionStates(),
                conditionMet(step, conditionContext), standingRungOnActionRadius(dealContext));
    }

    /**
     * Стои́т ли ступень радиуса действия — операнд гейта повтора.
     *
     * <p>Область — ровно две ступени инструмента, мягкая и жёсткая
     * (docs/rules/strategy-step-once-per-episode.md §«Надобность после
     * исчерпания бюджета — гейт по стоящей ступени»). Связь «строка →
     * причина ступени» не хранится намеренно: ступень читается по
     * ИНСТРУМЕНТУ, и чужая ступень того же инструмента замораживает повтор
     * наравне со своей — направление консервативное, лишнее ожидание
     * вместо петли вызовов по неустранённой причине. Биржевой радиус в
     * область не входит (docs/rules/exchange-hold.md).
     */
    private Boolean standingRungOnActionRadius(DealContext dealContext) {
        return dealContext.getInstrument().hasStandingSafetyRung();
    }

    /** Контекст оценки условий (свежие/предыдущие индикаторы, структуры, цена). */
    public ConditionEvaluationContext conditionContext(DealContext dealContext) {
        return conditionContextFactory.build(dealContext.getInstrument());
    }

    /** Состояние выполнения потраншевого action: существующее либо новая PLANNED-строка. */
    public DealActionState findOrCreateActionState(DealContext dealContext, DealTranche tranche,
                                                   StrategyAction action) {
        return findActionState(dealContext, tranche, action)
                .orElseGet(() -> createPlanned(dealContext, tranche, action));
    }

    /**
     * Состояние выполнения потраншевого action из контекста прохода.
     * Отбор идёт парой «транш + номер эпизода»: переоткрытие ведётся ТЕМ
     * ЖЕ траншем, поэтому без номера эпизода строки прошлого эпизода
     * неотличимы от строк текущего и шаг читался бы применённым.
     */
    public Optional<DealActionState> findActionState(DealContext dealContext, DealTranche tranche,
                                                     StrategyAction action) {
        if (isEmpty(dealContext.getActionStates())) {
            return Optional.empty();
        }
        return dealContext.getActionStates().stream()
                .filter(state -> Objects.equals(action.getId(), state.getStrategyActionId()))
                .filter(state -> Objects.equals(tranche.getId(), state.getDealTrancheId()))
                .filter(state -> Objects.equals(tranche.getEpisodeSeq(), state.getTrancheEpisodeSeq()))
                .findFirst();
    }

    /**
     * Состояние выполнения АГРЕГАТНОГО action (шаг уровня сделки): транша
     * у него нет ни одного, и отбор идёт без него.
     */
    public Optional<DealActionState> findDealLevelActionState(DealContext dealContext, StrategyAction action) {
        if (isEmpty(dealContext.getActionStates())) {
            return Optional.empty();
        }
        return dealContext.getActionStates().stream()
                .filter(state -> Objects.equals(action.getId(), state.getStrategyActionId()))
                .filter(state -> isNull(state.getDealTrancheId()))
                .findFirst();
    }

    private DealActionState createPlanned(DealContext dealContext, DealTranche tranche,
                                          StrategyAction action) {
        DealActionState state = new DealActionState();
        state.setDealId(dealContext.getDeal().getId());
        state.setDealTrancheId(tranche.getId());
        state.setTrancheEpisodeSeq(tranche.getEpisodeSeq());
        state.setStrategyActionId(action.getId());
        state.setStatus(DealActionStateStatus.PLANNED);
        return dealActionStateDataService.save(state);
    }

    /** REFRESH_BALANCE по settle currency последнего снапшота (null — аккаунт целиком). */
    public ServiceCommand refreshBalanceCommand(DealContext dealContext) {
        return ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_BALANCE)
                .dealId(dealContext.getDeal().getId())
                .payload(new RefreshBalanceCommandPayload(settleCurrency(dealContext.getBalanceContainer())))
                .build();
    }

    /** Эмиссия финализационной команды (FINALIZE_* / MARK_*) через фабрику. */
    public Optional<ServiceCommand> finalizationCommand(DealFinalizationType type, DealContext dealContext) {
        return finalizationFactory.finalizationCommand(type, dealContext);
    }

    /** Финализация type исчерпала повторы (FAILED) — сделку на ошибочную тропу (DEAL-Q2). */
    public Boolean finalizationFailed(DealContext dealContext, DealFinalizationType type) {
        if (isEmpty(dealContext.getFinalizationStates())) {
            return false;
        }
        return dealContext.getFinalizationStates().stream()
                .anyMatch(state -> Objects.equals(type, state.getType())
                        && Objects.equals(DealFinalizationStateStatus.FAILED, state.getStatus()));
    }

    /** Системный REFRESH_POSITION (без action-state): обновить/обнаружить позицию по фактам. */
    public ServiceCommand refreshPositionCommand(DealContext dealContext) {
        return systemCommand(ServiceCommandType.REFRESH_POSITION, dealContext, null);
    }

    /** Системный REFRESH_ORDER по локальному order id (evidence-cycle внутри executor'а). */
    public ServiceCommand refreshOrderCommand(DealContext dealContext, Long orderId) {
        return systemCommand(ServiceCommandType.REFRESH_ORDER, dealContext, new RefreshOrderCommandPayload(orderId));
    }

    /** Системный REFRESH_ALGO_ORDER по локальному algo id (evidence-cycle внутри executor'а). */
    public ServiceCommand refreshAlgoOrderCommand(DealContext dealContext, Long algoOrderId) {
        return systemCommand(ServiceCommandType.REFRESH_ALGO_ORDER, dealContext,
                new RefreshAlgoOrderCommandPayload(algoOrderId));
    }

    /** Cleanup CLOSE_POSITION (full close, reduce-only) без action-state. */
    public ServiceCommand closePositionCommand(DealContext dealContext, Long positionId,
                                               Position.CloseReason reason) {
        return systemCommand(ServiceCommandType.CLOSE_POSITION, dealContext,
                new ClosePositionCommandPayload(positionId, reason));
    }

    /** Cleanup CANCEL_ORDER без action-state. */
    public ServiceCommand cancelOrderCommand(DealContext dealContext, Long orderId, Order.CloseReason reason) {
        return systemCommand(ServiceCommandType.CANCEL_ORDER, dealContext,
                new CancelOrderCommandPayload(orderId, reason));
    }

    /** Cleanup CANCEL_ALGO_ORDER без action-state. */
    public ServiceCommand cancelAlgoOrderCommand(DealContext dealContext, Long algoOrderId,
                                                 AlgoOrder.CloseReason reason) {
        return systemCommand(ServiceCommandType.CANCEL_ALGO_ORDER, dealContext,
                new CancelAlgoOrderCommandPayload(algoOrderId, reason));
    }

    private ServiceCommand systemCommand(ServiceCommandType type, DealContext dealContext,
                                         ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealContext.getDeal().getId())
                .payload(payload)
                .build();
    }

    /** Первый entry-ордер сделки (ENTRY / ENTRY_ATTACHED_STOP_LOSS), если есть. */
    public Order entryOrder(Deal deal) {
        if (isEmpty(deal.getOrders())) {
            return null;
        }
        return deal.getOrders().stream()
                .filter(order -> Objects.equals(Order.Type.ENTRY, order.getType())
                        || Objects.equals(Order.Type.ENTRY_ATTACHED_STOP_LOSS, order.getType()))
                .findFirst()
                .orElse(null);
    }

    /** Позиция сделки несёт live market risk. */
    public Boolean positionLiveRisk(Deal deal) {
        return deal.hasLivePositionRisk();
    }

    /** Live ordinary orders сделки. */
    public List<Order> liveOrders(Deal deal) {
        return deal.liveOrders();
    }

    /** Live algo orders сделки. */
    public List<AlgoOrder> liveAlgoOrders(Deal deal) {
        return deal.liveAlgoOrders();
    }

    /** Баланс пригоден к risk-sensitive flow (присутствует с доступным капиталом). */
    public Boolean balanceUsable(DealContext dealContext) {
        BalanceContainer balance = dealContext.getBalanceContainer();
        return nonNull(balance) && nonNull(balance.getExternalAvailableEquity());
    }

    /** Чужой live risk на инструменте при отсутствии локальной позиции сделки (Precheck-чистота). */
    public Boolean foreignLiveRisk(DealContext dealContext) {
        if (nonNull(dealContext.getDeal().livePosition())) {
            return false;
        }
        PositionExternalSnapshot snapshot = integrationService.getPosition(
                dealContext.getInstrument().getExternalId());
        return nonNull(snapshot) && nonNull(snapshot.getExternalSize())
                && snapshot.getExternalSize().signum() > 0;
    }

    /**
     * Превращает {@link ActionPlan} в {@link DealTransition}: команда → её
     * исполнение; risk-block → реакция resolver'а (закрыть кандидата /
     * MARK_DEAL_ERROR / refresh / skip); ошибка расчёта → ждать (retryable)
     * либо MARK_DEAL_ERROR. Ничего нет — остаться.
     */
    public DealTransition reactToPlan(ActionPlan plan, DealContext dealContext) {
        if (nonNull(plan.getCommand())) {
            return DealTransition.command(plan.getCommand());
        }
        if (nonNull(plan.getBlockAction())) {
            return reactToBlock(plan.getBlockAction(), dealContext);
        }
        if (nonNull(plan.getCalcError())) {
            return reactToCalcError(plan.getCalcError(), dealContext);
        }
        return DealTransition.stay();
    }

    /**
     * Реакция ТРАНША на план действия. Род реакции на блок даёт карта
     * вердикта (`docs/components/RiskBlockResolver.md`), и на траншевом
     * уровне применимы ровно два её исхода:
     *
     * <ul>
     *   <li>{@code CLOSE_CANDIDATE_DEAL} — терминал ТРАНША: отказ бессрочен,
     *       вход по этому объявлению не состоится. Сделка закрывается, когда
     *       так закрылись все её транши, и решает это её собственный проход;</li>
     *   <li>{@code SKIP_ACTION} — отказ временный: действие не исполняется,
     *       транш остаётся в своём статусе и ждёт следующего прохода. Именно
     *       эта ветвь сохраняет уровень сетки, отвергнутый занятым бюджетом:
     *       бюджет освободится выходом соседнего транша.</li>
     * </ul>
     *
     * <p>Увод СДЕЛКИ в ошибку транш не принимает — он его просит
     * ({@link TrancheTransition#escalateToDealError()}): статус сделки
     * пишет сделочный проход.
     */
    public TrancheTransition reactToTranchePlan(ActionPlan plan) {
        if (nonNull(plan.getCommand())) {
            return TrancheTransition.command(plan.getCommand());
        }
        if (nonNull(plan.getBlockAction())) {
            return reactToTrancheBlock(plan.getBlockAction());
        }
        return TrancheTransition.stay();
    }

    private TrancheTransition reactToTrancheBlock(RiskBlockAction block) {
        return switch (block.getType()) {
            case CLOSE_CANDIDATE_DEAL -> TrancheTransition.builder()
                    .nextStatus(DealTranche.Status.CLOSED)
                    .closeReason(DealTranche.CloseReason.RISK_CONTROL)
                    .build();
            case MOVE_DEAL_TO_ERROR -> TrancheTransition.escalateToDealError();
            case REQUEST_REFRESH, SKIP_ACTION -> TrancheTransition.stay();
            default -> TrancheTransition.stay();
        };
    }

    /**
     * Перевод сделки на ошибочную тропу: эмиссия MARK_DEAL_ERROR (executor ставит
     * ERROR). Если в истории сделки есть controlled-violation
     * (ControlledExchangeException → VALIDATION_ERROR), дополнительно поднимает
     * L4-холд биржи (controlled-exchange-exceptions: «Deal→ERROR; Exchange→HOLD»).
     */
    public DealTransition markError(DealContext dealContext) {
        return buildMarkError(dealContext, controlledViolationHold(dealContext));
    }

    /**
     * Как {@link #markError}, но при отсутствии controlled-violation поднимает
     * L3-холд инструмента: бесстоповая risk-creating позиция постфактум
     * (§8.C — уровень 3, docs/rules/instrument-hold.md). Controlled-violation,
     * если он есть, доминирует (L4).
     */
    public DealTransition markErrorStopless(DealContext dealContext) {
        HoldSignal controlled = controlledViolationHold(dealContext);
        HoldSignal hold = nonNull(controlled)
                ? controlled
                : HoldSignal.instrument(RiskCheckResult.RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP.name());
        return buildMarkError(dealContext, hold);
    }

    private DealTransition buildMarkError(DealContext dealContext, HoldSignal holdSignal) {
        return finalizationCommand(DealFinalizationType.MARK_ERROR, dealContext)
                .map(command -> DealTransition.builder().command(command).holdSignal(holdSignal).build())
                .orElseGet(() -> nonNull(holdSignal)
                        ? DealTransition.builder().holdSignal(holdSignal).build()
                        : DealTransition.stay());
    }

    /** L4-холд биржи, если среди retry-anchor'ов сделки есть controlled-violation; иначе null. */
    private HoldSignal controlledViolationHold(DealContext dealContext) {
        boolean controlled = isTrue(hasControlledViolation(dealContext.getActionStates()))
                || isTrue(hasControlledViolation(dealContext.getFinalizationStates()));
        return controlled ? HoldSignal.exchange(Constants.Hold.EXCHANGE_CONTROLLED_VIOLATION) : null;
    }

    /** Есть ли среди retry-anchor'ов ошибка класса VALIDATION_ERROR (= ControlledExchangeException). */
    private Boolean hasControlledViolation(List<? extends Retryable> retryables) {
        if (isEmpty(retryables)) {
            return false;
        }
        return retryables.stream().anyMatch(retryable -> nonNull(retryable.getLastError())
                && Objects.equals(RuntimeErrorCode.VALIDATION_ERROR, retryable.getLastError().getType()));
    }

    private DealTransition reactToBlock(RiskBlockAction block, DealContext dealContext) {
        return switch (block.getType()) {
            case CLOSE_CANDIDATE_DEAL -> DealTransition.builder()
                    .nextStatus(Deal.Status.CLOSED)
                    .closeReason(block.getCloseReason())
                    .build();
            case MOVE_DEAL_TO_ERROR -> markError(dealContext);
            case REQUEST_REFRESH -> DealTransition.command(refreshBalanceCommand(dealContext));
            default -> DealTransition.stay();
        };
    }

    private DealTransition reactToCalcError(CalculationError error, DealContext dealContext) {
        if (isTrue(error.getRetryable())) {
            return DealTransition.stay();
        }
        return markError(dealContext);
    }

    private String settleCurrency(BalanceContainer balanceContainer) {
        if (isNull(balanceContainer) || isEmpty(balanceContainer.getBalances())) {
            return null;
        }
        return balanceContainer.getBalances().stream()
                .map(Balance::getExternalCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
