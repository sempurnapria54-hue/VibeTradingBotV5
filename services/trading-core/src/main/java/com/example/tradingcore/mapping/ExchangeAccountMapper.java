package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingcore.integration.model.ExchangeAccountAuthResponse;
import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг биржевого счёта integration → domain → persistence.
 *
 * <p><b>Колонки торгового состояния гасятся поимённо, и это несущее
 * свойство.</b> Строку счёта пишут две тропы: реестровую часть — тик
 * синка, торговое состояние — торговый код ядра
 * (docs/models/domain/core/ExchangeAccount.md §Персистентность). Доменная
 * модель несёт оба набора, а в ответе реестра торговых полей нет и быть
 * не может — перенос «всех полей» затирал бы базу риска, счётчики и
 * ступень пустотой.
 *
 * <p><b>Пустое значение проекционной колонки, наоборот, переносится.</b>
 * Метка, стёртая у владельца, обязана стереться и в проекции: проекция
 * повторяет реестр, а не накапливает его историю.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExchangeAccountMapper {

    /**
     * Сырой ответ реестра в доменную модель. Числовой идентификатор не
     * переносится: у владельца он свой, у нас свой.
     */
    @Mapping(target = "tenantId", source = "tenantInternalId")
    ExchangeAccount integrationToDomain(ExchangeAccountAuthResponse response);

    /**
     * Обновление строки проекционными колонками. Идентичность не
     * переписывается — по ней строка и найдена; момент снимка ставит
     * писатель, потому что он знает, полон ли снимок.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "projectedAt", ignore = true)
    @Mapping(target = "riskBase", ignore = true)
    @Mapping(target = "riskBaseCurrency", ignore = true)
    @Mapping(target = "consecutiveLossCount", ignore = true)
    @Mapping(target = "blindPassCount", ignore = true)
    @Mapping(target = "safetyRung", ignore = true)
    @Mapping(target = "tenantInternalId", source = "tenantId")
    void updateProjection(ExchangeAccount account, @MappingTarget ExchangeAccountEntity entity);
}
