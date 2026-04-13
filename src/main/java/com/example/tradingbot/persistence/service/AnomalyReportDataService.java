package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.mapping.AnomalyReportMapper;
import com.example.tradingbot.persistence.model.anomaly.AnomalyReportEntity;
import com.example.tradingbot.persistence.repository.AnomalyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.ANOMALY_REPORT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AnomalyReportDataService {

    private final AnomalyReportRepository repository;
    private final AnomalyReportMapper mapper;

    @Transactional
    public AnomalyReport save(AnomalyReport report) {
        AnomalyReportEntity data = mapper.domainToData(report);
        AnomalyReportEntity saved = repository.save(data);
        return mapper.dataToDomain(saved);
    }

    public AnomalyReport getRequiredById(Long id) {
        return repository.findById(id)
                         .map(mapper::dataToDomain)
                         .orElseThrow(() -> new RuntimeException(ANOMALY_REPORT_NOT_FOUND));
    }
}
