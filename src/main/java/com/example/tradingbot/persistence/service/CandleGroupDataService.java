package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.persistence.repository.CandleGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CandleGroupDataService {

    private final CandleGroupRepository candleGroupRepository;

    public List<CandleGroupEntity> findEligibleForRun(long nowMillis,
                                                      Collection<String> statuses,
                                                      int maxGroups) {
        return candleGroupRepository.findEligibleForRun(nowMillis, statuses, PageRequest.of(0, maxGroups));
    }

    public Optional<CandleGroupEntity> getById(Long groupId) {
        return candleGroupRepository.findById(groupId);
    }

    public Optional<CandleGroupEntity> findByInstrumentIdAndTimeframe(Long instrumentId, String timeframe) {
        return candleGroupRepository.findByInstrumentIdAndTimeframe(instrumentId, timeframe);
    }

    public List<CandleGroupEntity> findAllByInstrumentId(Long instrumentId) {
        return candleGroupRepository.findAllByInstrumentIdOrderByIdAsc(instrumentId);
    }

    @Transactional
    public CandleGroupEntity create(CandleGroupEntity candleGroupEntity) {
        return candleGroupRepository.save(candleGroupEntity);
    }

    @Transactional
    public boolean tryAcquireLease(Long groupId, String owner, long nowMillis, long leaseUntilMillis) {
        return candleGroupRepository.tryAcquireLease(groupId, owner, nowMillis, leaseUntilMillis) > 0;
    }

    @Transactional
    public void extendLease(Long groupId, String owner, long newLeaseUntilMillis) {
        candleGroupRepository.extendLease(groupId, owner, newLeaseUntilMillis);
    }

    @Transactional
    public void releaseLease(Long groupId, String owner) {
        candleGroupRepository.releaseLease(groupId, owner);
    }

    @Transactional
    public void markSuccess(Long groupId, OffsetDateTime now) {
        candleGroupRepository.markSuccess(groupId, now);
    }

    @Transactional
    public void markError(Long groupId, String code, String message, OffsetDateTime now, int attempts) {
        candleGroupRepository.markError(groupId, code, message, now, attempts);
    }

    @Transactional
    public void updateStatus(Long groupId, String status) {
        candleGroupRepository.updateStatus(groupId, status);
    }

    @Transactional
    public void updateBackfillCursor(Long groupId, Long cursorTs) {
        candleGroupRepository.updateBackfillCursor(groupId, cursorTs);
    }

    @Transactional
    public void updateLastTailSync(Long groupId, Long nowClosedTs) {
        candleGroupRepository.updateLastTailSync(groupId, nowClosedTs);
    }

    @Transactional
    public void incrementAttemptCount(Long groupId) {
        candleGroupRepository.incrementAttemptCount(groupId);
    }

    public CandleGroupEntity save(CandleGroupEntity candleGroup) {
        return candleGroupRepository.save(candleGroup);
    }
}
