package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.response.CandleGroupApiResponse;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Маппинг {@link CandleGroup} между слоями domain ↔ persistence ↔ api
 * (docs/models/domain/other/CandleGroup.md). В домене инструмент —
 * плоский {@code instrumentId}; в entity — связь {@code instrument}
 * (owning side агрегата), её проставляет {@code CandleGroupDataService}.
 */
@Mapper(componentModel = "spring")
public interface CandleGroupMapper {

    @Mapping(target = "instrumentId", source = "instrument.id")
    CandleGroup entityToDomain(CandleGroupEntity entity);

    @Mapping(target = "instrument", ignore = true)
    CandleGroupEntity domainToEntity(CandleGroup group);

    CandleGroupApiResponse domainToApi(CandleGroup group);
}
