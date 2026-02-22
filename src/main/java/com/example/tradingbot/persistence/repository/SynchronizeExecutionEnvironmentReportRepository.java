package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.ReconcileReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SynchronizeExecutionEnvironmentReportRepository extends JpaRepository<ReconcileReportEntity, Long> {

    Page<ReconcileReportEntity> findAllByExchangeIdOrderByStartedAtDesc(Long exchangeId, Pageable pageable);

    Page<ReconcileReportEntity> findDistinctByExchangeIdAndAnomaliesInstIdOrderByStartedAtDesc(
            Long exchangeId,
            String instId,
            Pageable pageable
    );

    List<ReconcileReportEntity> findAllByHasAnomaliesFalseAndFinishedAtBefore(Instant threshold);

    long deleteAllByHasAnomaliesFalseAndFinishedAtBefore(Instant threshold);

    Optional<ReconcileReportEntity> findByInternalId(String internalId);
}
