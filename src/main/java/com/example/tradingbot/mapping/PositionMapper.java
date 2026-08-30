package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.PositionOkxResponse;
import com.example.tradingbot.persistence.model.position.PositionEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link Position} между слоями: domain ↔ persistence и OKX
 * response → snapshot (docs/models/domain/core/Position.md,
 * docs/models/mapping/Position.md). pos → abs(size) + direction (знак)
 * через {@link OkxResponseConverter}; enum'ы ↔ строка — MapStruct
 * автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class)
public interface PositionMapper {

    PositionEntity domainToPersistence(Position position);

    Position persistenceToDomain(PositionEntity entity);

    @Mapping(target = "externalId", source = "posId")
    @Mapping(target = "externalSize", source = "pos", qualifiedByName = "okxAbsSize")
    @Mapping(target = "direction", source = "pos", qualifiedByName = "okxDirection")
    @Mapping(target = "externalAverageEntryPrice", source = "avgPx")
    @Mapping(target = "externalMarkPrice", source = "markPx")
    @Mapping(target = "externalLiquidationPrice", source = "liqPx")
    @Mapping(target = "externalMargin", source = "margin")
    @Mapping(target = "externalUnrealizedProfit", source = "upl")
    @Mapping(target = "externalCreatedAt", source = "cTime")
    @Mapping(target = "externalModifiedAt", source = "uTime")
    PositionExternalSnapshot integrationToSnapshot(PositionOkxResponse response);

    /**
     * Обновление полей Position из снапшота (REFRESH-контур). Доменный
     * status / closeReason применяет исполнитель через резолвер.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromSnapshot(PositionExternalSnapshot snapshot, @MappingTarget Position position);
}
