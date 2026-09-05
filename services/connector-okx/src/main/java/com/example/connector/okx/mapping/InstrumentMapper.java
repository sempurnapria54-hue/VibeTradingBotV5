package com.example.connector.okx.mapping;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.connector.okx.snapshot.InstrumentExternalSnapshot;
import com.example.connector.okx.integration.model.okx.response.InstrumentOkxResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Маппинг инструмента между слоями (docs/models/mapping/Instrument.md):
 * integration DTO OKX → граничный {@link InstrumentExternalSnapshot},
 * затем частичное обновление доменного {@link Instrument} из снапшота
 * (идентичность + биржевые externalStatus/externalLeverage; шаг 1).
 * Наружу — internalId (свой и exchangeInternalId), не id из БД.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstrumentMapper {

    @Mapping(target = "externalInstrumentId", source = "instId")
    @Mapping(target = "externalInstrumentType", source = "instType")
    @Mapping(target = "externalStatus", source = "state")
    @Mapping(target = "externalLeverage", source = "lever")
    @Mapping(target = "externalBaseCurrency", source = "baseCcy")
    @Mapping(target = "externalQuoteCurrency", source = "quoteCcy")
    @Mapping(target = "externalSettleCurrency", source = "settleCcy")
    @Mapping(target = "externalLotSize", source = "lotSz")
    @Mapping(target = "externalMinSize", source = "minSz")
    @Mapping(target = "externalContractValue", source = "ctVal")
    @Mapping(target = "externalContractMultiplier", source = "ctMult")
    @Mapping(target = "externalTickSize", source = "tickSz")
    InstrumentExternalSnapshot integrationToSnapshot(InstrumentOkxResponse response);

    /**
     * Частичное обновление инструмента из снапшота: идентичность +
     * биржевые статус/плечо + валюты инструмента. {@code externalStatus} /
     * {@code externalLeverage} совпадают по имени; {@code externalId} /
     * {@code externalType} — из снапшотных external*-полей; расчётная
     * валюта — операнд ветки чужой валюты движения
     * (docs/models/domain/core/Instrument.md). Прочие поля инструмента
     * (id, internalId, exchangeId, leverage, marginMode, ...) не
     * трогаются.
     */
    @Mapping(target = "externalId", source = "externalInstrumentId")
    @Mapping(target = "externalType", source = "externalInstrumentType")
    @Mapping(target = "externalSettlementCurrency", source = "externalSettleCurrency")
    @Mapping(target = "externalBaseCurrency", source = "externalBaseCurrency")
    @Mapping(target = "externalQuoteCurrency", source = "externalQuoteCurrency")
    void snapshotToDomain(@MappingTarget Instrument instrument, InstrumentExternalSnapshot snapshot);

}
