package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.domain.model.Candle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "confirm", target = "confirmed")
    Candle clientToDomain(CandleResponse source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "confirmed", target = "confirm")
    CandleResponse domainToClient(Candle source);


}
