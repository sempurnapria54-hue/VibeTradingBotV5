package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPositionCloseOrderAlgoDto;
import com.example.tradingbot.domain.model.PositionCloseOrderAlgo;
import com.example.tradingbot.rest.model.PositionCloseOrderAlgoRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PositionCloseOrderAlgoMapper {
    PositionCloseOrderAlgo clientToDomain(OkxPositionCloseOrderAlgoDto dto);

    PositionCloseOrderAlgoRest domainToRest(PositionCloseOrderAlgo domain);
}
