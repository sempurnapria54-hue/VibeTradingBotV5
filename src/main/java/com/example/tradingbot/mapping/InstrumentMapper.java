package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.rest.model.request.CreateInstrumentRequest;
import com.example.tradingbot.rest.model.response.InstrumentResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstrumentMapper {

    @Mapping(source = "instId", target = "externalId")
    @Mapping(source = "instType", target = "type")
    Instrument clientOkxResponseToDomain(com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    @Mapping(source = "externalId", target = "instId")
    @Mapping(source = "type", target = "instType")
    com.example.tradingbot.client.model.okx.response.InstrumentResponse domainToClient(Instrument source);

    @Mapping(source = "type", target = "instrumentType")
    @Mapping(source = "externalId", target = "instrumentId")
    InstrumentsRequest domainToClientOkxRequest(Instrument source);

    List<Instrument> clientOkxResponseToDomain(List<com.example.tradingbot.client.model.okx.response.InstrumentResponse> source);

    @Mapping(source = "internalId", target = "internalId")
    @Mapping(source = "exchangeId", target = "exchangeInternalId")
    @Mapping(source = "externalId", target = "instId")
    @Mapping(source = "type", target = "instType")
    InstrumentResponse domainToRest(InstrumentEntity source);

    List<InstrumentResponse> domainToRest(List<InstrumentEntity> source);

    @Mapping(source = "externalId", target = "externalId")
    InstrumentEntity restToDomain(CreateInstrumentRequest request);
}
