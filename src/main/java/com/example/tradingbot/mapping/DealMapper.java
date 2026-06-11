package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.persistence.model.deal.DealEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link Deal} между domain ↔ persistence
 * (docs/models/domain/aggregate/Deal.md). Runtime graph
 * (orders/algoOrders/position) — не поля DealEntity: грузится отдельно
 * DataService по deal_id, здесь не маппится. Enum'ы ↔ строка — MapStruct
 * автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealMapper {

    DealEntity domainToPersistence(Deal deal);

    Deal persistenceToDomain(DealEntity entity);
}
