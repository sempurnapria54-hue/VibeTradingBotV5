package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Component
public class ExternalOrderSnapshotReader {

    public List<OrderExternalSnapshot> readActiveOrders(ClientService clientService, Instrument instrument) {
        return emptyIfNull(clientService.getActiveOrdersByInstrument(instrument)).stream()
                                                                                 .filter(Objects::nonNull)
                                                                                 .toList();
    }

    public List<OrderExternalSnapshot> readRelatedInactiveOrders(ClientService clientService, Instrument instrument) {
        return Stream.concat(
                             emptyIfNull(clientService.getOrdersHistory(instrument)).stream(),
                             emptyIfNull(clientService.getOrdersHistoryArchive(instrument)).stream()
                     )
                     .filter(Objects::nonNull)
                     .filter(snapshot -> !isLive(snapshot))
                     .toList();
    }

    private boolean isLive(OrderExternalSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.getExternalStatus() == null) {
            return false;
        }
        return "live".equalsIgnoreCase(snapshot.getExternalStatus())
                || "partially_filled".equalsIgnoreCase(snapshot.getExternalStatus());
    }
}
