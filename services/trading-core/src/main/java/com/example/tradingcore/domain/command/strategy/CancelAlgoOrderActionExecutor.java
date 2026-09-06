package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.command.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.RefreshAlgoOrderCommandPayload;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Планирует снятие отдельной защиты, объявленное стратегией
 * (docs/components/CancelAlgoOrderActionExecutor.md).
 *
 * <pre>
 * PLANNED   → преконтроль снятия → CANCEL_ALGO_ORDER_COMMAND
 * SUBMITTED → REFRESH_ALGO_ORDER_COMMAND
 * </pre>
 *
 * <p><b>Стадии {@code CREATED} у снятия нет:</b> локальной сущности оно не
 * создаёт, а факт снятия подтверждает добыча, а не приём команды
 * (docs/rules/ack-not-runtime-truth.md).
 *
 * <p><b>Преконтроль стои́т в двух местах, и это не дубль.</b> Гейт
 * готовности отвечает ДО строки исполнения: отложенное действие
 * исполнения не начинает вовсе, и бюджет попыток не расходуется. Проверка
 * на самой команде отвечает МЕЖДУ проходами: строка живёт дольше одного
 * прохода, а покрытие за это время меняется.
 *
 * <p><b>Резолв цели по корню цепочки замещений здесь не воплощён</b> —
 * названное ограничение: цепочек в рантайме не существует, пока фабрика
 * замещающих ног их не порождает.
 */
@Component
@RequiredArgsConstructor
public class CancelAlgoOrderActionExecutor implements StrategyActionExecutor {

    private final ActionRiskGate riskGate;

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyAlgoOrderAction
                && StrategyActionType.CANCEL_ACTION.equals(action.getActionType());
    }

    /**
     * Цели среди живых защит нет — снимать нечего: действие неактуально, и
     * пакет берёт следующее. Ждать тут нечего, поэтому исход
     * {@code IRRELEVANT}, а не {@code DEFERRED}: отложение остановило бы
     * пакет навсегда.
     */
    @Override
    public ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        return targetAlgoOrder(action, dealContext, tranche)
                .map(algoOrder -> ActionReadiness.READY)
                .orElse(ActionReadiness.IRRELEVANT);
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        return switch (state.getStatus()) {
            case PLANNED -> planCancel(action, state, dealContext, tranche);
            case SUBMITTED -> refresh(state);
            default -> ActionPlan.nothing();
        };
    }

    private ActionPlan planCancel(StrategyAction action, DealActionState state, DealContext dealContext,
                                  DealTranche tranche) {
        AlgoOrder target = targetAlgoOrder(action, dealContext, tranche).orElse(null);
        if (isNull(target)) {
            return ActionPlan.nothing();
        }
        Optional<ActionPlan> blocked = riskGate.gateProtectionRemoval(dealContext, tranche, target.getId());
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CANCEL_ALGO_ORDER_COMMAND)
                .dealId(dealContext.getDeal().getId())
                .dealActionStateId(state.getId())
                .payload(new CancelAlgoOrderCommandPayload(target.getId(), AlgoOrder.CloseReason.CANCELED_BY_STRATEGY))
                .build());
    }

    private ActionPlan refresh(DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.REFRESH_ALGO_ORDER_COMMAND)
                .dealId(state.getDealId())
                .dealActionStateId(state.getId())
                .payload(new RefreshAlgoOrderCommandPayload(state.getTargetEntityId()))
                .build());
    }

    /**
     * Цель снятия: ключ объявления → строка исполнения целевого действия
     * на текущем эпизоде транша → живая отдельная защита транша.
     *
     * <p><b>Резолв идёт через строку исполнения, а не через объявление
     * напрямую:</b> объявление называет, что ставилось, а какая именно
     * сущность из этого вышла, знает только строка — она и несёт цель.
     */
    private Optional<AlgoOrder> targetAlgoOrder(StrategyAction action, DealContext dealContext,
                                                DealTranche tranche) {
        StrategyAction target = targetDeclaration(action, dealContext);
        if (isNull(target) || isNull(tranche)) {
            return Optional.empty();
        }
        DealActionState state = dealContext.actionState(target.getId(), tranche).orElse(null);
        if (isNull(state) || isFalse(targetsAlgoOrder(state))) {
            return Optional.empty();
        }
        return emptyIfNull(tranche.liveAlgoOrders()).stream()
                .filter(algoOrder -> Objects.equals(algoOrder.getId(), state.getTargetEntityId()))
                .findFirst();
    }

    private StrategyAction targetDeclaration(StrategyAction action, DealContext dealContext) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        return isNull(detail) ? null : detail.actionByKey(action.getTargetActionKey());
    }

    /** Строка целевого действия завела именно условную заявку, и цель у неё есть. */
    private Boolean targetsAlgoOrder(DealActionState state) {
        return nonNull(state.getTargetEntityId())
                && Objects.equals(TargetEntityType.ALGO_ORDER, state.getTargetEntityType());
    }
}
