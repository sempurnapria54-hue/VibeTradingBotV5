package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxInstrumentDto;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.rest.model.InstrumentRest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InstrumentMapper {
    Instrument clientToDomain(OkxInstrumentDto dto);

    InstrumentRest domainToRest(Instrument domain);
}
