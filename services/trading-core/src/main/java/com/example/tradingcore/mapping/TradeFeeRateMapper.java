package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.tradingcore.persistence.model.TradeFeeRateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Граница domain ↔ persistence для ставки комиссии
 * (docs/models/domain/other/TradeFeeRate.md). Переносит только данные:
 * правило истории записи — доменное решение и живёт у
 * {@code TradeFeeRateDataService}.
 *
 * <p>Имена полей совпадают, поэтому явных {@code @Mapping} здесь нет
 * (.claude/rules/codestyle.md §Маппинг): у ядра владелец ставки назван
 * счётом и в модели, и в колонке.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TradeFeeRateMapper {

    TradeFeeRateEntity domainToPersistence(TradeFeeRate rate);

    TradeFeeRate persistenceToDomain(TradeFeeRateEntity entity);
}
