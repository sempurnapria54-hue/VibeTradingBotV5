package com.example.tradingbot.domain.service.deal.command.order;

import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.CreateOrderCommandPayload;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateOrderExecutor {

    private static final BigDecimal DEFAULT_SIZE = BigDecimal.ONE;

    private final OrderDataService orderDataService;

    @Transactional
    public Order execute(ServiceCommand command) {
        CreateOrderCommandPayload payload = requirePayload(command);
        Long dealId = requireDealId(command);
        Long strategyActionId = requireStrategyActionId(payload);

        Order existing = orderDataService.findByDealIdAndStrategyActionId(dealId, strategyActionId)
                                         .orElse(null);
        if (Objects.nonNull(existing)) {
            return existing;
        }

        Order order = new Order();
        order.setDealId(dealId);
        order.setStrategyActionId(strategyActionId);
        order.setInternalId(UUID.randomUUID().toString());
        order.setStatus(Order.Status.CREATED);
        order.setType(payload.getOrderType());
        order.setSide(payload.getSide());
        order.setPrice(payload.getPrice());
        order.setSize(resolveSize(payload.getSize()));
        order.setAttachedAlgoOrders(List.of());

        Order savedOrder = orderDataService.save(order);
        List<AttachedAlgoOrder> attachedAlgoOrders = buildAttachedAlgoOrders(payload, savedOrder);
        if (attachedAlgoOrders.isEmpty()) {
            return savedOrder;
        }

        savedOrder.setAttachedAlgoOrders(attachedAlgoOrders);
        return orderDataService.save(savedOrder);
    }

    private List<AttachedAlgoOrder> buildAttachedAlgoOrders(CreateOrderCommandPayload payload, Order savedOrder) {
        if (CollectionUtils.isEmpty(payload.getAttachedAlgoOrders())) {
            return List.of();
        }

        List<AttachedAlgoOrder> result = new ArrayList<>();
        for (AttachedAlgoOrder source : payload.getAttachedAlgoOrders()) {
            if (Objects.isNull(source)) {
                continue;
            }

            AttachedAlgoOrder attachedAlgoOrder = new AttachedAlgoOrder();
            attachedAlgoOrder.setOrderId(savedOrder.getId());
            attachedAlgoOrder.setInternalId(resolveInternalId(source.getInternalId()));
            attachedAlgoOrder.setStatus(AttachedAlgoOrder.Status.CREATED);
            attachedAlgoOrder.setType(source.getType());
            attachedAlgoOrder.setExternalType(source.getExternalType());
            attachedAlgoOrder.setSize(resolveSize(source.getSize()));
            attachedAlgoOrder.setStopLossTriggerPrice(resolveSize(source.getStopLossTriggerPrice()));
            result.add(attachedAlgoOrder);
        }

        return result;
    }

    private CreateOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("CREATE_ORDER payload is required");
        }
        if (command.getPayload() instanceof CreateOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("CREATE_ORDER payload has unsupported type");
    }

    private Long requireDealId(ServiceCommand command) {
        if (Objects.isNull(command.getDealId())) {
            throw new IllegalArgumentException("CREATE_ORDER dealId is required");
        }
        return command.getDealId();
    }

    private Long requireStrategyActionId(CreateOrderCommandPayload payload) {
        if (Objects.isNull(payload.getStrategyActionId())) {
            throw new IllegalArgumentException("CREATE_ORDER strategyActionId is required");
        }
        return payload.getStrategyActionId();
    }

    private String resolveInternalId(String source) {
        if (StringUtils.isNotBlank(source)) {
            return source;
        }
        return UUID.randomUUID().toString();
    }

    private BigDecimal resolveSize(BigDecimal source) {
        if (Objects.nonNull(source)) {
            return source;
        }
        return DEFAULT_SIZE;
    }
}
