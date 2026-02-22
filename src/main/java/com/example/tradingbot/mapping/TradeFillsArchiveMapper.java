package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.response.TradeFillsArchiveResponse;
import com.example.tradingbot.domain.model.TradeFillsArchive;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "code", target = "externalStatusCode")
    @Mapping(source = "msg", target = "externalStatusMessage")
    TradeFillsArchive clientToDomain(TradeFillsArchiveResponse source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "externalStatusCode", target = "code")
    @Mapping(source = "externalStatusMessage", target = "msg")
    TradeFillsArchiveResponse domainToClient(TradeFillsArchive source);

    List<TradeFillsArchive> clientToDomain(List<TradeFillsArchiveResponse> source);


}
