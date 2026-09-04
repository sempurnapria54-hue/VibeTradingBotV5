package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.request.CreateExchangeApiRequest;
import com.example.tradingbot.api.model.response.ExchangeApiResponse;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link Exchange} между слоями api ↔ domain ↔ persistence
 * (docs/models/domain/core/Exchange.md). Поля совпадают по имени; enum
 * {@code Status} ↔ строка конвертируется MapStruct автоматически.
 * Наружу отдаётся internalId, не id (api-модель без id).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExchangeMapper {

    ExchangeEntity domainToPersistence(Exchange exchange);

    Exchange persistenceToDomain(ExchangeEntity entity);

    Exchange apiToDomain(CreateExchangeApiRequest request);

    ExchangeApiResponse domainToApi(Exchange exchange);
}
