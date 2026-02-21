package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.config.SynchronizeExecutionEnvironmentProperties;
import com.example.tradingbot.persistence.service.ReconcileReportDataService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupReconcileReportsService {

    private final ReconcileReportDataService reportDataService;
    private final SynchronizeExecutionEnvironmentProperties properties;
    private final Clock clock;

    public int cleanup() {
        int retentionDays = properties.getReports().getRetentionDays();
        if (retentionDays <= 0) {
            log.info("Cleanup SynchronizeExecutionEnvironmentReport skipped: retentionDays={}", retentionDays);
            return 0;
        }

        Instant threshold = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int deleted = reportDataService.deleteFinishedNoAnomaliesBefore(threshold);
        log.info("Cleanup SynchronizeExecutionEnvironmentReport completed: threshold={}, deleted={}", threshold, deleted);
        return deleted;
    }
}
