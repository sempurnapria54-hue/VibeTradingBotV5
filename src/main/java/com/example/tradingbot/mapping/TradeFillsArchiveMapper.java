package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResult;
import com.example.tradingbot.domain.model.TradeFillsArchive;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveMapper {

    TradeFillsArchive clientToDomain(OkxTradeFillsArchiveResult result);

    List<TradeFillsArchive> clientToDomain(List<OkxTradeFillsArchiveResult> results);

    com.example.tradingbot.rest.model.TradeFillsArchive domainToRest(TradeFillsArchive archive);

    List<com.example.tradingbot.rest.model.TradeFillsArchive> domainToRest(List<TradeFillsArchive> archives);
}
