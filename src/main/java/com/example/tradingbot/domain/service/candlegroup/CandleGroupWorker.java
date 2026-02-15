package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.integrity.CandleIntegrityService;
import com.example.tradingbot.domain.service.candlegroup.integrity.IntegrityResult;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.domain.service.candlegroup.repair.CandleRepairService;
import com.example.tradingbot.domain.service.candlegroup.repair.RepairResult;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.CandleGroupStatus;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleGroupWorker {

    private final CandleGroupDataService candleGroupDataService;
    private final CandleGroupRunContextFactory runContextFactory;
    private final CandleGroupLeaseService candleGroupLeaseService;
    private final CandleGroupsProperties candleGroupsProperties;
    private final TailSyncService tailSyncService;
    private final BackfillService backfillService;
    private final CandleIntegrityService candleIntegrityService;
    private final CandleRepairService candleRepairService;
    private final java.time.Clock clock;
    private final ConcurrentMap<Long, Integer> syncRunCounters = new ConcurrentHashMap<>();

    public void processGroup(Long candleGroupId) {
        CandleGroupEntity group = candleGroupDataService.getById(candleGroupId)
            .orElseThrow(() -> new IllegalStateException("Candle group not found, id=" + candleGroupId));

        CandleGroupRunContext context = runContextFactory.create(group);
        log.info("CandleGroup run start: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, instanceId={}",
            group.getId(),
            group.getInstrumentId(),
            group.getTimeframe(),
            group.getStatus(),
            context.nowClosedTs(),
            group.getCoverageStartTs(),
            group.getBackfillCursorTs(),
            context.instanceId());

        try {
            candleGroupLeaseService.extendLease(group.getId());
            runSkeleton(group, context);
            candleGroupDataService.markSuccess(group.getId(), nowUtc());
            log.info("CandleGroup run success: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, attemptsReset=true",
                group.getId(), group.getInstrumentId(), group.getTimeframe(), group.getStatus(), context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
        } catch (Exception ex) {
            handleFailure(group, context, ex);
            throw ex;
        }
    }

    private void runSkeleton(CandleGroupEntity group, CandleGroupRunContext context) {
        if (group.getStatus() == CandleGroupStatus.NEW) {
            candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.BACKFILL_RUNNING);
            group.setStatus(CandleGroupStatus.BACKFILL_RUNNING);
            log.info("CandleGroup status transition: groupId={}, instrumentId={}, timeframe={}, fromStatus=NEW, toStatus=BACKFILL_RUNNING, nowClosedTs={}, coverageStartTs={}, cursor={}",
                group.getId(), group.getInstrumentId(), group.getTimeframe(), context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
            runTailSync(group, context);
            runBackfill(group, context);
            return;
        }

        if (group.getStatus() == CandleGroupStatus.BACKFILL_RUNNING) {
            runBackfill(group, context);
            return;
        }

        if (group.getStatus() == CandleGroupStatus.REPAIR_RUNNING) {
            runTailSync(group, context);
            runIntegrity(group, context, "S6");
            return;
        }

        if (group.getStatus() == CandleGroupStatus.SYNC) {
            runTailSync(group, context);
            if (shouldRunSyncIntegrityCheck(group.getId())) {
                runIntegrity(group, context, "S2");
            }
            return;
        }

        throw new IllegalStateException("Unsupported candle group status for worker: " + group.getStatus());
    }

    private void runTailSync(CandleGroupEntity group, CandleGroupRunContext context) {
        TailSyncResult result = tailSyncService.syncTail(group, context);
        log.info("CandleGroup S2 tail sync: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, fetched={}, saved={}, updatedLastTailSyncTs={}",
            group.getId(),
            group.getInstrumentId(),
            group.getTimeframe(),
            group.getStatus(),
            context.nowClosedTs(),
            group.getCoverageStartTs(),
            group.getBackfillCursorTs(),
            result.fetched(),
            result.saved(),
            result.updatedLastTailSyncTs());
    }

    private void runBackfill(CandleGroupEntity group, CandleGroupRunContext context) {
        BackfillResult result = backfillService.backfillToCoverage(group, context);
        group.setBackfillCursorTs(result.newCursorTs());
        log.info("CandleGroup S3 backfill: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, completed={}, batchesProcessed={}, newCursorTs={}, fetched={}, saved={}",
            group.getId(),
            group.getInstrumentId(),
            group.getTimeframe(),
            group.getStatus(),
            context.nowClosedTs(),
            group.getCoverageStartTs(),
            result.completed(),
            result.batchesProcessed(),
            result.newCursorTs(),
            result.fetched(),
            result.saved());

        if (result.completed()) {
            candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.REPAIR_RUNNING);
            group.setStatus(CandleGroupStatus.REPAIR_RUNNING);
            log.info("CandleGroup status transition: groupId={}, instrumentId={}, timeframe={}, fromStatus=BACKFILL_RUNNING, toStatus=REPAIR_RUNNING, nowClosedTs={}, coverageStartTs={}, cursor={}",
                group.getId(), group.getInstrumentId(), group.getTimeframe(), context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
            runIntegrity(group, context, "S1");
        }
    }

    private void runIntegrity(CandleGroupEntity group, CandleGroupRunContext context, String scenario) {
        CandleGroupsProperties.IntegrityCheckMode mode = candleGroupsProperties.getIntegrityCheckMode();
        if (mode == CandleGroupsProperties.IntegrityCheckMode.NONE) {
            log.debug("CandleGroup integrity skipped: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, mode=NONE, scenario={}",
                group.getId(),
                group.getInstrumentId(),
                group.getTimeframe(),
                group.getStatus(),
                context.nowClosedTs(),
                group.getCoverageStartTs(),
                group.getBackfillCursorTs(),
                scenario);
            return;
        }

        IntegrityResult result = candleIntegrityService.checkCountOnly(group, context);
        log.info("CandleGroup integrity count: groupId={}, instrumentId={}, timeframe={}, status={}, scenario={}, nowClosedTs={}, coverageStartTs={}, cursor={}, startTs={}, endTs={}, expected={}, actual={}, ok={}, mode={}",
            group.getId(),
            group.getInstrumentId(),
            group.getTimeframe(),
            group.getStatus(),
            scenario,
            context.nowClosedTs(),
            group.getCoverageStartTs(),
            group.getBackfillCursorTs(),
            result.startTs(),
            result.endTs(),
            result.expected(),
            result.actual(),
            result.ok(),
            mode);

        if (result.ok()) {
            if (group.getStatus() == CandleGroupStatus.REPAIR_RUNNING || group.getStatus() == CandleGroupStatus.BACKFILL_RUNNING) {
                CandleGroupStatus fromStatus = group.getStatus();
                candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.SYNC);
                group.setStatus(CandleGroupStatus.SYNC);
                log.info("CandleGroup status transition: groupId={}, instrumentId={}, timeframe={}, fromStatus={}, toStatus=SYNC, nowClosedTs={}, coverageStartTs={}, cursor={}",
                    group.getId(), group.getInstrumentId(), group.getTimeframe(), fromStatus, context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
            }
            return;
        }

        if (group.getStatus() != CandleGroupStatus.REPAIR_RUNNING) {
            CandleGroupStatus fromStatus = group.getStatus();
            candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.REPAIR_RUNNING);
            group.setStatus(CandleGroupStatus.REPAIR_RUNNING);
            log.info("CandleGroup status transition: groupId={}, instrumentId={}, timeframe={}, fromStatus={}, toStatus=REPAIR_RUNNING, nowClosedTs={}, coverageStartTs={}, cursor={}, reason=integrity-mismatch",
                group.getId(), group.getInstrumentId(), group.getTimeframe(), fromStatus, context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
        }

        if (mode == CandleGroupsProperties.IntegrityCheckMode.COUNT_PLUS_REPAIR) {
            RepairResult repairResult = candleRepairService.repair(group, context);
            log.info("CandleGroup repair run: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, countOkBefore={}, countOkAfter={}, leafWindows={}, gapWindows={}, repairedGaps={}",
                group.getId(),
                group.getInstrumentId(),
                group.getTimeframe(),
                group.getStatus(),
                context.nowClosedTs(),
                group.getCoverageStartTs(),
                group.getBackfillCursorTs(),
                repairResult.countOkBeforeRepair(),
                repairResult.countOkAfterRepair(),
                repairResult.leafWindows().size(),
                repairResult.gapWindows().size(),
                repairResult.repairedGaps().stream().filter(gap -> gap.repaired()).count());
            return;
        }

        log.info("CandleGroup integrity mismatch: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, action=COUNT_ONLY-stop",
            group.getId(), group.getInstrumentId(), group.getTimeframe(), group.getStatus(), context.nowClosedTs(), group.getCoverageStartTs(), group.getBackfillCursorTs());
    }

    private boolean shouldRunSyncIntegrityCheck(Long groupId) {
        int everyNRuns = candleGroupsProperties.getSyncIntegrityEveryNRuns();
        if (everyNRuns <= 0) {
            return false;
        }

        int current = syncRunCounters.merge(groupId, 1, Integer::sum);
        if (current >= everyNRuns) {
            syncRunCounters.put(groupId, 0);
            return true;
        }
        return false;
    }

    private void handleFailure(CandleGroupEntity group, CandleGroupRunContext context, Exception ex) {
        CandleGroupEntity current = candleGroupDataService.getById(group.getId())
            .orElseThrow(() -> new IllegalStateException("Candle group not found during error handling, id=" + group.getId()));

        int nextAttempt = (current.getAttemptCount() == null ? 0 : current.getAttemptCount()) + 1;
        String errorCode = ex.getClass().getSimpleName();
        String errorMessage = trimErrorMessage(ex.getMessage());

        candleGroupDataService.markError(group.getId(), errorCode, errorMessage, nowUtc(), nextAttempt);

        if (nextAttempt >= candleGroupsProperties.getMaxAttemptsBeforeError()) {
            CandleGroupStatus fromStatus = current.getStatus();
            candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.ERROR);
            current.setStatus(CandleGroupStatus.ERROR);
            log.error("CandleGroup run failed: groupId={}, instrumentId={}, timeframe={}, fromStatus={}, toStatus=ERROR, nowClosedTs={}, coverageStartTs={}, cursor={}, attemptCount={}, threshold={}, errorCode={}, message={}",
                group.getId(),
                current.getInstrumentId(),
                current.getTimeframe(),
                fromStatus,
                context.nowClosedTs(),
                current.getCoverageStartTs(),
                current.getBackfillCursorTs(),
                nextAttempt,
                candleGroupsProperties.getMaxAttemptsBeforeError(),
                errorCode,
                errorMessage);
            return;
        }

        log.warn("CandleGroup run failed: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, coverageStartTs={}, cursor={}, attemptCount={}, threshold={}, action=RETRY, errorCode={}, message={}",
            group.getId(),
            current.getInstrumentId(),
            current.getTimeframe(),
            current.getStatus(),
            context.nowClosedTs(),
            current.getCoverageStartTs(),
            current.getBackfillCursorTs(),
            nextAttempt,
            candleGroupsProperties.getMaxAttemptsBeforeError(),
            errorCode,
            errorMessage);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private String trimErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= CandleGroupEntity.ERROR_MESSAGE_LENGTH
            ? message
            : message.substring(0, CandleGroupEntity.ERROR_MESSAGE_LENGTH);
    }
}
