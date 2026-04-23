# Strategy API examples

Ниже вынесены JSONC-примеры для strategy-layer.

# 15. Три финальных JSONC-примера

Ниже `JSONC`, а не строгий JSON.
Комментарии `//` нужны только для чтения.

## 15.1 BULL_TREND / FOLLOW_PHASE

```jsonc
{
  "marketPhaseType": "BULL_TREND",
  // Деталь действует только в бычьей фазе рынка.

  "phaseEntryPolicy": "FOLLOW_PHASE",
  // Работаем по тренду: ищем long-сценарий.

  "riskPerTradePercent": 1.0,
  // Максимум 1% риска на сделку.

  "maxLeverage": 10,
  // Верхний лимит плеча.

  "targetRiskRewardRatio": 3.0,
  // High-level ориентир стратегии: 1:3.

  "stepsByStatus": {
    "PRECHECK": [
      {
        "stepType": "ENTRY",
        // На PRECHECK готовим входной ордер со страховочным attached SL.

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
            // Это StrategyOrderAction.

            "actionType": "CREATE",
            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
            // Реальный Order.Type: входной ордер с attached SL.

            "direction": "LONG",
            // Нормализованное торговое направление strategy-layer.

            "allocationPercents": 100,
            // Берём 100% от объёма, который уже посчитал PositionCalculator.

            "level": 1,

            "placement": null,
            // Для такого входа цену может определить отдельный price resolver.

            "attachedProtection": {
              "attachedType": "ATTACHED_STOP_LOSS",
              // Attached protection на входе.

              "stopLossSettings": {
                "calculationType": "ATR_PERCENT",
                "distancePercents": 150,
                // 150% ATR = 1.5 * ATR.

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
        // После подтверждения позиции создаём основную защиту.

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
            // Это StrategyAlgoOrderAction.

            "actionType": "CREATE",
            "conditionType": "OCO_FULL",
            // Основная защита = OCO (SL + TP).

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ATR_PERCENT",
              "distancePercents": 150,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,

            "closeFractionPercents": 100,
            // Закрываем 100% позиции.

            "triggerProfitPercents": 3.0,
            // TP на +3% от entry.

            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +1% переносим защиту ближе к безубытку.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 1.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "AMEND",
            "conditionType": "STOP_LOSS",
            // Обновляем stop-loss до BE-подобного уровня.

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ENTRY_PRICE_PERCENT",
              "distancePercents": 0.10,
              // Новый stop = около entry с буфером 0.10%.

              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +2% включаем trailing.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 2.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "TRAILING_PERCENTS",
            // Создаём / включаем trailing-защиту.

            "level": 2,

            "stopLossSettings": null,

            "trailingSettings": {
              "activationProfitPercents": 2.0,
              "callbackPercents": 0.70,
              "activationBufferPercents": 0.10
            },

            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "EXIT",
        // Если тренд сломался, стратегия закрывает позицию.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "TREND_CHANGED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "POSITION",
            // Это StrategyPositionAction.

            "actionType": "CLOSE_FULL",
            "level": 1,
            "closeFractionPercents": 100
          }
        ]
      }
    ]
  }
}
```

## 15.2 BEAR_TREND / FOLLOW_PHASE с partial exit лесенкой

```jsonc
{
  "marketPhaseType": "BEAR_TREND",
  // Деталь действует только в медвежьей фазе рынка.

  "phaseEntryPolicy": "FOLLOW_PHASE",
  // Работаем по тренду: short-сценарий.

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
            "direction": "SHORT",
            "allocationPercents": 100,
            "level": 1,
            "placement": null,

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
            "conditionType": "STOP_LOSS",
            // Сначала ставим базовый стоп без OCO.

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ATR_PERCENT",
              "distancePercents": 150,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PARTIAL_EXIT",
        // Один общий condition -> пакет действий.
        // Здесь сразу создаём TP1/TP2/TP3.

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
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 1,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 1.0,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 2,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 2.0,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 3,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 50,
            "triggerProfitPercents": 3.0,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +1% подтягиваем stop-loss ближе к entry.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 1.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "AMEND",
            "conditionType": "STOP_LOSS",
            "level": 4,

            "stopLossSettings": {
              "calculationType": "ENTRY_PRICE_PERCENT",
              "distancePercents": 0.10,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ]
  }
}
```

## 15.3 RANGE / GRID

```jsonc
{
  "marketPhaseType": "RANGE",
  // Деталь действует только во флэте.

  "phaseEntryPolicy": "GRID",
  // Внутри RANGE работаем сеткой.

  "riskPerTradePercent": 0.6,
  // Для grid риск ниже, чем в тренде.

  "maxLeverage": 4,
  // И сильнее ограничиваем плечо.

  "targetRiskRewardRatio": 1.5,

  "stepsByStatus": {
    "PRECHECK": [
      {
        "stepType": "GRID_ENTRY",
        // Один шаг с общим condition создаёт сразу 4 лимитных входа.

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
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": 25,
            "level": 1,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 10
            },
            // Первый long grid-level = +10% от нижней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": 25,
            "level": 2,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 30
            },
            // Второй long grid-level = +30% от нижней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": 25,
            "level": 3,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 30
            },
            // Первый short grid-level = -30% от верхней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": 25,
            "level": 4,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 10
            },
            // Второй short grid-level = -10% от верхней границы range.

            "attachedProtection": null
          }
        ]
      }
    ],

    "ENTRY_FINALIZED": [
      {
        "stepType": "MAIN_PROTECTION",
        // После появления basket-позиции ставим общий защитный stop-loss.

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
            "conditionType": "STOP_LOSS",
            "level": 1,

            "stopLossSettings": {
              "calculationType": "MARKET_STRUCTURE_BUFFER_PERCENT",
              "distancePercents": 20,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PARTIAL_EXIT",
        // Во флэте можно заранее выставить лестницу частичного выхода.

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
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 1,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 0.50,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 2,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 1.00,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 3,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 50,
            "triggerProfitPercents": 1.50,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "GRID_MANAGEMENT",
        // Если диапазон сломан — снимаем все grid-входы.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "RANGE_BREAKOUT_CONFIRMED",
              "percents": 15
              // Breakout подтверждён с буфером 15% ширины диапазона.
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": null,
            "level": 1,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 10
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": null,
            "level": 2,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 30
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 3,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 30
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 4,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 10
            },
            "attachedProtection": null
          }
        ]
      },

      {
        "stepType": "EXIT",
        // При смене режима рынка закрываем basket-позицию.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "TREND_CHANGED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "POSITION",
            "actionType": "CLOSE_FULL",
            "level": 1,
            "closeFractionPercents": 100
          }
        ]
      }
    ]
  }
}
```

---

# 16. Итог по смыслу

Эта модель сейчас фиксирует следующее:

* стратегия хранит **торговые правила**;
* шаги сгруппированы по `Deal.Status`, а не живут отдельной осью статусов;
* один шаг = одно condition + пакет действий;
* `level` нужен у `StrategyConditionRule` и у `StrategyAction`, но не у самого step;
* grid выражается не через отдельный `gridSettings`, а через несколько `StrategyOrderAction` с разным `StrategyPricePlacement`;
* partial exits выражаются несколькими `StrategyAlgoOrderAction` в одном шаге;
* breakeven и exit by efficiency — это отдельные steps, а не settings-объекты.
