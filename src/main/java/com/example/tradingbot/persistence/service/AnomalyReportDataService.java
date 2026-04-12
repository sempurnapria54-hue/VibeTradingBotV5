package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.mapping.AnomalyReportMapper;
import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import com.example.tradingbot.persistence.repository.AnomalyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.tradingbot.util.Constant.ErrorCode.ANOMALY_REPORT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AnomalyReportDataService {

    private final AnomalyReportRepository repository;
    private final AnomalyReportMapper mapper;

    @Transactional
    public AnomalyReport save(AnomalyReport report) {
        if (report.getId() == null) {
            AnomalyReportEntity created = repository.save(mapper.toEntity(report));
            return mapper.toDomain(created);
        }

        AnomalyReportEntity existing = repository.findById(report.getId())
                .orElseThrow(() -> new RuntimeException(ANOMALY_REPORT_NOT_FOUND));
        mapper.updateEntity(report, existing);
        AnomalyReportEntity saved = repository.save(existing);
        return mapper.toDomain(saved);
    }

    public AnomalyReport getRequiredById(Long id) {
        return repository.findById(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new RuntimeException(ANOMALY_REPORT_NOT_FOUND));
    }

    public List<AnomalyReport> findByStatuses(List<AnomalyReport.Status> statuses) {
        List<AnomalyReportEntity> entities = repository.findAllByStatusInOrderByIdDesc(statuses);
        return mapper.toDomain(entities);
    }

    public List<AnomalyReport> findByExchangeId(Long exchangeId) {
        List<AnomalyReportEntity> entities = repository.findAllByExchangeIdOrderByIdDesc(exchangeId);
        return mapper.toDomain(entities);
    }

    public List<AnomalyReport> findByInstrumentId(Long instrumentId) {
        List<AnomalyReportEntity> entities = repository.findAllByInstrumentIdOrderByIdDesc(instrumentId);
        return mapper.toDomain(entities);
    }
}
