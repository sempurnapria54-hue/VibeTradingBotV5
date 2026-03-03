package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.response.TickerRequest;
import com.example.tradingbot.domain.model.PriceTicker;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriceTickerMapper {

    @Mapping(source = "instId", target = "externalInstrumentId")
    @Mapping(source = "last", target = "lastPrice")
    @Mapping(source = "markPx", target = "markPrice")
    @Mapping(source = "idxPx", target = "indexPrice")
    @Mapping(source = "askPx", target = "askPrice")
    @Mapping(source = "bidPx", target = "bidPrice")
    @Mapping(source = "ts", target = "timestamp")
    PriceTicker clientOkxResponseToDomain(PriceTickerResponse source);

    @Mapping(source = "externalInstrumentId", target = "instId")
    @Mapping(source = "lastPrice", target = "last")
    @Mapping(source = "markPrice", target = "markPx")
    @Mapping(source = "indexPrice", target = "idxPx")
    @Mapping(source = "askPrice", target = "askPx")
    @Mapping(source = "bidPrice", target = "bidPx")
    @Mapping(source = "timestamp", target = "ts")
    PriceTickerResponse domainToClient(PriceTicker source);

    @Mapping(source = "externalInstrumentId", target = "instrumentId")
    TickerRequest domainToClientOkxRequest(PriceTicker source);

    List<PriceTicker> clientOkxResponseToDomain(List<PriceTickerResponse> source);
}
