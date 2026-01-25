package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPositionDto;
import com.example.tradingbot.domain.model.Position;
import com.example.tradingbot.rest.model.PositionRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = PositionCloseOrderAlgoMapper.class)
public interface PositionMapper {
    Position clientToDomain(OkxPositionDto dto);

    PositionRest domainToRest(Position domain);
}
