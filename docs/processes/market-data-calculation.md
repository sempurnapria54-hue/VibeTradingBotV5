# Подготовка рыночных данных (market-data-calculation)

## На какой вопрос отвечает этот файл

Как устроен процесс вычисления производных рыночных данных (индикаторы /
структура / фаза) поверх загруженных свечей.

## Главная идея

Индикаторы, структура рынка и фаза рынка готовятся **отдельно** от
жизненного цикла сделки. FSM и калькуляторы не считают эти данные
сами — jobs готовят их заранее, потребители читают готовые
результаты. (Подготовка спеков инструмента — `InstrumentExternalRules`
через `InstrumentSyncJob` — в оркестрацию рыночных данных не входит,
см. ниже.)

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
EntryScannerJob / DealOrchestratorJob (FSM)
  -> используют готовые данные (уже зона deal-management);
     фазу получают через MarketPhaseService (вычисляется на лету)
```

`InstrumentSyncJob` (каталог и спеки инструмента) в активную
оркестрацию рыночных данных **не входит**: он ведёт каталог и
`InstrumentExternalRules` отдельным контуром
(`docs/components/InstrumentSyncJob.md`,
`docs/models/domain/other/InstrumentExternalRules.md`).

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
  -> MarketPhaseService (на чтение) -> MarketPhase (не персистится)
```

## Условия запуска и инварианты

- Все расчёты — только по закрытым свечам, без look-ahead.
- Jobs идемпотентны (уникальность по `instrument` + **идентичность
  вычисления** (`indicator_config_id` / `market_structure_config_id`) +
  candle/window timestamp — `docs/models/domain/other/IndicatorValue.md`
  §«Ключевание — идентичностью вычисления»; checkpoint **производный** —
  `max(timestamp)` по таблице результатов на (инструмент + идентичность),
  отдельного состояния не храним). Фаза job'ом не считается (вычисляется
  на чтение), идемпотентность к ней неприменима.
- **Что считать, определяет требование потребителя, а не стратегия
  напрямую.** Расчёт идёт по идентичностям, которые кто-то заказал
  (`docs/architecture/market-data-collection.md` §«Как потребность
  доходит до сбора»); статуса стратегий сервис рыночных данных не видит —
  снятие стратегии убирает её требование, а не строки результата, на
  которых стои́т чужой бэктест.
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
после warmup, структуру и фазу. Если нет — стратегия не активируется либо
активируется только после backfill/warmup (загрузка свечной
истории — процесс `docs/processes/candle-loading.md`).

- **Владелец проверки на шаге 3 не материализуется.** Активационная
  проверка — потребитель сервисов этого шага, но сам её исполнитель
  появляется по линии activate-валидации (см.
  `docs/rules/strategy-validation.md`: линия реза
  create = структура / activate = готовность-к-запуску). Семантика
  activate (422) отложена до зрелости поздних шагов (4/7); назначать
  владельца проверки на шаге 3 — спекуляция (нет текущего потребителя).
- **Пункт «есть ли актуальные `InstrumentExternalRules`» — не на шаге 3**:
  модель материализуется на шаге 5 (риск-преконтроль), вне расчёта
  рыночных данных; до её материализации активация её не требует.
