package com.example.tradingbot.domain.service.kill_switch.reader;

import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;

@Component
public class ExternalPositionSnapshotReader {

    public List<PositionExternalSnapshot> readActivePositions(ClientService clientService, Instrument instrument) {
        return emptyIfNull(clientService.getPositionsByInstrument(instrument)).stream()
                                                                              .filter(Objects::nonNull)
                                                                              .toList();
    }
}
