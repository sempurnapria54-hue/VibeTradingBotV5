package com.example.tradingbot.domain.deal.action;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingbot.domain.command.payload.ClosePositionCommandPayload;
import com.example.tradingbot.domain.deal.ActionPlan;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Per-pass исполнитель ДЕЙСТВИЯ ВЫХОДА, объявленного шагом стратегии
 * ({@code StrategyPositionAction} + {@code EXIT_ACTION}).
 *
 * <p><b>Действие выхода — не одна команда.</b> Команда закрытия закрывает
 * позицию, и всё; осмысленное действие — выход, и отмена живых входных
 * заявок входит в его состав, а не является внешней дочисткой. Порядок
 * задан инвариантом docs/rules/exit-teardown-order.md:
 *
 * <pre>{@code
 * живые входные (не reduce-only) заявки есть → отмена этих заявок
 * живых входных заявок нет                   → закрытие экспозиции
 * команда закрытия отправлена                → дальше ведёт статус выхода
 * }</pre>
 *
 * <p><b>Область «этих заявок» задаёт уровень объявления, а не поле
 * действия:</b> у выхода транша — его собственные ноги, у выхода сделки
 * (узкая агрегатная поверхность, транша у строки нет) — ноги всех
 * траншей. Закрытие нетто-экспозиции одной командой законно только на
 * втором.
 *
 * <p><b>Стадия выводится из подтверждённых фактов, а не из счётчика
 * проходов:</b> исполнитель не ветвится по статусу строки, кроме
 * терминальных — он смотрит, что в его области ещё живо. Закрытый объём
 * никуда не записывается: он приписывается траншам правилом
 * сопоставления и считается производной
 * (docs/models/domain/aggregate/DealTranche.md).
 *
 * <p><b>Reduce-only ноги под отмену не идут:</b> они риск снимают, а не
 * создают, и их дочищает обработчик выхода уже после подтверждённого
 * закрытия. Преконтроль риска действие не проходит — оно риск снимает
 * (docs/rules/risk-validator-scope.md).
 *
 * <p>См. docs/components/ExitActionExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class ExitActionExecutor implements StrategyActionExecutor {

    private static final Set<DealActionStateStatus> TERMINAL_STAGES =
            EnumSet.of(DealActionStateStatus.COMPLETED, DealActionStateStatus.FAILED,
                    DealActionStateStatus.SKIPPED);

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyPositionAction
                && StrategyActionType.EXIT_ACTION.equals(action.getActionType());
    }

    /**
     * Гейт ДО строки исполнения: выходить не из чего — ни живой входной
     * ноги в области, ни живой экспозиции. Заводить строку под пустой
     * выход значило бы объявить шаг применённым, ничего не сделав.
     */
    @Override
    public ActionReadiness readiness(StrategyAction action, DealContext dealContext, DealTranche tranche) {
        return isNotEmpty(liveEntryLegs(dealContext, tranche)) || isTrue(hasLiveExposure(dealContext))
                ? ActionReadiness.READY
                : ActionReadiness.IRRELEVANT;
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext,
                           DealTranche tranche) {
        if (TERMINAL_STAGES.contains(state.getStatus())) {
            return ActionPlan.empty();
        }
        List<Order> entryLegs = liveEntryLegs(dealContext, tranche);
        if (isNotEmpty(entryLegs)) {
            return ActionPlan.command(command(ServiceCommandType.CANCEL_ORDER, dealContext, state,
                    new CancelOrderCommandPayload(entryLegs.getFirst().getId(),
                            Order.CloseReason.CANCELED_BY_STRATEGY)));
        }
        if (isFalse(hasLiveExposure(dealContext))) {
            // Закрывать нечего: команда закрытия отправлена раньше либо
            // экспозиция ушла сама — дальше ведёт статус выхода.
            return ActionPlan.empty();
        }
        return ActionPlan.command(command(ServiceCommandType.CLOSE_POSITION, dealContext, state,
                new ClosePositionCommandPayload(dealContext.getDeal().livePosition().getId(),
                        Position.CloseReason.CLOSED_BY_STRATEGY)));
    }

    /**
     * Живые ВХОДНЫЕ ноги области действия: свои у выхода транша, всех
     * траншей — у выхода сделки. Признак входа берётся у доменного
     * намерения заявки, а не у её типа: reduce-only ногу от входной
     * отличает именно оно.
     */
    private List<Order> liveEntryLegs(DealContext dealContext, DealTranche tranche) {
        List<Order> orders = isNull(tranche)
                ? emptyIfNull(dealContext.getDeal().getTranches()).stream()
                        .flatMap(item -> emptyIfNull(item.getOrders()).stream())
                        .collect(Collectors.toList())
                : emptyIfNull(tranche.getOrders()).stream().collect(Collectors.toList());
        return orders.stream()
                .filter(order -> isTrue(order.isLive()) && isFalse(order.getPositionReducingOnly()))
                .collect(Collectors.toList());
    }

    /** У сделки есть живой эпизод, несущий рыночный риск. */
    private Boolean hasLiveExposure(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        return deal.hasLivePositionRisk();
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
