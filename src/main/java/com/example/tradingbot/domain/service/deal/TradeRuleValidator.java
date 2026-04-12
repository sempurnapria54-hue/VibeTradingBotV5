package com.example.tradingbot.domain.service.deal;

import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.anomaly.AnomalyReport.Severity;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ONE;
import static org.hibernate.internal.util.collections.CollectionHelper.isNotEmpty;

@Service
@RequiredArgsConstructor
public class TradeRuleValidator {

    private static final String OVERHEAD_POSITIONS_COUNT = "OVERHEAD_POSITIONS_COUNT";

    private final KillSwitchService killSwitchService;

    public void validatePositions(Exchange exchange,
                                  Instrument instrument,
                                  Long dealId,
                                  List<PositionExternalSnapshot> externalSnapshots,
                                  List<Position> domainPositions) {
        if (hasOverheadPositions(externalSnapshots, domainPositions)) {
            String code = resolvePositionsCode(externalSnapshots, domainPositions);
            Severity severity = resolvePositionsSeverity(externalSnapshots, domainPositions);
            triggerTradeRuleViolation(exchange, instrument, dealId, code, severity);
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

    private void triggerTradeRuleViolation(Exchange exchange,
                                           Instrument instrument,
                                           Long dealId,
                                           String code,
                                           Severity severity) {
        killSwitchService.executeTradeRuleViolationStub(exchange, instrument, dealId, code);
    }
}
