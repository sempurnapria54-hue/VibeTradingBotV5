package com.example.tradingbot.domain.deal;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.ActionKind;
import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.command.action.SystemActionExecutor;
import com.example.tradingbot.domain.command.calc.CalculationError;
import com.example.tradingbot.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshBalanceCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.risk.RiskBlockAction;
import com.example.tradingbot.domain.deal.action.StrategyStepEligibility;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
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
 * транша и сделки, оценка StrategyCondition, find-or-create строки
 * исполнения под стратегийное действие, эмиссия звеньев СИСТЕМНЫХ
 * действий через {@link SystemActionExecutor} и сборка команд дочистки.
 * Оркестрацию последовательности держит сам handler.
 *
 * <p><b>Дочистка идёт напрямую, добыча и финализация — звеньями</b>:
 * отмены и закрытие позиции риск-снижающие и иерархии создания торгового
 * намерения не подчинены, поэтому анкера у них нет вовсе
 * (docs/components/SystemActionExecutor.md §«Дочистка звеном не
 * является»).
 */
@Component
@RequiredArgsConstructor
public class DealFsmSupport {

    private final DealActionStateDataService dealActionStateDataService;
    private final StrategyConditionEvaluator conditionEvaluator;
    private final MarketConditionContextFactory conditionContextFactory;
    private final SystemActionExecutor systemActionExecutor;
    private final IntegrationService integrationService;
    private final StrategyStepEligibility stepEligibility;

    /**
     * Допустимые шаги для текущего статуса ТРАНША (упорядочены =
     * приоритет). Шаги живут на ОБЪЯВЛЕНИИ транша, а не на детали: два
     * объявления одной фазы ведут свои входы по-разному, и общий набор
     * склеил бы их в один.
     *
     * <p>Объявления нет — шагов нет: так у восстановленного транша, и
     * это факт его тропы (ведётся safety-тропой), а не недогруженное
     * дерево.
     */
    public List<StrategyStep> stepsFor(DealContext dealContext, DealTranche tranche) {
        StrategyTranche declaration = dealContext.declarationOf(tranche);
        if (isNull(declaration) || isNull(declaration.getStepsByStatus())) {
            return List.of();
        }
        List<StrategyStep> steps = declaration.getStepsByStatus().get(tranche.getStatus());
        return nonNull(steps) ? steps : List.of();
    }

    /**
     * Шаги УЗКОЙ АГРЕГАТНОЙ ПОВЕРХНОСТИ для текущего статуса сделки —
     * `EXIT` и `FAIL_SAFE` уровня сделки. Они живут на детали, а не на
     * объявлении: выход есть утверждение обо всех траншах сразу.
     * Закреплённой детали нет (восстановленная сделка) — шагов нет ни
     * на одном уровне.
     */
    public List<StrategyStep> dealLevelSteps(DealContext dealContext) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        return isNull(detail) ? List.of() : detail.dealLevelSteps(dealContext.getDeal().getStatus());
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
     * Шаг СРАБОТАЛ — то есть сделал всё, что объявил. Читается по форме
     * объявления: у шага без действий (условная форма выхода) срабатывание
     * есть само истинное условие, у шага с пакетом — исчерпание пакета на
     * текущем эпизоде объекта.
     *
     * <p>Условие шага предикатом не проверяется: его проверяет
     * вызывающая сторона, и склеивать две проверки значило бы получить
     * «сработал» истинным у шага, чьё условие ложно, но пакет когда-то
     * отработал.
     */
    public Boolean stepEmpty(StrategyStep step) {
        return isEmpty(step.getActions());
    }

    /** Шаг сработал: пустой пакет — сразу, непустой — по исчерпании. */
    public Boolean stepFired(StrategyStep step, DealContext dealContext, DealTranche tranche) {
        return isTrue(stepEmpty(step))
                || isTrue(stepEligibility.appliedOnEpisode(step, tranche, dealContext.getActionStates()));
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
        return dealContext.actionState(action.getId(), tranche);
    }

    /**
     * Новая строка исполнения. Транш и номер эпизода пусты у исполнения
     * УРОВНЯ СДЕЛКИ: выбирать «носителем» один из N траншей было бы
     * произволом — выход есть утверждение обо всех сразу.
     */
    private DealActionState createPlanned(DealContext dealContext, DealTranche tranche,
                                          StrategyAction action) {
        DealActionState state = new DealActionState();
        state.setDealId(dealContext.getDeal().getId());
        state.setActionKind(ActionKind.STRATEGY);
        state.setDealTrancheId(nonNull(tranche) ? tranche.getId() : null);
        state.setTrancheEpisodeSeq(nonNull(tranche) ? tranche.getEpisodeSeq() : null);
        state.setStrategyActionId(action.getId());
        state.setStatus(DealActionStateStatus.PLANNED);
        DealActionState saved = dealActionStateDataService.save(state);
        // Контекст собран до этой строки, а анкер команды обязан резолвиться сразу.
        dealContext.register(saved);
        return saved;
    }

    /** Звено добычи баланса по settle currency последнего снапшота (null — аккаунт целиком). */
    public Optional<ServiceCommand> refreshBalanceCommand(DealContext dealContext) {
        return fetchLink(dealContext, ServiceCommandType.REFRESH_BALANCE_COMMAND,
                new RefreshBalanceCommandPayload(settleCurrency(dealContext.getBalanceContainer())));
    }

    /**
     * Очередное звено системного действия, выводимое из подтверждённых
     * фактов сделки (финализация, терминалы, цикл добычи). Пусто —
     * исполнять нечего: звено ждёт времени следующей попытки либо
     * надобности больше нет.
     */
    public Optional<ServiceCommand> systemAction(SystemActionType type, DealContext dealContext,
                                                 DealTranche tranche) {
        return systemActionExecutor.next(type, dealContext, tranche);
    }

    /**
     * Системное действие названного типа исчерпало бюджет попыток —
     * сделку на ошибочную тропу. Читается по строкам исполнения самой
     * сделки: отказавшая строка терминальна и живой уже не станет.
     */
    public Boolean systemActionFailed(DealContext dealContext, SystemActionType type) {
        return dealContext.systemActionStates(type).stream()
                .anyMatch(state -> DealActionStateStatus.FAILED.equals(state.getStatus()));
    }

    /**
     * Звено добычи позиции: обновить либо обнаружить эпизод по фактам.
     * Идёт анкером цикла добычи — у звена есть бюджет попыток и ревизия,
     * в отличие от прежней безанкерной эмиссии.
     */
    public Optional<ServiceCommand> refreshPositionCommand(DealContext dealContext) {
        return fetchLink(dealContext, ServiceCommandType.REFRESH_POSITION_COMMAND, null);
    }

    /**
     * Звено добычи движений средств окна сделки. Конвейер «свежий
     * эндпоинт → архив» и границу разбора ведёт сам исполнитель — здесь
     * только эмиссия звена. Звено ВЫХОДНОЙ тропы: на аварийной оно не
     * эмитится вовсе.
     */
    public Optional<ServiceCommand> refreshBillsCommand(DealContext dealContext) {
        return fetchLink(dealContext, ServiceCommandType.REFRESH_BILLS_COMMAND, null);
    }

    /** Граф сделки предъявлен контекстом целиком — признак читается ГОТОВЫМ. */
    public Boolean graphComplete(DealContext dealContext) {
        return isTrue(dealContext.getGraphComplete());
    }

    /**
     * У сделки есть живая строка исполнения УРОВНЯ СДЕЛКИ — различитель
     * форм полного выхода: явная форма (действие шага) строку заводит,
     * условная нет. Терминальные строки различителем не служат: строка
     * отработавшего выхода остаётся навсегда, и по ней условная форма
     * читалась бы явной у всякой сделки, однажды вышедшей действием.
     */
    public Boolean hasDealLevelExecution(DealContext dealContext) {
        return emptyIfNull(dealContext.getActionStates()).stream()
                .filter(state -> isNull(state.getDealTrancheId()))
                .anyMatch(state -> !DealActionStateStatus.SKIPPED.equals(state.getStatus()));
    }

    /** Звено добычи заявки по локальному идентификатору (evidence-cycle внутри исполнителя). */
    public Optional<ServiceCommand> refreshOrderCommand(DealContext dealContext, Long orderId) {
        return fetchLink(dealContext, ServiceCommandType.REFRESH_ORDER_COMMAND,
                new RefreshOrderCommandPayload(orderId));
    }

    /** Звено добычи условной заявки по локальному идентификатору. */
    public Optional<ServiceCommand> refreshAlgoOrderCommand(DealContext dealContext, Long algoOrderId) {
        return fetchLink(dealContext, ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND,
                new RefreshAlgoOrderCommandPayload(algoOrderId));
    }

    /**
     * Звено цикла добычи с ЯВНО названной сущностью: durable-факт
     * надобности прочитал вызывающий обработчик, и повторять его разбор
     * в исполнителе значило бы завести второго носителя одного решения.
     *
     * <p>Действие добычи — <b>агрегатное</b>: транша у него нет ни
     * одного, потому что добываются факты сделки целиком, а потраншевым
     * бывает единственное системное действие — консолидация входа транша
     * (docs/models/domain/other/DealActionState.md §Енумы).
     */
    private Optional<ServiceCommand> fetchLink(DealContext dealContext, ServiceCommandType link,
                                               ServiceCommandPayload payload) {
        return systemActionExecutor.next(SystemActionType.REFRESH_DEAL_CONTEXT_ACTION, dealContext, null,
                link, payload);
    }

    /** Cleanup CLOSE_POSITION (full close, reduce-only) без action-state. */
    public ServiceCommand closePositionCommand(DealContext dealContext, Long positionId,
                                               Position.CloseReason reason) {
        return systemCommand(ServiceCommandType.CLOSE_POSITION_COMMAND, dealContext,
                new ClosePositionCommandPayload(positionId, reason));
    }

    /** Cleanup CANCEL_ORDER без action-state. */
    public ServiceCommand cancelOrderCommand(DealContext dealContext, Long orderId, Order.CloseReason reason) {
        return systemCommand(ServiceCommandType.CANCEL_ORDER_COMMAND, dealContext,
                new CancelOrderCommandPayload(orderId, reason));
    }

    /** Cleanup CANCEL_ALGO_ORDER без action-state. */
    public ServiceCommand cancelAlgoOrderCommand(DealContext dealContext, Long algoOrderId,
                                                 AlgoOrder.CloseReason reason) {
        return systemCommand(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND, dealContext,
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

    private DealTransition buildMarkError(DealContext dealContext, HoldSignal holdSignal) {
        return systemAction(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, dealContext, null)
                .map(command -> DealTransition.builder().command(command).holdSignal(holdSignal).build())
                .orElseGet(() -> nonNull(holdSignal)
                        ? DealTransition.builder().holdSignal(holdSignal).build()
                        : DealTransition.stay());
    }

    /**
     * L4-холд биржи, если среди анкеров сделки есть controlled-violation;
     * иначе null. Анкер один на оба вида действий, поэтому и перечень
     * один — строки исполнения сделки.
     */
    private HoldSignal controlledViolationHold(DealContext dealContext) {
        List<DealActionState> states = dealContext.getActionStates();
        if (isEmpty(states)) {
            return null;
        }
        boolean controlled = states.stream().anyMatch(state -> nonNull(state.getLastError())
                && Objects.equals(RuntimeErrorCode.VALIDATION_ERROR, state.getLastError().getType()));
        return controlled ? HoldSignal.exchange(Constants.Hold.EXCHANGE_CONTROLLED_VIOLATION) : null;
    }

    private DealTransition reactToBlock(RiskBlockAction block, DealContext dealContext) {
        return switch (block.getType()) {
            case CLOSE_CANDIDATE_DEAL -> DealTransition.builder()
                    .nextStatus(Deal.Status.CLOSED)
                    .closeReason(block.getCloseReason())
                    .build();
            case MOVE_DEAL_TO_ERROR -> markError(dealContext);
            case REQUEST_REFRESH -> refreshBalanceCommand(dealContext)
                    .map(DealTransition::command)
                    .orElseGet(DealTransition::stay);
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
