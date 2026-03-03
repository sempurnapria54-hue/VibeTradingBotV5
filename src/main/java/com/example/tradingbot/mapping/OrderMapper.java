package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.OrderDetailsRequest;
import com.example.tradingbot.client.model.okx.request.OrdersHistoryRequest;
import com.example.tradingbot.client.model.okx.request.OrdersPendingRequest;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.rest.model.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "ordId", target = "externalId")
    @Mapping(source = "clOrdId", target = "internalId")
    @Mapping(source = "ordType", target = "type")
    @Mapping(source = "state", target = "externalStatus")
    @Mapping(source = "px", target = "price")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "avgPx", target = "averagePrice")
    @Mapping(source = "accFillSz", target = "accumulatedFillSize")
    @Mapping(source = "fee", target = "fee")
    Order clientOkxResponseToDomain(com.example.tradingbot.client.model.okx.response.OrderResponse source);

    @Mapping(source = "externalId", target = "ordId")
    @Mapping(source = "internalId", target = "clOrdId")
    @Mapping(source = "type", target = "ordType")
    @Mapping(source = "externalStatus", target = "state")
    @Mapping(source = "price", target = "px")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "averagePrice", target = "avgPx")
    @Mapping(source = "accumulatedFillSize", target = "accFillSz")
    @Mapping(source = "fee", target = "fee")
    com.example.tradingbot.client.model.okx.response.OrderResponse domainToClient(Order source);

    @Mapping(source = "internalId", target = "clientOrderId")
    @Mapping(source = "side", target = "side")
    @Mapping(source = "type", target = "orderType")
    @Mapping(source = "size", target = "size")
    @Mapping(source = "price", target = "price")
    CreateOrderRequest domainToClientOkxRequest(Order source);

    @Mapping(source = "externalId", target = "orderId")
    @Mapping(source = "internalId", target = "clientOrderId")
    CancelOrderRequest domainToClientOkxCancelRequest(Order source);

    @Mapping(source = "externalId", target = "orderId")
    @Mapping(source = "internalId", target = "clientOrderId")
    @Mapping(source = "size", target = "newSize")
    @Mapping(source = "price", target = "newPrice")
    AmendOrderRequest domainToClientOkxAmendRequest(Order source);

    @Mapping(source = "externalId", target = "orderId")
    @Mapping(source = "internalId", target = "clientOrderId")
    OrderDetailsRequest domainToClientOkxOrderDetailsRequest(Order source);

    @Mapping(source = "externalStatus", target = "state")
    OrdersHistoryRequest domainToClientOkxOrdersHistoryRequest(Order source);

    OrdersPendingRequest domainToClientOkxOrdersPendingRequest(Order source);

    OrderResponse domainToRest(OrderEntity source);

    List<Order> clientOkxResponseToDomain(List<com.example.tradingbot.client.model.okx.response.OrderResponse> source);

    Order restRequestToDomain(com.example.tradingbot.rest.model.request.CreateOrderRequest source);

    @Mapping(target = "id", ignore = true)
    void domainToEntityOnCreate(Order source, @MappingTarget OrderEntity target);
}
