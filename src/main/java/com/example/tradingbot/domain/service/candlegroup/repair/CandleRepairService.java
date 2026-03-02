package com.example.tradingbot.domain.service.candlegroup.repair;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.domain.service.candlegroup.integrity.CandleIntegrityService;
import com.example.tradingbot.domain.service.candlegroup.integrity.IntegrityResult;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.example.tradingbot.util.Constant.Status.CandleGroup.CANDLE_GROUP_STATUS_REPAIR;
import static com.example.tradingbot.util.Constant.Status.CandleGroup.CANDLE_GROUP_STATUS_SYNC;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleRepairService {

    private final CandleIntegrityService candleIntegrityService;
    private final CandleGapLocator candleGapLocator;
    private final MissingTimestampsResolver missingTimestampsResolver;
    private final GapWindowDownloader gapWindowDownloader;
    private final CandleGroupDataService candleGroupDataService;
    private final CandleGroupsProperties candleGroupsProperties;

    public RepairResult repair(CandleGroupEntity group, CandleGroupRunContext ctx) {
        IntegrityResult before = candleIntegrityService.checkCountOnly(group, ctx);
        if (before.ok()) {
            moveToSync(group);
            return new RepairResult(true, true, List.of(), List.of(), List.of());
        }

        List<TimeWindow> leafWindows = candleGapLocator.locateLeafWindows(
            group.getId(),
            before.startTs(),
            before.endTs(),
            ctx.tfMillis(),
            candleGroupsProperties.getRepairLeafBars()
        );

        List<TimeWindow> gapWindows = new ArrayList<>();
        for (TimeWindow leaf : leafWindows) {
            List<Long> missing = missingTimestampsResolver.findMissingTimestamps(group.getId(), leaf, ctx.tfMillis());
            gapWindows.addAll(missingTimestampsResolver.groupIntoGapWindows(missing, ctx.tfMillis()));
        }

        List<GapRepairResult> repairResults = new ArrayList<>();
        for (TimeWindow gap : gapWindows) {
            GapRepairResult repaired = gapWindowDownloader.repairWindow(group, ctx, gap);
            repairResults.add(repaired);
        }

        IntegrityResult after = candleIntegrityService.checkCountOnly(group, ctx);
        if (after.ok()) {
            moveToSync(group);
        } else {
            candleGroupDataService.updateStatus(group.getId(), CANDLE_GROUP_STATUS_REPAIR);
            group.setStatus(CANDLE_GROUP_STATUS_REPAIR);
        }

        log.info("CandleGroup repair pass: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, startTs={}, endTs={}, beforeExpected={}, beforeActual={}, afterExpected={}, afterActual={}, leafWindows={}, gapWindows={}, repairedGaps={}",
            group.getId(),
            group.getInstrument().getId(),
            group.getTimeframe(),
            group.getStatus(),
            ctx.nowClosedTs(),
            group.getCoverageStartTs(),
            before.startTs(),
            before.endTs(),
            before.expected(),
            before.actual(),
            after.expected(),
            after.actual(),
            leafWindows.size(),
            gapWindows.size(),
            repairResults.stream().filter(GapRepairResult::repaired).count());

        return new RepairResult(false, after.ok(), leafWindows, gapWindows, repairResults);
    }

    private void moveToSync(CandleGroupEntity group) {
        candleGroupDataService.updateStatus(group.getId(), CANDLE_GROUP_STATUS_SYNC);
        group.setStatus(CANDLE_GROUP_STATUS_SYNC);
    }
}
