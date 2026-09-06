package com.example.tradingcore.mapping;

import com.example.tradingcore.domain.safety.AnomalyReport;
import com.example.tradingcore.persistence.model.AnomalyReportEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг отчёта о происшествии domain ↔ persistence
 * (docs/models/domain/other/AnomalyReport.md). Перечни хранятся строкой и
 * конвертируются по имени; снимки состояния едут строкой JSONB.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AnomalyReportMapper {

    AnomalyReportEntity domainToPersistence(AnomalyReport report);

    AnomalyReport persistenceToDomain(AnomalyReportEntity entity);
}
