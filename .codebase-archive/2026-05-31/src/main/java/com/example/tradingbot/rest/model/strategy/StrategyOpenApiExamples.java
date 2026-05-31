package com.example.tradingbot.rest.model.strategy;

public final class StrategyOpenApiExamples {

    public static final String CREATE_REQUEST = """
            {
              "instrumentId": 101,
              "name": "ETH SWAP Trend/Grid v3",
              "version": 3,
              "details": [
                {
                  "marketPhaseType": "BULL_TREND",
                  "phaseEntryPolicy": "FOLLOW_PHASE",
                  "riskPerTradePercent": 1.0,
                  "maxLeverage": 10,
                  "targetRiskRewardRatio": 3.0,
                  "stepsByStatus": {
                    "PRECHECK": [
                      {
                        "stepType": "ENTRY",
                        "condition": {
                          "rules": [
                            {
                              "level": 1,
                              "ruleType": "NO_OPEN_POSITION"
                            }
                          ]
                        },
                        "actions": [
                          {
                            "actionKind": "ORDER",
                            "actionType": "CREATE",
                            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
                            "direction": "LONG",
                            "allocationPercents": 100,
                            "level": 1,
                            "attachedProtection": {
                              "attachedType": "ATTACHED_STOP_LOSS",
                              "stopLossSettings": {
                                "calculationType": "ATR_PERCENT",
                                "distancePercents": 150,
                                "triggerPriceType": "MARK"
                              }
                            }
                          }
                        ]
                      }
                    ],
                    "ENTRY_FINALIZED": [
                      {
                        "stepType": "MAIN_PROTECTION",
                        "condition": {
                          "rules": [
                            {
                              "level": 1,
                              "ruleType": "POSITION_OPENED"
                            }
                          ]
                        },
                        "actions": [
                          {
                            "actionKind": "ALGO_ORDER",
                            "actionType": "CREATE",
                            "conditionType": "OCO_FULL",
                            "level": 1,
                            "stopLossSettings": {
                              "calculationType": "ATR_PERCENT",
                              "distancePercents": 150,
                              "triggerPriceType": "MARK"
                            },
                            "closeFractionPercents": 100,
                            "triggerProfitPercents": 3.0,
                            "triggerPriceType": "MARK"
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
            """;

    public static final String FULL_RESPONSE = """
            {
              "internalId": "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7",
              "instrumentId": 101,
              "name": "ETH SWAP Trend/Grid v3",
              "version": 3,
              "status": "ACTIVE",
              "details": [
                {
                  "marketPhaseType": "BULL_TREND",
                  "phaseEntryPolicy": "FOLLOW_PHASE",
                  "riskPerTradePercent": 1.0,
                  "maxLeverage": 10,
                  "targetRiskRewardRatio": 3.0,
                  "stepsByStatus": {
                    "PRECHECK": [
                      {
                        "stepType": "ENTRY",
                        "condition": {
                          "rules": [
                            {
                              "level": 1,
                              "ruleType": "NO_OPEN_POSITION"
                            }
                          ]
                        },
                        "actions": [
                          {
                            "actionKind": "ORDER",
                            "actionType": "CREATE",
                            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
                            "direction": "LONG",
                            "allocationPercents": 100,
                            "level": 1,
                            "attachedProtection": {
                              "attachedType": "ATTACHED_STOP_LOSS",
                              "stopLossSettings": {
                                "calculationType": "ATR_PERCENT",
                                "distancePercents": 150,
                                "triggerPriceType": "MARK"
                              }
                            }
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
            """;

    public static final String STATUS_RESPONSE = """
            {
              "internalId": "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7",
              "status": "ACTIVE"
            }
            """;

    private StrategyOpenApiExamples() {
    }
}
