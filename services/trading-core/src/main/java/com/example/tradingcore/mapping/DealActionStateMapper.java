package com.example.tradingcore.mapping;

import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.persistence.model.DealStrategyActionStateEntity;
import com.example.tradingcore.persistence.model.DealSystemActionStateEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Границы domain ↔ persistence строки исполнения
 * (docs/models/domain/other/DealActionState.md).
 *
 * <p><b>Модель одна, таблиц две:</b> вид действия кодируется таблицей,
 * поэтому обратный маппинг ставит вид константой своей таблицы, а прямой
 * его не переносит — колонки рода в схеме нет. Последняя ошибка едет
 * навесом JSONB; скаляры повторов, поля аудита и перечни ↔ строка —
 * автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DealActionStateMapper {

    DealStrategyActionStateEntity domainToStrategyPersistence(DealActionState state);

    @Mapping(target = "actionKind", constant = "STRATEGY")
    DealActionState strategyPersistenceToDomain(DealStrategyActionStateEntity entity);

    DealSystemActionStateEntity domainToSystemPersistence(DealActionState state);

    @Mapping(target = "actionKind", constant = "SYSTEM")
    DealActionState systemPersistenceToDomain(DealSystemActionStateEntity entity);
}
