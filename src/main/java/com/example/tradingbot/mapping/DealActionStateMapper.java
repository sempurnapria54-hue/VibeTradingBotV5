package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.persistence.model.command.DealActionStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link DealActionState} между domain ↔ persistence
 * (docs/models/domain/other/DealActionState.md). target и lastError —
 * JSONB через {@link RuntimeJsonConverter}; retry-скаляры (от Retryable)
 * и status ↔ строка — MapStruct автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class)
public interface DealActionStateMapper {

    DealActionStateEntity domainToPersistence(DealActionState state);

    DealActionState persistenceToDomain(DealActionStateEntity entity);
}
