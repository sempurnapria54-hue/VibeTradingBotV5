package com.example.marketdata.mapping;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.marketdata.persistence.model.MarketPriceLevelEntity;
import com.example.marketdata.persistence.model.MarketStructureEntity;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг структуры рынка domain ↔ persistence. Уровни — element-wise;
 * событие пробоя — плоские breakout_*-колонки (на чтение собирается
 * обратно в {@link MarketBreakoutEvent}, пусто — пробоя нет). Перечни
 * конвертирует MapStruct на границе.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketStructureMapper {

    @Mapping(target = "breakoutBrokenLevelType", source = "breakoutEvent.brokenLevelType")
    @Mapping(target = "breakoutDirection", source = "breakoutEvent.direction")
    @Mapping(target = "breakoutLevelPrice", source = "breakoutEvent.levelPrice")
    @Mapping(target = "breakoutConfirmedAt", source = "breakoutEvent.confirmedAt")
    MarketStructureEntity domainToPersistence(MarketStructure structure);

    MarketPriceLevelEntity domainToPersistence(MarketPriceLevel level);

    @Mapping(target = "breakoutEvent", source = "entity", qualifiedByName = "toBreakoutEvent")
    MarketStructure persistenceToDomain(MarketStructureEntity entity);

    MarketPriceLevel persistenceToDomain(MarketPriceLevelEntity entity);

    /** Плоские breakout_*-колонки в событие пробоя; пусто — пробоя нет. */
    @Named("toBreakoutEvent")
    default MarketBreakoutEvent toBreakoutEvent(MarketStructureEntity entity) {
        if (isNull(entity.getBreakoutDirection())) {
            return null;
        }
        MarketBreakoutEvent event = new MarketBreakoutEvent();
        event.setDirection(MarketBreakoutEvent.Direction.valueOf(entity.getBreakoutDirection()));
        if (nonNull(entity.getBreakoutBrokenLevelType())) {
            event.setBrokenLevelType(MarketPriceLevel.Type.valueOf(entity.getBreakoutBrokenLevelType()));
        }
        event.setLevelPrice(entity.getBreakoutLevelPrice());
        event.setConfirmedAt(entity.getBreakoutConfirmedAt());
        return event;
    }
}
