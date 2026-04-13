package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.candle.CandleGroup;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import com.example.tradingbot.rest.model.request.candle_group.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.candle_group.CandleGroupContainerResponse;
import com.example.tradingbot.rest.model.response.candle_group.CandleGroupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CandleGroupMapper {

    CandleGroupContainerResponse domainListToRestContainer(List<CandleGroup> source);

    CandleGroupResponse domainToRest(CandleGroup source);

    CandleGroup restToDomain(CreateCandleGroupRequest request);

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(CandleGroup source, @MappingTarget CandleGroup target);

    CandleGroupEntity domainToData(CandleGroup candleGroup);

    CandleGroup dataToDomain(CandleGroupEntity candleGroup);

    @Mapping(target = "timeframe", source = ".")
    CandleGroup restToDomain(String value);
}
