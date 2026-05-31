package com.example.tradingbot.mapping;

import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
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

    /**
     * CLIENT
     */

    @Mapping(source = "externalType", target = "externalType")
    @Mapping(source = "externalId", target = "externalId")
    InstrumentsRequest domainSearchParamsToClientOkxRequest(InstrumentSearchParams source);

    @Mapping(source = "instId", target = "externalId")
    @Mapping(source = "instType", target = "externalType")
    Instrument clientToDomain(
            com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    List<Instrument> clientToDomain(
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
    InstrumentExternalSnapshot clientToExternalSnapshot(
            com.example.tradingbot.client.model.okx.response.InstrumentResponse source);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<InstrumentExternalSnapshot> clientToExternalSnapshot(
            List<com.example.tradingbot.client.model.okx.response.InstrumentResponse> source);


    /**
     * REST
     */

    @Mapping(target = "instrument", source = ".")
    InstrumentResponse domainToRest(Instrument source);

    List<com.example.tradingbot.rest.model.response.instrument.Instrument> domainToRest(List<Instrument> source);

    @Mapping(target = "externalType", source = "type")
    Instrument restToDomain(CreateInstrumentRequest request);

    @Mapping(target = "externalType", source = "externalType")
    @Mapping(target = "externalId", source = "externalId")
    InstrumentSearchParams restToDomainSearchParams(
            com.example.tradingbot.rest.model.request.instrument.search_params.InstrumentSearchParams source);

    default InstrumentPageResponse domainToRest(Page<Instrument> result) {
        if (result == null) {
            return new InstrumentPageResponse(Page.empty());
        }

        return new InstrumentPageResponse(result.map(this::domainToRestModel));
    }

    com.example.tradingbot.rest.model.response.instrument.Instrument domainToRestModel(Instrument source);


    /**
     * DATA
     */

    InstrumentEntity domainToData(Instrument source);

    Instrument dataToDomain(InstrumentEntity source);

    List<Instrument> dataToDomain(List<InstrumentEntity> source);

    default Page<Instrument> dataToDomain(Page<InstrumentEntity> data) {
        if (data == null) {
            return Page.empty();
        }

        return data.map(this::dataToDomain);
    }


    /**
     * DOMAIN_COPY
     */

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(Instrument source, @MappingTarget Instrument target);
}
