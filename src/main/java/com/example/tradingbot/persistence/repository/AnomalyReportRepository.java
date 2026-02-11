package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyReportRepository extends JpaRepository<AnomalyReportEntity, Long> {

    List<AnomalyReportEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId);
}
