package com.example.tradingbot.domain.service.deal.command.order;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.AmendOrderCommandPayload;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AmendOrderExecutor {

    private final ClientManager clientManager;
    private final OrderDataService orderDataService;

    @Transactional
    public Order execute(DealContext context, ServiceCommand command) {
        AmendOrderCommandPayload payload = requirePayload(command);
        Order order = findOrder(command, payload);
        if (Objects.nonNull(payload.getPrice())) {
            order.setPrice(payload.getPrice());
        }
        if (Objects.nonNull(payload.getSize())) {
            order.setSize(payload.getSize());
        }

        Exchange exchange = context.getExchange();
        Instrument instrument = context.getInstrument();
        ClientService clientService = clientManager.getClientService(exchange.getName());
        List<Order> amendedOrders = clientService.amendOrder(order, instrument.getExternalId());
        applyAck(order, amendedOrders);
        return orderDataService.save(order);
    }

    private Order findOrder(ServiceCommand command, AmendOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getOrderId())) {
            return orderDataService.findRequiredById(payload.getOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return orderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                   .orElseThrow(() -> new IllegalStateException("Order not found by strategyActionId"));
        }
        throw new IllegalArgumentException("AMEND_ORDER requires orderId or strategyActionId");
    }

    private void applyAck(Order target, List<Order> orders) {
        if (CollectionUtils.isEmpty(orders)) {
            return;
        }

        Order ack = orders.getFirst();
        if (Objects.nonNull(ack.getExternalId())) {
            target.setExternalId(ack.getExternalId());
        }
        if (Objects.nonNull(ack.getExternalStatus())) {
            target.setExternalStatus(ack.getExternalStatus());
        }
    }

    private AmendOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("AMEND_ORDER payload is required");
        }
        if (command.getPayload() instanceof AmendOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("AMEND_ORDER payload has unsupported type");
    }
}
