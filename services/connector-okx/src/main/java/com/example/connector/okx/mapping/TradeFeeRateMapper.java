package com.example.connector.okx.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.other.TradeFeeRate;
import com.example.connector.okx.snapshot.TradeFeeRateExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.TradeFeeOkxResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг ставки комиссии (docs/models/mapping/TradeFeeRate.md):
 * integration DTO OKX → граничный {@link TradeFeeRateExternalSnapshot}
 * (по снапшоту на группу), затем материализация {@link TradeFeeRate} с
 * резолвом доменной проекции типа.
 *
 * <p><b>Знак снимается на границе, а не в формулах.</b> Источник
 * записывает комиссию отрицательной, ребейт положительным (наблюдение
 * `AG12.1`: {@code taker = -0.0005}); ниже маппинга ставка есть
 * ИЗДЕРЖКА, и {@code abs} в формулах не появляется.
 *
 * <p>{@code refreshCount} маппером не переносится: версионирование строки
 * — доменное решение записи, а маппер только переносит данные.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TradeFeeRateMapper {

    /** Ответ источника плюс одна его группа → снапшот этой группы. */
    @Mapping(target = "externalInstrumentType", source = "response.instType")
    @Mapping(target = "externalFeeGroupId", source = "group.groupId")
    @Mapping(target = "externalTakerFeeRate", source = "group.taker", qualifiedByName = "rateAsCost")
    @Mapping(target = "externalMakerFeeRate", source = "group.maker", qualifiedByName = "rateAsCost")
    @Mapping(target = "externalFeeLevel", source = "response.level")
    @Mapping(target = "externalModifiedAt", source = "response.ts", qualifiedByName = "sourceTimestamp")
    TradeFeeRateExternalSnapshot integrationToSnapshot(TradeFeeOkxResponse response,
                                                       TradeFeeOkxResponse.FeeGroupOkxResponse group);

    /**
     * Материализация ставки из снапшота: {@code external*} переносятся по
     * имени, доменная проекция типа резолвится из сырого значения,
     * {@code exchangeId} проставляет вызывающий — синк знает биржу.
     */
    @Mapping(target = "instrumentType", source = "snapshot.externalInstrumentType",
            qualifiedByName = "resolveFeeInstrumentType")
    TradeFeeRate snapshotToDomain(TradeFeeRateExternalSnapshot snapshot, Long exchangeId);

    /**
     * Снапшот → доменная модель БЕЗ числового ключа связанной
     * сущности.
     *
     * <p><b>Коннектор его и не может заполнить:</b> числовые ключи баз
     * границу сервиса не пересекают
     * ({@code docs/architecture/data-ownership.md} §Идентификаторы), а
     * у коннектора базы нет вовсе. Связь проставляет владелец
     * сущности, получив модель. Соседний метод с ключом остаётся для
     * внутреннего употребления донора, пока тот жив.
     */
    TradeFeeRate snapshotToDomain(TradeFeeRateExternalSnapshot snapshot);

    /** Ставка источника со снятым знаком: комиссия положительна, ребейт отрицателен. */
    @Named("rateAsCost")
    default String rateAsCost(String sourceRate) {
        if (isBlank(sourceRate)) {
            return null;
        }
        try {
            return new BigDecimal(sourceRate.trim()).negate().toPlainString();
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    /** Время данных источника (эпоха в миллисекундах) → UTC-момент. */
    @Named("sourceTimestamp")
    default OffsetDateTime sourceTimestamp(String epochMillis) {
        if (isBlank(epochMillis)) {
            return null;
        }
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(epochMillis.trim())), ZoneOffset.UTC);
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    /** Сырой тип инструмента → доменная проекция; неизвестное — {@code UNKNOWN}. */
    @Named("resolveFeeInstrumentType")
    default InstrumentExternalRules.InstrumentType resolveFeeInstrumentType(String raw) {
        if (isBlank(raw)) {
            return InstrumentExternalRules.InstrumentType.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SWAP" -> InstrumentExternalRules.InstrumentType.SWAP;
            case "FUTURES" -> InstrumentExternalRules.InstrumentType.FUTURES;
            case "SPOT" -> InstrumentExternalRules.InstrumentType.SPOT;
            case "MARGIN" -> InstrumentExternalRules.InstrumentType.MARGIN;
            case "OPTION" -> InstrumentExternalRules.InstrumentType.OPTION;
            default -> InstrumentExternalRules.InstrumentType.UNKNOWN;
        };
    }
}
