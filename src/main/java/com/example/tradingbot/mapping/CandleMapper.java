package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.domain.model.Candle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    @Mapping(source = "ts", target = "openTimestamp")
    Candle clientOkxResponseToDomain(CandleResponse source);

    @Mapping(source = "openTimestamp", target = "ts")
    CandleResponse domainToClient(Candle source);

    List<Candle> clientOkxResponseToDomain(List<CandleResponse> source);
}
