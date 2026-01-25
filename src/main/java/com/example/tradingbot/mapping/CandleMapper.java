package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxCandleDto;
import com.example.tradingbot.domain.model.Candle;
import com.example.tradingbot.rest.model.CandleRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CandleMapper {
    Candle clientToDomain(OkxCandleDto dto);

    CandleRest domainToRest(Candle domain);
}
