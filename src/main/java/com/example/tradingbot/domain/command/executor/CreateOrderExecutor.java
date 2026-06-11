package com.example.tradingbot.domain.command.executor;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.domain.command.DealActionStateStatus;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.RuntimeTarget;
import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.command.ServiceCommandExecutionResult;
import com.example.tradingbot.domain.command.ServiceCommandType;
import com.example.tradingbot.domain.command.TargetEntityType;
import com.example.tradingbot.domain.command.payload.AttachedProtectionPayload;
import com.example.tradingbot.domain.command.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.persistence.service.DealActionStateDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.util.ClientIdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполняет CREATE_ORDER: создаёт локальный Order (CREATED) с
 * сгенерированным internalId и рассчитанными параметрами, attached
 * protection (если есть), обновляет DealActionState (target = ORDER,
 * status = CREATED) — всё одной транзакцией. На биржу не ходит. См.
 * docs/components/CreateOrderExecutor.md.
 */
@Component
@RequiredArgsConstructor
public class CreateOrderExecutor implements CommandExecutor {

    private final OrderDataService orderDataService;
    private final DealActionStateDataService dealActionStateDataService;

    @Override
    public ServiceCommandType supportedType() {
        return ServiceCommandType.CREATE_ORDER;
    }

    @Override
    @Transactional
    public ServiceCommandExecutionResult execute(ServiceCommand command, DealActionState actionState,
                                                 DealContext dealContext) {
        CreateOrderCommandPayload payload = (CreateOrderCommandPayload) command.getPayload();
        Order saved = orderDataService.save(buildOrder(payload, dealContext.getDeal().getId()));
        actionState.setTarget(new RuntimeTarget(TargetEntityType.ORDER, saved.getId()));
        actionState.setStatus(DealActionStateStatus.CREATED);
        dealActionStateDataService.save(actionState);
        return ServiceCommandExecutionResult.ok();
    }

    private Order buildOrder(CreateOrderCommandPayload payload, Long dealId) {
        Order order = new Order();
        order.setDealId(dealId);
        order.setInternalId(ClientIdGenerator.generate());
        order.setStatus(Order.Status.CREATED);
        order.setType(payload.getOrderType());
        order.setSide(payload.getSide());
        order.setSize(payload.getSizeContracts());
        order.setPositionReducingOnly(payload.getPositionReducingOnly());
        if (isTrue(payload.getSendPriceToExchange())) {
            order.setPrice(payload.getPrice());
        }
        order.setAttachedAlgoOrders(buildAttached(payload.getAttachedProtection()));
        return order;
    }

    private List<AttachedAlgoOrder> buildAttached(AttachedProtectionPayload protection) {
        if (isNull(protection)) {
            return null;
        }
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setInternalId(ClientIdGenerator.generate());
        attached.setStatus(AttachedAlgoOrder.Status.CREATED);
        attached.setType(protection.getAttachedType());
        attached.setStopLossTriggerPrice(protection.getStopLossTriggerPrice());
        attached.setSize(protection.getSize());
        return List.of(attached);
    }
}
