package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.anomaly.AnomalyReport;
import com.example.tradingbot.persistence.model.AnomalyReportEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AnomalyReportMapper {

    AnomalyReport toDomain(AnomalyReportEntity entity);

    List<AnomalyReport> toDomain(List<AnomalyReportEntity> entities);

    AnomalyReportEntity toEntity(AnomalyReport domain);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "message", source = "message")
    @Mapping(target = "internalAfter", source = "internalAfter")
    @Mapping(target = "externalAfter", source = "externalAfter")
    void updateEntity(AnomalyReport domain, @MappingTarget AnomalyReportEntity entity);
}
