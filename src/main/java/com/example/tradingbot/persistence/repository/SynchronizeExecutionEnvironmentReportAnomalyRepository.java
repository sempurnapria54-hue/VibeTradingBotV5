package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.SynchronizeExecutionEnvironmentReportAnomalyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynchronizeExecutionEnvironmentReportAnomalyRepository extends JpaRepository<SynchronizeExecutionEnvironmentReportAnomalyEntity, Long> {

    List<SynchronizeExecutionEnvironmentReportAnomalyEntity> findAllByReportIdOrderByCreatedAtAsc(Long reportId);
}
