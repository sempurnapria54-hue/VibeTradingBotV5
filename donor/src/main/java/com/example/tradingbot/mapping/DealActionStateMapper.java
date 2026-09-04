package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.command.DealActionState;
import com.example.tradingbot.persistence.model.command.DealStrategyActionStateEntity;
import com.example.tradingbot.persistence.model.command.DealSystemActionStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг {@link DealActionState} между domain ↔ persistence
 * (docs/models/domain/other/DealActionState.md). Модель одна, таблиц две:
 * вид действия кодируется таблицей, поэтому обратный маппинг ставит
 * {@code actionKind} константой своей таблицы, а прямой его не переносит
 * — колонки рода в схеме нет. lastError — JSONB через
 * {@link RuntimeJsonConverter}; retry-скаляры, поля аудита и енумы ↔
 * строка — MapStruct автоматически.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = RuntimeJsonConverter.class)
public interface DealActionStateMapper {

    DealStrategyActionStateEntity domainToStrategyPersistence(DealActionState state);

    @Mapping(target = "actionKind", constant = "STRATEGY")
    DealActionState strategyPersistenceToDomain(DealStrategyActionStateEntity entity);

    DealSystemActionStateEntity domainToSystemPersistence(DealActionState state);

    @Mapping(target = "actionKind", constant = "SYSTEM")
    DealActionState systemPersistenceToDomain(DealSystemActionStateEntity entity);
}
