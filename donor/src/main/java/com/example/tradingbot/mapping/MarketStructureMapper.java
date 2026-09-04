package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.persistence.model.marketdata.MarketPriceLevelEntity;
import com.example.tradingbot.persistence.model.marketdata.MarketStructureEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг структуры рынка domain ↔ persistence. Уровни — element-wise;
 * событие пробоя — плоские breakout_*-колонки (на чтение собирается
 * обратно в MarketBreakoutEvent, null — пробоя нет). Enum'ы конвертирует
 * MapStruct (name()/valueOf) на границе. См.
 * docs/models/domain/other/MarketStructure.md.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketStructureMapper {

    @Mapping(target = "breakoutBrokenLevelType", source = "breakoutEvent.brokenLevelType")
    @Mapping(target = "breakoutDirection", source = "breakoutEvent.direction")
    @Mapping(target = "breakoutLevelPrice", source = "breakoutEvent.levelPrice")
    @Mapping(target = "breakoutConfirmedAt", source = "breakoutEvent.confirmedAt")
    MarketStructureEntity domainToPersistence(MarketStructure structure);

    MarketPriceLevelEntity domainToPersistence(MarketPriceLevel level);

    /** Back-ссылка контейнмента: каждый уровень знает свою структуру (FK на вставке). */
    @AfterMapping
    default void wireLevels(@MappingTarget MarketStructureEntity entity) {
        if (nonNull(entity.getLevels())) {
            entity.getLevels().forEach(level -> level.setStructure(entity));
        }
    }

    @Mapping(target = "breakoutEvent", source = "entity", qualifiedByName = "toBreakoutEvent")
    MarketStructure persistenceToDomain(MarketStructureEntity entity);

    MarketPriceLevel persistenceToDomain(MarketPriceLevelEntity entity);

    /** Плоские breakout_*-колонки → MarketBreakoutEvent (null, если пробоя нет). */
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
