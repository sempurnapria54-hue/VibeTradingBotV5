package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxClosePositionResult;
import com.example.tradingbot.domain.model.ClosePositionResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClosePositionResultMapper {

    ClosePositionResult clientToDomain(OkxClosePositionResult result);

    List<ClosePositionResult> clientToDomain(List<OkxClosePositionResult> results);

    com.example.tradingbot.rest.model.ClosePositionResult domainToRest(ClosePositionResult result);

    List<com.example.tradingbot.rest.model.ClosePositionResult> domainToRest(List<ClosePositionResult> results);
}
