package com.example.connector.okx.mapping;

import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.connector.okx.snapshot.BalanceContainerExternalSnapshot;
import com.example.connector.okx.snapshot.BalanceExternalSnapshot;
import com.example.connector.okx.integration.external.api.model.okx.response.AccountConfigOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceDetailOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.BalanceOkxResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг OKX balance response → нормализованные снапшоты
 * (account-level + currency-level). Числовые поля переносятся строками
 * (валидированы как parseable), uTime → OffsetDateTime через
 * {@link OkxResponseConverter}. Счёта коннектор не знает — его
 * проставляет ядро, приземляя снимок. См.
 * docs/models/domain/core/BalanceContainer.md, docs/models/mapping/Balance.md.
 *
 * <p><b>Снимок собирается из ДВУХ ответов площадки:</b> баланса и
 * конфигурации счёта. Режимы счёта и позиций едут в снапшот сырыми
 * строками, а в доменный словарь переводятся при сборке контейнера —
 * там же, где числа становятся числами.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = OkxResponseConverter.class)
public interface BalanceContainerMapper {

    @Mapping(target = "externalUpdatedAt", source = "response.uTime")
    @Mapping(target = "externalTotalEquity", source = "response.totalEq")
    @Mapping(target = "externalAdjustedEquity", source = "response.adjEq")
    @Mapping(target = "externalAvailableEquity", source = "response.availEq")
    @Mapping(target = "balances", source = "response.details")
    @Mapping(target = "externalAccountLevel", source = "config.acctLv")
    @Mapping(target = "externalPositionMode", source = "config.posMode")
    BalanceContainerExternalSnapshot integrationToSnapshot(BalanceOkxResponse response,
                                                           AccountConfigOkxResponse config);

    /**
     * Снапшот → доменный контейнер баланса, СОЗДАНИЕМ.
     *
     * <p>Успешное чтение баланса обязано вернуть валидный контейнер с
     * расчётной валютой; пустота здесь — контролируемая ошибка, а не
     * «не найдено» (docs/components/IntegrationService.md §«Контракт
     * чтения»).
     *
     * <p>Режимы переводятся в доменный словарь конвертером границы;
     * значение вне словаря даёт пустоту, а не угаданный режим
     * (docs/models/mapping/Balance.md).
     */
    @Mapping(target = "accountMode", source = "externalAccountLevel", qualifiedByName = "okxAccountMode")
    @Mapping(target = "positionMode", source = "externalPositionMode", qualifiedByName = "okxPositionMode")
    BalanceContainer snapshotToDomain(BalanceContainerExternalSnapshot snapshot);

    @Mapping(target = "externalCurrency", source = "ccy")
    @Mapping(target = "externalUpdatedAt", source = "uTime")
    @Mapping(target = "externalEquity", source = "eq")
    @Mapping(target = "externalCashBalance", source = "cashBal")
    @Mapping(target = "externalAvailableBalance", source = "availBal")
    @Mapping(target = "externalFrozenBalance", source = "frozenBal")
    BalanceExternalSnapshot integrationToSnapshot(BalanceDetailOkxResponse detail);

}
