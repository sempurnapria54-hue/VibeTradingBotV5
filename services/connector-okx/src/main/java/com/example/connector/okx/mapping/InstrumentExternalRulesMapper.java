package com.example.connector.okx.mapping;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.instrument.PositionTier;
import com.example.connector.okx.snapshot.InstrumentExternalRulesExternalSnapshot;
import com.example.connector.okx.snapshot.PositionTierExternalSnapshot;
import com.example.connector.okx.integration.external.api.model.okx.response.InstrumentOkxResponse;
import com.example.connector.okx.integration.external.api.model.okx.response.PositionTierOkxResponse;
import java.util.List;
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
 *
 * <p><b>Правила собираются из ДВУХ ответов площадки:</b> спецификации
 * инструмента и позиционных тиров его семьи. Тиры едут в снапшот сырыми
 * строками и разбираются в числа при материализации.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstrumentExternalRulesMapper {

    @Mapping(target = "externalInstrumentId", source = "response.instId")
    @Mapping(target = "externalInstrumentType", source = "response.instType")
    @Mapping(target = "externalContractType", source = "response.ctType")
    @Mapping(target = "externalContractValue", source = "response.ctVal")
    @Mapping(target = "externalContractValueCurrency", source = "response.ctValCcy")
    @Mapping(target = "externalTickSize", source = "response.tickSz")
    @Mapping(target = "externalLotSize", source = "response.lotSz")
    @Mapping(target = "externalMinSize", source = "response.minSz")
    @Mapping(target = "externalMaxLimitSize", source = "response.maxLmtSz")
    @Mapping(target = "externalMaxMarketSize", source = "response.maxMktSz")
    @Mapping(target = "externalMaxTriggerSize", source = "response.maxTriggerSz")
    @Mapping(target = "externalMaxStopSize", source = "response.maxStopSz")
    @Mapping(target = "externalMaxLeverage", source = "response.lever")
    @Mapping(target = "externalState", source = "response.state")
    @Mapping(target = "externalFeeGroupId", source = "response.groupId")
    @Mapping(target = "externalPositionTiers", source = "tiers")
    InstrumentExternalRulesExternalSnapshot integrationToSnapshot(InstrumentOkxResponse response,
                                                                  List<PositionTierOkxResponse> tiers);

    /** Позиционный тир площадки → граничный снапшот тира: сырые строки один к одному. */
    @Mapping(target = "externalMinSize", source = "minSz")
    @Mapping(target = "externalMaxSize", source = "maxSz")
    @Mapping(target = "externalMaintenanceMarginRate", source = "mmr")
    PositionTierExternalSnapshot integrationToSnapshot(PositionTierOkxResponse tier);

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
    @Mapping(target = "positionTiers", source = "snapshot.externalPositionTiers")
    InstrumentExternalRules snapshotToDomain(InstrumentExternalRulesExternalSnapshot snapshot, Long instrumentId);

    /**
     * Снапшот тира → доменный тир: строки площадки разбираются в числа.
     * Непустоту гарантирует валидация читателя; неразбираемое число
     * отвергает сеть разбора шлюза как нарушение контракта.
     */
    @Mapping(target = "minSize", source = "externalMinSize")
    @Mapping(target = "maxSize", source = "externalMaxSize")
    @Mapping(target = "maintenanceMarginRate", source = "externalMaintenanceMarginRate")
    PositionTier snapshotToDomain(PositionTierExternalSnapshot tier);

    /**
     * Перевод без ключа навеса.
     *
     * <p>Числовой ключ базы границу сервиса не переходит: у коннектора
     * базы нет, а ключ принадлежит владельцу инструмента. Читатель
     * проставляет его сам, когда кладёт правила к себе.
     */
    default InstrumentExternalRules snapshotToDomain(InstrumentExternalRulesExternalSnapshot snapshot) {
        return snapshotToDomain(snapshot, null);
    }

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
