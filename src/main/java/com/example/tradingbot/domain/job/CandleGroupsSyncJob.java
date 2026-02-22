package com.example.tradingbot.domain.job;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.CandleGroupLeaseService;
import com.example.tradingbot.domain.service.candlegroup.CandleGroupWorker;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandleGroupsSyncJob {

    private final CandleGroupsProperties candleGroupsProperties;
    private final CandleGroupLeaseService candleGroupLeaseService;
    private final CandleGroupWorker candleGroupWorker;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${candle-groups.job.fixed-delay-ms:10000}")
    public void run() {
        if (!candleGroupsProperties.isEnabled()) {
            return;
        }

        long nowMillis = clock.millis();
        List<CandleGroupEntity> groups = candleGroupLeaseService.pickEligibleGroups(
            nowMillis,
            candleGroupsProperties.getMaxGroupsPerRun()
        );

        for (CandleGroupEntity group : groups) {
            boolean acquired = candleGroupLeaseService.acquireLease(group.getId());
            if (!acquired) {
                log.debug("CandleGroup lease skipped: groupId={}, reason=already-leased", group.getId());
                continue;
            }

            try {
                candleGroupWorker.processGroup(group.getId());
            } catch (Exception ex) {
                log.warn("CandleGroup processing ended with error: groupId={}, message={}", group.getId(), ex.getMessage());
            } finally {
                candleGroupLeaseService.releaseLease(group.getId());
            }
        }
    }
}
