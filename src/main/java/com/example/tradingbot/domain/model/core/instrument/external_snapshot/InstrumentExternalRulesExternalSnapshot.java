package com.example.tradingbot.domain.model.core.instrument.external_snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот внешних правил инструмента —
 * выход маппера из client-модели биржи до материализации
 * {@code InstrumentExternalRules}. Несёт сырые {@code external*}-строки
 * спецификации (без доменных enum'ов instrumentType/contractType/status —
 * они резолвятся уже при материализации). Сырой OKX DTO за
 * IntegrationService/adapter не выходит. См.
 * docs/models/mapping/InstrumentExternalRules.md,
 * docs/rules/raw-exchange-dto-boundary.md.
 */
@Value
@Builder
public class InstrumentExternalRulesExternalSnapshot {

    /** Сырой тип инструмента биржи (OKX instType). */
    String externalInstrumentType;

    /** Имя инструмента на бирже (OKX instId). */
    String externalInstrumentId;

    /** Сырой тип контракта (OKX ctType). */
    String externalContractType;

    /** Стоимость контракта (OKX ctVal). */
    String externalContractValue;

    /** Валюта стоимости контракта (OKX ctValCcy). */
    String externalContractValueCurrency;

    /** Шаг цены (OKX tickSz). */
    String externalTickSize;

    /** Шаг размера (OKX lotSz). */
    String externalLotSize;

    /** Минимальный размер ордера (OKX minSz). */
    String externalMinSize;

    /** Максимальный размер limit-ордера (OKX maxLmtSz). */
    String externalMaxLimitSize;

    /** Максимальный размер market-ордера (OKX maxMktSz). */
    String externalMaxMarketSize;

    /** Максимальный размер trigger-ордера (OKX maxTriggerSz). */
    String externalMaxTriggerSize;

    /** Максимальный размер stop-ордера (OKX maxStopSz). */
    String externalMaxStopSize;

    /** Биржевой максимум плеча инструмента (OKX lever). */
    String externalMaxLeverage;

    /** Сырой статус инструмента биржи (OKX state). */
    String externalState;
}
