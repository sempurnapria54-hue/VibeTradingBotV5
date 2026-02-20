package com.example.tradingbot.mapping.okxproxy;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OkxProxyRequestMapper {


    com.example.tradingbot.client.model.okx.PositionsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.PositionsRequest source);


    com.example.tradingbot.client.model.okx.OrdersPendingRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersPendingRequest source);



    com.example.tradingbot.client.model.okx.OrdersAlgoPendingRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersAlgoPendingRequest source);


    com.example.tradingbot.client.model.okx.OrderDetailsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrderDetailsRequest source);


    com.example.tradingbot.client.model.okx.OrdersHistoryRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.OrdersHistoryRequest source);


    com.example.tradingbot.client.model.okx.FillsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsRequest source);


    com.example.tradingbot.client.model.okx.FillsArchiveRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsArchiveRequest source);


    com.example.tradingbot.client.model.okx.FillsArchiveLinkRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.FillsArchiveLinkRequest source);


    com.example.tradingbot.client.model.okx.CandlesRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CandlesRequest source);


    com.example.tradingbot.client.model.okx.CreateOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CreateOrderRequest source);


    com.example.tradingbot.client.model.okx.AmendOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.AmendOrderRequest source);


    com.example.tradingbot.client.model.okx.CancelOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CancelOrderRequest source);


    com.example.tradingbot.client.model.okx.CreateAlgoOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CreateAlgoOrderRequest source);


    com.example.tradingbot.client.model.okx.CancelAlgoOrderRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.CancelAlgoOrderRequest source);


    com.example.tradingbot.client.model.okx.ClosePositionRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.ClosePositionRequest source);


    com.example.tradingbot.client.model.okx.InstrumentsRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.InstrumentsRequest source);


    com.example.tradingbot.client.model.okx.TickerRequest domainToClient(com.example.tradingbot.domain.model.okxproxy.TickerRequest source);
}
