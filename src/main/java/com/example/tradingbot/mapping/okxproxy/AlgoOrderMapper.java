package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.AlgoOrderDto;
import com.example.tradingbot.domain.model.okxproxy.AlgoOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AlgoOrderMapper {

    @Mapping(source = "algoId", target = "algoOrderId")
    @Mapping(source = "clOrdId", target = "clientOrderId")
    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "ordType", target = "orderType")
    @Mapping(source = "state", target = "state")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "triggerPx", target = "triggerPrice")
    @Mapping(source = "ordPx", target = "orderPrice")
    @Mapping(source = "tpTriggerPx", target = "takeProfitTriggerPrice")
    @Mapping(source = "tpOrdPx", target = "takeProfitOrderPrice")
    @Mapping(source = "slTriggerPx", target = "stopLossTriggerPrice")
    @Mapping(source = "slOrdPx", target = "stopLossOrderPrice")
    @Mapping(source = "callbackRatio", target = "callbackRatio")
    @Mapping(source = "callbackSpread", target = "callbackSpread")
    @Mapping(source = "cTime", target = "createTime")
    @Mapping(source = "uTime", target = "updateTime")
    @Mapping(source = "sCode", target = "statusCode")
    @Mapping(source = "sMsg", target = "statusMessage")
    AlgoOrder clientToDomain(AlgoOrderDto source);

    @Mapping(source = "algoOrderId", target = "algoId")
    @Mapping(source = "clientOrderId", target = "clOrdId")
    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "orderType", target = "ordType")
    @Mapping(source = "state", target = "state")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "triggerPrice", target = "triggerPx")
    @Mapping(source = "orderPrice", target = "ordPx")
    @Mapping(source = "takeProfitTriggerPrice", target = "tpTriggerPx")
    @Mapping(source = "takeProfitOrderPrice", target = "tpOrdPx")
    @Mapping(source = "stopLossTriggerPrice", target = "slTriggerPx")
    @Mapping(source = "stopLossOrderPrice", target = "slOrdPx")
    @Mapping(source = "callbackRatio", target = "callbackRatio")
    @Mapping(source = "callbackSpread", target = "callbackSpread")
    @Mapping(source = "createTime", target = "cTime")
    @Mapping(source = "updateTime", target = "uTime")
    @Mapping(source = "statusCode", target = "sCode")
    @Mapping(source = "statusMessage", target = "sMsg")
    AlgoOrderDto domainToClient(AlgoOrder source);

    com.example.tradingbot.rest.model.okxproxy.AlgoOrder domainToRest(AlgoOrder source);

    AlgoOrder restToDomain(com.example.tradingbot.rest.model.okxproxy.AlgoOrder source);
}
