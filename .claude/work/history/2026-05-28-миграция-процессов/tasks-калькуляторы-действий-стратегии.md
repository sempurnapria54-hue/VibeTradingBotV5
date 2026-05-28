# Локальные вопросы: миграция «Калькуляторы действий стратегии»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Калькуляторы действий стратегии»
(локальные вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **КЛ-Q1. Противоречие по `PositionContext`.** Модель `CalculationContext`
  (§4) содержит поле `private PositionContext positionContext;` и
  отдельно `private Position activePosition;`. Но «Жизненный цикл сделки»
  §5.3 явно исключает `PositionContext` (максимум одна `Position` на
  `Deal`). Существует ли `PositionContext` как самостоятельный RVO или
  это рудимент — решить. Материализация под вопросом. Зеркальная заметка
  в `tasks-жизненный-цикл-сделки.md` (ЖЦ-Q2). Кандидат в общий открытый
  вопрос.
- **КЛ-Q2. Процесс расчёта vs только компоненты.** Док описывает и flow
  («как считаются параметры action»), и исполнителей
  (`StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator`,
  `CalculationContextFactory`). Развилка: завести
  `docs/processes/<strategy-action-calculation>.md` для flow + компоненты,
  либо обойтись компонентами + процессом «Жизненный цикл сделки». Решить
  на проходе 2.
- **КЛ-Q3. `RiskSettings` — материализация под вопросом.** Упомянут как
  поле `CalculationContext` и вход `RiskValidator`, но нигде не описан
  детально (структура неизвестна). Возможно — часть `StrategyDetail`
  (riskPerTradePercent/maxLeverage) или отдельный RVO. Только
  name-level — на проходе 2 уточнить, заводить ли артефакт.

## Форвард-заметки

- **КЛ-FW1.** §3/§3.1 — primary source для RVO: `CalculatedStrategyAction`,
  `StrategyActionCalculationResult` (wrapper SUCCESS/ERROR). §3.2 —
  `CalculationError` (+ `CalculationErrorType` TEMPORARY/PERMANENT) +
  политика TEMPORARY→RETRY_PENDING / PERMANENT→FAILED→Deal ERROR.
- **КЛ-FW2.** §4 — полная модель `CalculationContext` (RVO) — primary
  source (в «Жизненном цикле» §6 только краткая). §4.1 «один action =
  один свежий CalculationContext», §4.2 balance freshness boundary.
- **КЛ-FW3.** §6 — `InstrumentExternalRules` (persisted Auditable-модель,
  + enum Status LIVE/SUSPEND/PREOPEN/EXPIRED/TEST/UNKNOWN). Дублируется в
  «Расчёт индикаторов…» §6.2 (там в контексте sync-job). Выбрать primary
  (модель — здесь и там идентична; sync-job — в «Расчёт…»).
- **КЛ-FW4.** §7 — `MarketPriceData` (+ `MarketPriceDataExternalSnapshot`)
  — RVO, не persisted. Дублируется в «Расчёт…» §7. Backlog п.3 →
  `docs/components/models/`.
- **КЛ-FW5.** §8–10 — `IndicatorValue`, `MarketStructure`/
  `MarketPriceLevel`, `MarketPhase` — persisted market-data модели.
  Primary — «Расчёт индикаторов и рыночных данных» (полнее: + params,
  settings, jobs). Здесь — в контексте чтения калькулятором.
- **КЛ-FW6.** §11–17 — `PriceCalculator` + `CalculatedPrice` (+ `PriceMode`,
  `StrategyPricePurpose`, `StrategyPriceSource`, `ResolvedStopLossPrice`/
  `ResolvedTakeProfitPrice`/`ResolvedTrailingPrice`, `PriceRoundingPolicy`),
  формулы SL/TP/trailing/limit, округление по tickSize, таблица
  источник↔тип цены. Primary для price-калькулятора и его RVO.
- **КЛ-FW7.** §18 — `SizeCalculator` + `CalculatedSize` (+ `SizeMode`),
  `closeFraction`, расчёт контрактов через ctVal/lotSz/minSz. Primary.
- **КЛ-FW8.** §19/§20 — связь calculator↔risk-layer (калькулятор НЕ
  возвращает `RiskValidationResult`/`CalculatedRiskMetrics`), ошибки
  расчёта vs risk-policy. Пересекается с «Оценка рисков». Не дублировать.
- **КЛ-FW9.** Поле `explanation` помечено legacy → будущее `description`/
  `comment` (в `CalculatedStrategyAction`, `CalculatedPrice`,
  `CalculatedSize`). Зафиксировать унификацию имени на проходе 2.
- **КЛ-FW10.** §5 — сервисы-источники данных (`MarketPriceDataService`,
  `InstrumentExternalRulesService`, `IndicatorService`,
  `MarketStructureService`, `MarketPhaseService`, `RiskSettingsService`,
  `CalculationContextFactory`) — компоненты.
