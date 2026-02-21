package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.CandleGroupEntity;
import com.example.tradingbot.rest.model.request.candlegroup.CreateCandleGroupRequest;
import com.example.tradingbot.rest.model.response.candlegroup.CandleGroupResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandleGroupMapper {

    List<CandleGroupResponse> domainToRest(List<CandleGroupEntity> source);

    @Mapping(source = "instrument.internalId", target = "instrumentInternalId")
    CandleGroupResponse domainToRest(CandleGroupEntity source);

    CandleGroupEntity restToDomain(CreateCandleGroupRequest source);
}
