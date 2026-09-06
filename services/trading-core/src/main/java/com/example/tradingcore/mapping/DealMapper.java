package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.persistence.model.DealEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг сделки domain ↔ persistence (docs/models/domain/aggregate/Deal.md §Персистентность).
 *
 * <p>Граф агрегата — транши и эпизоды позиции — полями строки не
 * является: он грузится своими запросами по {@code deal_id} и собирается
 * контекстом прохода. Енумы ↔ строка MapStruct делает автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealMapper {

    DealEntity domainToPersistence(Deal deal);

    Deal persistenceToDomain(DealEntity entity);
}
