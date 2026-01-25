package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxPriceTickerDto;
import com.example.tradingbot.domain.model.PriceTicker;
import com.example.tradingbot.rest.model.PriceTickerRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PriceTickerMapper {
    PriceTicker clientToDomain(OkxPriceTickerDto dto);

    PriceTickerRest domainToRest(PriceTicker domain);
}
