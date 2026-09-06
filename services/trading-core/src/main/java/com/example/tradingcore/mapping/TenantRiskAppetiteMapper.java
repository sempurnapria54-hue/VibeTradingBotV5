package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.persistence.model.TenantRiskAppetiteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Граница domain ↔ persistence для чисел риск-аппетита тенанта
 * (docs/models/domain/core/Tenant.md §«Структура — риск-аппетит (писатель
 * `trading-core`)»).
 *
 * <p><b>Тенант материализуется НЕПОЛНЫМ, и это норма модели.</b> Реестровую
 * часть — имя и статус — пишет {@code auth}, и у ядра её нет ни в каком
 * виде: перечня тенантов оно не держит. Каждый сервис материализует тот
 * набор полей, которым владеет; здесь это идентичность плюс три числа.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantRiskAppetiteMapper {

    @Mapping(target = "internalId", source = "tenantInternalId")
    @Mapping(target = "id", ignore = true)
    Tenant persistenceToDomain(TenantRiskAppetiteEntity entity);
}
