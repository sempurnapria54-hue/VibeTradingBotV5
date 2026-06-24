package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.command.DealFinalizationState;
import com.example.tradingbot.persistence.model.command.DealFinalizationStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link DealFinalizationState} между domain ↔ persistence
 * (docs/models/domain/other/DealFinalizationState.md). lastError — JSONB
 * через {@link RuntimeJsonConverter}; retry-скаляры (от Retryable), type и
 * status ↔ строка — MapStruct автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class)
public interface DealFinalizationStateMapper {

    DealFinalizationStateEntity domainToPersistence(DealFinalizationState state);

    DealFinalizationState persistenceToDomain(DealFinalizationStateEntity entity);
}
