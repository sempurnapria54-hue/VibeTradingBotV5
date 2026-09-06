package com.example.tradingcore.persistence.service;

import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.mapping.AnomalyReportMapper;
import com.example.tradingcore.persistence.repository.AnomalyReportRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Граница domain ↔ persistence для журнала происшествий. */
@Service
@RequiredArgsConstructor
public class AnomalyReportDataService {

    private final AnomalyReportRepository repository;
    private final AnomalyReportMapper mapper;

    @Transactional
    public AnomalyReport save(AnomalyReport report) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(report)));
    }

    /**
     * Строка по ключу состояния стои́т в окне наблюдения. Ключ — объект
     * радиуса, сущность-предмет, код и критичность
     * (docs/models/domain/other/AnomalyReport.md §«Ключ дедупа у
     * состояния»).
     */
    @Transactional(readOnly = true)
    public Boolean existsStanding(Long exchangeAccountId, Long instrumentId, String subjectExternalId,
                                  String code, AnomalyReport.Severity severity,
                                  OffsetDateTime since, OffsetDateTime until) {
        return repository.existsStanding(exchangeAccountId, instrumentId, subjectExternalId, code,
                severity.name(), since, until);
    }
}
