package com.example.tradingbot.mapping;

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
    @Mapping(source = "baseCcy", target = "baseCurrency")
    @Mapping(source = "quoteCcy", target = "quoteCurrency")
    @Mapping(source = "settleCcy", target = "settleCurrency")
    @Mapping(source = "lotSz", target = "stepSize")
    @Mapping(source = "minSz", target = "minimumSize")
    @Mapping(source = "ctVal", target = "contractValue")
    @Mapping(source = "ctMult", target = "contractMultiplier")
    @Mapping(source = "tickSz", target = "minPriceStep")
    Instrument clientToDomain(com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    @Mapping(source = "externalId", target = "instId")
    @Mapping(source = "type", target = "instType")
    @Mapping(source = "baseCurrency", target = "baseCcy")
    @Mapping(source = "quoteCurrency", target = "quoteCcy")
    @Mapping(source = "settleCurrency", target = "settleCcy")
    @Mapping(source = "stepSize", target = "lotSz")
    @Mapping(source = "minimumSize", target = "minSz")
    @Mapping(source = "contractValue", target = "ctVal")
    @Mapping(source = "contractMultiplier", target = "ctMult")
    @Mapping(source = "minPriceStep", target = "tickSz")
    com.example.tradingbot.client.model.okx.response.InstrumentResponse domainToClient(Instrument source);

    List<Instrument> clientToDomain(List<com.example.tradingbot.client.model.okx.response.InstrumentResponse> source);

    @Mapping(source = "internalId", target = "internalId")
    @Mapping(source = "exchangeId", target = "exchangeInternalId")
    @Mapping(source = "externalId", target = "instId")
    @Mapping(source = "type", target = "instType")
    InstrumentResponse domainToRest(InstrumentEntity source);

    List<InstrumentResponse> domainToRest(List<InstrumentEntity> source);

    @Mapping(source = "externalId", target = "externalId")
    InstrumentEntity restToDomain(CreateInstrumentRequest request);
}
