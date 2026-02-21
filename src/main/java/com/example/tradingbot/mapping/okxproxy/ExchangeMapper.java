package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.rest.model.request.exchange.CreateExchangeRequest;
import com.example.tradingbot.rest.model.response.exchange.ExchangeResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    ExchangeEntity restToDomain(CreateExchangeRequest exchange);

    @Mapping(source = "internalId", target = "internalId")
    ExchangeResponse domainToRest(ExchangeEntity source);

    List<ExchangeResponse> domainToRest(List<ExchangeEntity> source);
}
