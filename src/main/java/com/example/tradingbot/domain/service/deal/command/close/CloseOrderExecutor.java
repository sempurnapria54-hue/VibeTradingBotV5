package com.example.tradingbot.domain.service.deal.command.close;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.persistence.service.OrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CloseOrderExecutor {

    private final OrderDataService orderDataService;

    public void execute(ClientService clientService, Instrument instrument, List<Order> liveOrders) {
        for (Order order : liveOrders) {
            if (order == null) {
                continue;
            }
            clientService.cancelOrder(order, instrument.getExternalId());
            order.toClose(Order.CloseReason.KILL_SWITCH);
            orderDataService.save(order);
        }
    }
}
