package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.OrdersAlgoPendingRequest;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.rest.model.response.AlgoOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlgoOrderMapper {

    @Mapping(source = "algoId", target = "externalId")
    @Mapping(source = "clOrdId", target = "internalId")
    @Mapping(source = "state", target = "externalStatus")
    @Mapping(source = "ordType", target = "externalType")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "tpTriggerPx", target = "takeProfitTriggerPrice")
    @Mapping(source = "slTriggerPx", target = "stopLossTriggerPrice")
    @Mapping(source = "callbackRatio", target = "trailingFallenPercents")
    @Mapping(source = "callbackSpread", target = "trailingFallenAbsoluteValue")
    AlgoOrder clientOkxResponseToDomain(com.example.tradingbot.client.model.okx.response.AlgoOrderResponse source);

    @Mapping(source = "externalId", target = "algoId")
    @Mapping(source = "internalId", target = "clOrdId")
    @Mapping(source = "externalStatus", target = "state")
    @Mapping(source = "externalType", target = "ordType")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "takeProfitTriggerPrice", target = "tpTriggerPx")
    @Mapping(source = "stopLossTriggerPrice", target = "slTriggerPx")
    @Mapping(source = "trailingFallenPercents", target = "callbackRatio")
    @Mapping(source = "trailingFallenAbsoluteValue", target = "callbackSpread")
    com.example.tradingbot.client.model.okx.response.AlgoOrderResponse domainToClient(AlgoOrder source);

    @Mapping(source = "internalId", target = "clientOrderId")
    @Mapping(source = "externalType", target = "orderType")
    @Mapping(source = "size", target = "size")
    @Mapping(source = "takeProfitTriggerPrice", target = "triggerPrice")
    @Mapping(source = "stopLossTriggerPrice", target = "orderPrice")
    CreateAlgoOrderRequest domainToClientOkxRequest(AlgoOrder source);

    @Mapping(source = "externalId", target = "algoOrderId")
    @Mapping(source = "internalId", target = "clientOrderId")
    CancelAlgoOrderRequest domainToClientOkxCancelRequest(AlgoOrder source);

    @Mapping(source = "externalType", target = "orderType")
    OrdersAlgoPendingRequest domainToClientOkxOrdersAlgoPendingRequest(AlgoOrder source);

    AlgoOrderResponse domainToRest(AlgoOrderEntity source);

    @Mapping(source = "pos", target = "size")
    AlgoOrder closePositionToDomain(PositionResponse source);

    List<AlgoOrder> clientOkxResponseToDomain(List<com.example.tradingbot.client.model.okx.response.AlgoOrderResponse> source);
}
