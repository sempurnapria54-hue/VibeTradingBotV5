package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxTradeFillDto;
import com.example.tradingbot.domain.model.TradeFill;
import com.example.tradingbot.rest.model.TradeFillRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradeFillMapper {
    TradeFill clientToDomain(OkxTradeFillDto dto);

    TradeFillRest domainToRest(TradeFill domain);
}
