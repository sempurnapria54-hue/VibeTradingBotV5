# Подготовка рыночных данных (market-data-calculation)

## На какой вопрос отвечает этот файл

Как устроен процесс подготовки рыночных данных: какие jobs, в какой
последовательности, по какой цепочке зависимостей, и где результаты
используются.

## Главная идея

Индикаторы, структура рынка, фаза рынка и внешние правила инструмента
готовятся **отдельно** от жизненного цикла сделки. FSM и калькуляторы не
считают эти данные сами — jobs готовят их заранее, потребители читают
готовые результаты.

Процесс — поставщик данных для `docs/processes/deal-management.md` (см.
условие 1/2 в `.claude/decisions/process-materialization-criterion.md`).

## Jobs и последовательность

```text
CandleJob
  -> обновляет свечи
InstrumentExternalRulesSyncJob
  -> обновляет внешние правила инструмента из REST
IndicatorJob
  -> считает IndicatorValue
MarketStructureJob
  -> считает MarketStructure / MarketPriceLevel
MarketPhaseJob
  -> считает MarketPhase
EntryScannerJob / DealOrchestratorJob (FSM)
  -> используют готовые данные (уже зона deal-management)
```

Компоненты: `docs/components/CandleJob.md`,
`InstrumentExternalRulesSyncJob.md`, `IndicatorJob.md`,
`MarketStructureJob.md`, `MarketPhaseJob.md`.

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
активируется только после backfill/warmup (механизм backfill —
форвард-заметка backlog п.8).
