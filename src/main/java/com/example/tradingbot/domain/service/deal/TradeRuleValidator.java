package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.deal.KillSwitchResult;
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
            executeTradeRuleViolationFlow(exchange,
                                          instrument,
                                          dealId,
                                          OVERHEAD_POSITIONS_COUNT,
                                          Severity.CRITICAL,
                                          externalSnapshots,
                                          domainPositions);
            throw new TradeRuleViolationException(
                    "Trade rule violation detected for positions: " + OVERHEAD_POSITIONS_COUNT);
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
            KillSwitchResult killSwitchResult =
                    killSwitchService.executeTradeRuleViolation(exchange, instrument, dealId, code);
            anomalyService.markKillSwitchExecuted(report.getId());

            if (killSwitchResult.isSuccess()) {
                anomalyService.complete(report.getId(),
                                        killSwitchResult.getMessage(),
                                        killSwitchResult.getInternalAfter(),
                                        killSwitchResult.getExternalAfter());
            } else {
                anomalyService.markError(report.getId(),
                                         killSwitchResult.getMessage(),
                                         killSwitchResult.getInternalAfter(),
                                         killSwitchResult.getExternalAfter());
            }
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

    private List<PositionExternalSnapshot> loadExternalSnapshotAfterKillSwitch(Exchange exchange,
                                                                               Instrument instrument) {
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
