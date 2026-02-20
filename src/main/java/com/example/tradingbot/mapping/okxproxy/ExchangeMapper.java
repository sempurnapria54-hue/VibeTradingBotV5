package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.rest.model.Exchange;
import com.example.tradingbot.persistence.model.ExchangeEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    ExchangeEntity restToDomain(Exchange exchange);

    Exchange domainToRest(ExchangeEntity source);

    List<Exchange> domainToRest(List<ExchangeEntity> source);
}
