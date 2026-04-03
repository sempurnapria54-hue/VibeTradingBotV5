package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.rest.model.request.instrument.CreateInstrumentRequest;
import com.example.tradingbot.rest.model.response.instrument.InstrumentPageResponse;
import com.example.tradingbot.rest.model.response.instrument.InstrumentResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", uses = CandleGroupMapper.class)
public interface InstrumentMapper extends CommonMapper {

    @Mapping(source = "type", target = "instrumentType")
    @Mapping(source = "externalId", target = "instrumentId")
    InstrumentsRequest domainSearchParamsToClientOkxRequest(InstrumentSearchParams source);

    @Mapping(source = "instId", target = "externalId")
    @Mapping(source = "instType", target = "type")
    Instrument clientOkxResponseToDomain(
            com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    List<Instrument> clientOkxResponseToDomain(
            List<com.example.tradingbot.client.model.okx.response.InstrumentResponse> source);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalInstrumentType", source = "instType")
    @Mapping(target = "baseCurrency", source = "baseCcy")
    @Mapping(target = "quoteCurrency", source = "quoteCcy")
    @Mapping(target = "settleCurrency", source = "settleCcy")
    @Mapping(target = "lotSize", source = "lotSz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "minimumOrderSize", source = "minSz", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "contractValue", source = "ctVal", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "contractMultiplier", source = "ctMult", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "priceTickSize", source = "tickSz", qualifiedByName = "stringToBigDecimal")
    InstrumentExternalSnapshot clientOkxToExternalSnapshot(
            com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<InstrumentExternalSnapshot> clientOkxToExternalSnapshot(
            List<com.example.tradingbot.client.model.okx.response.InstrumentResponse> source);

    InstrumentResponse domainToRest(Instrument source);

    List<com.example.tradingbot.rest.model.response.instrument.Instrument> domainToRest(List<Instrument> source);

    Instrument restToDomain(CreateInstrumentRequest request);

    InstrumentEntity domainToData(Instrument source);

    Instrument dataToDomain(InstrumentEntity source);

    List<Instrument> dataToDomain(List<InstrumentEntity> source);

    InstrumentSearchParams restToDomain(
            com.example.tradingbot.rest.model.request.instrument.search_params.InstrumentSearchParams source);

    InstrumentPageResponse domainToRest(Page<Instrument> result);

    Page<Instrument> dataToDomain(Page<InstrumentEntity> data);

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(Instrument source, @MappingTarget Instrument target);
}
