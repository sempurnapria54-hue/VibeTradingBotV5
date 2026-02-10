package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.TradeFillsArchiveDto;
import com.example.tradingbot.domain.model.okxproxy.TradeFillsArchive;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "msg", target = "message")
    TradeFillsArchive clientToDomain(TradeFillsArchiveDto source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "message", target = "msg")
    TradeFillsArchiveDto domainToClient(TradeFillsArchive source);

    com.example.tradingbot.rest.model.okxproxy.TradeFillsArchive domainToRest(TradeFillsArchive source);

    TradeFillsArchive restToDomain(com.example.tradingbot.rest.model.okxproxy.TradeFillsArchive source);
}
