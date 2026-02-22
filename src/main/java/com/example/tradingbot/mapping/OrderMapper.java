package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.exchange.ExchangeOrder;
import com.example.tradingbot.rest.model.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "ordId", target = "externalId")
    @Mapping(source = "clOrdId", target = "internalId")
    @Mapping(source = "instId", target = "externalInstrumentId")
    @Mapping(source = "instType", target = "instrumentType")
    @Mapping(source = "posSide", target = "positionSide")
    @Mapping(source = "ordType", target = "type")
    @Mapping(source = "state", target = "status")
    @Mapping(source = "px", target = "price")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "avgPx", target = "averagePrice")
    @Mapping(source = "accFillSz", target = "accumulatedFillSize")
    @Mapping(source = "cTime", target = "createTime")
    @Mapping(source = "uTime", target = "updateTime")
    @Mapping(source = "sCode", target = "externalStatusCode")
    @Mapping(source = "sMsg", target = "externalStatusMessage")
    ExchangeOrder clientToDomain(com.example.tradingbot.client.model.okx.OrderResponse source);

    @Mapping(source = "externalId", target = "ordId")
    @Mapping(source = "internalId", target = "clOrdId")
    @Mapping(source = "externalInstrumentId", target = "instId")
    @Mapping(source = "instrumentType", target = "instType")
    @Mapping(source = "positionSide", target = "posSide")
    @Mapping(source = "type", target = "ordType")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "price", target = "px")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "averagePrice", target = "avgPx")
    @Mapping(source = "accumulatedFillSize", target = "accFillSz")
    @Mapping(source = "createTime", target = "cTime")
    @Mapping(source = "updateTime", target = "uTime")
    @Mapping(source = "externalStatusCode", target = "sCode")
    @Mapping(source = "externalStatusMessage", target = "sMsg")
    com.example.tradingbot.client.model.okx.OrderResponse domainToClient(ExchangeOrder source);

    OrderResponse domainToRest(OrderEntity source);
}
