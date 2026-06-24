package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.anomaly.AnomalyReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {
}
