package com.example.tradingbot.domain.deal.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.SystemActionType;
import com.example.tradingbot.domain.deal.DealFsmSupport;
import com.example.tradingbot.domain.deal.DealTransition;
import com.example.tradingbot.domain.deal.DealFsmHandler;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FSM handler статуса ERROR (non-terminal): обычная strategy/FSM-логика
 * заблокирована, разрешены только safety / recovery / проверка фактов.
 * Снимает live risk риск-минимизирующим порядком (закрыть позицию → отменить
 * orders → отменить algo-защиту последней), затем подтверждает факт снятия
 * через REFRESH_* (ACK не truth). Подтверждено отсутствие live risk →
 * ERROR → EMERGENCY_CLOSED (ошибочный терминал; resultProfit не блокирует,
 * DEAL-Q2). Safety-команды — без RiskValidator. См.
 * docs/components/ErrorHandler.md.
 */
@Component
@RequiredArgsConstructor
public class ErrorHandler implements DealFsmHandler {

    private final DealFsmSupport support;

    @Override
    public Deal.Status supportedStatus() {
        return Deal.Status.ERROR;
    }

    /**
     * Прямого ребра в аварийный терминал здесь нет, и это не пропуск:
     * терминал ставит звено аварийного действия — оно пишет число
     * best-effort, признаки отбора и причину закрытия той же
     * транзакцией, что и ребро
     * (docs/components/MarkDealEmergencyClosedExecutor.md). Прямой
     * переход опережал бы их и закрывал сделку без числа.
     */
    @Override
    public Optional<DealTransition> checkTransition(DealContext dealContext) {
        return Optional.empty();
    }

    @Override
    public DealTransition handle(DealContext dealContext) {
        Deal deal = dealContext.getDeal();
        if (isTrue(support.positionLiveRisk(deal))) {
            return reduceOrConfirmPosition(dealContext, deal.livePosition());
        }
        DealTransition orderSafety = reduceOrConfirmOrders(dealContext, support.liveOrders(deal));
        if (nonNull(orderSafety)) {
            return orderSafety;
        }
        DealTransition algoSafety = reduceOrConfirmAlgoOrders(dealContext, support.liveAlgoOrders(deal));
        if (nonNull(algoSafety)) {
            return algoSafety;
        }
        return emergencyTerminal(dealContext);
    }

    /**
     * Живой риск снят и подтверждён фактами — аварийный терминал звеном
     * аварийного действия. Исчерпание его бюджета терминал не отменяет и
     * второй тропы не открывает: из ошибочного состояния другого ребра
     * нет (docs/lifecycles/Deal.md).
     */
    private DealTransition emergencyTerminal(DealContext dealContext) {
        return support.systemAction(SystemActionType.FINALIZE_DEAL_ERROR_ACTION, dealContext, null)
                .map(DealTransition::command)
                .orElseGet(DealTransition::stay);
    }

    private DealTransition reduceOrConfirmPosition(DealContext dealContext, Position position) {
        if (isNull(position.getCloseReason())) {
            return DealTransition.command(support.closePositionCommand(dealContext, position.getId(),
                    Position.CloseReason.KILL_SWITCH));
        }
        // Закрытие уже запрошено — подтвердить flat фактами (ACK не truth).
        return support.refreshPositionCommand(dealContext)
                .map(DealTransition::command)
                .orElseGet(DealTransition::stay);
    }

    private DealTransition reduceOrConfirmOrders(DealContext dealContext, List<Order> liveOrders) {
        if (isEmpty(liveOrders)) {
            return null;
        }
        Order order = liveOrders.getFirst();
        if (isNull(order.getCloseReason())) {
            return DealTransition.command(support.cancelOrderCommand(dealContext, order.getId(),
                    Order.CloseReason.KILL_SWITCH));
        }
        return support.refreshOrderCommand(dealContext, order.getId())
                .map(DealTransition::command)
                .orElseGet(DealTransition::stay);
    }

    private DealTransition reduceOrConfirmAlgoOrders(DealContext dealContext, List<AlgoOrder> liveAlgoOrders) {
        if (isEmpty(liveAlgoOrders)) {
            return null;
        }
        AlgoOrder algoOrder = liveAlgoOrders.getFirst();
        if (isNull(algoOrder.getCloseReason())) {
            return DealTransition.command(support.cancelAlgoOrderCommand(dealContext, algoOrder.getId(),
                    AlgoOrder.CloseReason.KILL_SWITCH));
        }
        return support.refreshAlgoOrderCommand(dealContext, algoOrder.getId())
                .map(DealTransition::command)
                .orElseGet(DealTransition::stay);
    }
}
