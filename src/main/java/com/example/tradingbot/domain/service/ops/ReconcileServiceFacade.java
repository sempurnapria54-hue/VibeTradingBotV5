package com.example.tradingbot.domain.service.ops;

import com.example.tradingbot.domain.model.entity.ReconcileAnomalyEntity;
import com.example.tradingbot.domain.model.entity.ReconcileReportEntity;
import com.example.tradingbot.domain.service.reconcile.ReconcileService;
import com.example.tradingbot.persistence.service.ExchangeDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.ReconcileReportDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReconcileServiceFacade {

    private static final int DEFAULT_LIMIT = 20;

    private final ReconcileService reconcileService;
    private final ReconcileReportDataService reportDataService;
    private final ExchangeDataService exchangeDataService;
    private final InstrumentDataService instrumentDataService;

    public ReconcileReportEntity run(String exchangeInternalId) {
        var exchangeEntity = exchangeDataService.findRequiredByInternalId(exchangeInternalId);
        return reconcileService.run(exchangeEntity);
    }

    public List<ReconcileReportEntity> getByExchange(String exchangeInternalId, Integer limit) {
        Long exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);
        int resolvedLimit = resolveLimit(limit);
        return reportDataService.findByExchangeId(exchangeId, resolvedLimit);
    }

    public List<ReconcileReportEntity> getByInstrument(String exchangeInternalId, String instrumentInternalId, Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        Long exchangeId = exchangeDataService.getRequiredIdByInternalId(exchangeInternalId);

        String exchangeInstId = instrumentDataService
                .findExternalIdByExchangeIdAndInternalId(exchangeId, instrumentInternalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));

        return reportDataService.findByExchangeIdAndInstId(exchangeId, exchangeInstId, resolvedLimit);
    }

    public ReconcileReportEntity getByInternalId(String internalId) {
        ReconcileReportEntity report = reportDataService.findRequiredByInternalId(internalId);
        List<ReconcileAnomalyEntity> anomalies = reportDataService.findAnomaliesByReportId(report.getId());
        return report;
    }

    private int resolveLimit(Integer limit) {
        int resolvedLimit = DEFAULT_LIMIT;
        if (Objects.nonNull(limit)) {
            resolvedLimit = limit;
        }

        if (resolvedLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than zero");
        }
        return resolvedLimit;
    }

}
