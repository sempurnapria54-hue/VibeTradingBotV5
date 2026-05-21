# Strategy API examples

Ниже вынесены JSONC-примеры для strategy-layer.

Это `JSONC`, а не строгий JSON.
Комментарии `//` нужны только для чтения.

---

# 1. Что изменено относительно старых примеров

1. В `StrategyPricePlacement` поле `marketPriceType` заменено на `priceSource`.
2. `StopLossSettings.calculationType` трактуется как enum `StopLossCalculationType`:

    * `ENTRY_PRICE_PERCENT`
    * `ATR_PERCENT`
    * `MARKET_STRUCTURE_BUFFER_PERCENT`
3. `StrategyConditionRule` расширен:

    * `timeframe`
    * `sourceType`
    * `leftOperand`
    * `operator`
    * `rightOperand`
    * `params`
4. Для простых доменных правил можно по-прежнему указывать только:

    * `level`
    * `ruleType`
    * `percents`, если он нужен.
5. `level` остаётся в strategy action, но не переносится в `Order` / `AlgoOrder` как runtime-role.
6. Runtime-связь `StrategyAction -> Order / AlgoOrder / Position` выполняется через `DealActionState`.
7. `Order`, `AlgoOrder`, `Position` не хранят `strategyActionId`.

---

# 2. BULL_TREND / FOLLOW_PHASE

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
        // Входные условия также может предварительно проверять EntryScannerJob
        // до создания Deal.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "NO_OPEN_POSITION"
              // По инструменту нет открытой позиции.
            },
            {
              "level": 2,
              "ruleType": "MARKET_PHASE_IS",
              "sourceType": "MARKET_PHASE",
              "leftOperand": "MARKET_PHASE",
              "operator": "EQ",
              "rightOperand": {
                "sourceType": "CONSTANT",
                "valueType": "ENUM",
                "name": "BULL_TREND",
                "stringValue": "BULL_TREND"
              }
              // Фаза рынка должна быть BULL_TREND.
            },
            {
              "level": 3,
              "ruleType": "INDICATOR_COMPARE",
              "timeframe": "15m",
              "sourceType": "INDICATOR",
              "leftOperand": "EMA_FAST",
              "operator": "GT",
              "rightOperand": {
                "sourceType": "INDICATOR",
                "valueType": "INDICATOR_VALUE",
                "name": "EMA_SLOW"
              },
              "params": {
                "fastPeriod": 10,
                "slowPeriod": 50
              }
              // EMA fast выше EMA slow на 15m.
            },
            {
              "level": 4,
              "ruleType": "INDICATOR_COMPARE",
              "timeframe": "5m",
              "sourceType": "INDICATOR",
              "leftOperand": "RSI_14",
              "operator": "GTE",
              "rightOperand": {
                "sourceType": "CONSTANT",
                "valueType": "NUMBER",
                "numberValue": 50
              }
              // RSI подтверждает long-сценарий.
            },
            {
              "level": 5,
              "ruleType": "CANDLE_CLOSED",
              "timeframe": "5m",
              "sourceType": "TIME",
              "operator": "IS_TRUE"
              // Используем закрытую свечу, чтобы не ловить look-ahead.
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            // JSON discriminator: это StrategyOrderAction.

            "actionType": "CREATE",
            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
            // Реальный Order.Type: входной ордер с attached SL.

            "direction": "LONG",
            // Нормализованное направление strategy-layer.
            // Runtime mapper / command factory потом превратит его в buy/long.

            "allocationPercents": 100,
            // Берём 100% от объёма, который посчитает SizeCalculator.

            "level": 1,
            // Уровень action внутри стратегии.
            // Не переносится в Order / AlgoOrder как runtime-role.

            "placement": null,
            // Market-like вход.
            // PriceCalculator рассчитает reference price в runtime.

            "attachedProtection": {
              "attachedType": "ATTACHED_STOP_LOSS",

              "stopLossSettings": {
                "calculationType": "ATR_PERCENT",
                // StopLossCalculationType.ATR_PERCENT

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
        // После подтверждения позиции создаём основную standalone-защиту.

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
            // JSON discriminator: это StrategyAlgoOrderAction.

            "actionType": "CREATE",
            "conditionType": "OCO_FULL",
            // Основная защита = OCO: SL + TP.

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
        // После +1% переносим stop-loss ближе к безубытку.

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
            // JSON discriminator: это StrategyPositionAction.

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

# 3. BEAR_TREND / FOLLOW_PHASE с partial exit лесенкой

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
            },
            {
              "level": 2,
              "ruleType": "MARKET_PHASE_IS",
              "sourceType": "MARKET_PHASE",
              "leftOperand": "MARKET_PHASE",
              "operator": "EQ",
              "rightOperand": {
                "sourceType": "CONSTANT",
                "valueType": "ENUM",
                "name": "BEAR_TREND",
                "stringValue": "BEAR_TREND"
              }
            },
            {
              "level": 3,
              "ruleType": "INDICATOR_COMPARE",
              "timeframe": "15m",
              "sourceType": "INDICATOR",
              "leftOperand": "EMA_FAST",
              "operator": "LT",
              "rightOperand": {
                "sourceType": "INDICATOR",
                "valueType": "INDICATOR_VALUE",
                "name": "EMA_SLOW"
              },
              "params": {
                "fastPeriod": 10,
                "slowPeriod": 50
              }
              // EMA fast ниже EMA slow на 15m.
            },
            {
              "level": 4,
              "ruleType": "INDICATOR_COMPARE",
              "timeframe": "5m",
              "sourceType": "INDICATOR",
              "leftOperand": "RSI_14",
              "operator": "LTE",
              "rightOperand": {
                "sourceType": "CONSTANT",
                "valueType": "NUMBER",
                "numberValue": 50
              }
              // RSI подтверждает short-сценарий.
            },
            {
              "level": 5,
              "ruleType": "CANDLE_CLOSED",
              "timeframe": "5m",
              "sourceType": "TIME",
              "operator": "IS_TRUE"
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
            // Сначала ставим базовый standalone stop-loss без OCO.

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
        // Здесь сразу создаём TP1 / TP2 / TP3.

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

---

# 4. RANGE / GRID

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
            },
            {
              "level": 2,
              "ruleType": "MARKET_PHASE_IS",
              "sourceType": "MARKET_PHASE",
              "leftOperand": "MARKET_PHASE",
              "operator": "EQ",
              "rightOperand": {
                "sourceType": "CONSTANT",
                "valueType": "ENUM",
                "name": "RANGE",
                "stringValue": "RANGE"
              }
            },
            {
              "level": 3,
              "ruleType": "PRICE_COMPARE",
              "timeframe": "5m",
              "sourceType": "PRICE",
              "leftOperand": "MARK_PRICE",
              "operator": "BETWEEN",
              "rightOperand": {
                "sourceType": "MARKET_STRUCTURE",
                "valueType": "MARKET_STRUCTURE_LEVEL",
                "name": "RANGE_LOW_RANGE_HIGH"
              },
              "params": {
                "lowerBound": "RANGE_LOW",
                "upperBound": "RANGE_HIGH"
              }
              // Mark price находится внутри диапазона.
            },
            {
              "level": 4,
              "ruleType": "CANDLE_CLOSED",
              "timeframe": "5m",
              "sourceType": "TIME",
              "operator": "IS_TRUE"
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
              "priceSource": null,
              "offsetSide": "ABOVE",
              "percents": 10
            },
            // Первый long grid-level = +10% от ширины range выше нижней границы.

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
              "priceSource": null,
              "offsetSide": "ABOVE",
              "percents": 30
            },
            // Второй long grid-level = +30% от ширины range выше нижней границы.

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
              "priceSource": null,
              "offsetSide": "BELOW",
              "percents": 30
            },
            // Первый short grid-level = -30% от ширины range ниже верхней границы.

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
              "priceSource": null,
              "offsetSide": "BELOW",
              "percents": 10
            },
            // Второй short grid-level = -10% от ширины range ниже верхней границы.

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
        // Runtime-сущности для отмены определяются через DealActionState,
        // а не через placement как основной идентификатор.

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
            "placement": null,
            // Для CANCEL placement не обязателен.
            // Целевая runtime-сущность определяется через DealActionState.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": null,
            "level": 2,
            "placement": null,
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 3,
            "placement": null,
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 4,
            "placement": null,
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

# 5. Итог по смыслу

Эти примеры фиксируют следующее:

* стратегия хранит торговые правила;
* шаги сгруппированы по `Deal.Status`;
* один step = одно condition + пакет actions;
* `StrategyConditionRule` может быть простым доменным правилом или гибким правилом по индикаторам/цене/фазе;
* `level` нужен у condition rules и strategy actions;
* `level` не переносится в `Order` / `AlgoOrder` как runtime-role;
* grid выражается несколькими `StrategyOrderAction` с разным `StrategyPricePlacement`;
* partial exits выражаются несколькими `StrategyAlgoOrderAction`;
* breakeven и exit by efficiency — отдельные steps;
* runtime-связь action -> entity хранится в `DealActionState`;
* аудит не является источником runtime-логики FSM.
