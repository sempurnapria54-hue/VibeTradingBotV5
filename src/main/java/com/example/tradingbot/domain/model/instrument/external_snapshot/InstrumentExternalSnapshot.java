package com.example.tradingbot.domain.model.instrument.external_snapshot;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Внешний снапшот инструмента, полученный из OKX REST GET /public/instruments.
 */
@Getter
@Setter
public class InstrumentExternalSnapshot {

    /**
     * Идентификатор инструмента на бирже (instId), например ETH-USDT-SWAP.
     */
    private String externalInstrumentId;

    /**
     * Тип инструмента на бирже (instType): SPOT/MARGIN/SWAP/FUTURES/OPTION.
     */
    private String externalInstrumentType;

    /**
     * Базовая валюта инструмента (baseCcy), например ETH.
     */
    private String baseCurrency;

    /**
     * Котируемая валюта (quoteCcy), например USDT.
     */
    private String quoteCurrency;

    /**
     * Валюта расчётов по инструменту (settleCcy).
     */
    private String settleCurrency;

    /**
     * Шаг количества (lotSz) в терминах биржи.
     */
    private BigDecimal lotSize;

    /**
     * Минимальный размер ордера (minSz).
     */
    private BigDecimal minimumOrderSize;

    /**
     * Стоимость контракта (ctVal), актуально для деривативов.
     */
    private BigDecimal contractValue;

    /**
     * Мультипликатор контракта (ctMult), актуально для деривативов.
     */
    private BigDecimal contractMultiplier;

    /**
     * Шаг цены (tickSz).
     */
    private BigDecimal priceTickSize;
}
