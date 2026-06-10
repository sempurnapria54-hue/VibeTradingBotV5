# MarketPhaseResolver

## На какой вопрос отвечает этот файл

Кто резолвит авторские правила фазы в `MarketPhase.Type` (компонент):
что делает, на каких данных, границы.

## Назначение

`MarketPhaseResolver` — доменный компонент, исполняющий авторские
правила определения фазы (`StrategyMarketPhaseSetting.phaseRules`) и
возвращающий `MarketPhase.Type`. Заменяет прежний скоринговый алгоритм
фазы (см. `docs/decisions/market-phase-conditional-classification.md`).
Зовётся `docs/components/MarketPhaseService.md` **на чтение** (фаза
вычисляется на лету, не персистится — `docs/decisions/market-phase-stateless.md`;
прежний `MarketPhaseJob` удалён); сам данные по свечам не считает и не
персистит. Имя `Resolver` (не `Classifier`) — симметрия с
`MarketStructureResolver` и семантика «резолвится на лету».

## Контракт

```
resolve(
    phaseRules,          // List<StrategyMarketPhaseRule>, порядок = позиция в списке
    evaluationContext    // готовые IndicatorValue / MarketStructure / MarketPriceData
) -> type: MarketPhase.Type
```

- **First-match по позиции в списке.** Для каждой клаузы истинность её
  `condition` спрашивается у `docs/components/StrategyConditionEvaluator.md`;
  первая истинная клауза задаёт `type`. Не сработала ни одна → `UNKNOWN`
  (консервативный дефолт). Порядок — позиция клаузы в списке (поле `level`
  снято ревизией трек D, `docs/decisions/market-phase-stateless.md`).
- **`confirmedAt` у фазы больше нет.** Поле снято вместе с персистом фазы:
  резолвер возвращает только `type`. Гейт «без look-ahead» наследуется от
  входов — `evaluationContext` содержит результаты, посчитанные только по
  закрытым свечам (структура несёт свой `confirmedAt`, индикатор —
  `candleTimestamp`).
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
- Не персистит `MarketPhase` — фаза вообще не персистируется; результат
  резолвера возвращается `MarketPhaseService` вызывающему на чтение.
- Истинность условий не вычисляет сам — делегирует
  `StrategyConditionEvaluator` (переиспользование грамматики условий, не
  второй движок). Сигнатура контекст-объекта evaluator под фазу
  уточняется при реализации (`CODE`).
