package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.CandleGroupEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandleGroupRepository extends JpaRepository<CandleGroupEntity, Long> {

    Optional<CandleGroupEntity> findByInstrumentIdAndTimeframe(Long instrumentId, String timeframe);

    List<CandleGroupEntity> findAllByInstrumentIdOrderByIdAsc(Long instrumentId);

    @Query("""
        select cg
        from CandleGroupEntity cg
        where cg.status in :statuses
          and (cg.leaseUntil is null or cg.leaseUntil < :nowMillis)
        order by cg.id asc
        """)
    List<CandleGroupEntity> findEligibleForRun(
        @Param("nowMillis") long nowMillis,
        @Param("statuses") Collection<String> statuses,
        Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.leaseOwner = :owner,
            cg.leaseUntil = :leaseUntilMillis
        where cg.id = :groupId
          and (cg.leaseUntil is null or cg.leaseUntil < :nowMillis or cg.leaseOwner = :owner)
        """)
    int tryAcquireLease(
        @Param("groupId") Long groupId,
        @Param("owner") String owner,
        @Param("nowMillis") long nowMillis,
        @Param("leaseUntilMillis") long leaseUntilMillis
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.leaseUntil = :newLeaseUntilMillis
        where cg.id = :groupId and cg.leaseOwner = :owner
        """)
    int extendLease(
        @Param("groupId") Long groupId,
        @Param("owner") String owner,
        @Param("newLeaseUntilMillis") long newLeaseUntilMillis
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.leaseOwner = null,
            cg.leaseUntil = null
        where cg.id = :groupId and cg.leaseOwner = :owner
        """)
    int releaseLease(@Param("groupId") Long groupId, @Param("owner") String owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.lastSuccessAt = :now,
            cg.lastErrorAt = null,
            cg.lastErrorCode = null,
            cg.lastErrorMessage = null,
            cg.attemptCount = 0
        where cg.id = :groupId
        """)
    int markSuccess(@Param("groupId") Long groupId, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.lastErrorAt = :now,
            cg.lastErrorCode = :code,
            cg.lastErrorMessage = :message,
            cg.attemptCount = :attempts
        where cg.id = :groupId
        """)
    int markError(
        @Param("groupId") Long groupId,
        @Param("code") String code,
        @Param("message") String message,
        @Param("now") OffsetDateTime now,
        @Param("attempts") int attempts
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.status = :status
        where cg.id = :groupId
        """)
    int updateStatus(@Param("groupId") Long groupId, @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.backfillCursorTs = :cursorTs
        where cg.id = :groupId
        """)
    int updateBackfillCursor(@Param("groupId") Long groupId, @Param("cursorTs") Long cursorTs);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.lastTailSyncTs = :nowClosedTs
        where cg.id = :groupId
        """)
    int updateLastTailSync(@Param("groupId") Long groupId, @Param("nowClosedTs") Long nowClosedTs);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CandleGroupEntity cg
        set cg.attemptCount = cg.attemptCount + 1
        where cg.id = :groupId
        """)
    int incrementAttemptCount(@Param("groupId") Long groupId);
}
