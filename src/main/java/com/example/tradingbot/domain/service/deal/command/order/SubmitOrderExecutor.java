package com.example.tradingbot.domain.service.deal.command.order;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.SubmitOrderCommandPayload;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.service.deal.command.refresh.OrderStatusResolver;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SubmitOrderExecutor {

    private static final String DEFAULT_POSITION_SIDE = "net";

    private final ClientManager clientManager;
    private final OrderDataService orderDataService;
    private final OrderMapper orderMapper;
    private final OrderStatusResolver orderStatusResolver;

    @Transactional
    public Order execute(DealContext context, ServiceCommand command) {
        SubmitOrderCommandPayload payload = requirePayload(command);
        Order order = findOrder(command, payload);
        if (Objects.nonNull(order.getExternalId())) {
            return order;
        }

        Exchange exchange = requireExchange(context);
        Instrument instrument = requireInstrument(context);
        ClientService clientService = clientManager.getClientService(exchange.getName());

        OrderExternalSnapshot recoveredSnapshot = tryGetOrder(clientService, instrument, order);
        if (Objects.nonNull(recoveredSnapshot)) {
            applySnapshot(order, recoveredSnapshot);
            return orderDataService.save(order);
        }

        List<Order> submittedOrders = clientService.createOrder(order,
                                                                instrument.getExternalId(),
                                                                instrument.getExternalMarginMode(),
                                                                DEFAULT_POSITION_SIDE);
        applyAck(order, submittedOrders);
        return orderDataService.save(order);
    }

    private Order findOrder(ServiceCommand command, SubmitOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getOrderId())) {
            return orderDataService.findRequiredById(payload.getOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return orderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                   .orElseThrow(() -> new IllegalStateException("Order not found by strategyActionId"));
        }
        throw new IllegalArgumentException("SUBMIT_ORDER requires orderId or strategyActionId");
    }

    private OrderExternalSnapshot tryGetOrder(ClientService clientService, Instrument instrument, Order order) {
        try {
            return clientService.getOrder(instrument.getExternalId(), null, order.getInternalId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void applySnapshot(Order order, OrderExternalSnapshot snapshot) {
        orderMapper.updateDomainFromExternalSnapshot(snapshot, order);
        order.setStatus(orderStatusResolver.resolveStatus(snapshot));
    }

    private void applyAck(Order target, List<Order> submittedOrders) {
        if (CollectionUtils.isEmpty(submittedOrders)) {
            target.setStatus(Order.Status.PENDING);
            return;
        }

        Order ack = submittedOrders.getFirst();
        if (Objects.nonNull(ack.getExternalId())) {
            target.setExternalId(ack.getExternalId());
        }
        if (Objects.nonNull(ack.getInternalId())) {
            target.setInternalId(ack.getInternalId());
        }
        if (Objects.nonNull(ack.getExternalStatus())) {
            target.setExternalStatus(ack.getExternalStatus());
        }
        target.setStatus(Order.Status.PENDING);
    }

    private SubmitOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("SUBMIT_ORDER payload is required");
        }
        if (command.getPayload() instanceof SubmitOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("SUBMIT_ORDER payload has unsupported type");
    }

    private Exchange requireExchange(DealContext context) {
        if (Objects.isNull(context) || Objects.isNull(context.getExchange())) {
            throw new IllegalArgumentException("SUBMIT_ORDER exchange is required");
        }
        return context.getExchange();
    }

    private Instrument requireInstrument(DealContext context) {
        if (Objects.isNull(context) || Objects.isNull(context.getInstrument())) {
            throw new IllegalArgumentException("SUBMIT_ORDER instrument is required");
        }
        return context.getInstrument();
    }
}
