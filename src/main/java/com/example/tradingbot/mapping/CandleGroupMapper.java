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

    /**
     * REST
     */

    @Mapping(target = "candleGroup", source = ".")
    CandleGroupResponse domainToRest(CandleGroup source);

    com.example.tradingbot.rest.model.response.candle_group.CandleGroup domainToRestModel(CandleGroup source);

    List<com.example.tradingbot.rest.model.response.candle_group.CandleGroup> domainListToRestModelList(
            List<CandleGroup> source);

    default CandleGroupContainerResponse domainListToRestContainer(List<CandleGroup> source) {
        CandleGroupContainerResponse response = new CandleGroupContainerResponse();
        response.setCandleGroups(domainListToRestModelList(source));
        return response;
    }

    @Mapping(target = "externalTimeframe", source = "timeframe")
    CandleGroup restToDomain(CreateCandleGroupRequest request);

    @Mapping(target = "timeframe", source = ".")
    @Mapping(target = "externalTimeframe", source = ".")
    CandleGroup restToDomain(String value);


    /**
     * DOMAIN_COPY
     */

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(CandleGroup source, @MappingTarget CandleGroup target);


    /**
     * DATA
     */

    CandleGroupEntity domainToData(CandleGroup candleGroup);

    CandleGroup dataToDomain(CandleGroupEntity candleGroup);
}
