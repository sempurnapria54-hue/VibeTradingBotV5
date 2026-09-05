package com.example.connector.okx.mapping;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.connector.okx.snapshot.PositionCloseResultExternalSnapshot;
import com.example.connector.okx.snapshot.PositionExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.PositionOkxResponse;
import com.example.connector.okx.integration.model.okx.response.PositionsHistoryOkxResponse;
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

    @Mapping(target = "externalId", source = "posId")
    @Mapping(target = "externalInstrumentId", source = "instId")
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

    /**
     * Снапшот → доменная позиция, СОЗДАНИЕМ: коннектор отдаёт модель, а
     * не доливает чужую. Доменный статус резолвит вызывающий.
     */
    Position snapshotToDomain(PositionExternalSnapshot snapshot);

    /**
     * Запись истории позиций → граничный снапшот положения закрытия.
     * Четыре слагаемых тождества конвертируются по НЕСОБЫТИЙНОЙ природе
     * (пусто → ноль), финансирование дополнительно нормализуется в
     * издержку, направление резолвится в доменное значение здесь же.
     */
    @Mapping(target = "externalPosId", source = "posId")
    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalRealizedPnl", source = "realizedPnl")
    @Mapping(target = "externalResultCurrency", source = "ccy")
    @Mapping(target = "externalCloseAveragePrice", source = "closeAvgPx")
    @Mapping(target = "externalCloseType", source = "type")
    @Mapping(target = "externalRealizedPnlGross", source = "pnl", qualifiedByName = "okxNonEventDecimal")
    @Mapping(target = "externalFee", source = "fee", qualifiedByName = "okxNonEventDecimal")
    @Mapping(target = "externalFundingCost", source = "fundingFee", qualifiedByName = "okxFundingCost")
    @Mapping(target = "externalLiquidationPenalty", source = "liqPenalty", qualifiedByName = "okxNonEventDecimal")
    @Mapping(target = "direction", source = "direction", qualifiedByName = "okxCloseDirection")
    @Mapping(target = "externalCreatedAt", source = "cTime")
    @Mapping(target = "externalModifiedAt", source = "uTime")
    PositionCloseResultExternalSnapshot integrationToCloseSnapshot(PositionsHistoryOkxResponse response);

    /**
     * Наполнение строки эпизода положением закрытия — тропа ОБНОВЛЕНИЯ:
     * строку закрыла нога 1, здесь появляются реализованные факты.
     * Идентичность эпизода (пара) и направление уже стоят — снапшот их
     * не перезаписывает.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "externalRealizedProfit", source = "externalRealizedPnl")
    @Mapping(target = "externalRealizedProfitGross", source = "externalRealizedPnlGross")
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "direction", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    void updateFromCloseSnapshot(PositionCloseResultExternalSnapshot snapshot, @MappingTarget Position position);

    /**
     * Наполнение строки эпизода положением закрытия — тропа
     * МАТЕРИАЛИЗАЦИИ: позиция впервые увидена уже закрытой, поэтому
     * ложатся и данные идентичности (идентификатор, время создания) и
     * направление.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "externalRealizedProfit", source = "externalRealizedPnl")
    @Mapping(target = "externalRealizedProfitGross", source = "externalRealizedPnlGross")
    @Mapping(target = "externalId", source = "externalPosId")
    void materializeFromCloseSnapshot(PositionCloseResultExternalSnapshot snapshot, @MappingTarget Position position);
}
