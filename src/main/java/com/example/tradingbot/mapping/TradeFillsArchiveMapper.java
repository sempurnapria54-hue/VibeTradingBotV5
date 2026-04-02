package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.FillsArchiveLinkRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveRequest;
import com.example.tradingbot.client.model.okx.response.TradeFillsArchiveResponse;
import com.example.tradingbot.domain.model.TradeFillsArchive;
import com.example.tradingbot.domain.model.search_params.TradeFillsSearchParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TradeFillsArchiveMapper {

    @Mapping(source = "ts", target = "timestamp")
    @Mapping(source = "code", target = "externalStatusCode")
    @Mapping(source = "msg", target = "externalStatusMessage")
    TradeFillsArchive clientOkxResponseToDomain(TradeFillsArchiveResponse source);

    @Mapping(source = "timestamp", target = "ts")
    @Mapping(source = "externalStatusCode", target = "code")
    @Mapping(source = "externalStatusMessage", target = "msg")
    TradeFillsArchiveResponse domainToClient(TradeFillsArchive source);

    FillsArchiveRequest domainSearchParamsToClientOkxRequest(TradeFillsSearchParams source);

    FillsArchiveLinkRequest domainSearchParamsToClientOkxLinkRequest(TradeFillsSearchParams source);

    List<TradeFillsArchive> clientOkxResponseToDomain(List<TradeFillsArchiveResponse> source);
}
