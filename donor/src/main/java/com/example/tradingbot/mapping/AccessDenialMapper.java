package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.security.AccessDenial;
import com.example.tradingbot.persistence.model.security.AccessDenialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct-маппер {@link AccessDenial} ↔ {@link AccessDenialEntity}. Енум
 * класса отказа конвертируется по имени автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccessDenialMapper {

    AccessDenialEntity domainToPersistence(AccessDenial denial);

    AccessDenial persistenceToDomain(AccessDenialEntity entity);
}
