package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.rest.model.request.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.CandleGroupResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CandleGroupMapper {

    List<CandleGroupResponse> domainToRest(List<CandleGroupEntity> source);

    CandleGroupResponse domainToRest(CandleGroupEntity source);

    CandleGroupEntity restToDomain(CreateCandleGroupRequest source);
}
