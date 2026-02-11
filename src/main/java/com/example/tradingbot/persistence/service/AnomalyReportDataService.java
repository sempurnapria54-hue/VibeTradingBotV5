package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import com.example.tradingbot.persistence.repository.AnomalyReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnomalyReportDataService {

    private final AnomalyReportRepository anomalyReportRepository;

    @Transactional
    public AnomalyReportEntity save(AnomalyReportEntity anomalyReportEntity) {
        return anomalyReportRepository.save(anomalyReportEntity);
    }

    @Transactional
    public List<AnomalyReportEntity> saveAll(List<AnomalyReportEntity> anomalyReportEntities) {
        return anomalyReportRepository.saveAll(anomalyReportEntities);
    }

    public List<AnomalyReportEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId) {
        return anomalyReportRepository.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
    }
}
