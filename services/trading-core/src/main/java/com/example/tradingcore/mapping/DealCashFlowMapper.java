package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.persistence.model.DealCashFlowEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг строки разбивки движений средств domain ↔ persistence
 * (docs/models/domain/other/DealCashFlow.md).
 *
 * <p>Форм источника здесь нет: разговор с площадкой ведёт коннектор, и
 * ядру движение приезжает уже доменной моделью
 * (docs/architecture/contracts.md). Перечни — категория, состояние курса,
 * таймфрейм координаты — хранятся строкой и конвертируются по имени.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealCashFlowMapper {

    DealCashFlowEntity domainToPersistence(DealCashFlow flow);

    DealCashFlow persistenceToDomain(DealCashFlowEntity entity);
}
