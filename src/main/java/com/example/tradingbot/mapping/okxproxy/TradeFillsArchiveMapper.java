package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.model.okx.TradeFillsArchiveResponse;
import com.example.tradingbot.domain.model.exchange.ExchangeTradeFillsArchive;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "code", target = "externalStatusCode")
    @Mapping(source = "msg", target = "externalStatusMessage")
    ExchangeTradeFillsArchive clientToDomain(TradeFillsArchiveResponse source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "externalStatusCode", target = "code")
    @Mapping(source = "externalStatusMessage", target = "msg")
    TradeFillsArchiveResponse domainToClient(ExchangeTradeFillsArchive source);

}
