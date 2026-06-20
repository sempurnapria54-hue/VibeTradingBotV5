package com.example.tradingbot.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.InstrumentOkxResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг внешних правил инструмента (docs/models/mapping/InstrumentExternalRules.md):
 * integration DTO OKX → граничный {@link InstrumentExternalRulesExternalSnapshot}
 * (сырые external*-строки), затем материализация
 * {@link InstrumentExternalRules} из снапшота с резолвом доменных проекций
 * (тип инструмента/контракта, статус торгуемости). Неизвестное сырое
 * значение нормализуется в {@code UNKNOWN} соответствующего enum.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstrumentExternalRulesMapper {

    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalInstrumentType", source = "instType")
    @Mapping(target = "externalContractType", source = "ctType")
    @Mapping(target = "externalContractValue", source = "ctVal")
    @Mapping(target = "externalContractValueCurrency", source = "ctValCcy")
    @Mapping(target = "externalTickSize", source = "tickSz")
    @Mapping(target = "externalLotSize", source = "lotSz")
    @Mapping(target = "externalMinSize", source = "minSz")
    @Mapping(target = "externalMaxLimitSize", source = "maxLmtSz")
    @Mapping(target = "externalMaxMarketSize", source = "maxMktSz")
    @Mapping(target = "externalMaxTriggerSize", source = "maxTriggerSz")
    @Mapping(target = "externalMaxStopSize", source = "maxStopSz")
    @Mapping(target = "externalMaxLeverage", source = "lever")
    @Mapping(target = "externalState", source = "state")
    InstrumentExternalRulesExternalSnapshot integrationToSnapshot(InstrumentOkxResponse response);

    /**
     * Материализация правил из снапшота: external*-поля переносятся по
     * имени; доменные проекции (тип инструмента/контракта, статус)
     * резолвятся из сырых значений; {@code instrumentId} — ключ навеса
     * владельца.
     */
    @Mapping(target = "instrumentType", source = "snapshot.externalInstrumentType",
            qualifiedByName = "resolveInstrumentType")
    @Mapping(target = "contractType", source = "snapshot.externalContractType",
            qualifiedByName = "resolveContractType")
    @Mapping(target = "status", source = "snapshot.externalState", qualifiedByName = "resolveStatus")
    InstrumentExternalRules snapshotToDomain(InstrumentExternalRulesExternalSnapshot snapshot, Long instrumentId);

    /** Сырой OKX instType → нормализованный {@link InstrumentExternalRules.InstrumentType}. */
    @Named("resolveInstrumentType")
    default InstrumentExternalRules.InstrumentType resolveInstrumentType(String raw) {
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

    /** Сырой OKX ctType → нормализованный {@link InstrumentExternalRules.ContractType}. */
    @Named("resolveContractType")
    default InstrumentExternalRules.ContractType resolveContractType(String raw) {
        if (isBlank(raw)) {
            return InstrumentExternalRules.ContractType.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "LINEAR" -> InstrumentExternalRules.ContractType.LINEAR;
            case "INVERSE" -> InstrumentExternalRules.ContractType.INVERSE;
            default -> InstrumentExternalRules.ContractType.UNKNOWN;
        };
    }

    /** Сырой OKX state → нормализованный {@link InstrumentExternalRules.Status}. */
    @Named("resolveStatus")
    default InstrumentExternalRules.Status resolveStatus(String raw) {
        if (isBlank(raw)) {
            return InstrumentExternalRules.Status.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "LIVE" -> InstrumentExternalRules.Status.LIVE;
            case "SUSPEND" -> InstrumentExternalRules.Status.SUSPEND;
            case "PREOPEN" -> InstrumentExternalRules.Status.PREOPEN;
            case "EXPIRED" -> InstrumentExternalRules.Status.EXPIRED;
            case "TEST" -> InstrumentExternalRules.Status.TEST;
            default -> InstrumentExternalRules.Status.UNKNOWN;
        };
    }
}
