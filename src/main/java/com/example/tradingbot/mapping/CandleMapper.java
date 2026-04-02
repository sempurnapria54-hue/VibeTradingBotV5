package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.CandlesRequest;
import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.domain.model.Candle;
import com.example.tradingbot.domain.model.search_params.CandleSearchParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    @Mapping(source = "ts", target = "openTimestamp")
    Candle clientOkxResponseToDomain(CandleResponse source);

    @Mapping(source = "openTimestamp", target = "ts")
    CandleResponse domainToClient(Candle source);

    CandlesRequest domainSearchParamsToClientOkxRequest(CandleSearchParams source);

    List<Candle> clientOkxResponseToDomain(List<CandleResponse> source);
}
