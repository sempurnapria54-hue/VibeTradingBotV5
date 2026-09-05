package com.example.marketdata.mapping;

import com.example.marketdata.persistence.model.CandleGroupEntity;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг единицы сбора свечей domain ↔ persistence. Доменные перечни
 * (таймфрейм, статус) конвертирует MapStruct строкой на границе.
 *
 * <p>Таймфрейма площадки на строке нет: словарь баров принадлежит
 * коннектору, и перевод делает он (.claude/rules/codestyle.md §Слои).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CandleGroupMapper {

    CandleGroupEntity domainToPersistence(CandleGroup group);

    CandleGroup persistenceToDomain(CandleGroupEntity entity);
}
