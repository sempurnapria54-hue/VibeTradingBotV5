package com.example.tradingbot.domain.service.candlegroup.repair;

import com.example.tradingbot.persistence.service.CandleDataService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissingTimestampsResolver {

    private final CandleDataService candleDataService;

    public List<Long> findMissingTimestamps(Long groupId, TimeWindow window, long tfMillis) {
        List<Long> dbTimestamps = candleDataService.loadTimestamps(groupId, window.fromTs(), window.toTs());
        Set<Long> existing = new HashSet<>(dbTimestamps);

        List<Long> missing = new ArrayList<>();
        for (long ts = window.fromTs(); ts <= window.toTs(); ts += tfMillis) {
            if (!existing.contains(ts)) {
                missing.add(ts);
            }
        }
        return missing;
    }

    public List<TimeWindow> groupIntoGapWindows(List<Long> missing, long tfMillis) {
        if (missing == null || missing.isEmpty()) {
            return List.of();
        }

        List<TimeWindow> gaps = new ArrayList<>();
        long gapStart = missing.getFirst();
        long prev = gapStart;

        for (int i = 1; i < missing.size(); i++) {
            long current = missing.get(i);
            if (current - prev != tfMillis) {
                gaps.add(new TimeWindow(gapStart, prev));
                gapStart = current;
            }
            prev = current;
        }

        gaps.add(new TimeWindow(gapStart, prev));
        return gaps;
    }
}
