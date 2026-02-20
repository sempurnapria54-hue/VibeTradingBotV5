package com.example.tradingbot.mapping.okxproxy;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import com.example.tradingbot.rest.model.request.exchange.ExchangeCreateRequest;
import com.example.tradingbot.rest.model.response.exchange.ExchangeResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    ExchangeEntity restToDomain(ExchangeCreateRequest exchange);

    ExchangeResponse domainToRest(ExchangeEntity source);

    List<ExchangeResponse> domainToRest(List<ExchangeEntity> source);
}
