package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.persistence.model.DealTrancheEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг транша domain ↔ persistence
 * (docs/models/domain/aggregate/DealTranche.md §Персистентность).
 *
 * <p>Ноги транша в строку не входят — они грузятся своим запросом на
 * сделку и раскладываются по траншам в памяти; приписанный траншу объём
 * закрытия уровня сделки не хранится вовсе — он производен и считается
 * контекстом прохода.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealTrancheMapper {

    DealTrancheEntity domainToPersistence(DealTranche tranche);

    DealTranche persistenceToDomain(DealTrancheEntity entity);
}
