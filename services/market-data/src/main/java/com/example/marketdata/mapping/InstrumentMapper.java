package com.example.marketdata.mapping;

import com.example.marketdata.persistence.model.InstrumentEntity;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг инструмента domain ↔ persistence.
 *
 * <p><b>Навес справочных правил через границу не едет:</b> в колонке он
 * лежит сериализованным, и его разбор — забота
 * {@link InstrumentExternalRulesJsonConverter}, а не переноса полей.
 *
 * <p><b>Числовой идентификатор площадки не пишется</b> — market-data
 * адресует площадку кодом, а числовой ключ существует только в схеме
 * донора (docs/models/domain/core/Instrument.md).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstrumentMapper {

    @Mapping(target = "externalRules", ignore = true)
    InstrumentEntity domainToPersistence(Instrument instrument);

    @Mapping(target = "exchangeId", ignore = true)
    Instrument persistenceToDomain(InstrumentEntity entity);

    /**
     * Обновление каталожной строки спецификацией из листинга: пустое
     * значение ответа не затирает уже известного — площадка отдаёт часть
     * полей не в каждом чтении.
     *
     * <p>Идентичность строки и её статус из листинга не переписываются:
     * первую назначает заведение, второй ведёт сам сервис.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "internalId", ignore = true)
    @Mapping(target = "exchangeCode", ignore = true)
    @Mapping(target = "exchangeId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "candleGroups", ignore = true)
    void updateFromListing(Instrument listed, @MappingTarget Instrument stored);
}
