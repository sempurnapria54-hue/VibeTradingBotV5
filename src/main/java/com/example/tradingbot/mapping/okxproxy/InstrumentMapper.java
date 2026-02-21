package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.exchange.ExchangeInstrument;
import com.example.tradingbot.rest.model.request.instrument.InstrumentCreateRq;
import com.example.tradingbot.rest.model.response.instrument.InstrumentResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    ExchangeInstrument clientToDomain(com.example.tradingbot.client.model.okx.InstrumentResponse source);

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
    com.example.tradingbot.client.model.okx.InstrumentResponse domainToClient(ExchangeInstrument source);

    @Mapping(source = "externalName", target = "instId")
    @Mapping(source = "type", target = "instType")
    InstrumentResponse domainToRest(InstrumentEntity source);

    List<InstrumentResponse> domainToRest(List<InstrumentEntity> source);

    InstrumentEntity restToDomain(InstrumentCreateRq request);
}
