package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxAlgoOrderResultDto;
import com.example.tradingbot.domain.model.AlgoOrderResult;
import com.example.tradingbot.rest.model.AlgoOrderResultRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlgoOrderResultMapper {
    AlgoOrderResult clientToDomain(OkxAlgoOrderResultDto dto);

    AlgoOrderResultRest domainToRest(AlgoOrderResult domain);
}
