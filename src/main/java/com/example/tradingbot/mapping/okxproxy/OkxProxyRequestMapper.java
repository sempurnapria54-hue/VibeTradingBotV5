package com.example.tradingbot.mapping.okxproxy;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OkxProxyRequestMapper {

    com.example.tradingbot.domain.model.okxproxy.PositionsRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.PositionsRequest source);

    com.example.tradingbot.client.okx.dto.PositionsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.PositionsRequest source);

    com.example.tradingbot.domain.model.okxproxy.OrdersPendingRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.OrdersPendingRequest source);

    com.example.tradingbot.client.okx.dto.OrdersPendingRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersPendingRequest source);


    com.example.tradingbot.domain.model.okxproxy.OrdersAlgoPendingRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.OrdersAlgoPendingRequest source);

    com.example.tradingbot.client.okx.dto.OrdersAlgoPendingRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersAlgoPendingRequest source);

    com.example.tradingbot.domain.model.okxproxy.OrderDetailsRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.OrderDetailsRequest source);

    com.example.tradingbot.client.okx.dto.OrderDetailsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrderDetailsRequest source);

    com.example.tradingbot.domain.model.okxproxy.OrdersHistoryRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.OrdersHistoryRequest source);

    com.example.tradingbot.client.okx.dto.OrdersHistoryRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersHistoryRequest source);

    com.example.tradingbot.domain.model.okxproxy.FillsRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.FillsRequest source);

    com.example.tradingbot.client.okx.dto.FillsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsRequest source);

    com.example.tradingbot.domain.model.okxproxy.FillsArchiveRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.FillsArchiveRequest source);

    com.example.tradingbot.client.okx.dto.FillsArchiveRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsArchiveRequest source);

    com.example.tradingbot.domain.model.okxproxy.FillsArchiveLinkRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.FillsArchiveLinkRequest source);

    com.example.tradingbot.client.okx.dto.FillsArchiveLinkRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsArchiveLinkRequest source);

    com.example.tradingbot.domain.model.okxproxy.CandlesRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.CandlesRequest source);

    com.example.tradingbot.client.okx.dto.CandlesRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CandlesRequest source);

    com.example.tradingbot.domain.model.okxproxy.CreateOrderRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.CreateOrderRequest source);

    com.example.tradingbot.client.okx.dto.CreateOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CreateOrderRequest source);

    com.example.tradingbot.domain.model.okxproxy.AmendOrderRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.AmendOrderRequest source);

    com.example.tradingbot.client.okx.dto.AmendOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.AmendOrderRequest source);

    com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.CancelOrderRequest source);

    com.example.tradingbot.client.okx.dto.CancelOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest source);

    com.example.tradingbot.domain.model.okxproxy.CreateAlgoOrderRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.CreateAlgoOrderRequest source);

    com.example.tradingbot.client.okx.dto.CreateAlgoOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CreateAlgoOrderRequest source);

    com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.CancelAlgoOrderRequest source);

    com.example.tradingbot.client.okx.dto.CancelAlgoOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest source);

    com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.ClosePositionRequest source);

    com.example.tradingbot.client.okx.dto.ClosePositionRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest source);

    com.example.tradingbot.domain.model.okxproxy.InstrumentsRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.InstrumentsRequest source);

    com.example.tradingbot.client.okx.dto.InstrumentsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.InstrumentsRequest source);

    com.example.tradingbot.domain.model.okxproxy.TickerRequest restToDomain(com.example.tradingbot.rest.model.okxproxy.TickerRequest source);

    com.example.tradingbot.client.okx.dto.TickerRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.TickerRequest source);
}
