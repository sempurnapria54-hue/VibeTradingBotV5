package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.client.okx.dto.InstrumentDto;
import com.example.tradingbot.domain.model.okxproxy.Instrument;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.rest.model.request.instrument.InstrumentCreateRq;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InstrumentMapper {

    @Mapping(source = "instId", target = "instrumentId")
    @Mapping(source = "instType", target = "instrumentType")
    @Mapping(source = "baseCcy", target = "baseCurrency")
    @Mapping(source = "quoteCcy", target = "quoteCurrency")
    @Mapping(source = "settleCcy", target = "settleCurrency")
    @Mapping(source = "lotSz", target = "lotSize")
    @Mapping(source = "minSz", target = "minimumSize")
    @Mapping(source = "ctVal", target = "contractValue")
    @Mapping(source = "ctMult", target = "contractMultiplier")
    @Mapping(source = "tickSz", target = "tickSize")
    Instrument clientToDomain(InstrumentDto source);

    @Mapping(source = "instrumentId", target = "instId")
    @Mapping(source = "instrumentType", target = "instType")
    @Mapping(source = "baseCurrency", target = "baseCcy")
    @Mapping(source = "quoteCurrency", target = "quoteCcy")
    @Mapping(source = "settleCurrency", target = "settleCcy")
    @Mapping(source = "lotSize", target = "lotSz")
    @Mapping(source = "minimumSize", target = "minSz")
    @Mapping(source = "contractValue", target = "ctVal")
    @Mapping(source = "contractMultiplier", target = "ctMult")
    @Mapping(source = "tickSize", target = "tickSz")
    InstrumentDto domainToClient(Instrument source);

    com.example.tradingbot.rest.model.Instrument domainToRest(InstrumentEntity source);

    List<com.example.tradingbot.rest.model.Instrument> domainToRest(List<InstrumentEntity> source);

    InstrumentEntity restToDomain(com.example.tradingbot.rest.model.Instrument source);

    InstrumentEntity restToDomain(InstrumentCreateRq request);
}
