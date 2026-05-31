package com.example.tradingbot.mapping;

import com.example.tradingbot.api.model.request.CreateInstrumentApiRequest;
import com.example.tradingbot.api.model.response.InstrumentApiResponse;
import com.example.tradingbot.client.model.okx.response.InstrumentResponse;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Маппинг инструмента между слоями (docs/models/mapping/Instrument.md):
 * client DTO OKX → граничный {@link InstrumentExternalSnapshot}, затем
 * частичное обновление доменного {@link Instrument} из снапшота
 * (идентичность + биржевые externalStatus/externalLeverage; шаг 1).
 */
@Mapper(componentModel = "spring")
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
    InstrumentExternalSnapshot clientToSnapshot(InstrumentResponse response);

    /**
     * Частичное обновление инструмента из снапшота: только поля шага 1
     * (идентичность + биржевые статус/плечо). Прочие поля инструмента
     * (id, internalId, exchangeId, leverage, marginMode, ...) не
     * трогаются.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalId", source = "externalInstrumentId")
    @Mapping(target = "externalType", source = "externalInstrumentType")
    @Mapping(target = "externalStatus", source = "externalStatus")
    @Mapping(target = "externalLeverage", source = "externalLeverage")
    void updateFromSnapshot(@MappingTarget Instrument instrument, InstrumentExternalSnapshot snapshot);

    /**
     * Domain → entity. {@code candleGroups} не траверсируется маппером —
     * группами управляет {@code CandleGroupDataService} (агрегат
     * cascade всё равно объявлен на entity).
     */
    @Mapping(target = "candleGroups", ignore = true)
    InstrumentEntity domainToEntity(Instrument instrument);

    @Mapping(target = "candleGroups", ignore = true)
    Instrument entityToDomain(InstrumentEntity entity);

    /**
     * Api → domain при заведении: статус, биржевые поля и группы свечей
     * проставляются системой/онбордингом, не из запроса. marginMode —
     * строка запроса → enum по имени.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "internalId", source = "internalId")
    @Mapping(target = "exchangeId", source = "exchangeId")
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "externalType", source = "externalType")
    @Mapping(target = "marginMode", source = "marginMode")
    @Mapping(target = "externalMarginMode", source = "externalMarginMode")
    @Mapping(target = "leverage", source = "leverage")
    @Mapping(target = "plannedCandleStartDate", source = "plannedCandleStartDate")
    Instrument apiToDomain(CreateInstrumentApiRequest request);

    InstrumentApiResponse domainToApi(Instrument instrument);
}
