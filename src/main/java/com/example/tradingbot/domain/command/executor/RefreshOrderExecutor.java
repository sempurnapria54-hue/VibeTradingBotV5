package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.payload.RefreshOrderCommandPayload;
import com.example.tradingbot.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingbot.domain.command.resolve.OrderExternalStatusResolver;
import com.example.tradingbot.domain.command.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.integration.service.ExternalStatusException;
import com.example.tradingbot.integration.service.ExternalStatusReason;
import com.example.tradingbot.integration.service.IntegrationService;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет REFRESH_ORDER, обходя order evidence-cycle внутри команды:
 * GET /trade/order → orders-pending → orders-history, обрыв на первом
 * найденном (матч по internalId/clOrdId). Не найден после полного цикла
 * → Order.ERROR + MISSING_AFTER_REFRESH (пустой ответ одного эндпоинта —
 * не основание). Найден → обновляет поля, применяет статус через
 * OrderExternalStatusResolver (unknown status → ERROR), резолвит attached
 * protection. ACK не использует. См.
 * docs/components/RefreshOrderExecutor.md,
 * docs/decisions/refresh-evidence-cycle-ownership.md.
 */
@Component
@RequiredArgsConstructor
public class RefreshOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;
    private final IntegrationService integrationService;
    private final OrderMapper orderMapper;
    private final OrderExternalStatusResolver orderStatusResolver;
    private final AttachedAlgoOrderStateResolver attachedStateResolver;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.REFRESH_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        RefreshOrderCommandPayload payload = (RefreshOrderCommandPayload) command.getPayload();
        Order order = orderDataService.getRequiredById(payload.getOrderId());
        OrderExternalSnapshot snapshot = findSnapshot(order, dealContext.getInstrument().getExternalId());
        if (isNull(snapshot)) {
            order.toError(Order.CloseReason.MISSING_AFTER_REFRESH);
        } else {
            applyOrUnknown(order, snapshot);
        }
        orderDataService.save(order);
        completeAction(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    /** Order evidence-cycle: single → pending → history; обрыв на первом найденном. */
    private OrderExternalSnapshot findSnapshot(Order order, String instId) {
        OrderExternalSnapshot single = integrationService.getOrder(instId, order.getExternalId(), order.getInternalId());
        if (nonNull(single)) {
            return single;
        }
        OrderExternalSnapshot pending = matchByInternalId(integrationService.getPendingOrders(instId),
                order.getInternalId());
        if (nonNull(pending)) {
            return pending;
        }
        return matchByInternalId(integrationService.getOrderHistory(instId), order.getInternalId());
    }

    private OrderExternalSnapshot matchByInternalId(List<OrderExternalSnapshot> snapshots, String internalId) {
        if (isEmpty(snapshots)) {
            return null;
        }
        return snapshots.stream()
                .filter(snapshot -> isNotBlank(snapshot.getInternalId())
                        && Objects.equals(internalId, snapshot.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    private void applyOrUnknown(Order order, OrderExternalSnapshot snapshot) {
        orderMapper.updateFromSnapshot(snapshot, order);
        try {
            applyStatus(order, snapshot);
            resolveAttached(order, snapshot);
        } catch (ExternalStatusException e) {
            order.toError(toCloseReason(e.getReasonCode()));
        }
    }

    private void applyStatus(Order order, OrderExternalSnapshot snapshot) {
        StatusResolveResult<Order.Status, Order.CloseReason> result =
                orderStatusResolver.resolve(snapshot.getExternalStatus());
        order.setStatus(result.getStatus());
        if (isNull(order.getCloseReason()) && nonNull(result.getCloseReason())) {
            order.setCloseReason(result.getCloseReason());
        }
    }

    private void resolveAttached(Order order, OrderExternalSnapshot snapshot) {
        if (isEmpty(order.getAttachedAlgoOrders())) {
            return;
        }
        order.getAttachedAlgoOrders().forEach(attached -> {
            StatusResolveResult<AttachedAlgoOrder.Status, AttachedAlgoOrder.CloseReason> result =
                    attachedStateResolver.resolve(matchAttached(snapshot, attached.getInternalId()), order.getStatus());
            attached.setStatus(result.getStatus());
            if (isNull(attached.getCloseReason()) && nonNull(result.getCloseReason())) {
                attached.setCloseReason(result.getCloseReason());
            }
        });
    }

    private AttachedAlgoOrderExternalSnapshot matchAttached(OrderExternalSnapshot snapshot, String internalId) {
        if (isEmpty(snapshot.getAttachedAlgoOrders())) {
            return null;
        }
        return snapshot.getAttachedAlgoOrders().stream()
                .filter(item -> isNotBlank(item.getInternalId()) && Objects.equals(internalId, item.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    private Order.CloseReason toCloseReason(ExternalStatusReason reason) {
        return ExternalStatusReason.UNKNOWN_EXTERNAL_STATUS.equals(reason)
                ? Order.CloseReason.UNKNOWN_EXTERNAL_STATUS
                : Order.CloseReason.UNKNOWN;
    }

    private void completeAction(DealActionState actionState) {
        if (nonNull(actionState)) {
            actionState.setStatus(DealActionStateStatus.COMPLETED);
            dealActionStateDataService.save(actionState);
        }
    }
}
