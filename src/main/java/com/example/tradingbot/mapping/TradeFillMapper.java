package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.FillsRequest;
import com.example.tradingbot.client.model.okx.response.TradeFillResponse;
import com.example.tradingbot.domain.model.market.TradeFill;
import com.example.tradingbot.domain.model.search_params.TradeFillsSearchParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TradeFillMapper {

    @Mapping(source = "billId", target = "externalBillId")
    @Mapping(source = "tradeId", target = "externalTradeId")
    @Mapping(source = "ordId", target = "externalOrderId")
    @Mapping(source = "instId", target = "externalInstrumentId")
    @Mapping(source = "side", target = "side")
    @Mapping(source = "fillSz", target = "fillSize")
    @Mapping(source = "fillPx", target = "fillPrice")
    @Mapping(source = "fillPnl", target = "fillPnl")
    @Mapping(source = "ts", target = "timestamp")
    TradeFill clientOkxResponseToDomain(TradeFillResponse source);

    @Mapping(source = "externalBillId", target = "billId")
    @Mapping(source = "externalTradeId", target = "tradeId")
    @Mapping(source = "externalOrderId", target = "ordId")
    @Mapping(source = "externalInstrumentId", target = "instId")
    @Mapping(source = "side", target = "side")
    @Mapping(source = "fillSize", target = "fillSz")
    @Mapping(source = "fillPrice", target = "fillPx")
    @Mapping(source = "fillPnl", target = "fillPnl")
    @Mapping(source = "timestamp", target = "ts")
    TradeFillResponse domainToClient(TradeFill source);

    FillsRequest domainSearchParamsToClientOkxRequest(TradeFillsSearchParams source);

    List<TradeFill> clientOkxResponseToDomain(List<TradeFillResponse> source);
}
