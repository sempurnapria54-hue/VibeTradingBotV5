package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position.Side;
import com.example.tradingbot.domain.model.position.Position.Status;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.DealDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionDataService positionDataService;
    private final ClientManager clientManager;
    private final DealDataService dealDataService;
    private final PositionMapper mapper;

    @Transactional
    public Position refreshPosition(Exchange exchange, Instrument instrument) {
        List<PositionExternalSnapshot> externalSnapshots = clientManager.getClientService(exchange.getName())
                                                                        .getPositionsByInstrument(instrument);
        if (externalSnapshots == null) {
            externalSnapshots = List.of();
        }
        if (!isEmpty(externalSnapshots) && externalSnapshots.size() > 1) {
            throw new RuntimeException("Invalid positions in deal");
        }

        Position position = resolveCurrentPosition(instrument.getId());
        if (isEmpty(externalSnapshots)) {
            if (position == null) {
                return null;
            }
            position.setStatus(Status.CLOSED);
            return positionDataService.save(position);
        }

        PositionExternalSnapshot snapshot = externalSnapshots.getFirst();
        Position target = prepareTargetPosition(position, snapshot, instrument.getId());
        mapper.updateDomainFromExternalSnapshot(snapshot, target);
        target.setStatus(resolvePositionStatus(snapshot));
        return positionDataService.save(target);
    }

    private Position resolveCurrentPosition(Long instrumentId) {
        List<Position> positions = positionDataService.findByInstrumentId(instrumentId);
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        return positions.getFirst();
    }

    private Position prepareTargetPosition(Position existingPosition,
                                           PositionExternalSnapshot snapshot,
                                           Long instrumentId) {
        if (existingPosition != null) {
            return existingPosition;
        }

        if (snapshot != null && snapshot.getExternalId() != null) {
            Optional<Position> existingByExternalId = positionDataService.findByExternalId(snapshot.getExternalId());
            if (existingByExternalId.isPresent()) {
                return existingByExternalId.get();
            }
        }

        Optional<com.example.tradingbot.domain.model.deal.Deal> dealOptional =
                dealDataService.findLatestByInstrumentId(instrumentId);
        if (dealOptional.isEmpty()) {
            throw new IllegalStateException("Deal is missing for instrument: " + instrumentId);
        }

        Position created = new Position();
        created.setDealId(dealOptional.get().getId());
        created.setInternalId(UUID.randomUUID()
                                  .toString());
        created.setSide(Side.NET);
        created.setStatus(Status.ACTIVE);
        return created;
    }

    private Status resolvePositionStatus(PositionExternalSnapshot snapshot) {
        if (snapshot == null || snapshot.getSize() == null) {
            return Status.CLOSED;
        }
        if (snapshot.getSize()
                    .signum() <= 0) {
            return Status.CLOSED;
        }
        return Status.ACTIVE;
    }
}
