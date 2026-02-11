package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynchronizeExecutionEnvironmentReportRepository extends JpaRepository<SynchronizeExecutionEnvironmentReportEntity, Long> {

    Page<SynchronizeExecutionEnvironmentReportEntity> findAllByExchangeIdOrderByStartedAtDesc(Long exchangeId, Pageable pageable);

    List<SynchronizeExecutionEnvironmentReportEntity> findAllByHasAnomaliesFalseAndFinishedAtBefore(Instant threshold);

    long deleteAllByHasAnomaliesFalseAndFinishedAtBefore(Instant threshold);
}
