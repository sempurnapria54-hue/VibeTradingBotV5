package com.example.tradingbot.domain.service.candlegroup.repair;

import com.example.tradingbot.persistence.service.CandleDataService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CandleGapLocator {

    private final CandleDataService candleDataService;

    public List<TimeWindow> locateLeafWindows(Long groupId, long startTs, long endTs, long tfMillis, int leafBars) {
        if (startTs > endTs) {
            return List.of();
        }

        int resolvedLeafBars = Math.max(1, leafBars);
        List<TimeWindow> result = new ArrayList<>();
        checkRange(groupId, startTs, endTs, tfMillis, resolvedLeafBars, result);
        return result;
    }

    private void checkRange(Long groupId,
                            long fromTs,
                            long toTs,
                            long tfMillis,
                            int leafBars,
                            List<TimeWindow> leafWindows) {
        long bars = ((toTs - fromTs) / tfMillis) + 1;
        long expected = bars;
        long actual = candleDataService.countBetween(groupId, fromTs, toTs);

        if (actual == expected) {
            return;
        }

        if (bars <= leafBars) {
            leafWindows.add(new TimeWindow(fromTs, toTs));
            return;
        }

        long mid = fromTs + (((bars / 2) - 1) * tfMillis);
        checkRange(groupId, fromTs, mid, tfMillis, leafBars, leafWindows);
        checkRange(groupId, mid + tfMillis, toTs, tfMillis, leafBars, leafWindows);
    }
}
