package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.persistence.model.marketdata.MarketPhaseEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг фазы рынка domain ↔ persistence. Ключ хранения — id
 * настройки-контейнера: на запись strategyMarketPhaseSettingId берётся
 * из setting.id; на чтение setting не регидрируется (потребитель
 * передаёт настройку сам, фазе достаточно type/таймстемпов). Enum type
 * конвертирует MapStruct (name()/valueOf). См.
 * docs/models/domain/other/MarketPhase.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketPhaseMapper {

    @Mapping(target = "strategyMarketPhaseSettingId", source = "setting.id")
    MarketPhaseEntity domainToPersistence(MarketPhase phase);

    @Mapping(target = "setting", ignore = true)
    MarketPhase persistenceToDomain(MarketPhaseEntity entity);
}
