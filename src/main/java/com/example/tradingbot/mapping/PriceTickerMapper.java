package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPriceTicker;
import com.example.tradingbot.domain.model.PriceTicker;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PriceTickerMapper {

    PriceTicker clientToDomain(OkxPriceTicker ticker);

    List<PriceTicker> clientToDomain(List<OkxPriceTicker> tickers);

    com.example.tradingbot.rest.model.PriceTicker domainToRest(PriceTicker ticker);

    List<com.example.tradingbot.rest.model.PriceTicker> domainToRest(List<PriceTicker> tickers);
}
