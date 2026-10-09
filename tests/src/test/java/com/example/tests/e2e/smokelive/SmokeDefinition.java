package com.example.tests.e2e.smokelive;

/**
 * Определение стратегии дыма — тело {@code POST /api/v1/strategies} на паре
 * «счёт дыма × инструмент дыма» (.claude/tests/cases/smoke-live.md, кейсы
 * {@code E4.1}, {@code E4.2}, {@code E4.4}, {@code E5.1}).
 *
 * <p><b>Вход не зависит от направления рынка, и это достигнуто формой
 * определения, а не данными.</b> Фаза у ядра вычисляется на каждом тике, и
 * фаза {@code UNKNOWN} торговли не допускает; поэтому правило фазы одно и
 * истинно на всяком живом рынке — последняя цена больше нуля. Входное условие
 * — «открытой позиции нет». Индикаторов у определения нет вовсе: стоп входа
 * считается долей от цены входа ({@code ENTRY_PRICE_PERCENT}), и вход от
 * свежести индикаторов не зависит. Остаточная зависимость названа: правило
 * фазы читает цену площадки, и без неё фаза {@code UNKNOWN} — входа нет.
 *
 * <p><b>Размер задаёт не определение, а сайзинг:</b> малая доля аллокации
 * опускает расчётный размер ниже лота, и сайзинг поднимает его до минимального
 * лота инструмента ({@code SizeCalculator}: {@code floorTo(...).max(minSize)}).
 *
 * <p><b>Защита после наполнения — отдельная условная заявка</b> шага
 * {@code ENTRY_FINALIZED}: кейс {@code E5.2} ждёт её на площадке.
 */
final class SmokeDefinition {

    /** Доля, на которую неисполнимая лимитная заявка отстоит от последней цены вниз, %. */
    static final String RESTING_OFFSET_PERCENTS = "5";

    /**
     * Доля, на которую заявка {@code E4.4} отстоит от последней цены ВВЕРХ, %:
     * покупка выше верхней границы ценового бэнда площадки ({@code buyLmt}).
     * Ниже рынка бэнд покупку не ограничивает
     * (docs/integrations/okx/contracts/price-limit.md), поэтому заведомо
     * избыточная доля берётся в сторону, которую бэнд режет.
     */
    static final String BAND_EXCEEDING_PERCENTS = "50";

    private static final String TEMPLATE = """
            {
              "exchangeAccountInternalId": "%1$s",
              "instrumentInternalId": "%2$s",
              "name": "%3$s",
              "marketPhaseSetting": {
                "phaseRules": [
                  {
                    "type": "BULL_TREND",
                    "condition": {"rules": [{
                      "level": 1, "ruleType": "PRICE_COMPARE", "operator": "GT",
                      "leftOperand": {"sourceType": "PRICE"},
                      "rightOperand": {"sourceType": "CONSTANT", "valueType": "NUMBER", "value": "0"}
                    }]}
                  }
                ]
              },
              "indicatorSettings": [],
              "marketStructureSettings": [],
              "details": [
                {
                  "marketPhaseType": "BULL_TREND",
                  "phaseEntryPolicy": "FOLLOW_PHASE",
                  "riskPerActionPercent": 0.5,
                  "cumulativeRiskPerDealMultiplier": 2,
                  "strategySimultaneousRiskPerDealPercent": 1,
                  "tranches": [
                    {
                      "key": "smoke_main",
                      "levelCount": 1,
                      "positionReopenAllowed": false,
                      "stepsByStatus": {
                        "PRECHECK": [
                          {
                            "stepType": "ENTRY",
                            "condition": {"rules": [{"level": 1, "ruleType": "NO_OPEN_POSITION"}]},
                            "actions": [
                              {
                                "actionKind": "ORDER",
                                "key": "smoke_entry",
                                "actionType": "CREATE_ACTION",
                                "orderType": "ENTRY_ATTACHED_STOP_LOSS",
                                "direction": "LONG",
                                "allocationPercents": 0.01,
                                "positionReducingOnly": false,%4$s
                                "attachedProtection": {
                                  "attachedType": "ATTACHED_STOP_LOSS",
                                  "stopLossSettings": {"calculationType": "ENTRY_PRICE_PERCENT",
                                    "distancePercents": 2, "triggerPriceType": "MARK"}
                                }
                              }
                            ],
                            "marketDataExpiredSetting": {"protectedPositionAction": "BLOCK_STEP",
                              "unprotectedPositionAction": "BLOCK_STEP"}
                          }
                        ],
                        "ENTRY_FINALIZED": [
                          {
                            "stepType": "MAIN_PROTECTION",
                            "condition": {"rules": [{"level": 1, "ruleType": "POSITION_OPENED"}]},
                            "actions": [
                              {
                                "actionKind": "ALGO_ORDER",
                                "key": "smoke_main_sl",
                                "actionType": "CREATE_ACTION",
                                "conditionType": "STOP_LOSS",
                                "stopLossSettings": {"calculationType": "ENTRY_PRICE_PERCENT",
                                  "distancePercents": 2, "triggerPriceType": "MARK"},
                                "closeFractionPercents": 100,
                                "triggerPriceType": "MARK"
                              }
                            ],
                            "marketDataExpiredSetting": {"protectedPositionAction": "WAIT",
                              "unprotectedPositionAction": "GRACEFUL_CLOSE"}
                          }
                        ]
                      }
                    }
                  ]
                },
                {"marketPhaseType": "BEAR_TREND", "phaseEntryPolicy": "NO_TRADE"},
                {"marketPhaseType": "RANGE", "phaseEntryPolicy": "NO_TRADE"},
                {"marketPhaseType": "UNKNOWN", "phaseEntryPolicy": "NO_TRADE"}
              ]
            }
            """;

    private SmokeDefinition() {
    }

    /** Лимитный вход ниже рынка на {@link #RESTING_OFFSET_PERCENTS}: заявка стои́т в книге ({@code E4.2}). */
    static String resting(String account, String instrument) {
        return TEMPLATE.formatted(account, instrument, "smoke-live resting limit", placement("BELOW",
                RESTING_OFFSET_PERCENTS));
    }

    /** Лимитный вход выше верхней границы бэнда: площадка отвергает постановку ({@code E4.4}). */
    static String bandExceeding(String account, String instrument) {
        return TEMPLATE.formatted(account, instrument, "smoke-live band exceeding", placement("ABOVE",
                BAND_EXCEEDING_PERCENTS));
    }

    /** Рыночный вход — {@code placement} не объявлен ({@code E5.1}). */
    static String market(String account, String instrument) {
        return TEMPLATE.formatted(account, instrument, "smoke-live market entry", "");
    }

    private static String placement(String side, String percents) {
        return """

                                "placement": {"baseType": "MARKET_PRICE", "priceSource": "LAST_PRICE",
                                  "offsetSide": "%s", "percents": %s},""".formatted(side, percents);
    }
}
