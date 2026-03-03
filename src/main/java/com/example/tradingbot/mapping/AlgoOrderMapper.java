package com.example.tradingbot.mapping;

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
    AlgoOrder clientToDomain(com.example.tradingbot.client.model.okx.response.AlgoOrderResponse source);

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

    AlgoOrderResponse domainToRest(AlgoOrderEntity source);

    @Mapping(source = "pos", target = "size")
    AlgoOrder closePositionToDomain(PositionResponse source);

    List<AlgoOrder> clientToDomain(List<com.example.tradingbot.client.model.okx.response.AlgoOrderResponse> source);
}
