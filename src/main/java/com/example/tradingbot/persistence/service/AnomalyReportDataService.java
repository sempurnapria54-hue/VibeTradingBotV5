package com.example.tradingbot.persistence.service;

import com.example.tradingbot.domain.safety.AnomalyReport;
import com.example.tradingbot.mapping.AnomalyReportMapper;
import com.example.tradingbot.persistence.repository.AnomalyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Граница domain ↔ persistence для {@link AnomalyReport}. Создание и
 * продвижение по lifecycle идут через один {@code save} (по id).
 */
@Service
@RequiredArgsConstructor
public class AnomalyReportDataService {

    private final AnomalyReportRepository repository;
    private final AnomalyReportMapper mapper;

    @Transactional
    public AnomalyReport save(AnomalyReport report) {
        return mapper.persistenceToDomain(repository.save(mapper.domainToPersistence(report)));
    }
}
