package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.TradeFillResponse;
import com.example.tradingbot.domain.model.okxproxy.TradeFill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TradeFillMapper {

    @Mapping(source = "ordId", target = "orderId")
    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "fillSz", target = "fillSize")
    @Mapping(source = "fillPx", target = "fillPrice")
    @Mapping(source = "ts", target = "timestamp")
    TradeFill clientToDomain(TradeFillResponse source);

    @Mapping(source = "orderId", target = "ordId")
    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "fillSize", target = "fillSz")
    @Mapping(source = "fillPrice", target = "fillPx")
    @Mapping(source = "timestamp", target = "ts")
    TradeFillResponse domainToClient(TradeFill source);

    com.example.tradingbot.rest.model.okxproxy.TradeFill domainToRest(TradeFill source);

    TradeFill restToDomain(com.example.tradingbot.rest.model.okxproxy.TradeFill source);
}
