package com.example.tradingbot.domain.service.candlegroup.integrity;

import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.persistence.service.CandleDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleIntegrityService {

    private final CandleDataService candleDataService;

    public IntegrityResult checkCountOnly(CandleGroupEntity group, CandleGroupRunContext ctx) {
        long startTs = group.getCoverageStartTs();
        long endTs = ctx.nowClosedTs();

        if (startTs > endTs) {
            return new IntegrityResult(startTs, endTs, 0, 0, true);
        }

        long expected = ((endTs - startTs) / ctx.tfMillis()) + 1;
        long actual = candleDataService.countBetween(group.getId(), startTs, endTs);
        boolean ok = actual == expected;

        if (actual > expected) {
            log.warn("CandleGroup integrity anomaly: groupId={}, timeframe={}, startTs={}, endTs={}, expected={}, actual={}, reason=extra-bars",
                group.getId(),
                group.getTimeframe(),
                startTs,
                endTs,
                expected,
                actual);
        }

        return new IntegrityResult(startTs, endTs, expected, actual, ok);
    }
}
