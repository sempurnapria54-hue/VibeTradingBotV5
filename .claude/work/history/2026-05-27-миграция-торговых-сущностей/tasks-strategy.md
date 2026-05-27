# Локальные вопросы: миграция Strategy

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности Strategy
(immutable strategy-tree).

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/Strategy.md`
(+ `Strategy API examples.md`). `Strategy` — не биржевая сущность,
OKX mapping не нужен. Весь strategy-tree — разделы внутри
`docs/models/core/Strategy.md` по `model-granularity.md`. Стратегия:
парковать cross-cutting, создавать владение Strategy.

## Форвард-заметки (мигрируются с владельцем)

- **STR-FW1. Расчётные jobs.** `EntryScannerJob` (поиск входа, выбор
  `StrategyDetail` по `MarketPhase.Type`), `MarketPhaseJob`,
  `IndicatorJob`, `MarketStructureJob`. Источники — `Жизненный цикл
  сделки.md`, `Расчёт индикаторов и рыночных данных.md`. Компоненты/
  процессы, отдельная миграция.

- **STR-FW2. Калькуляторы (Калькуляторы действий стратегии).**
  `StrategyActionCalculator` (собирает свежий `CalculationContext` на
  каждый action), `PriceCalculator`, `SizeCalculator` (включая
  `closeFractionPercents`/`allocationPercents` → размер),
  `StrategyConditionEvaluator`. RVO: `CalculationContext`,
  `MarketPriceData`, `CalculatedStrategyAction`,
  `InstrumentExternalRules` → `docs/components/models/`. Источник —
  `Калькуляторы действий стратегии.md`. Консолидирует ORD-Q6.

- **STR-FW3. Risk-layer.** `RiskValidator` (после расчёта action, до
  торговой команды; не перед read-only), `RiskCheckResult`/
  `RiskCheckCode`/`RiskDecision` (RVO/енумы), `RiskBlockResolver`
  (реакция FSM на `BLOCKED`). Источник — `Оценка рисков.md`.
  Консолидирует BAL-Q2, POS-Q3, ORD-Q4, ALGO-Q5, DEAL-FW6.

- **STR-FW4. `MarketDataExpirationChecker` (checker).** Runtime-
  проверка свежести по `expirationDuration` settings;
  `Strategy.Status` не меняет. Источник — `Расчёт индикаторов...`.
  Компонент, отдельная миграция.

- **STR-FW5. `ServiceCommandFactory` + command-подсистема.**
  Превращает `CalculatedStrategyAction` в атомарные `ServiceCommand`.
  Связь `targetActionKey → target StrategyAction → DealActionState →
  RuntimeTarget → ServiceCommand`. Источник — `Сервисные команды.md`.
  Консолидирует command-форвард-заметки всех сущностей; кластер Deal
  management (DEAL-FW2, DEAL-FW4).

- **STR-FW6. Модели рыночных данных (`docs/models/other/` или
  отдельный кластер).** `MarketPhase` (+ `Type`), `MarketStructure`
  (+ `Type`), `MarketPriceLevel`, `IndicatorValue` (+ `Type`). На них
  ссылаются настройки strategy-tree. Источник — `Расчёт
  индикаторов...`. Отдельная миграция (timeseries/market-data).

- **STR-FW7. `TimeFrameMapper` (OKX timeframe).** Доменный `TimeFrame`
  зафиксирован в `Strategy.md`; маппинг OKX-строк (`1H` ↔ `ONE_HOUR`)
  — `TimeFrameMapper` в client/adapter. Мигрируется с
  candles/calc-кластером (`docs/client/okx/rules/` + компонент).

- **STR-FW8. Валидатор стратегии (компонент/процесс).** 12-пунктная
  валидация (key/targetActionKey/CLOSE_FULL/partial-exit и т.д.)
  зафиксирована как правила в `Strategy.md`; сам валидатор —
  компонент/процесс (`.claude/decisions/rule-source-of-truth.md`:
  валидация → процесс/компонент-валидатор). Отдельная миграция.

- **STR-FW9. `Strategy API examples.md` (JSON-примеры).** Отдельный
  файл иллюстративных JSON. Производный от модели; мигрируется как
  reference/пример при необходимости (тип «reference»/`docs/` —
  уточнить при миграции, возможно не воспроизводить как файл знания
  по аналогии с derived-материалом). Также `api/API стратегии.md` и
  `api/Справочник по API сервиса.md` — API-кластер, отдельно.

- **STR-FW10. `Strategy.INACTIVE`/`DELETED` runtime-резолвинг.**
  Статусная семантика — в `docs/lifecycles/Strategy.md`. Тройная
  развилка B3 (`rule-source-of-truth.md`): enforcement в
  `EntryScannerJob` (блок новых) и FSM/lifecycle Deal (graceful
  shutdown открытых) — компоненты, мигрируются с jobs (STR-FW1) и
  кластером Deal management.

## Открытые вопросы

Открытых вопросов, требующих решения, по Strategy нет. Все развилки —
форвард-заметки на отдельные кластеры (jobs / calc / risk / commands /
market-data / api). §37 (JSON-примеры) и §36 (загрузка из БД,
`@EntityGraph`/`JOIN FETCH`) — реализационные детали; в `Strategy.md`
зафиксированы как инварианты модели (immutable, объектные связи,
pinned detail), детали загрузки — реализация, не доменное знание.
