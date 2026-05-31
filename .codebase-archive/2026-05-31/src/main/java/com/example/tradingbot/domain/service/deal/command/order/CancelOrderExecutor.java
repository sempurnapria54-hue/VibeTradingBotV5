package com.example.tradingbot.domain.service.deal.command.order;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.CancelOrderCommandPayload;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CancelOrderExecutor {

    private final ClientManager clientManager;
    private final OrderDataService orderDataService;

    @Transactional
    public Order execute(DealContext context, ServiceCommand command) {
        CancelOrderCommandPayload payload = requirePayload(command);
        Order order = findOrder(command, payload);
        if (order.isNotLive()) {
            return order;
        }

        Exchange exchange = context.getExchange();
        Instrument instrument = context.getInstrument();
        ClientService clientService = clientManager.getClientService(exchange.getName());
        clientService.cancelOrder(order, instrument.getExternalId());
        order.setStatus(Order.Status.CLOSED);
        return orderDataService.save(order);
    }

    private Order findOrder(ServiceCommand command, CancelOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getOrderId())) {
            return orderDataService.findRequiredById(payload.getOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return orderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                   .orElseThrow(() -> new IllegalStateException("Order not found by strategyActionId"));
        }
        throw new IllegalArgumentException("CANCEL_ORDER requires orderId or strategyActionId");
    }

    private CancelOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("CANCEL_ORDER payload is required");
        }
        if (command.getPayload() instanceof CancelOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("CANCEL_ORDER payload has unsupported type");
    }
}
