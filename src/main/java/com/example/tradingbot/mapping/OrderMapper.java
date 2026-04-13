package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.response.OrderResponse.AttachAlgoOrd;
import com.example.tradingbot.domain.model.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.OrderSearchParams;
import com.example.tradingbot.persistence.model.deal.order.OrderEntity;
import com.example.tradingbot.rest.model.response.order.OrderPageResponse;
import com.example.tradingbot.rest.model.response.order.OrderResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {


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

    GetOrdersHistorySearchParams domainSearchParamsToClientOkxOrdersHistoryRequest(OrderSearchParams source);

    OrderResponse domainToRest(Order source);

    OrderPageResponse domainToRest(Page<Order> source);

    List<Order> clientOkxResponseToDomain(List<com.example.tradingbot.client.model.okx.response.OrderResponse> source);


    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "clOrdId")
    @Mapping(target = "externalId", source = "ordId")
    @Mapping(target = "type", source = "ordType")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "price", source = "px", qualifiedByName = "toBigDecimal")
    @Mapping(target = "size", source = "sz", qualifiedByName = "toBigDecimal")
    @Mapping(target = "accumulatedFillSize", source = "accFillSz", qualifiedByName = "toBigDecimal")
    @Mapping(target = "averagePrice", source = "avgPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "fee", source = "fee", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalCreatedAt", source = "cTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "attachedAlgoOrders", source = "attachAlgoOrds")
    OrderExternalSnapshot clientOkxToExternalSnapshot(
            com.example.tradingbot.client.model.okx.response.OrderResponse source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<OrderExternalSnapshot> clientOkxToExternalSnapshot(
            List<com.example.tradingbot.client.model.okx.response.OrderResponse> data);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "attachAlgoClOrdId")
    @Mapping(target = "externalAttachedId", source = "attachAlgoId")
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "externalType", source = "tpOrdKind")
    @Mapping(target = "size", source = "sz")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx")
    AttachedAlgoOrderExternalSnapshot clientOkxToExternalSnapshot(AttachAlgoOrd source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "internalId")
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "externalStatus", source = "externalStatus")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "accumulatedFillSize", source = "accumulatedFillSize")
    @Mapping(target = "averagePrice", source = "averagePrice")
    @Mapping(target = "fee", source = "fee")
    @Mapping(target = "externalCreatedAt", source = "externalCreatedAt")
    @Mapping(target = "externalModifiedAt", source = "externalModifiedAt")
    void updateDomainFromExternalSnapshot(OrderExternalSnapshot source, @MappingTarget Order target);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "clOrdId")
    @Mapping(target = "externalId", source = "ordId")
    @Mapping(target = "type", source = "ordType")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "price", source = "px", qualifiedByName = "toBigDecimal")
    @Mapping(target = "size", source = "sz", qualifiedByName = "toBigDecimal")
    @Mapping(target = "accumulatedFillSize", source = "accFillSz", qualifiedByName = "toBigDecimal")
    @Mapping(target = "averagePrice", source = "avgPx", qualifiedByName = "toBigDecimal")
    @Mapping(target = "fee", source = "fee", qualifiedByName = "toBigDecimal")
    @Mapping(target = "externalCreatedAt", source = "cTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "attachedAlgoOrders", source = "attachAlgoOrds")
    Order clientOkxToDomain(com.example.tradingbot.client.model.okx.response.OrderResponse source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "type", constant = "ATTACHED_STOP_LOSS")
    @Mapping(target = "internalId", source = "attachAlgoClOrdId")
    @Mapping(target = "externalAttachedId", source = "attachAlgoId")
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "externalType", constant = "attachAlgoOrds")
    @Mapping(target = "size", source = "sz", qualifiedByName = "toBigDecimal")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx", qualifiedByName = "toBigDecimal")
    AttachedAlgoOrder clientOkxToDomain(AttachAlgoOrd source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<Order> clientOkxToDomain(List<com.example.tradingbot.client.model.okx.response.OrderResponse> data);

    Order restRequestToDomain(com.example.tradingbot.rest.model.request.order.CreateOrderRequest source);

    Order dataToDomain(OrderEntity data);

    Page<Order> dataToDomain(Page<OrderEntity> data);

    OrderEntity domainToData(Order data);

    OrderSearchParams restToDomainSearchParams(
            com.example.tradingbot.rest.model.request.order.search_params.OrderSearchParams request);

    void domainToDomainOnCreate(Order source, @MappingTarget Order target);

    void domainToDomainOnCancel(Order source, @MappingTarget Order target);

}
