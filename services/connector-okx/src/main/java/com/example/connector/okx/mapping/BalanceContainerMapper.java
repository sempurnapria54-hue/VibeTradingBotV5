package com.example.connector.okx.mapping;

import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.connector.okx.snapshot.BalanceContainerExternalSnapshot;
import com.example.connector.okx.snapshot.BalanceExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.BalanceDetailOkxResponse;
import com.example.connector.okx.integration.model.okx.response.BalanceOkxResponse;
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
    BalanceContainerExternalSnapshot integrationToSnapshot(BalanceOkxResponse response);

    /**
     * Снапшот → доменный контейнер баланса, СОЗДАНИЕМ.
     *
     * <p>Успешное чтение баланса обязано вернуть валидный контейнер с
     * расчётной валютой; пустота здесь — контролируемая ошибка, а не
     * «не найдено» (docs/components/IntegrationService.md §«Контракт
     * чтения»).
     */
    BalanceContainer snapshotToDomain(BalanceContainerExternalSnapshot snapshot);

    @Mapping(target = "externalCurrency", source = "ccy")
    @Mapping(target = "externalUpdatedAt", source = "uTime")
    @Mapping(target = "externalEquity", source = "eq")
    @Mapping(target = "externalCashBalance", source = "cashBal")
    @Mapping(target = "externalAvailableBalance", source = "availBal")
    @Mapping(target = "externalFrozenBalance", source = "frozenBal")
    BalanceExternalSnapshot integrationToSnapshot(BalanceDetailOkxResponse detail);

}
