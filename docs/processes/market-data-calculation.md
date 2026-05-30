# Подготовка рыночных данных (market-data-calculation)

## На какой вопрос отвечает этот файл

Как устроен процесс вычисления производных рыночных данных
(индикаторы / структура / фаза) поверх загруженных свечей: какие
jobs, в какой последовательности, по какой цепочке зависимостей, и
где результаты используются.

## Главная идея

Индикаторы, структура рынка и фаза рынка готовятся **отдельно** от
жизненного цикла сделки. FSM и калькуляторы не считают эти данные
сами — jobs готовят их заранее, потребители читают готовые
результаты. (Подготовка спеков инструмента — `InstrumentExternalRules`
через `InstrumentExternalRulesSyncJob` — в шаге 1 отложена, см. ниже.)

Свечи этот процесс **не добывает** — он вычисляет поверх уже
загруженных свечей; их добыча и целостность — отдельный процесс
`docs/processes/candle-loading.md` (поставщик). Сам
`market-data-calculation` — поставщик производных данных для
`docs/processes/deal-management.md` (см. условие 1/2 в
`.claude/decisions/process-materialization-criterion.md`).

## Jobs и последовательность

```text
candle-loading
  -> поставляет загруженные свечи (docs/processes/candle-loading.md)
IndicatorJob
  -> считает IndicatorValue
MarketStructureJob
  -> считает MarketStructure / MarketPriceLevel
MarketPhaseJob
  -> считает MarketPhase
EntryScannerJob / DealOrchestratorJob (FSM)
  -> используют готовые данные (уже зона deal-management)
```

Компоненты: `docs/components/IndicatorJob.md`,
`MarketStructureJob.md`, `MarketPhaseJob.md`. Загрузку свечей ведёт
`docs/components/CandleJob.md` в процессе
`docs/processes/candle-loading.md`.

`InstrumentExternalRulesSyncJob` (подготовка спеков инструмента) в
активную оркестрацию шага 1 **не входит**: он готовит
`InstrumentExternalRules` — модель, отложенную за пределы шага 1
(backlog п.9 / отложенная rules-подсистема), и материализуется
вместе с правилами на поздних шагах
(`docs/components/InstrumentExternalRulesSyncJob.md`).

## Свечи как вход

Свечная история (добыча, целостность по count, онбординг
инструмента) — процесс `docs/processes/candle-loading.md`. Сюда
свечи приходят уже загруженными; этот процесс их только
потребляет. Инвариант «только закрытые свечи, без look-ahead»
обеспечивается на стороне загрузки.

## Цепочка зависимостей данных

```text
Candles
  -> IndicatorJob -> IndicatorValue

Candles + optional IndicatorValue
  -> MarketStructureJob -> MarketStructure -> MarketPriceLevel

IndicatorValue + MarketStructure
  -> MarketPhaseJob -> MarketPhase
```

## Условия запуска и инварианты

- Все расчёты — только по закрытым свечам, без look-ahead.
- Jobs идемпотентны (уникальность по instrument + setting +
  candle/window timestamp; checkpoint по последнему timestamp).
- Jobs **не** меняют `Strategy.Status`; для `DELETED`-стратегий новые
  данные не считаются (правило — `docs/rules/market-data-freshness.md`).
- При отсутствии свежих входных данных job не создаёт новый result;
  старый постепенно становится expired (свежесть проверяет
  `docs/components/MarketDataExpirationChecker.md`).

## Где используются результаты

```text
EntryScannerJob          -> можно ли создать Deal
StrategyConditionEvaluator -> можно ли выполнить step в открытой сделке
StrategyActionCalculator -> расчёт цены / размера
```

Раздача готовых данных потребителям — через сервисы
`docs/components/IndicatorService.md`, `MarketStructureService.md`,
`MarketPhaseService.md`, `MarketPriceDataService.md`.

## Активация стратегии и готовность данных

Перед активацией стратегии проверяется, что нужные данные могут быть
подготовлены: хватает ли свечной истории, можно ли рассчитать индикаторы
после warmup, структуру и фазу, есть ли актуальные
`InstrumentExternalRules`. Если нет — стратегия не активируется либо
активируется только после backfill/warmup (загрузка свечной
истории — процесс `docs/processes/candle-loading.md`).
