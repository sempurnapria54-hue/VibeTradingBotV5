package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.RefreshAlgoOrderCommandPayload;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass executor CANCEL-действия над standalone algo-order: снимает
 * защиту, объявленную стратегией ({@code StrategyAlgoOrderAction} +
 * {@code actionType = CANCEL}). По стадии {@link DealActionState}:
 *
 * <pre>{@code
 * PLANNED   -> преконтроль снятия -> CANCEL_ALGO_ORDER
 * SUBMITTED -> REFRESH_ALGO_ORDER (факт снятия подтверждает добыча, не ACK)
 * }</pre>
 *
 * <p><b>Снятие проходит преконтроль по ветке ослабления защиты</b>
 * (docs/rules/risk-validator-scope.md): снятие защиты при живой
 * экспозиции риск не снимает, а увеличивает. Отказ по покрытию —
 * ОТЛОЖЕНИЕ, а не авария: действие не исполняется, транш остаётся в
 * своём статусе, позицию всё это время держит прежняя защита
 * (docs/rules/live-risk-protection.md §«Снятие защиты — риск-увеличивающее
 * действие»).
 *
 * <p><b>Цель снятия резолвится через строку исполнения целевого
 * действия</b> ({@code targetActionKey} → {@code DealActionState.target}).
 * Резолв по КОРНЮ ЦЕПОЧКИ ЗАМЕЩЕНИЙ (docs/spec/strategy-walkthrough.json,
 * величина {@code cancelTargetCandidates}) здесь не воплощён и это
 * названное ограничение: цепочек в рантайме не существует, пока фабрика
 * `REPLACE`-ног их не порождает.
 */
@Component
@RequiredArgsConstructor
public class CancelAlgoOrderActionExecutor implements StrategyActionExecutor {

    private final ActionRiskGate riskGate;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CANCEL.equals(action.getActionType());
    }

    /**
     * Гейт ДО строки исполнения: объявленной цели среди живых защит
     * транша нет — снимать нечего ({@code IRRELEVANT}); покрытие после
     * снятия ниже экспозиции — ждём ({@code DEFERRED}).
     */
    @Override
    public ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        AlgoOrder target = declaredTarget(action, dealContext, tranche);
        if (isNull(target)) {
            return ActionReadiness.IRRELEVANT;
        }
        return nonNull(riskGate.removalBlockingPlan(tranche, target.getId(), dealContext))
                ? ActionReadiness.DEFERRED
                : ActionReadiness.READY;
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                           DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> cancelCommand(action, state, dealContext, tranche);
            case SUBMITTED -> refreshCommand(action, state, dealContext, tranche);
            default -> ActionPlan.empty();
        };
    }

    /**
     * Преконтроль повторяется на самой команде, а не только в гейте
     * готовности: строка живёт между проходами, и покрытие между ними
     * меняется — защита-заместитель могла не подтвердиться либо уйти.
     */
    private ActionPlan cancelCommand(StrategyAction action, DealActionState state, DealContext dealContext,
                                     DealTranche tranche) {
        AlgoOrder target = declaredTarget(action, dealContext, tranche);
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        ActionPlan blocked = riskGate.removalBlockingPlan(tranche, target.getId(), dealContext);
        if (nonNull(blocked)) {
            return blocked;
        }
        return ActionPlan.command(command(ServiceCommandType.CANCEL_ALGO_ORDER, dealContext, state,
                new CancelAlgoOrderCommandPayload(target.getId(), AlgoOrder.CloseReason.CANCELED_BY_STRATEGY)));
    }

    /** Подтверждение снятия фактом: цель ещё жива локально — добываем её статус. */
    private ActionPlan refreshCommand(StrategyAction action, DealActionState state, DealContext dealContext,
                                      DealTranche tranche) {
        AlgoOrder target = declaredTarget(action, dealContext, tranche);
        if (isNull(target)) {
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.REFRESH_ALGO_ORDER, dealContext, state,
                new RefreshAlgoOrderCommandPayload(target.getId())));
    }

    /**
     * Живая отдельная защита транша, созданная целевым действием: ключ
     * цели → её строка исполнения на текущем эпизоде → runtime-цель.
     */
    private AlgoOrder declaredTarget(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        StrategyAction targetAction = dealContext.getStrategyDetail().actionByKey(action.getTargetActionKey());
        if (isNull(targetAction)) {
            return null;
        }
        Long targetEntityId = dealContext.actionState(targetAction.getId(), tranche)
                .map(DealActionState::getTarget)
                .map(RuntimeTarget::getEntityId)
                .orElse(null);
        if (isNull(targetEntityId)) {
            return null;
        }
        return tranche.liveAlgoOrders().stream()
                .filter(algoOrder -> Objects.equals(targetEntityId, algoOrder.getId()))
                .findFirst()
                .orElse(null);
    }

    private ServiceCommand command(ServiceCommandType type, DealContext dealContext, DealActionState state,
                                   ServiceCommandPayload payload) {
        return ServiceCommand.builder()
                .type(type)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(payload)
                .build();
    }
}
