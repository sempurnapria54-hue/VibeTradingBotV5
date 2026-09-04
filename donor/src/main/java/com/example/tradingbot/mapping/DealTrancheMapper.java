package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.persistence.model.deal.DealTrancheEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link DealTranche} между domain ↔ persistence
 * (docs/models/mapping/DealTranche.md). Заявки транша в строку не входят
 * — они грузятся своим запросом в контекст прохода; status ↔ строка
 * MapStruct делает автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealTrancheMapper {

    DealTrancheEntity domainToPersistence(DealTranche tranche);

    DealTranche persistenceToDomain(DealTrancheEntity entity);
}
