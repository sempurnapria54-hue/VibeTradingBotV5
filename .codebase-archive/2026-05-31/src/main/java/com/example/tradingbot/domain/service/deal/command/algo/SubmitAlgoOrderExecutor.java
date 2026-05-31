package com.example.tradingbot.domain.service.deal.command.algo;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.commands.ServiceCommand;
import com.example.tradingbot.domain.model.commands.payload.SubmitAlgoOrderCommandPayload;
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
public class SubmitAlgoOrderExecutor {

    private final ClientManager clientManager;
    private final AlgoOrderDataService algoOrderDataService;
    private final AlgoOrderSyncService algoOrderSyncService;

    @Transactional
    public AlgoOrder execute(DealContext context, ServiceCommand command) {
        SubmitAlgoOrderCommandPayload payload = requirePayload(command);
        AlgoOrder algoOrder = findAlgoOrder(command, payload);
        if (Objects.nonNull(algoOrder.getExternalId())) {
            return algoOrder;
        }

        Exchange exchange = requireExchange(context);
        Instrument instrument = requireInstrument(context);
        ClientService clientService = clientManager.getClientService(exchange.getName());

        AlgoOrderExternalSnapshot recoveredSnapshot = tryGetAlgoOrder(clientService, algoOrder);
        if (Objects.nonNull(recoveredSnapshot)) {
            algoOrderSyncService.applySnapshot(algoOrder, recoveredSnapshot);
            return algoOrderDataService.save(algoOrder);
        }

        Position position = requirePosition(context);
        AlgoOrderExternalSnapshot submittedSnapshot = clientService.createAlgoOrder(algoOrder, instrument, position);
        if (Objects.nonNull(submittedSnapshot)) {
            algoOrderSyncService.applySnapshot(algoOrder, submittedSnapshot);
        }
        if (Objects.nonNull(algoOrder.getExternalId()) && Objects.equals(algoOrder.getStatus(), AlgoOrder.Status.CREATED)) {
            algoOrder.setStatus(AlgoOrder.Status.PENDING);
        }
        return algoOrderDataService.save(algoOrder);
    }

    private AlgoOrder findAlgoOrder(ServiceCommand command, SubmitAlgoOrderCommandPayload payload) {
        if (Objects.nonNull(payload.getAlgoOrderId())) {
            return algoOrderDataService.findRequiredById(payload.getAlgoOrderId());
        }
        if (Objects.nonNull(command.getDealId()) && Objects.nonNull(payload.getStrategyActionId())) {
            return algoOrderDataService.findByDealIdAndStrategyActionId(command.getDealId(), payload.getStrategyActionId())
                                       .orElseThrow(() -> new IllegalStateException(
                                               "AlgoOrder not found by strategyActionId"));
        }
        throw new IllegalArgumentException("SUBMIT_ALGO_ORDER requires algoOrderId or strategyActionId");
    }

    private AlgoOrderExternalSnapshot tryGetAlgoOrder(ClientService clientService, AlgoOrder algoOrder) {
        try {
            AlgoOrder probe = new AlgoOrder();
            probe.setInternalId(algoOrder.getInternalId());
            probe.setExternalId(algoOrder.getExternalId());
            probe.setExternalType(algoOrder.getExternalType());
            return clientService.getAlgoOrder(probe);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private SubmitAlgoOrderCommandPayload requirePayload(ServiceCommand command) {
        if (Objects.isNull(command) || Objects.isNull(command.getPayload())) {
            throw new IllegalArgumentException("SUBMIT_ALGO_ORDER payload is required");
        }
        if (command.getPayload() instanceof SubmitAlgoOrderCommandPayload payload) {
            return payload;
        }
        throw new IllegalArgumentException("SUBMIT_ALGO_ORDER payload has unsupported type");
    }

    private Exchange requireExchange(DealContext context) {
        if (Objects.isNull(context) || Objects.isNull(context.getExchange())) {
            throw new IllegalArgumentException("SUBMIT_ALGO_ORDER exchange is required");
        }
        return context.getExchange();
    }

    private Instrument requireInstrument(DealContext context) {
        if (Objects.isNull(context) || Objects.isNull(context.getInstrument())) {
            throw new IllegalArgumentException("SUBMIT_ALGO_ORDER instrument is required");
        }
        return context.getInstrument();
    }

    private Position requirePosition(DealContext context) {
        if (Objects.isNull(context) || Objects.isNull(context.getActivePosition())) {
            throw new IllegalArgumentException("SUBMIT_ALGO_ORDER active position is required");
        }
        return context.getActivePosition();
    }
}
