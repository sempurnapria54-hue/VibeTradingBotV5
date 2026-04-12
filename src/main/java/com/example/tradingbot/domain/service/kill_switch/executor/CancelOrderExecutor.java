package com.example.tradingbot.domain.service.kill_switch.executor;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CancelOrderExecutor {

    private final OrderDataService orderDataService;

    public void execute(ClientService clientService, Instrument instrument, List<Order> liveOrders) {
        for (Order order : liveOrders) {
            if (order == null) {
                continue;
            }
            clientService.cancelOrder(order, instrument.getExternalId());
            order.setStatus(Order.Status.CLOSED);
            orderDataService.save(order);
        }
    }
}
