package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.response.OrderResponse.AttachAlgoOrd;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.core.order.external_snapshot.OrderExternalSnapshot;
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
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring", uses = AttachedAlgoOrderMapper.class)
public interface OrderMapper extends CommonMapper {

    /**
     * REST
     */

    @Mapping(target = "order", source = ".")
    OrderResponse domainToRest(Order source);

    default OrderPageResponse domainToRest(Page<Order> source) {
        if (Objects.isNull(source)) {
            return new OrderPageResponse(Page.empty());
        }

        return new OrderPageResponse(source.map(this::domainToRestModel));
    }

    @Mapping(target = "attachedStopLosses", source = "attachedAlgoOrders")
    com.example.tradingbot.rest.model.response.order.Order domainToRestModel(Order source);

    com.example.tradingbot.rest.model.response.order.AttachedStopLoss domainToRestModel(AttachedAlgoOrder source);

    @Mapping(target = "attachedAlgoOrders", source = "attachedAlgoOrderRequests")
    Order restToDomain(com.example.tradingbot.rest.model.request.order.CreateOrderRequest source);

    @Mapping(target = "stopLossTriggerPrice", source = "triggerPrice")
    AttachedAlgoOrder restToDomain(com.example.tradingbot.rest.model.request.order.CreateAttachedAlgoOrderRequest source);

    @Mapping(target = "externalAfter", source = "after")
    @Mapping(target = "externalBefore", source = "before")
    OrderSearchParams restToDomainSearchParams(
            com.example.tradingbot.rest.model.request.order.search_params.OrderSearchParams request);


    /**
     * CLIENT
     */

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

    @Mapping(target = "instrumentExternalType", source = "externalInstrumentType")
    @Mapping(target = "instrumentExternalId", source = "externalInstrumentId")
    @Mapping(target = "externalType", source = "type")
    @Mapping(target = "externalStatus", source = "externalStatus")
    @Mapping(target = "afterOrderExternalId", source = "externalAfter")
    @Mapping(target = "beforeOrderExternalId", source = "externalBefore")
    @Mapping(target = "limit", source = "externalLimit")
    GetOrdersHistorySearchParams domainSearchParamsToClientOkxOrdersHistoryRequest(OrderSearchParams source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "clOrdId")
    @Mapping(target = "externalId", source = "ordId")
    @Mapping(target = "type", source = "ordType")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "price", source = "px", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "size", source = "sz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "accumulatedFillSize", source = "accFillSz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "averagePrice", source = "avgPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "fee", source = "fee", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalCreatedAt", source = "cTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "attachedAlgoOrders", source = "attachAlgoOrds")
    @Mapping(target = "attachedAlgoInternalId", source = "attachAlgoClOrdId")
    @Mapping(target = "takeProfitTriggerPrice", source = "tpTriggerPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx", qualifiedByName = "stringToBigDecimal")
    OrderExternalSnapshot clientToExternalSnapshot(com.example.tradingbot.client.model.okx.response.OrderResponse source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<OrderExternalSnapshot> clientToExternalSnapshot(
            List<com.example.tradingbot.client.model.okx.response.OrderResponse> source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "attachAlgoClOrdId")
    @Mapping(target = "externalAttachedId", source = "attachAlgoId")
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "externalType", source = "tpOrdKind")
    @Mapping(target = "size", source = "sz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "failCode", source = "failCode")
    @Mapping(target = "failReason", source = "failReason")
    AttachedAlgoOrderExternalSnapshot clientToExternalSnapshot(AttachAlgoOrd source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "clOrdId")
    @Mapping(target = "externalId", source = "ordId")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "price", source = "px", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "size", source = "sz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "accumulatedFillSize", source = "accFillSz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "averagePrice", source = "avgPx", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "fee", source = "fee", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "externalCreatedAt", source = "cTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "externalModifiedAt", source = "uTime", qualifiedByName = "toOffsetDateTimeUtc")
    @Mapping(target = "attachedAlgoOrders", source = "attachAlgoOrds")
    Order clientToDomain(com.example.tradingbot.client.model.okx.response.OrderResponse source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "type", constant = "ATTACHED_STOP_LOSS")
    @Mapping(target = "internalId", source = "attachAlgoClOrdId")
    @Mapping(target = "externalAttachedId", source = "attachAlgoId")
    @Mapping(target = "externalId", source = "algoId")
    @Mapping(target = "externalType", constant = "attachAlgoOrds")
    @Mapping(target = "size", source = "sz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "stopLossTriggerPrice", source = "slTriggerPx", qualifiedByName = "stringToBigDecimal")
    AttachedAlgoOrder clientToDomain(AttachAlgoOrd source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<Order> clientToDomain(List<com.example.tradingbot.client.model.okx.response.OrderResponse> source);


    /**
     * DATA
     */

    Order dataToDomain(OrderEntity source);

    default Page<Order> dataToDomain(Page<OrderEntity> source) {
        if (Objects.isNull(source)) {
            return Page.empty();
        }

        return source.map(this::dataToDomain);
    }

    OrderEntity domainToData(Order source);


    /**
     * DOMAIN_COPY
     */

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "externalStatus", ignore = true)
    @Mapping(target = "accumulatedFillSize", ignore = true)
    @Mapping(target = "averagePrice", ignore = true)
    @Mapping(target = "fee", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "externalCreatedAt", ignore = true)
    @Mapping(target = "externalModifiedAt", ignore = true)
    void domainToDomainOnCreate(Order source, @MappingTarget Order target);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void domainToDomainOnCancel(Order source, @MappingTarget Order target);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
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
    @Mapping(target = "strategyActionId", ignore = true)
    void updateDomainFromExternalSnapshot(OrderExternalSnapshot source, @MappingTarget Order target);
}
