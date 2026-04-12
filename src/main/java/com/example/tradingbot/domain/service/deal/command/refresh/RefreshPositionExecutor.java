package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.Position.CloseReason;
import com.example.tradingbot.domain.model.position.Position.Status;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.deal.TradeRuleValidator;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.tradingbot.util.factory.PositionFactory.createPosition;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshPositionExecutor {

    private final PositionDataService positionDataService;
    private final ClientManager clientManager;
    private final PositionMapper mapper;
    private final TradeRuleValidator tradeRuleValidator;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument, Long dealId) {
        List<PositionExternalSnapshot> externalSnapshots = clientManager.getClientService(exchange.getName())
                                                                        .getPositionsByInstrument(instrument);

        List<Position> activeDomainPositions =
                positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                     Set.of(Status.ACTIVE.name()));

        tradeRuleValidator.validatePositions(exchange, instrument, dealId, externalSnapshots, activeDomainPositions);

        if (isEmpty(externalSnapshots) && isEmpty(activeDomainPositions)) {
            log.info("Not active positions and snapshots for exchange {}, instrument {}, dealId {}",
                     exchange.getName(), instrument.getExchangeId(), dealId);
            return;
        }

        PositionExternalSnapshot snapshot = externalSnapshots.getFirst();
        Position position = activeDomainPositions.getFirst();

        if (nonNull(position)) {
            refreshFromSnapshot(activeDomainPositions, snapshot);
        } else {
            createFromSnapshot(snapshot, dealId);
        }
    }

    private void createFromSnapshot(PositionExternalSnapshot snapshot, Long dealId) {
        Position position = positionDataService.findByExternalId(snapshot.getExternalId());
        if (isNull(position)) {
            position = createPosition(dealId);
            mapper.updateDomainFromExternalSnapshot(snapshot, position);
            positionDataService.save(position);
        }
    }

    private void refreshFromSnapshot(List<Position> activeDomainPositions, PositionExternalSnapshot snapshot) {
        activeDomainPositions.forEach(position -> refreshFromSnapshots(position, snapshot));
    }

    private void refreshFromSnapshots(Position position, PositionExternalSnapshot snapshot) {
        if (nonNull(snapshot) && Objects.equals(position.getExternalId(), snapshot.getExternalId())) {
            mapper.updateDomainFromExternalSnapshot(snapshot, position);
        } else {
            position.toClose(CloseReason.EXCHANGE_FORCED);
        }
        positionDataService.save(position);
    }
}
