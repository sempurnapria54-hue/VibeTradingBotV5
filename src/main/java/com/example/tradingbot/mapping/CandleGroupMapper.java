package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.response.CandleGroupApiResponse;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link CandleGroup} между слоями domain ↔ persistence ↔ api
 * (docs/models/domain/other/CandleGroup.md). В домене инструмент —
 * плоский {@code instrumentId}; в entity — связь {@code instrument}
 * (owning side агрегата), её проставляет {@code CandleGroupDataService}.
 * Наружу — internalId группы и instrumentInternalId, не id из БД.
 * Таймфрейм/статус enum ↔ строка конвертирует MapStruct.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandleGroupMapper {

    @Mapping(target = "instrumentId", source = "instrument.id")
    CandleGroup persistenceToDomain(CandleGroupEntity entity);

    @Mapping(target = "instrument", ignore = true)
    CandleGroupEntity domainToPersistence(CandleGroup group);

    @Mapping(target = "instrumentInternalId", source = "instrumentInternalId")
    CandleGroupApiResponse domainToApi(CandleGroup group, String instrumentInternalId);
}
