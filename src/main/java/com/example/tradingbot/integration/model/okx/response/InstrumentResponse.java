package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO OKX спецификации инструмента (GET /api/v5/public/instruments).
 * Несёт только snapshot-релевантное подмножество полей; все приходят
 * строками. Не выходит за IntegrationService/adapter
 * (docs/models/integrations/okx/OkxInstrumentResponse.md).
 */
@Getter
@Setter
public class InstrumentResponse {

    /** Имя инструмента на бирже (instId). */
    private String instId;

    /** Тип инструмента (instType): SPOT/MARGIN/SWAP/FUTURES/OPTION. */
    private String instType;

    /** Базовая валюта (baseCcy). */
    private String baseCcy;

    /** Котируемая валюта (quoteCcy). */
    private String quoteCcy;

    /** Валюта расчётов (settleCcy). */
    private String settleCcy;

    /** Размер лота (lotSz, decimal-строка). */
    private String lotSz;

    /** Минимальный размер ордера (minSz, decimal-строка). */
    private String minSz;

    /** Стоимость контракта (ctVal, decimal-строка). */
    private String ctVal;

    /** Множитель контракта (ctMult, decimal-строка). */
    private String ctMult;

    /** Шаг цены (tickSz, decimal-строка). */
    private String tickSz;

    /** Биржевой статус инструмента (state). */
    private String state;

    /** Биржевое плечо (lever). */
    private String lever;
}
