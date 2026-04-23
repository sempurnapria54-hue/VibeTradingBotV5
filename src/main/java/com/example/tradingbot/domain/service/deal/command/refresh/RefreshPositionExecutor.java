package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.validator.TradeRuleValidator;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshPositionExecutor {

    private final PositionDataService positionDataService;
    private final ClientManager clientManager;
    private final TradeRuleValidator tradeRuleValidator;
    private final PositionStatusResolver positionStatusResolver;
    private final PositionSyncService positionSyncService;

    @Transactional
    public void execute(Exchange exchange, Instrument instrument, Long dealId) {
        List<PositionExternalSnapshot> externalSnapshots = readLiveExternalSnapshots(exchange, instrument);
        List<Position> activeDomainPositions = positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                                                    Position.liveStatusNames());

        tradeRuleValidator.validatePositions(exchange,
                                             instrument,
                                             dealId,
                                             externalSnapshots,
                                             activeDomainPositions);

        PositionExternalSnapshot externalSnapshot = getFirstExternalSnapshot(externalSnapshots);
        Position activeDomainPosition = getFirstActivePosition(activeDomainPositions);

        if (Objects.isNull(externalSnapshot) && Objects.isNull(activeDomainPosition)) {
            log.info("No live positions to refresh for exchange {}, instrument {}, dealId {}",
                     exchange.getName(),
                     instrument.getExternalId(),
                     dealId);
            return;
        }

        if (Objects.nonNull(externalSnapshot) && Objects.nonNull(activeDomainPosition)) {
            positionSyncService.applySnapshot(activeDomainPosition, externalSnapshot);
            positionDataService.save(activeDomainPosition);
            return;
        }

        if (Objects.nonNull(externalSnapshot)) {
            createOrReviveFromSnapshot(externalSnapshot, dealId);
            return;
        }

        positionSyncService.closeMissingPosition(activeDomainPosition);
        positionDataService.save(activeDomainPosition);
    }

    private List<PositionExternalSnapshot> readLiveExternalSnapshots(Exchange exchange, Instrument instrument) {
        ClientService clientService = clientManager.getClientService(exchange.getName());
        List<PositionExternalSnapshot> externalSnapshots = clientService.getPositionsByInstrument(instrument);
        if (CollectionUtils.isEmpty(externalSnapshots)) {
            return List.of();
        }

        return externalSnapshots.stream()
                                .filter(Objects::nonNull)
                                .filter(this::isLiveExternalSnapshot)
                                .toList();
    }

    private boolean isLiveExternalSnapshot(PositionExternalSnapshot snapshot) {
        return Objects.equals(positionStatusResolver.resolveStatus(snapshot), Position.Status.ACTIVE);
    }

    private void createOrReviveFromSnapshot(PositionExternalSnapshot snapshot, Long dealId) {
        Position position = positionDataService.findByExternalId(snapshot.getExternalId());
        if (Objects.isNull(position)) {
            position = positionSyncService.createFromSnapshot(snapshot, dealId);
        } else {
            positionSyncService.applySnapshot(position, snapshot);
        }
        positionDataService.save(position);
    }

    private PositionExternalSnapshot getFirstExternalSnapshot(List<PositionExternalSnapshot> externalSnapshots) {
        if (CollectionUtils.isEmpty(externalSnapshots)) {
            return null;
        }
        return externalSnapshots.getFirst();
    }

    private Position getFirstActivePosition(List<Position> activeDomainPositions) {
        if (CollectionUtils.isEmpty(activeDomainPositions)) {
            return null;
        }
        return activeDomainPositions.getFirst();
    }
}
