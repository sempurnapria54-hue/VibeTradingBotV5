package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    List<AnomalyReportEntity> findAllByStatusInOrderByIdDesc(Collection<AnomalyReport.Status> statuses);

    List<AnomalyReportEntity> findAllByExchangeIdOrderByIdDesc(Long exchangeId);

    List<AnomalyReportEntity> findAllByInstrumentIdOrderByIdDesc(Long instrumentId);
}
