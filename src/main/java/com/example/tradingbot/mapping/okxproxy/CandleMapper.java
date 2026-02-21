package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.model.okx.CandleResponse;
import com.example.tradingbot.domain.model.exchange.ExchangeCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "confirm", target = "confirmed")
    ExchangeCandle clientToDomain(CandleResponse source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "confirmed", target = "confirm")
    CandleResponse domainToClient(ExchangeCandle source);


}
