package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.deal.KillSwitchResult;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.service.InstrumentService;
import com.example.tradingbot.domain.service.kill_switch.KillSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ONE;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Service
@RequiredArgsConstructor
public class TradeRuleValidator {

    private static final String OVERHEAD_POSITIONS_COUNT = "OVERHEAD_POSITIONS_COUNT";

    private final KillSwitchService killSwitchService;
    private final InstrumentService instrumentService;
    private final AnomalyService anomalyService;

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
                                          Severity.CRITICAL);
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
                                               Severity severity) {
        instrumentService.holdInstrument(instrument);

        AnomalyReport report = anomalyService.create(exchange.getId(), instrument.getId(), severity, code);
        anomalyService.markInProgress(report.getId());

        try {
            KillSwitchResult killSwitchResult =
                    killSwitchService.executeKillSwitch(exchange, instrument, dealId, code);
            anomalyService.markKillSwitchExecuted(report.getId(),
                                                  killSwitchResult.getInternalBefore(),
                                                  killSwitchResult.getExternalBefore(),
                                                  killSwitchResult.getInternalAfter(),
                                                  killSwitchResult.getExternalAfter());

            if (killSwitchResult.isSuccess()) {
                anomalyService.complete(report.getId(), killSwitchResult.getMessage());
                if (severity == Severity.NON_CRITICAL) {
                    instrumentService.activateInstrument(instrument);
                    return;
                }
            } else {
                anomalyService.markError(report.getId(), killSwitchResult.getMessage());
            }
            instrumentService.blockInstrument(instrument);
        } catch (Exception exception) {
            String errorMessage = getRootCauseMessage(exception);
            anomalyService.markError(report.getId(), errorMessage);
            instrumentService.blockInstrument(instrument);
        }
    }
}
