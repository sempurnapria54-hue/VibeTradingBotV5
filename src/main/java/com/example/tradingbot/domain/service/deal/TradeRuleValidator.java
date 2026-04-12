package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.Position.Status;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.persistence.service.PositionDataService;
import com.example.tradingbot.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ONE;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Service
@RequiredArgsConstructor
public class TradeRuleValidator {

    private static final String OVERHEAD_POSITIONS_COUNT = "OVERHEAD_POSITIONS_COUNT";

    private final KillSwitchService killSwitchService;

    private final AnomalyService anomalyService;

    private final PositionDataService positionDataService;

    private final ClientManager clientManager;

    private final JsonUtils jsonUtils;

    public void validatePositions(Exchange exchange,
                                  Instrument instrument,
                                  Long dealId,
                                  List<PositionExternalSnapshot> externalSnapshots,
                                  List<Position> domainPositions) {
        if (hasOverheadPositions(externalSnapshots, domainPositions)) {
            String code = resolvePositionsCode(externalSnapshots, domainPositions);
            Severity severity = resolvePositionsSeverity(externalSnapshots, domainPositions);
            executeTradeRuleViolationFlow(exchange,
                                          instrument,
                                          dealId,
                                          code,
                                          severity,
                                          externalSnapshots,
                                          domainPositions);
            throw new TradeRuleViolationException("Trade rule violation detected for positions: " + code);
        }
    }

    private boolean hasOverheadPositions(List<PositionExternalSnapshot> externalSnapshots,
                                         List<Position> domainPositions) {
        return hasExternalOverheadPositions(externalSnapshots) || hasDomainOverheadPositions(domainPositions);
    }

    private boolean hasExternalOverheadPositions(List<PositionExternalSnapshot> externalSnapshots) {
        return isNotEmpty(externalSnapshots) && externalSnapshots.size() > INTEGER_ONE;
    }

    private boolean hasDomainOverheadPositions(List<Position> domainPositions) {
        return isNotEmpty(domainPositions) && domainPositions.size() > INTEGER_ONE;
    }

    private String resolvePositionsCode(List<PositionExternalSnapshot> externalSnapshots,
                                        List<Position> domainPositions) {
        return OVERHEAD_POSITIONS_COUNT;
    }

    private Severity resolvePositionsSeverity(List<PositionExternalSnapshot> externalSnapshots,
                                              List<Position> domainPositions) {
        return Severity.CRITICAL;
    }

    private void executeTradeRuleViolationFlow(Exchange exchange,
                                               Instrument instrument,
                                               Long dealId,
                                               String code,
                                               Severity severity,
                                               List<PositionExternalSnapshot> externalSnapshots,
                                               List<Position> domainPositions) {
        String internalBefore = serializeInternalSnapshot(domainPositions);
        String externalBefore = serializeExternalSnapshot(externalSnapshots);

        AnomalyReport report = anomalyService.create(exchange.getId(),
                                                     instrument.getId(),
                                                     severity,
                                                     code,
                                                     internalBefore,
                                                     externalBefore);
        anomalyService.markInProgress(report.getId());

        try {
            killSwitchService.executeTradeRuleViolation(exchange, instrument, dealId, code);

            String internalAfter = serializeInternalSnapshot(loadInternalSnapshotAfterKillSwitch(instrument));
            String externalAfter = serializeExternalSnapshot(loadExternalSnapshotAfterKillSwitch(exchange, instrument));

            anomalyService.markKillSwitchExecuted(report.getId(), internalAfter, externalAfter);
            anomalyService.complete(report.getId(), null, internalAfter, externalAfter);
        } catch (Exception exception) {
            String internalAfter = serializeInternalSnapshot(loadInternalSnapshotAfterKillSwitch(instrument));
            String externalAfter = serializeExternalSnapshot(loadExternalSnapshotAfterKillSwitch(exchange, instrument));
            String errorMessage = getRootCauseMessage(exception);
            anomalyService.markError(report.getId(), errorMessage, internalAfter, externalAfter);
        }
    }

    private List<Position> loadInternalSnapshotAfterKillSwitch(Instrument instrument) {
        return positionDataService.findAllByInstrumentIdAndStatuses(instrument.getId(),
                                                                    Set.of(Status.ACTIVE.name()));
    }

    private List<PositionExternalSnapshot> loadExternalSnapshotAfterKillSwitch(Exchange exchange, Instrument instrument) {
        return clientManager.getClientService(exchange.getName())
                            .getPositionsByInstrument(instrument);
    }

    private String serializeInternalSnapshot(List<Position> domainPositions) {
        return jsonUtils.toJson(domainPositions);
    }

    private String serializeExternalSnapshot(List<PositionExternalSnapshot> externalSnapshots) {
        return jsonUtils.toJson(externalSnapshots);
    }
}
