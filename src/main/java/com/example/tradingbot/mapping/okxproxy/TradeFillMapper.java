package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.model.okx.TradeFillResponse;
import com.example.tradingbot.domain.model.exchange.ExchangeTradeFill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TradeFillMapper {

    @Mapping(source = "ordId", target = "orderId")
    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "fillSz", target = "fillSize")
    @Mapping(source = "fillPx", target = "fillPrice")
    @Mapping(source = "ts", target = "timestamp")
    ExchangeTradeFill clientToDomain(TradeFillResponse source);

    @Mapping(source = "orderId", target = "ordId")
    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "fillSize", target = "fillSz")
    @Mapping(source = "fillPrice", target = "fillPx")
    @Mapping(source = "timestamp", target = "ts")
    TradeFillResponse domainToClient(ExchangeTradeFill source);


}
