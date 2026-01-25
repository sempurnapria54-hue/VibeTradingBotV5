package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveLinkDto;
import com.example.tradingbot.domain.model.TradeFillsArchiveLink;
import com.example.tradingbot.rest.model.TradeFillsArchiveLinkRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveLinkMapper {
    TradeFillsArchiveLink clientToDomain(OkxTradeFillsArchiveLinkDto dto);

    TradeFillsArchiveLinkRest domainToRest(TradeFillsArchiveLink domain);
}
