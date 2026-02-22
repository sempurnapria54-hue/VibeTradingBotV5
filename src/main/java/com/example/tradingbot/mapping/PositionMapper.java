package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.PositionResponse;
import com.example.tradingbot.domain.model.exchange.ExchangePosition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(source = "instId", target = "externalInstrumentId")
    @Mapping(source = "instType", target = "instrumentType")
    @Mapping(source = "posSide", target = "positionSide")
    @Mapping(source = "pos", target = "positionSize")
    @Mapping(source = "avgPx", target = "averagePrice")
    @Mapping(source = "markPx", target = "markPrice")
    @Mapping(source = "liqPx", target = "liquidationPrice")
    @Mapping(source = "upl", target = "unrealizedProfit")
    @Mapping(source = "lever", target = "leverage")
    @Mapping(source = "mgnMode", target = "marginMode")
    @Mapping(source = "uTime", target = "updateTime")
    ExchangePosition clientToDomain(PositionResponse source);

    @Mapping(source = "externalInstrumentId", target = "instId")
    @Mapping(source = "instrumentType", target = "instType")
    @Mapping(source = "positionSide", target = "posSide")
    @Mapping(source = "positionSize", target = "pos")
    @Mapping(source = "averagePrice", target = "avgPx")
    @Mapping(source = "markPrice", target = "markPx")
    @Mapping(source = "liquidationPrice", target = "liqPx")
    @Mapping(source = "unrealizedProfit", target = "upl")
    @Mapping(source = "leverage", target = "lever")
    @Mapping(source = "marginMode", target = "mgnMode")
    @Mapping(source = "updateTime", target = "uTime")
    PositionResponse domainToClient(ExchangePosition source);
}
