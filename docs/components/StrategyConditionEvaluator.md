# StrategyConditionEvaluator

## На какой вопрос отвечает этот файл

Кто проверяет применимость `StrategyCondition`.

## Назначение

`StrategyConditionEvaluator` проверяет, выполнено ли `StrategyCondition`
(применим ли `StrategyStep`) на готовых рыночных данных. Используется
`EntryScannerJob` (вход) и FSM handler'ами (steps в открытой сделке).
Индикаторы и структуру по свечам **сам не считает** — читает готовые
результаты (см. `docs/processes/market-data-calculation.md`).

## Данные

Готовые `IndicatorValue`, `MarketStructure`, `MarketPhase`,
`MarketPriceData`, `Position` facts. Примеры:

```text
RANGE_BREAKOUT_CONFIRMED -> MarketStructure + MarketPriceData
TREND_CHANGED            -> IndicatorValue + MarketPhase
PROFIT/LOSS_PERCENTS_REACHED -> Position.avgPrice + MarketPriceData
```

Привязка `TREND_CHANGED → MarketPhase` — **entry/deal-контекст**. В
контексте классификации фазы `TREND_CHANGED` не используется: whitelist
`StrategyMarketPhaseRule` запрещает и `MARKET_PHASE` (само-референция), и
сам `TREND_CHANGED` (темпоральное несовместимо со stateless-классификатором
— см. `docs/models/domain/aggregate/Strategy.md`).

## Границы

Freshness нужных данных проверяется до evaluator'а
(`MarketDataExpirationChecker.checkForStep`, правило —
`docs/rules/market-data-freshness.md`). Evaluator только отвечает
true/false по правилам condition (структура `StrategyCondition` /
`StrategyConditionRule` — `docs/models/domain/aggregate/Strategy.md`); решение о
применении step и выборе action принимает FSM handler.
