package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.rest.model.response.AlgoOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AlgoOrderMapper {

    @Mapping(source = "algoId", target = "externalId")
    @Mapping(source = "clOrdId", target = "internalOrderId")
    @Mapping(source = "instId", target = "externalInstrumentId")
    @Mapping(source = "ordType", target = "type")
    @Mapping(source = "state", target = "status")
    @Mapping(source = "sz", target = "size")
    @Mapping(source = "triggerPx", target = "triggerPrice")
    @Mapping(source = "ordPx", target = "orderPrice")
    @Mapping(source = "tpTriggerPx", target = "takeProfitTriggerPrice")
    @Mapping(source = "tpOrdPx", target = "takeProfitOrderPrice")
    @Mapping(source = "slTriggerPx", target = "stopLossTriggerPrice")
    @Mapping(source = "slOrdPx", target = "stopLossOrderPrice")
    @Mapping(source = "callbackSpread", target = "callbackStep")
    @Mapping(source = "cTime", target = "createTime")
    @Mapping(source = "uTime", target = "updateTime")
    @Mapping(source = "sCode", target = "externalStatusCode")
    @Mapping(source = "sMsg", target = "externalStatusMessage")
    AlgoOrder clientToDomain(com.example.tradingbot.client.model.okx.response.AlgoOrderResponse source);

    @Mapping(source = "externalId", target = "algoId")
    @Mapping(source = "internalOrderId", target = "clOrdId")
    @Mapping(source = "externalInstrumentId", target = "instId")
    @Mapping(source = "type", target = "ordType")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "size", target = "sz")
    @Mapping(source = "triggerPrice", target = "triggerPx")
    @Mapping(source = "orderPrice", target = "ordPx")
    @Mapping(source = "takeProfitTriggerPrice", target = "tpTriggerPx")
    @Mapping(source = "takeProfitOrderPrice", target = "tpOrdPx")
    @Mapping(source = "stopLossTriggerPrice", target = "slTriggerPx")
    @Mapping(source = "stopLossOrderPrice", target = "slOrdPx")
    @Mapping(source = "callbackStep", target = "callbackSpread")
    @Mapping(source = "createTime", target = "cTime")
    @Mapping(source = "updateTime", target = "uTime")
    @Mapping(source = "externalStatusCode", target = "sCode")
    @Mapping(source = "externalStatusMessage", target = "sMsg")
    com.example.tradingbot.client.model.okx.response.AlgoOrderResponse domainToClient(AlgoOrder source);

    AlgoOrderResponse domainToRest(AlgoOrderEntity source);
}
