package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import com.example.tradingbot.rest.model.request.exchange.CreateExchangeRequest;
import com.example.tradingbot.rest.model.response.exchange.ExchangeContainerResponse;
import com.example.tradingbot.rest.model.response.exchange.ExchangeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    /**
     * REST
     */

    Exchange restToDomain(CreateExchangeRequest exchange);

    @Mapping(target = "exchange", source = ".")
    ExchangeResponse domainToRest(Exchange source);

    com.example.tradingbot.rest.model.response.exchange.Exchange domainToRestModel(Exchange source);

    @Mapping(target = "exchanges", source = ".")
    ExchangeContainerResponse domainListToRestContainer(List<Exchange> exchanges);


    /**
     * DATA
     */

    ExchangeEntity domainToData(Exchange source);

    Exchange dataToDomain(ExchangeEntity source);

    List<Exchange> dataToDomain(List<ExchangeEntity> source);


    /**
     * DOMAIN_COPY
     */

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(Exchange source, @MappingTarget Exchange target);
}
