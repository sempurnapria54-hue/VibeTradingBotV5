package com.example.tradingbot.domain.service.deal.command.algo;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.AmendAlgoOrderCommandPayload;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.service.deal.command.refresh.AlgoOrderSyncService;
import com.example.tradingbot.domain.service.deal.state_machine.DealContext;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AmendAlgoOrderExecutor {

    private final ClientManager clientManager;
    private final AlgoOrderDataService algoOrderDataService;
    private final AlgoOrderSyncService algoOrderSyncService;

    @Transactional
    public AlgoOrder execute(DealContext context, ServiceCommand command) {
        AmendAlgoOrderCommandPayload payload = requirePayload(command);
        AlgoOrder algoOrder = findAlgoOrder(command, payload);
        if (Objects.nonNull(payload.getSize())) {
            algoOrder.setSize(payload.getSize());
        }
        if (Objects.nonNull(payload.getCondition())) {
            algoOrder.setCondition(payload.getCondition());
        }

        Exchange exchange = context.getExchange();
        Instrument instrument = context.getInstrument();
        Position position = context.getActivePosition();
        ClientService clientService = clientManager.getClientService(exchange.getName());

        if (Objects.nonNull(algoOrder.getExternalId())) {
            clientService.cancelAlgoOrder(algoOrder, instrument.getExternalId());
        }

        AlgoOrderExternalSnapshot submittedSnapshot = clientService.createAlgoOrder(algoOrder, instrument, position);
        if (Objects.nonNull(submittedSnapshot)) {
            algoOrderSyncService.applySnapshot(algoOrder, submittedSnapshot);
        }

        return algoOrderDataService.save(algoOrder);
    }

    private AlgoOrder findAlgoOrder(ServiceCommand command, AmendAlgoOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getAlgoOrderId())) {
            return algoOrderDataService.findRequiredById(payload.getAlgoOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return algoOrderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                       .orElseThrow(() -> new IllegalStateException(
                                               "AlgoOrder not found by strategyActionId"));
        }
        throw new IllegalArgumentException("AMEND_ALGO_ORDER requires algoOrderId or strategyActionId");
    }

    private AmendAlgoOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("AMEND_ALGO_ORDER payload is required");
        }
        if (command.getPayload() instanceof AmendAlgoOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("AMEND_ALGO_ORDER payload has unsupported type");
    }
}
