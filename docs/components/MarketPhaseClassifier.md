# MarketPhaseClassifier

## На какой вопрос отвечает этот файл

Кто переводит авторские правила фазы в `MarketPhase.Type` (компонент):
что делает, на каких данных, границы.

## Назначение

`MarketPhaseClassifier` — доменный компонент, исполняющий авторские
правила определения фазы (`StrategyMarketPhaseSetting.phaseRules`) и
возвращающий `MarketPhase.Type`. Заменяет прежний скоринговый алгоритм
фазы (см. `docs/decisions/market-phase-conditional-classification.md`).
Используется `docs/components/MarketPhaseJob.md`; сам данные по свечам не
считает и не персистит.

## Контракт

```
classify(
    phaseRules,          // List<StrategyMarketPhaseRule>, упорядочены по level
    evaluationContext    // готовые IndicatorValue / MarketStructure / MarketPriceData
) -> (type: MarketPhase.Type, confirmedAt: OffsetDateTime)
```

- **First-match по `level` ASC.** Для каждой клаузы истинность её
  `condition` спрашивается у `docs/components/StrategyConditionEvaluator.md`;
  первая истинная клауза задаёт `type`. Не сработала ни одна → `UNKNOWN`
  (консервативный дефолт).
- **`confirmedAt` — производный от операндов сработавшей клаузы.**
  Консервативный `max` по гейт-операндам матча: структурный операнд → его
  `confirmedAt`, индикаторный → `candleTimestamp`; клауза без
  гейт-операндов → `candleTimestamp` бара оценки. Это гейт «без
  look-ahead» (прежний скоринговый `confirmationBars` распущен редизайном),
  отдельного состояния не вводит — согласуется со stateless-контрактом.
  Точная арифметика — `CODE`. См.
  `docs/models/domain/other/MarketPhase.md` §Деривация `confirmedAt`.
- **Stateless.** Решение — функция только от `phaseRules` и текущего
  `evaluationContext`; история прошлых фаз не читается. Отдельного
  фаза-уровневого дебаунса нет — анти-whipsaw операнд-уровневый
  (сглаживающие периоды индикаторов, переиспользуемые по `key`;
  структурный `breakoutConfirmationBars`).
- **Контекст без сделки.** `evaluationContext` не содержит
  `Position`/`Order`-фактов: контекстный whitelist `phaseRules`
  (операнды `INDICATOR`/`MARKET_STRUCTURE`/`PRICE`/`CONSTANT`/`TIME`, без
  `MARKET_PHASE` и runtime-источников сделки — см.
  `docs/models/domain/aggregate/Strategy.md` §StrategyMarketPhaseRule)
  гарантирует, что deal-контекст не требуется.

## Границы

- Не считает индикаторы/структуру по свечам — читает готовые результаты
  (см. `docs/processes/market-data-calculation.md`).
- Не персистит `MarketPhase` — это `MarketPhaseJob`.
- Истинность условий не вычисляет сам — делегирует
  `StrategyConditionEvaluator` (переиспользование грамматики условий, не
  второй движок). Сигнатура контекст-объекта evaluator под фазу
  уточняется при реализации (`CODE`).
