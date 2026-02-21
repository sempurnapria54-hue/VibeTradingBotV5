package com.example.tradingbot.domain.job;

import com.example.tradingbot.domain.service.reconcile.CleanupReconcileReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CleanupSynchronizeExecutionEnvironmentReportsJob {

    private final CleanupReconcileReportsService cleanupService;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void run() {
        cleanupService.cleanup();
    }
}
