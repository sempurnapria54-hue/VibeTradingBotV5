package com.example.tradingcore.mapping;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.integration.model.InstrumentMarketDataResponse;
import com.example.tradingcore.persistence.model.InstrumentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг инструмента integration → domain → persistence для проекции
 * каталога.
 *
 * <p><b>Навес справочных правил через границу полей не едет:</b> в колонке
 * он лежит сериализованным, и его разбор — забота
 * {@link InstrumentExternalRulesJsonConverter}, а не переноса полей.
 *
 * <p><b>Ступени, плеча и режима маржи в проекции нет</b> — их пишет само
 * ядро на паре «счёт, инструмент», и синк, переносящий их из ответа
 * владельца каталога, затирал бы запись ядра
 * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта на
 * инструменте»).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstrumentMapper {

    /** Сырой ответ каталога в доменную модель. */
    Instrument integrationToDomain(InstrumentMarketDataResponse response);

    /**
     * Обновление строки проекции спецификацией из каталога.
     *
     * <p>Идентичности не переписываются — по ним строка и найдена; навес
     * правил и момент снимка ставит писатель отдельно, потому что читает
     * их вторым вызовом и знает, полон ли снимок.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "exchangeCode", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "externalRules", ignore = true)
    @Mapping(target = "projectedAt", ignore = true)
    void updateProjection(Instrument instrument, @MappingTarget InstrumentEntity entity);
}
