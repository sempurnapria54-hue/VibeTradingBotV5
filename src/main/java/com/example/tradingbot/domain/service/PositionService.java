package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.deal.Deal;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionDataService positionDataService;
    private final ClientManager clientManager;
    private final ExchangeService exchangeService;
    private final InstrumentService instrumentService;
    private final PositionMapper mapper;

    public Position refreshPosition(Deal deal) {
        List<Position> positions = deal.getPositions();
        if (isEmpty(positions) || positions.size() > 1) {
            throw new RuntimeException("Invalid positions in deal");
        }
        Position currentPosition = positions.getFirst();
        Position saved = positionDataService.findByIdRequired(currentPosition.getId());
        Instrument instrument = instrumentService.getRequiredById(deal.getInstrumentId());
        Exchange exchange = exchangeService.getRequiredById(instrument.getExchangeId());

        List<PositionExternalSnapshot> externalSnapshots = clientManager.getClientService(exchange.getName())
                                                                        .getPositionsByInstrument(instrument);

        if (isNotEmpty(externalSnapshots) && externalSnapshots.size() > 1) {
            throw new RuntimeException("Invalid positions in deal");
        }

        mapper.updateDomainFromExternalSnapshot(externalSnapshots.getFirst(), saved);
        return positionDataService.save(saved);
    }
}
