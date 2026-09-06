package com.example.tradingcore.domain.command.strategy;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommand;
import com.example.tradingcore.domain.command.ServiceCommandType;
import com.example.tradingcore.domain.command.payload.CancelOrderCommandPayload;
import com.example.tradingcore.domain.command.payload.ClosePositionCommandPayload;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Исполняет действие ВЫХОДА, объявленное шагом стратегии
 * (docs/components/ExitActionExecutor.md), — по одной команде за проход.
 *
 * <p><b>Выход — не одна команда.</b> Команда закрытия закрывает позицию, и
 * всё; в стратегии закрытие одной позиции самостоятельного смысла не
 * имеет. Осмысленное действие — выход, и отмена живых входных заявок
 * входит в его состав, а не является внешней дочисткой.
 *
 * <pre>
 * живые входные (не reduce-only) заявки есть → отмена этих заявок
 * живых входных заявок нет                   → закрытие экспозиции
 * команда закрытия отправлена                → дальше ведёт статус выхода
 * </pre>
 *
 * <p><b>Порядок задан инвариантом</b>
 * (docs/rules/exit-teardown-order.md): не-reduce-only нога, наливившаяся
 * ПОСЛЕ закрытия, открыла бы позицию заново — полное закрытие перестало
 * бы быть финальным ровно тогда, когда основная защита уже снимается.
 *
 * <p><b>Область «этих заявок» задаёт уровень объявления:</b> у выхода
 * транша — его собственные ноги, у выхода сделки — ноги всех траншей.
 * Собственного маркера области у действия нет — он был бы второй копией
 * уровня, который уже читается носителем шага.
 *
 * <p><b>Reduce-only ноги под отмену не идут:</b> они риск снимают, а не
 * создают, и их дочищает обработчик выхода уже после подтверждённого
 * закрытия.
 *
 * <p>Преконтроль риска здесь не зовётся: выход риск снимает.
 */
@Component
@RequiredArgsConstructor
public class ExitActionExecutor implements StrategyActionExecutor {

    @Override
    public Boolean supports(StrategyAction action) {
        return action instanceof StrategyPositionAction
                && StrategyActionType.EXIT_ACTION.equals(action.getActionType());
    }

    @Override
    public ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state,
                           DealContext dealContext, DealTranche tranche) {
        List<Order> liveEntries = liveEntryLegs(dealContext, tranche);
        if (isFalse(liveEntries.isEmpty())) {
            return cancel(liveEntries.get(0), state);
        }
        Position position = dealContext.getDeal().livePosition();
        if (isNull(position) || isFalse(isTrue(position.hasLiveRisk()))) {
            return ActionPlan.nothing();
        }
        return close(position, state);
    }

    /**
     * Живые входные ноги области выхода: свои у выхода транша, всех
     * траншей — у выхода сделки. Уровень читается по наличию транша: он и
     * есть носитель уровня объявления.
     */
    private List<Order> liveEntryLegs(DealContext dealContext, DealTranche tranche) {
        List<Order> live = nonNull(tranche) ? tranche.liveOrders() : dealLiveOrders(dealContext.getDeal());
        return emptyIfNull(live).stream()
                .filter(order -> isFalse(isTrue(order.getPositionReducingOnly())))
                .collect(Collectors.toList());
    }

    /**
     * Живые ноги всей сделки — обходом траншей.
     *
     * <p>Поля {@code Deal.orders} в целевой модели нет: нога висит на
     * транше (docs/models/domain/aggregate/Deal.md §Структура).
     */
    private List<Order> dealLiveOrders(Deal deal) {
        return emptyIfNull(deal.getTranches()).stream()
                .flatMap(tranche -> emptyIfNull(tranche.liveOrders()).stream())
                .collect(Collectors.toList());
    }

    /**
     * Отмена входной ноги — <b>дочистка без анкера</b>: бюджета отказов у
     * неё нет, и повтор ведёт сам проход
     * (docs/components/models/ServiceCommand.md).
     */
    private ActionPlan cancel(Order order, DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CANCEL_ORDER_COMMAND)
                .dealId(state.getDealId())
                .payload(new CancelOrderCommandPayload(order.getId(), Order.CloseReason.CANCELED_BY_STRATEGY))
                .build());
    }

    private ActionPlan close(Position position, DealActionState state) {
        return ActionPlan.of(ServiceCommand.builder()
                .type(ServiceCommandType.CLOSE_POSITION_COMMAND)
                .dealId(state.getDealId())
                .payload(new ClosePositionCommandPayload(position.getId(), Position.CloseReason.CLOSED_BY_STRATEGY))
                .build());
    }
}
