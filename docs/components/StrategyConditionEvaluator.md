# StrategyConditionEvaluator

## На какой вопрос отвечает этот файл

Кто проверяет применимость `StrategyCondition`.

## Назначение

`StrategyConditionEvaluator` проверяет, выполнено ли `StrategyCondition`
(применим ли `StrategyStep`) на готовых рыночных данных. Индикаторы и
структуру по свечам **сам не считает** — читает готовые результаты (см.
`docs/processes/market-data-calculation.md`).

**Живёт в общем артефакте `strategy-engine`, а не у одного потребителя.**
Грамматику условий читают трое: живая торговля и бэктест (`trading-core`,
`strategies`) и классификация фазы рынка (`market-data`) — перечень
потребителей ведёт `docs/architecture/services.md`. Копия грамматики у любого из них была бы вторым
носителем одной истины и разошлась бы с первым при первом же расширении
каталога условий (`.claude/rules/policy-home.md`).

**Контексты у потребителей разные, и это не деталь.** В контексте
классификации фазы доступны сравнивающие и структурно-событийные
`ruleType` и операнды `INDICATOR` / `MARKET_STRUCTURE` / `PRICE` /
`CONSTANT` / `TIME`; deal-контекст (факты `Position` / `Order`)
приезжает с кластером сделки, и такие `ruleType` вне его консервативно
ложны.

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

**Оба предиката фазы определены, и определены не здесь.** Форма —
`docs/spec/market-phase-condition.json`, дом смысла —
`docs/models/domain/other/MarketPhase.md`. Темпоральный операнд `TREND_CHANGED` берётся у
**сделки** (`Deal.entryMarketPhase`), а не у истории фазы: истории у фазы
нет по построению, и именно поэтому предикат прежде не имел определения
ни в одном носителе.

## Границы

Freshness нужных данных проверяется до evaluator'а
(`MarketDataExpirationChecker.stepDataFresh`, правило —
`docs/rules/market-data-freshness.md`). Evaluator только отвечает
true/false по правилам condition (структура `StrategyCondition` /
`StrategyConditionRule` — `docs/models/domain/aggregate/Strategy.md`); решение о
применении step и выборе action принимает FSM handler.
