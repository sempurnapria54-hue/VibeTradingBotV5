package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.PriceTickerDto;
import com.example.tradingbot.domain.model.okxproxy.PriceTicker;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PriceTickerMapper {

    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "last", target = "lastPrice")
    @Mapping(source = "askPx", target = "askPrice")
    @Mapping(source = "bidPx", target = "bidPrice")
    @Mapping(source = "ts", target = "timestamp")
    PriceTicker clientToDomain(PriceTickerDto source);

    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "lastPrice", target = "last")
    @Mapping(source = "askPrice", target = "askPx")
    @Mapping(source = "bidPrice", target = "bidPx")
    @Mapping(source = "timestamp", target = "ts")
    PriceTickerDto domainToClient(PriceTicker source);

    com.example.tradingbot.rest.model.okxproxy.PriceTicker domainToRest(PriceTicker source);

    PriceTicker restToDomain(com.example.tradingbot.rest.model.okxproxy.PriceTicker source);
}
