package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxClosePositionResultDto;
import com.example.tradingbot.domain.model.ClosePositionResult;
import com.example.tradingbot.rest.model.ClosePositionResultRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClosePositionResultMapper {
    ClosePositionResult clientToDomain(OkxClosePositionResultDto dto);

    ClosePositionResultRest domainToRest(ClosePositionResult domain);
}
