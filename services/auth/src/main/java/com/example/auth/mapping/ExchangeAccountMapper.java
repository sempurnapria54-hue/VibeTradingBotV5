package com.example.auth.mapping;

import com.example.auth.api.model.ExchangeAccountApiResponse;
import com.example.auth.persistence.model.ExchangeAccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Persistence → api для биржевого счёта.
 *
 * <p>Маппинг только MapStruct, ручного нет (.claude/rules/codestyle.md
 * §Маппинг). Единственное несовпадение имён — `tenantId` строки против
 * `tenantInternalId` ответа: наружу идёт идентичность связанной сущности,
 * а не её ключ (§«Идентичность наружу»).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExchangeAccountMapper {

    @Mapping(target = "tenantInternalId", source = "tenantId")
    ExchangeAccountApiResponse persistenceToApi(ExchangeAccountEntity account);
}
