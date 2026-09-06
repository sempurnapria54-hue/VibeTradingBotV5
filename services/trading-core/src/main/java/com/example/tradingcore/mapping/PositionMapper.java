package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.persistence.model.PositionEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг эпизода позиции domain ↔ persistence
 * (docs/models/mapping/Position.md). Строка — на эпизод, не на сделку:
 * закрытые эпизоды остаются, и по ним считается результат.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PositionMapper {

    PositionEntity domainToPersistence(Position position);

    Position persistenceToDomain(PositionEntity entity);

    /**
     * Перенос добытых фактов на строку эпизода: и живого наблюдения, и
     * записи закрытия.
     *
     * <p><b>Метод один, а не два, потому что различие между «обновить» и
     * «материализовать» лежит не в переносе.</b> Идентичность эпизода —
     * пара «биржевой идентификатор, биржевое время создания» — у
     * найденной строки уже совпала с добытой (по ней её и нашли), а у
     * заводимой пуста; в обоих случаях перенос даёт верное значение.
     * Второй метод различал бы то, что здесь неразличимо
     * (.claude/rules/design-simplicity.md).
     *
     * <p><b>Пустое поле добытого не затирает наше:</b> запись закрытия
     * живого размера не несёт, и слепой перенос обнулил бы последний
     * наблюдённый размер закрывшегося эпизода.
     *
     * <p>Статус и причину закрытия переносить нельзя: их назначает
     * исполнитель резолвером и write-once
     * (docs/rules/external-status-resolution.md).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    void updateFromFetched(Position fetched, @MappingTarget Position episode);
}
