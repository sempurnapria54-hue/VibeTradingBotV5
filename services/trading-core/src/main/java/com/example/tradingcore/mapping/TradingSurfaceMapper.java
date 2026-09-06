package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.api.model.DealApiResponse;
import com.example.tradingcore.api.model.DealTrancheApiResponse;
import com.example.tradingcore.api.model.RiskAppetiteApiRequest;
import com.example.tradingcore.api.model.RiskAppetiteApiResponse;
import com.example.tradingcore.api.model.SafetyStateApiResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг domain ↔ api для поверхности торгового ядра.
 *
 * <p><b>Идентичности связанных сущностей резолвит вызывающий сервис</b>, а
 * не маппер: числовой ключ границу сервиса не пересекает, а перевод
 * «ключ → {@code internalId}» есть чтение, не перенос полей
 * (.claude/rules/codestyle.md §«Идентичность наружу»).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TradingSurfaceMapper {

    /**
     * Сделка в модель ответа. Идентичности счёта и инструмента маппером не
     * заполняются — их резолвит сервис.
     */
    DealApiResponse domainToApi(Deal deal);

    /** Транш в модель ответа; экспозиция — производная модели, а не колонка. */
    @Mapping(target = "exposure", expression = "java(tranche.exposure())")
    DealTrancheApiResponse domainToApi(DealTranche tranche);

    /**
     * Назначение чисел риск-аппетита в доменную модель тенанта.
     * Идентичность тенанта приходит путём вызова, а не телом: она адресует
     * ресурс, а не описывает его.
     */
    Tenant apiToDomain(RiskAppetiteApiRequest request);

    /** Числа риск-аппетита в модель ответа. */
    @Mapping(target = "tenantInternalId", source = "internalId")
    RiskAppetiteApiResponse domainToApi(Tenant tenant);

    /**
     * Торговое состояние счёта в модель ответа. Перечень инструментов со
     * стоящей ступенью маппером не заполняется — он приходит вторым
     * чтением, а не полем счёта.
     */
    @Mapping(target = "exchangeAccountInternalId", source = "internalId")
    @Mapping(target = "accountSafetyRung", source = "safetyRung")
    @Mapping(target = "accountStatus", source = "status")
    SafetyStateApiResponse domainToApi(ExchangeAccount account);
}
