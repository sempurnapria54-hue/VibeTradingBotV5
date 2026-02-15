package com.example.tradingbot.domain.service.candlegroup;

import com.example.tradingbot.config.CandleGroupsProperties;
import com.example.tradingbot.domain.service.candlegroup.model.CandleGroupRunContext;
import com.example.tradingbot.persistence.model.CandleGroupEntity;
import com.example.tradingbot.persistence.model.CandleGroupStatus;
import com.example.tradingbot.persistence.service.CandleGroupDataService;
import java.time.OffsetDateTime;
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
    private final java.time.Clock clock;

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
            log.info("CandleGroup S3 skeleton: groupId={}, nowClosedTs={}, action=backfill-placeholder", group.getId(), context.nowClosedTs());
            return;
        }

        if (group.getStatus() == CandleGroupStatus.BACKFILL_RUNNING) {
            runTailSync(group, context);
            log.info("CandleGroup S3 skeleton: groupId={}, nowClosedTs={}, action=continue-backfill-placeholder", group.getId(), context.nowClosedTs());
            return;
        }

        if (group.getStatus() == CandleGroupStatus.REPAIR_RUNNING) {
            runTailSync(group, context);
            log.info("CandleGroup S6 skeleton: groupId={}, nowClosedTs={}, action=integrity-repair-placeholder", group.getId(), context.nowClosedTs());
            return;
        }

        if (group.getStatus() == CandleGroupStatus.SYNC) {
            runTailSync(group, context);
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
