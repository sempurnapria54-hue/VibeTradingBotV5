package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import com.example.tradingbot.rest.model.request.exchange.CreateExchangeRequest;
import com.example.tradingbot.rest.model.response.exchange.ExchangeContainerResponse;
import com.example.tradingbot.rest.model.response.exchange.ExchangeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    Exchange restToDomain(CreateExchangeRequest exchange);

    ExchangeResponse domainToRest(Exchange source);

    List<com.example.tradingbot.rest.model.response.exchange.Exchange> domainToRest(List<Exchange> source);

    ExchangeEntity domainToData(Exchange source);

    Exchange dataToDomain(ExchangeEntity source);

    List<Exchange> dataToDomain(List<ExchangeEntity> source);

    @Mapping(target = "id", ignore = true)
    void domainToDomainOnCreate(Exchange source, @MappingTarget Exchange target);

    ExchangeContainerResponse domainListToRestContainer(List<Exchange> exchanges);
}
