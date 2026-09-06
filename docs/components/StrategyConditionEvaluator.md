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

**Предикаты правил, читающих факты сделки, определены не здесь.** Форма —
`docs/spec/deal-condition.json`: открытая позиция, финализированный вход,
наличие встроенной и основной защиты, пороги прибыли и убытка. Единица
порогов — **проценты хода от цены входа**, и объявлена она домом настроек
трейлинга (`docs/models/domain/aggregate/Strategy.md`); плечо в неё не
входит.

**Whitelist контекста выражен пустотой операндов, а не перечнем типов.**
Классификация фазы собирает контекст без эпизода, транша и фазы входа —
правила, читающие их, оказываются на пустом операнде и консервативно
ложны. Второй перечень разрешённых типов разошёлся бы с этим различением
при добавлении первого же типа.

**Оба предиката фазы определены, и определены не здесь.** Форма —
`docs/spec/market-phase-condition.json`, дом смысла —
`docs/models/domain/other/MarketPhase.md`. Темпоральный операнд `TREND_CHANGED` берётся у
**сделки** (`Deal.entryMarketPhase`), а не у истории фазы: истории у фазы
нет по построению, и именно поэтому предикат прежде не имел определения
ни в одном носителе.

## Границы

Свежесть нужных данных проверяется до evaluator'а, и **своего предиката
свежести у ядра нет** — он был бы вторым ответом на вопрос, на который уже
ответил владелец данных. Владелец отдаёт **пустое место** там, где
значение устарело либо не собрано, и оба состояния ведут к одной реакции
(`docs/spec/market-data-freshness.json`, величина `freshnessState`).

**Носитель гейта у ядра — «операнды шага покрыты снятой раскладкой»**
(`MarketFeatures.covers(condition)`): условие, чей операнд в раскладке
отсутствует, до оценки не доходит. Форма ровно та, какая нужна: «устарело»
и «нет» неотличимы по построению, и различать их ядру нечем и незачем
(`docs/rules/market-data-freshness.md`). Evaluator только отвечает
true/false по правилам condition (структура `StrategyCondition` /
`StrategyConditionRule` — `docs/models/domain/aggregate/Strategy.md`); решение о
применении step и выборе action принимает FSM handler.
