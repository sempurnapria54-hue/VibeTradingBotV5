# Локальные вопросы: миграция «Расчёт индикаторов и рыночных данных»

## На какой вопрос отвечает этот файл

Что неясно по миграции архивного дока «Расчёт индикаторов и рыночных
данных» (локальные вопросы и форвард-заметки прохода 1).

## Открытые вопросы

- **РИ-Q1. Размещение `TimeFrame` (доменный enum).** §8 — чистый
  доменный enum (ONE_MINUTE…ONE_DAY), OKX-строк не хранит. Развилка:
  `docs/models/other/` (как самостоятельная модель-enum), словарная
  статья `docs/dictionary/`, либо раздел внутри market-data модели.
  `TimeFrameMapper` (OKX↔domain) — отдельно в `docs/client/okx/` (backlog
  п.5). Решить на проходе 2.
- **РИ-Q2. market-data модели: core или other.** `IndicatorValue`,
  `MarketStructure`, `MarketPriceLevel`, `MarketPhase`,
  `InstrumentExternalRules` — persisted (Auditable), но не торговые
  бизнес-сущности сделки. По `models-core-vs-other.md` → `docs/models/other/`.
  Подтвердить на проходе 2 (особенно `InstrumentExternalRules` — внешние
  правила инструмента).
- **РИ-Q3. Strategy settings: расширение `Strategy.md` или отдельные
  файлы.** `StrategyIndicatorSetting`, `StrategyMarketStructureSetting`,
  `StrategyMarketPhaseSetting`, `IndicatorParams` (+ subclasses),
  `MarketStructureParams`, `MarketPhaseParams` — immutable-настройки,
  «живут вместе со стратегией» (§4.2). Развилка: разделы/вложенные модели
  внутри уже мигрированного `docs/models/core/Strategy.md` vs отдельные
  файлы. Решить на проходе 2.
- **РИ-Q4. Размещение `*ExternalSnapshot` (boundary DTO).**
  `MarketPriceDataExternalSnapshot`, `InstrumentExternalRulesExternalSnapshot`
  — выход маппера из client-модели. RVO/boundary в
  `docs/components/models/` vs `docs/client/okx/models/`. Связано с
  `raw-exchange-dto-boundary.md`. Решить на проходе 2.

## Форвард-заметки

- **РИ-FW1.** §3 — общая схема jobs (CandleJob → InstrumentExternalRulesSyncJob
  → IndicatorJob → MarketStructureJob → MarketPhaseJob → EntryScannerJob →
  DealOrchestratorJob/FSM) + цепочка зависимостей данных. Primary source
  для процесса `docs/processes/<market-data-calculation>.md`.
- **РИ-FW2.** Jobs-компоненты: `CandleJob` (§5),
  `InstrumentExternalRulesSyncJob` (§6), `IndicatorJob` (§9),
  `MarketStructureJob` (§14), `MarketPhaseJob` (§20). Primary source.
- **РИ-FW3.** Сервисы-компоненты: `IndicatorService` (§13),
  `MarketStructureService` (§19), `MarketPhaseService` (§24),
  `MarketDataExpirationChecker` (§25, + RVO `MarketDataExpirationResult`
  со Status NOT_EXPIRED/PARTIALLY_EXPIRED/EXPIRED/MISSING). Primary.
  `MarketDataExpirationChecker` — checker, статус стратегии не меняет
  (backlog п.5).
- **РИ-FW4.** Persisted-модели (§6/§12/§17/§18/§23): `InstrumentExternalRules`
  (+ Status/InstrumentType/ContractType), `IndicatorValue` (abstract +
  Atr/Ema/Rsi/Macd/BollingerBands/Stochastic/Obv + Type),
  `MarketStructure` (+ Type), `MarketPriceLevel` (+ Type), `MarketPhase`
  (+ Type). Primary source. Уникальности (§31): UNIQUE по instrument +
  setting + candle/window timestamp.
- **РИ-FW5.** Strategy settings + params (§10/§11/§15/§16/§21/§22) —
  immutable, привязаны к `Strategy`. Расширение `Strategy.md`. См. РИ-Q3.
- **РИ-FW6.** §8 `TimeFrame` + `TimeFrameMapper` (OKX «1H»↔ONE_HOUR,
  строгий mapping) → `docs/client/okx/rules/<okx-timeframe-mapping>.md`.
- **РИ-FW7.** §32 «Активация стратегии и готовность данных» (backfill/
  warmup перед активацией) — связано с lifecycle/валидатором `Strategy`
  (backlog п.8). §34 (Q2–Q8 дополнение) — дублирует risk/calc/error
  материал; не воспроизводить.
- **РИ-FW8.** `Strategy.Status` enforcement в jobs (ACTIVE/INACTIVE/
  DELETED не расширяется свежестью данных; jobs не меняют Status) —
  расширение `lifecycles/Strategy.md` + сквозное правило (тройная
  развилка B3, `rule-source-of-truth.md`).
