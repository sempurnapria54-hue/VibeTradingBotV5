package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.exception.TradingCommandException;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.util.Constant.ErrorCode.ALGO_ORDER_NOT_FOUND_ON_EXCHANGE;

@Component
@RequiredArgsConstructor
public class SyncAlgoOrderExecutor {

    private static final Set<String> HISTORY_FINAL_STATUSES = Set.of("effective", "canceled", "order_failed");

    private final ClientManager clientManager;
    private final InstrumentDataService instrumentDataService;
    private final AlgoOrderSyncService algoOrderSyncService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public AlgoOrder execute(Exchange exchange, AlgoOrder algoOrder) {
        ClientService clientService = clientManager.getClientService(exchange.getName());
        AlgoOrderExternalSnapshot snapshot = resolveSnapshot(clientService, algoOrder);

        if (snapshot == null) {
            Instrument instrument = instrumentDataService.findRequiredByDealId(algoOrder.getDealId());
            snapshot = resolveHistorySnapshot(clientService, instrument, algoOrder);
        }

        if (snapshot == null) {
            throw new TradingCommandException(HttpStatus.BAD_GATEWAY,
                                              "EMPTY_RESPONSE",
                                              ALGO_ORDER_NOT_FOUND_ON_EXCHANGE);
        }

        algoOrderSyncService.applySnapshot(algoOrder, snapshot);
        return algoOrderDataService.save(algoOrder);
    }

    private AlgoOrderExternalSnapshot resolveSnapshot(ClientService clientService, AlgoOrder algoOrder) {
        try {
            return clientService.getAlgoOrder(buildProbe(algoOrder, null));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private AlgoOrderExternalSnapshot resolveHistorySnapshot(ClientService clientService,
                                                             Instrument instrument,
                                                             AlgoOrder algoOrder) {
        for (String status : HISTORY_FINAL_STATUSES) {
            AlgoOrder probe = buildProbe(algoOrder, status);
            List<AlgoOrderExternalSnapshot> snapshots = clientService.getAlgoOrdersHistory(instrument, probe);
            AlgoOrderExternalSnapshot matchedSnapshot = findByIdentity(snapshots, algoOrder);
            if (matchedSnapshot != null) {
                return matchedSnapshot;
            }
        }
        return null;
    }

    private AlgoOrder buildProbe(AlgoOrder source, String externalStatus) {
        AlgoOrder probe = new AlgoOrder();
        probe.setInternalId(source.getInternalId());
        probe.setExternalId(source.getExternalId());
        probe.setExternalType(source.getExternalType());
        probe.setExternalStatus(externalStatus);
        return probe;
    }

    private AlgoOrderExternalSnapshot findByIdentity(List<AlgoOrderExternalSnapshot> snapshots, AlgoOrder algoOrder) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }

        return snapshots.stream()
                        .filter(snapshot -> Objects.equals(snapshot.getExternalId(), algoOrder.getExternalId())
                                || Objects.equals(snapshot.getInternalId(), algoOrder.getInternalId()))
                        .findFirst()
                        .orElse(null);
    }
}
