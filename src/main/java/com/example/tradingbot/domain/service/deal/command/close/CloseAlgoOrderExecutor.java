package com.example.tradingbot.domain.service.deal.command.close;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CloseAlgoOrderExecutor {

    private final AlgoOrderDataService algoOrderDataService;

    public void execute(ClientService clientService, Instrument instrument, List<AlgoOrder> liveAlgoOrders) {
        for (AlgoOrder algoOrder : liveAlgoOrders) {
            if (algoOrder == null) {
                continue;
            }
            clientService.cancelAlgoOrder(algoOrder, instrument.getExternalId());
            algoOrder.toClose(AlgoOrder.CloseReason.KILL_SWITCH);
            algoOrderDataService.save(algoOrder);
        }
    }
}
