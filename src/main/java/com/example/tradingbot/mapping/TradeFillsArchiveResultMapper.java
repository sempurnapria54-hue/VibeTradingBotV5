package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResultDto;
import com.example.tradingbot.domain.model.TradeFillsArchiveResult;
import com.example.tradingbot.rest.model.TradeFillsArchiveResultRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveResultMapper {
    TradeFillsArchiveResult clientToDomain(OkxTradeFillsArchiveResultDto dto);

    TradeFillsArchiveResultRest domainToRest(TradeFillsArchiveResult domain);
}
