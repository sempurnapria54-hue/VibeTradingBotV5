package com.example.tradingbot.domain.service.deal.command.algo;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.CancelAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CancelAlgoOrderExecutor {

    private final ClientManager clientManager;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public AlgoOrder execute(DealContext context, ServiceCommand command) {
        CancelAlgoOrderCommandPayload payload = requirePayload(command);
        AlgoOrder algoOrder = findAlgoOrder(command, payload);
        if (algoOrder.isNotLive()) {
            return algoOrder;
        }

        Exchange exchange = context.getExchange();
        Instrument instrument = context.getInstrument();
        ClientService clientService = clientManager.getClientService(exchange.getName());
        clientService.cancelAlgoOrder(algoOrder, instrument.getExternalId());
        algoOrder.setStatus(AlgoOrder.Status.CLOSED);
        return algoOrderDataService.save(algoOrder);
    }

    private AlgoOrder findAlgoOrder(ServiceCommand command, CancelAlgoOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getAlgoOrderId())) {
            return algoOrderDataService.findRequiredById(payload.getAlgoOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return algoOrderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                       .orElseThrow(() -> new IllegalStateException(
                                               "AlgoOrder not found by strategyActionId"));
        }
        throw new IllegalArgumentException("CANCEL_ALGO_ORDER requires algoOrderId or strategyActionId");
    }

    private CancelAlgoOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("CANCEL_ALGO_ORDER payload is required");
        }
        if (command.getPayload() instanceof CancelAlgoOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("CANCEL_ALGO_ORDER payload has unsupported type");
    }
}
