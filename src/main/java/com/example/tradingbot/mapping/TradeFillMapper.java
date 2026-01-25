package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxTradeFill;
import com.example.tradingbot.domain.model.TradeFill;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradeFillMapper {

    TradeFill clientToDomain(OkxTradeFill tradeFill);

    List<TradeFill> clientToDomain(List<OkxTradeFill> tradeFills);

    com.example.tradingbot.rest.model.TradeFill domainToRest(TradeFill tradeFill);

    List<com.example.tradingbot.rest.model.TradeFill> domainToRest(List<TradeFill> tradeFills);
}
