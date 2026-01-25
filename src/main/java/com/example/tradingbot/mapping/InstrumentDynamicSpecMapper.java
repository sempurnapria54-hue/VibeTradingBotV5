package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxInstrumentDynamicSpec;
import com.example.tradingbot.domain.model.InstrumentDynamicSpec;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InstrumentDynamicSpecMapper {

    InstrumentDynamicSpec clientToDomain(OkxInstrumentDynamicSpec spec);

    com.example.tradingbot.rest.model.InstrumentDynamicSpec domainToRest(InstrumentDynamicSpec spec);
}
