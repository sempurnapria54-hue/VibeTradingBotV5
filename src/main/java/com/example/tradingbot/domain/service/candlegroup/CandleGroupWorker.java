package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.integrity.CandleIntegrityService;
import com.example.tradingbot.domain.service.candlegroup.integrity.IntegrityResult;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
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
    private final java.time.Clock clock;
    private final ConcurrentMap<Long, Integer> syncRunCounters = new ConcurrentHashMap<>();

    public void processGroup(Long candleGroupId) {
        CandleGroupEntity group = candleGroupDataService.getById(candleGroupId)
            .orElseThrow(() -> new IllegalStateException("Candle group not found, id=" + candleGroupId));

        CandleGroupRunContext context = runContextFactory.create(group);
        log.info("CandleGroup run start: groupId={}, instrumentId={}, timeframe={}, status={}, nowClosedTs={}, instanceId={}",
            group.getId(),
            group.getInstrumentId(),
            group.getTimeframe(),
            group.getStatus(),
            context.nowClosedTs(),
            context.instanceId());

        try {
            candleGroupLeaseService.extendLease(group.getId());
            runSkeleton(group, context);
            candleGroupDataService.markSuccess(group.getId(), nowUtc());
            log.info("CandleGroup run success: groupId={}, status={}, nowClosedTs={}",
                group.getId(), group.getStatus(), context.nowClosedTs());
        } catch (Exception ex) {
            handleFailure(group.getId(), ex);
            throw ex;
        }
    }

    private void runSkeleton(CandleGroupEntity group, CandleGroupRunContext context) {
        if (group.getStatus() == CandleGroupStatus.NEW) {
            candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.BACKFILL_RUNNING);
            group.setStatus(CandleGroupStatus.BACKFILL_RUNNING);
            log.info("CandleGroup S1 skeleton: groupId={}, nowClosedTs={}, action=NEW->BACKFILL_RUNNING", group.getId(), context.nowClosedTs());
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
        log.info("CandleGroup S2 tail sync: groupId={}, timeframe={}, fetched={}, saved={}, updatedLastTailSyncTs={}",
            group.getId(),
            group.getTimeframe(),
            result.fetched(),
            result.saved(),
            result.updatedLastTailSyncTs());
    }

    private void runBackfill(CandleGroupEntity group, CandleGroupRunContext context) {
        BackfillResult result = backfillService.backfillToCoverage(group, context);
        log.info("CandleGroup S3 backfill: groupId={}, timeframe={}, completed={}, newCursorTs={}, fetched={}, saved={}",
            group.getId(),
            group.getTimeframe(),
            result.completed(),
            result.newCursorTs(),
            result.fetched(),
            result.saved());

        if (result.completed()) {
            runIntegrity(group, context, "S1");
            return;
        }

        candleGroupDataService.incrementAttemptCount(group.getId());
    }

    private void runIntegrity(CandleGroupEntity group, CandleGroupRunContext context, String scenario) {
        CandleGroupsProperties.IntegrityCheckMode mode = candleGroupsProperties.getIntegrityCheckMode();
        if (mode == CandleGroupsProperties.IntegrityCheckMode.NONE) {
            log.debug("CandleGroup integrity skipped: groupId={}, status={}, mode=NONE, scenario={}",
                group.getId(),
                group.getStatus(),
                scenario);
            return;
        }

        IntegrityResult result = candleIntegrityService.checkCountOnly(group, context);
        log.info("CandleGroup integrity count: groupId={}, timeframe={}, status={}, scenario={}, startTs={}, endTs={}, expected={}, actual={}, ok={}, mode={}",
            group.getId(),
            group.getTimeframe(),
            group.getStatus(),
            scenario,
            result.startTs(),
            result.endTs(),
            result.expected(),
            result.actual(),
            result.ok(),
            mode);

        if (result.ok()) {
            if (group.getStatus() == CandleGroupStatus.REPAIR_RUNNING || group.getStatus() == CandleGroupStatus.BACKFILL_RUNNING) {
                candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.SYNC);
                group.setStatus(CandleGroupStatus.SYNC);
            }
            return;
        }

        candleGroupDataService.updateStatus(group.getId(), CandleGroupStatus.REPAIR_RUNNING);
        group.setStatus(CandleGroupStatus.REPAIR_RUNNING);

        if (mode == CandleGroupsProperties.IntegrityCheckMode.COUNT_PLUS_REPAIR) {
            log.info("CandleGroup integrity mismatch: groupId={}, action=repair-pending-task-06G", group.getId());
            return;
        }

        log.info("CandleGroup integrity mismatch: groupId={}, action=COUNT_ONLY-stop", group.getId());
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

    private void handleFailure(Long groupId, Exception ex) {
        CandleGroupEntity current = candleGroupDataService.getById(groupId)
            .orElseThrow(() -> new IllegalStateException("Candle group not found during error handling, id=" + groupId));

        int nextAttempt = current.getAttemptCount() == null ? 1 : current.getAttemptCount() + 1;
        String errorCode = ex.getClass().getSimpleName();
        String errorMessage = trimErrorMessage(ex.getMessage());

        candleGroupDataService.markError(groupId, errorCode, errorMessage, nowUtc(), nextAttempt);

        if (nextAttempt >= candleGroupsProperties.getMaxAttemptsBeforeError()) {
            candleGroupDataService.updateStatus(groupId, CandleGroupStatus.ERROR);
            log.error("CandleGroup run failed: groupId={}, attemptCount={}, threshold={}, action=ERROR, errorCode={}, message={}",
                groupId,
                nextAttempt,
                candleGroupsProperties.getMaxAttemptsBeforeError(),
                errorCode,
                errorMessage);
            return;
        }

        log.warn("CandleGroup run failed: groupId={}, attemptCount={}, threshold={}, action=RETRY, errorCode={}, message={}",
            groupId,
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
