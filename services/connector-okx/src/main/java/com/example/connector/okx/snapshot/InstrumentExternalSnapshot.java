package com.example.connector.okx.snapshot;

import lombok.Builder;
import lombok.Value;

/**
 * Нормализованный граничный снапшот инструмента — единственное, что
 * выходит за ClientService/adapter при онбординге (шаг 1). Несёт
 * идентичность и биржевые externalStatus/externalLeverage (персистятся
 * на Instrument), плюс справочные sizing-поля спецификации
 * (транзиентны: в шаге 1 персистентного дома нет, INSTR-Q1). Все поля
 * сырые (String), доменные нормализации резолвятся при материализации.
 * См. docs/models/mapping/Instrument.md,
 * docs/rules/raw-exchange-dto-boundary.md.
 */
@Value
@Builder
public class InstrumentExternalSnapshot {

    /** Имя инструмента на бирже (OKX instId). */
    String externalInstrumentId;

    /** Тип инструмента на бирже (OKX instType). */
    String externalInstrumentType;

    /** Биржевой статус инструмента (OKX state). */
    String externalStatus;

    /** Биржевое плечо (OKX lever). */
    String externalLeverage;

    /** Базовая валюта (OKX baseCcy) — транзиентна в шаге 1. */
    String externalBaseCurrency;

    /** Котируемая валюта (OKX quoteCcy) — транзиентна в шаге 1. */
    String externalQuoteCurrency;

    /** Валюта расчётов (OKX settleCcy) — транзиентна в шаге 1. */
    String externalSettleCurrency;

    /** Размер лота (OKX lotSz) — транзиентен в шаге 1. */
    String externalLotSize;

    /** Минимальный размер ордера (OKX minSz) — транзиентен в шаге 1. */
    String externalMinSize;

    /** Стоимость контракта (OKX ctVal) — транзиентна в шаге 1. */
    String externalContractValue;

    /** Множитель контракта (OKX ctMult) — транзиентен в шаге 1. */
    String externalContractMultiplier;

    /** Шаг цены (OKX tickSz) — транзиентен в шаге 1. */
    String externalTickSize;
}
