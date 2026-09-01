package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO OKX спецификации инструмента (GET /api/v5/public/instruments).
 * Несёт только snapshot-релевантное подмножество полей; все приходят
 * строками. Не выходит за IntegrationService/adapter
 * (docs/models/integrations/okx/InstrumentOkxResponse.md).
 */
@Getter
@Setter
public class InstrumentOkxResponse {

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

    /** Валюта стоимости контракта (ctValCcy). */
    private String ctValCcy;

    /** Множитель контракта (ctMult, decimal-строка). */
    private String ctMult;

    /**
     * Идентификатор комиссионной группы инструмента (groupId) — КЛЮЧ
     * резолва ставки: пара (instType, groupId). Сама ставка приходит
     * отдельным эндпоинтом и живёт в своей модели.
     */
    private String groupId;

    /** Тип контракта (ctType): linear/inverse. */
    private String ctType;

    /** Шаг цены (tickSz, decimal-строка). */
    private String tickSz;

    /** Максимальный размер limit-ордера (maxLmtSz, decimal-строка). */
    private String maxLmtSz;

    /** Максимальный размер market-ордера (maxMktSz, decimal-строка). */
    private String maxMktSz;

    /** Максимальный размер trigger-ордера (maxTriggerSz, decimal-строка). */
    private String maxTriggerSz;

    /** Максимальный размер stop-ордера (maxStopSz, decimal-строка). */
    private String maxStopSz;

    /** Биржевой статус инструмента (state). */
    private String state;

    /** Биржевое плечо (lever). */
    private String lever;
}
