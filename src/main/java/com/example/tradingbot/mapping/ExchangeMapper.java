package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.request.CreateExchangeApiRequest;
import com.example.tradingbot.api.model.response.ExchangeApiResponse;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Маппинг {@link Exchange} между слоями api ↔ domain ↔ persistence
 * (docs/models/domain/core/Exchange.md). Поля совпадают 1:1, enum
 * {@code Status} в api отдаётся строкой.
 */
@Mapper(componentModel = "spring")
public interface ExchangeMapper {

    ExchangeEntity domainToEntity(Exchange exchange);

    Exchange entityToDomain(ExchangeEntity entity);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "internalId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "baseUrl", source = "baseUrl")
    Exchange apiToDomain(CreateExchangeApiRequest request);

    ExchangeApiResponse domainToApi(Exchange exchange);
}
