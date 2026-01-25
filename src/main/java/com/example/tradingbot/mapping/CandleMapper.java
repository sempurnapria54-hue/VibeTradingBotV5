package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxCandle;
import com.example.tradingbot.domain.model.Candle;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CandleMapper {

    Candle clientToDomain(OkxCandle candle);

    List<Candle> clientToDomain(List<OkxCandle> candles);

    com.example.tradingbot.rest.model.Candle domainToRest(Candle candle);

    List<com.example.tradingbot.rest.model.Candle> domainToRest(List<Candle> candles);
}
