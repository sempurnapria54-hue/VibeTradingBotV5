package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.safety.AnomalyReport;
import com.example.tradingbot.persistence.model.anomaly.AnomalyReportEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct-маппер {@link AnomalyReport} ↔ {@link AnomalyReportEntity}.
 * Енумы (scope/status/severity) конвертируются по имени автоматически;
 * jsonb-слепки переносятся как строки 1:1.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AnomalyReportMapper {

    AnomalyReportEntity domainToPersistence(AnomalyReport report);

    AnomalyReport persistenceToDomain(AnomalyReportEntity entity);
}
