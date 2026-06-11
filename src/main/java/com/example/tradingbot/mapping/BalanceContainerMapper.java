package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.OkxBalanceDetailResponse;
import com.example.tradingbot.integration.model.okx.response.OkxBalanceResponse;
import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг OKX balance response → нормализованные снапшоты
 * (account-level + currency-level). Числовые поля переносятся строками
 * (валидированы как parseable), uTime → OffsetDateTime через
 * {@link OkxResponseConverter}. exchangeId не из OKX (executor). См.
 * docs/models/domain/core/BalanceContainer.md, docs/models/mapping/Balance.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class)
public interface BalanceContainerMapper {

    @Mapping(target = "externalUpdatedAt", source = "uTime")
    @Mapping(target = "externalTotalEquity", source = "totalEq")
    @Mapping(target = "externalAdjustedEquity", source = "adjEq")
    @Mapping(target = "externalAvailableEquity", source = "availEq")
    @Mapping(target = "balances", source = "details")
    BalanceContainerExternalSnapshot integrationToSnapshot(OkxBalanceResponse response);

    @Mapping(target = "externalCurrency", source = "ccy")
    @Mapping(target = "externalUpdatedAt", source = "uTime")
    @Mapping(target = "externalEquity", source = "eq")
    @Mapping(target = "externalCashBalance", source = "cashBal")
    @Mapping(target = "externalAvailableBalance", source = "availBal")
    @Mapping(target = "externalFrozenBalance", source = "frozenBal")
    BalanceExternalSnapshot integrationToSnapshot(OkxBalanceDetailResponse detail);

    BalanceContainerEntity domainToPersistence(BalanceContainer container);

    BalanceContainer persistenceToDomain(BalanceContainerEntity entity);

    BalanceEntity domainToPersistence(Balance balance);

    Balance persistenceToDomain(BalanceEntity entity);
}
