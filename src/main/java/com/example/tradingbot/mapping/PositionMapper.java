package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.request.PositionsRequest;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.domain.model.Position;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(source = "posId", target = "externalId")
    @Mapping(source = "posSide", target = "side")
    @Mapping(source = "pos", target = "size")
    @Mapping(source = "avgPx", target = "averagePrice")
    @Mapping(source = "markPx", target = "markPrice")
    @Mapping(source = "liqPx", target = "liquidationPrice")
    @Mapping(source = "upl", target = "unrealizedProfit")
    @Mapping(source = "lever", target = "leverage")
    @Mapping(source = "mgnMode", target = "marginMode")
    Position clientOkxResponseToDomain(PositionResponse source);

    @Mapping(source = "externalId", target = "posId")
    @Mapping(source = "side", target = "posSide")
    @Mapping(source = "size", target = "pos")
    @Mapping(source = "averagePrice", target = "avgPx")
    @Mapping(source = "markPrice", target = "markPx")
    @Mapping(source = "liquidationPrice", target = "liqPx")
    @Mapping(source = "unrealizedProfit", target = "upl")
    @Mapping(source = "leverage", target = "lever")
    @Mapping(source = "marginMode", target = "mgnMode")
    PositionResponse domainToClient(Position source);

    @Mapping(source = "marginMode", target = "marginMode")
    @Mapping(source = "side", target = "positionSide")
    ClosePositionRequest domainToClientOkxRequest(Position source);

    @Mapping(source = "externalId", target = "instrumentId")
    PositionsRequest domainToClientOkxPositionsRequest(Position source);

    List<Position> clientOkxResponseToDomain(List<PositionResponse> source);
}
