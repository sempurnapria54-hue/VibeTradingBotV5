package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.request.CreateInstrumentApiRequest;
import com.example.tradingbot.api.model.response.InstrumentApiResponse;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.integration.model.okx.response.InstrumentOkxResponse;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
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
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = CandleGroupMapper.class)
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
     * биржевые статус/плечо (шаг 1). {@code externalStatus} /
     * {@code externalLeverage} совпадают по имени; {@code externalId} /
     * {@code externalType} — из снапшотных external*-полей. Прочие поля
     * инструмента (id, internalId, exchangeId, leverage, marginMode, ...)
     * не трогаются.
     */
    @Mapping(target = "externalId", source = "externalInstrumentId")
    @Mapping(target = "externalType", source = "externalInstrumentType")
    void snapshotToDomain(@MappingTarget Instrument instrument, InstrumentExternalSnapshot snapshot);

    @Mapping(target = "candleGroups", ignore = true)
    InstrumentEntity domainToPersistence(Instrument instrument);

    @Mapping(target = "candleGroups", ignore = true)
    Instrument persistenceToDomain(InstrumentEntity entity);

    /**
     * Entity → domain ВМЕСТЕ с группами свечей — для проверок, которым
     * нужны группы инструмента (например {@code isReadyForActivation}).
     * Группы мапятся через {@code CandleGroupMapper}; грузить их нужно
     * join fetch'ем, иначе коллекция не инициализирована.
     */
    Instrument persistenceToDomainWithCandleGroups(InstrumentEntity entity);

    /**
     * Api → domain при заведении: статус, биржевые поля и группы свечей
     * проставляются системой/онбордингом, не из запроса. {@code exchangeId}
     * резолвится сервисом из {@code exchangeInternalId} запроса (здесь не
     * маппится). marginMode — строка запроса → enum по имени.
     */
    Instrument apiToDomain(CreateInstrumentApiRequest request);

    @Mapping(target = "exchangeInternalId", source = "exchangeInternalId")
    InstrumentApiResponse domainToApi(Instrument instrument, String exchangeInternalId);
}
