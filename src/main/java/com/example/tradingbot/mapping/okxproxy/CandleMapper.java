package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.CandleDto;
import com.example.tradingbot.domain.model.okxproxy.Candle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "confirm", target = "confirmed")
    Candle clientToDomain(CandleDto source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "confirmed", target = "confirm")
    CandleDto domainToClient(Candle source);

    com.example.tradingbot.rest.model.okxproxy.Candle domainToRest(Candle source);

    Candle restToDomain(com.example.tradingbot.rest.model.okxproxy.Candle source);
}
