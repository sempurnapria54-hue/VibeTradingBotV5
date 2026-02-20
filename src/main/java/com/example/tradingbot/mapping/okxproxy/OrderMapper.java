package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.OrderDto;
import com.example.tradingbot.domain.model.okxproxy.Order;
import com.example.tradingbot.domain.model.trading.CreateOrderRequest;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "ordId", target = "orderId")
    @Mapping(source = "clOrdId", target = "clientOrderId")
    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "instType", target = "instrumentType")
    @Mapping(source = "posSide", target = "positionSide")
    @Mapping(source = "ordType", target = "orderType")
    @Mapping(source = "px", target = "price")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "avgPx", target = "averagePrice")
    @Mapping(source = "accFillSz", target = "accumulatedFillSize")
    @Mapping(source = "fee", target = "fee")
    @Mapping(source = "cTime", target = "createTime")
    @Mapping(source = "uTime", target = "updateTime")
    @Mapping(source = "sCode", target = "statusCode")
    @Mapping(source = "sMsg", target = "statusMessage")
    Order clientToDomain(OrderDto source);

    @Mapping(source = "orderId", target = "ordId")
    @Mapping(source = "clientOrderId", target = "clOrdId")
    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "instrumentType", target = "instType")
    @Mapping(source = "positionSide", target = "posSide")
    @Mapping(source = "orderType", target = "ordType")
    @Mapping(source = "price", target = "px")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "averagePrice", target = "avgPx")
    @Mapping(source = "accumulatedFillSize", target = "accFillSz")
    @Mapping(source = "fee", target = "fee")
    @Mapping(source = "createTime", target = "cTime")
    @Mapping(source = "updateTime", target = "uTime")
    @Mapping(source = "statusCode", target = "sCode")
    @Mapping(source = "statusMessage", target = "sMsg")
    OrderDto domainToClient(Order source);

    com.example.tradingbot.rest.model.okxproxy.Order domainToRest(Order source);

    Order restToDomain(com.example.tradingbot.rest.model.okxproxy.Order source);

    CreateOrderRequest restToDomain(com.example.tradingbot.rest.model.request.order.CreateOrderRequest source);

    com.example.tradingbot.rest.model.response.Order domainToRest(OrderEntity source);
}
