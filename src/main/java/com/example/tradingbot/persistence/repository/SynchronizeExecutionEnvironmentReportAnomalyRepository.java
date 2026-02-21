package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.entity.ReconcileAnomalyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynchronizeExecutionEnvironmentReportAnomalyRepository extends JpaRepository<ReconcileAnomalyEntity, Long> {

    List<ReconcileAnomalyEntity> findAllByReportIdOrderByCreatedAtAsc(Long reportId);
}
