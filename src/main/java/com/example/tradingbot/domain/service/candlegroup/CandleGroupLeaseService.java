package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.CandleGroupStatus;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CandleGroupLeaseService {

    private static final Set<CandleGroupStatus> ELIGIBLE_STATUSES = Set.of(
        CandleGroupStatus.NEW,
        CandleGroupStatus.BACKFILL_RUNNING,
        CandleGroupStatus.REPAIR_RUNNING,
        CandleGroupStatus.SYNC
    );

    private final CandleGroupDataService candleGroupDataService;
    private final CandleGroupsProperties candleGroupsProperties;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    public List<CandleGroupEntity> pickEligibleGroups(long nowMillis, int maxGroups) {
        return candleGroupDataService.findEligibleForRun(nowMillis, ELIGIBLE_STATUSES, maxGroups);
    }

    public boolean acquireLease(Long groupId) {
        long nowMillis = clock.millis();
        long leaseUntilMillis = nowMillis + candleGroupsProperties.getLeaseDurationSec() * 1_000L;
        return candleGroupDataService.tryAcquireLease(groupId, instanceId, nowMillis, leaseUntilMillis);
    }

    public void extendLease(Long groupId) {
        long nowMillis = clock.millis();
        long newLeaseUntilMillis = nowMillis + candleGroupsProperties.getLeaseDurationSec() * 1_000L;
        candleGroupDataService.extendLease(groupId, instanceId, newLeaseUntilMillis);
    }

    public void releaseLease(Long groupId) {
        candleGroupDataService.releaseLease(groupId, instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
