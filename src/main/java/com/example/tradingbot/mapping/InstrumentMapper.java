package com.example.tradingbot.mapping;

import com.example.tradingbot.client.okx.dto.OkxInstrument;
import com.example.tradingbot.domain.model.Instrument;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = InstrumentDynamicSpecMapper.class)
public interface InstrumentMapper {

    Instrument clientToDomain(OkxInstrument instrument);

    List<Instrument> clientToDomain(List<OkxInstrument> instruments);

    com.example.tradingbot.rest.model.Instrument domainToRest(Instrument instrument);

    List<com.example.tradingbot.rest.model.Instrument> domainToRest(List<Instrument> instruments);
}
