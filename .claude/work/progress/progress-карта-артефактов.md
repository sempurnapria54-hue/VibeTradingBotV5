# Карта артефактов миграции процессов

## На какой вопрос отвечает этот файл

Какие артефакты создаются миграцией процессов, где они упомянуты полнее
всего, в каком порядке создаются на проходе 2.

## Источники

Обработаны все 8 доков из
`.claude-archive/2026-05-21/docs/domain/processes/` (включая вложенные
папки `Audit/`, `Calculation/`, `Deal management/`):

| Код | Архивный док | Путь |
|---|---|---|
| **ЖЦ** | Жизненный цикл сделки | `Deal management/Жизненный цикл сделки.md` |
| **FSM** | FSM этапы сделки | `Deal management/FSM этапы сделки.md` |
| **СК** | Сервисные команды | `Deal management/Сервисные команды.md` |
| **СТ** | Статусы торговых сущностей | `Deal management/Статусы торговых сущностей.md` |
| **КЛ** | Калькуляторы действий стратегии | `Calculation/Калькуляторы действий стратегии.md` |
| **ОР** | Оценка рисков | `Calculation/Оценка рисков.md` |
| **РИ** | Расчёт индикаторов и рыночных данных | `Calculation/Расчёт индикаторов и рыночных данных.md` |
| **АУ** | Аудит и история исполнения | `Audit/Аудит и история исполнения.md` |

На каждый док — пара `progress-<имя>.md` + `tasks-<имя>.md`
(`.claude/work/progress/` и `.claude/work/questions/tasks/`).

## Грузппировка

Артефакты сгруппированы **по типу** (процессы → компоненты → RVO →
persisted-модели → сквозные правила → правила биржи → расширения).
Обоснование: группировка по типу совмещает каждую группу с одним целевым
каталогом `docs/` и делает порядок создания на проходе 2 (по зависимостям
между типами: модели/RVO → компоненты → процессы/правила) очевидным.

Целевые имена файлов в `docs/` — английские (конвенция уже мигрированных
`docs/rules/`, `docs/client/okx/`); модели/RVO — PascalCase = Java-класс.
Имена помечены как кандидаты — финализируются на проходе 2.

---

## Артефакты

Полнота упоминания: **детально** (есть Java-модель/полный регламент) /
**средне** (описано, но без полной модели) / **вскользь** / **только
название**.

### Тип: продуктовый процесс (`docs/processes/`)

| Артефакт (кандидат-путь) | Упомянут в | Primary | Зависимости |
|---|---|---|---|
| **deal-lifecycle** (сопровождение сделки: jobs → EntryScanner → DealOpening → Orchestrator → FSM → Calculator → Command → Executor) | ЖЦ детально; FSM средне; СК/КЛ/ОР/РИ вскользь | **ЖЦ** §1–3 | компоненты Deal management, `DealContext`, lifecycle `Deal` (уже мигрирован — не дублировать). См. ЖЦ-Q1. |
| **market-data-calculation** (CandleJob → Indicator/Structure/Phase prep) | РИ детально; ЖЦ §3.1 средне; КЛ §5 вскользь | **РИ** §3 | jobs (Indicator/Structure/Phase/Candle/Rules), market-data модели, `MarketDataExpirationChecker` |
| **strategy-action-calculation** (flow: build CalculationContext → Price → Size) — *возможно сольётся с deal-lifecycle/компонентами* | КЛ детально; ЖЦ §3.6 средне; ОР вскользь | **КЛ** | `StrategyActionCalculator` + RVO. См. КЛ-Q2. |
| **risk-evaluation** (flow risk-layer) — *возможно только компоненты + правило* | ОР детально; FSM §13 / СК §18 / КЛ §19 средне | **ОР** | `RiskValidator`, `RiskBlockResolver`, RVO risk. См. ОР-Q2. |
| **audit-execution-history** (тонкий каркас) | АУ — рабочий каркас, не финализирован | **АУ** | мало; в основном открытые вопросы. См. АУ-Q1. |

### Тип: компонент (`docs/components/`)

Jobs и сервисы рыночных данных:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `CandleJob` | РИ §5 средне | РИ |
| `InstrumentExternalRulesSyncJob` | РИ §6 средне | РИ |
| `IndicatorJob` | РИ §9 детально; ЖЦ §3.1 средне | РИ |
| `MarketStructureJob` | РИ §14 детально | РИ |
| `MarketPhaseJob` | РИ §20 детально | РИ |
| `IndicatorService` | РИ §13 средне | РИ |
| `MarketStructureService` | РИ §19 средне | РИ |
| `MarketPhaseService` | РИ §24 средне | РИ |
| `MarketPriceDataService` | КЛ §5 вскользь | КЛ |
| `InstrumentExternalRulesService` | КЛ §5 вскользь | КЛ |
| `MarketDataExpirationChecker` | РИ §25 детально; ЖЦ/FSM/КЛ средне | РИ |

Deal management — оркестрация и FSM:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `EntryScannerJob` | ЖЦ §3.2 детально (12 шагов); РИ §26 средне; СТ матрица | ЖЦ |
| `DealOpeningService` | ЖЦ §3.3 детально | ЖЦ |
| `DealOrchestratorJob` | ЖЦ §3.4 средне; FSM execution boundary | ЖЦ |
| `DealStateMachine` (+ паттерн «3 типа проверок») | ЖЦ §3.5 средне; FSM §1/§13.1 детально | FSM |
| `DealContextService` | СТ §11 вскользь | СТ |
| FSM handlers per-status: `PrecheckHandler`, `EntrySubmittedHandler`, `EntryFinalizedHandler`, `ProtectionSwitchedHandler`, `ManagingHandler`, `ExitPendingHandler`, `ErrorHandler` | FSM детально (per-status §4–11); СТ §9 (ExitPending/Error cleanup) средне | FSM (см. `fsm-handler-as-component.md`) |
| `StrategyConditionEvaluator` | РИ §27 / FSM §2.3 средне; ЖЦ §3.1 вскользь | РИ/FSM |

Калькуляторы:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `StrategyActionCalculator` | КЛ §3 детально; ЖЦ §3.6 средне | КЛ |
| `CalculationContextFactory` | КЛ §3/§5 средне | КЛ |
| `PriceCalculator` | КЛ §11 детально | КЛ |
| `SizeCalculator` | КЛ §18 детально | КЛ |

Risk-layer:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `RiskValidator` | ОР §2.3/§3 детально; FSM/СК/КЛ/ЖЦ/СТ средне | ОР |
| `RiskBlockResolver` | ОР §6 детально | ОР |

Command-layer и executor'ы:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `ServiceCommandExecutor` | СК (контракты) детально | СК |
| `ServiceCommandFactory` | СК §5 детально; КЛ §21 / ЖЦ §14.6 средне | СК |
| `RetryPolicyService` | СК §11.5 средне | СК |
| `CreateOrderExecutor`, `SubmitOrderExecutor`, `RefreshOrderExecutor`, `AmendOrderExecutor`, `CancelOrderExecutor` | СК §13 детально | СК |
| `CreateAlgoOrderExecutor`, `SubmitAlgoOrderExecutor`, `RefreshAlgoOrderExecutor`, `AmendAlgoOrderExecutor`, `CancelAlgoOrderExecutor` | СК §13 детально | СК |
| `RefreshPositionExecutor` | СК §13.11 детально; СТ §8.5 | СК |
| `ClosePositionExecutor` | СК §13.12 детально; СТ §8.5 | СК |
| `RefreshFillsExecutor` | СК §13.13 детально; АУ §7.5 | СК |
| `RefreshBalanceExecutor` | СК §«REFRESH_BALANCE» детально | СК |
| Refresh-executor'ы под `REFRESH_PENDING_ORDERS` / `REFRESH_ALGO_ORDERS` / `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDER_HISTORY` | СК средне (без отдельных секций) | СК — *гранулярность под вопросом (СК-Q2)* |
| `FinalizeDealEntry`/`FinalizeDealExit`/`MarkDealClosed`/`MarkDealError` executors | СК §«FINALIZE_*» средне | СК — *материализация под вопросом, связано DEAL-Q1 (СК-Q4)* |
| `KillSwitchExecutor` (EXECUTE_KILL_SWITCH) | СТ §9.3 средне; ЖЦ §11 | СТ (backlog п.7) |
| `ClientService` (adapter boundary, nullable contract) | СК (контракты) детально | СК |

Resolver'ы (backlog п.2):

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `OrderExternalStatusResolver` (+ `OkxOrderExternalStatusResolver`) | СТ §5/§8.3 детально; СК §2.5 | СТ |
| `AlgoOrderExternalStatusResolver` (+ Okx…) | СТ §8.4 детально | СТ |
| `PositionStatusResolver` (+ Okx…) | СТ §8.5 детально; СК §13.11 | СТ |

Anomaly / safety:

| Артефакт | Упомянут в | Primary |
|---|---|---|
| `AnomalyJob` | ЖЦ §11 средне; СТ §10 детально | СТ |
| `ReconciliationJob` | ЖЦ §11 / lifecycle вскользь | ЖЦ — *только название* |

Mappers (backlog п.2; доменное существо уже в `docs/client/okx/rules/`):
`OrderMapper`, `PositionMapper`, `AlgoOrderMapper`, `BalanceContainerMapper`
— упомянуты вскользь; `docs/components/<X>.md` отложены, пересекаются с
уже мигрированными mapping-правилами OKX.

### Тип: runtime value object (`docs/components/models/`)

| Артефакт | Упомянут в | Primary | Зависимости |
|---|---|---|---|
| `DealContext` | ЖЦ §5 детально (полная модель) | **ЖЦ** | `Deal`, `Exchange`, `Instrument`, `StrategyDetail`, `BalanceContainer`, `DealActionState` |
| `CalculationContext` | КЛ §4 детально; ЖЦ §6 средне | **КЛ** | `DealContext`, `StrategyAction`, market-data, `InstrumentExternalRules` |
| `ServiceCommand` (+ `ServiceCommandType` enum) | СК §3/§4 детально; ЖЦ §8 средне | **СК** | `ServiceCommandPayload`, `DealActionState` |
| `ServiceCommandPayload` + subclasses (Create/Submit/Amend/Cancel Order/AlgoOrder, ClosePosition, AttachedProtection) | СК §10 детально | **СК** | `Order`/`AlgoOrder`/`Position` типы. *Гранулярность СК-Q3* |
| `CalculatedStrategyAction` | КЛ §3 детально | **КЛ** | `CalculatedPrice`, `CalculatedSize` |
| `StrategyActionCalculationResult` (SUCCESS/ERROR wrapper) | КЛ §3.1 детально | **КЛ** | `CalculatedStrategyAction`, `CalculationError` |
| `CalculationError` (+ `CalculationErrorType`) | КЛ §3.2 детально | **КЛ** | — |
| `CalculatedPrice` (+ `PriceMode`, `StrategyPricePurpose`, `StrategyPriceSource`, `ResolvedStopLossPrice`/`ResolvedTakeProfitPrice`/`ResolvedTrailingPrice`, `PriceRoundingPolicy`) | КЛ §12–17 детально | **КЛ** | `InstrumentExternalRules`, `MarketPriceData` |
| `CalculatedSize` (+ `SizeMode`) | КЛ §18 детально | **КЛ** | `CalculatedPrice`, `InstrumentExternalRules` |
| `RiskValidationResult` (+ `RiskDecision`) | ОР §4.1 детально | **ОР** | `RiskCheckResult` |
| `RiskCheckResult` (+ `RiskCheckStatus`, `RiskCheckCode`) | ОР §4.2/§5 детально | **ОР** | — |
| `RiskBlockAction` (+ Type) | ОР §7 детально | **ОР** | — |
| `MarketPriceData` (+ `MarketPriceDataExternalSnapshot`) | РИ §7 / КЛ §7 детально | **РИ** | — (не persisted) |
| `MarketDataExpirationResult` (+ Status) | РИ §25 детально | **РИ** | — |
| `PositionStatusResolveResult` (+ generic `EntityStatusResolveResult`/`StatusResolveResult`) | СТ §5.2/§8.5 детально; СК контракты | **СТ** | `Position.Status`/`CloseReason` |
| `RetryError` (+ `RetryErrorType` legacy), `ServiceCommandRetryPolicy` (+ `RetryBackoffType`), `RuntimeErrorCode`, `Retryable` (база) | СК §11 детально | **СК** | *размещение/legacy — СК-Q1; `Retryable`/`RetryError` — база persisted `DealActionState`* |
| `PositionContext` | КЛ §4 (поле) vs ЖЦ §5.3 (исключён) | — | **материализация под вопросом** (КЛ-Q1/ЖЦ-Q2) |
| `RiskSettings` | КЛ §4 / ОР §2.1 (поле/вход) | — | **материализация под вопросом, только name-level** (КЛ-Q3) |
| `InstrumentExternalRulesExternalSnapshot`, `BalanceContainerExternalSnapshot` | РИ §6.1 / СК §REFRESH_BALANCE | РИ/СК | boundary DTO — *размещение RVO vs client (РИ-Q4)* |

### Тип: persisted-модель (`docs/models/other/` — кандидат) и settings

| Артефакт | Упомянут в | Primary | Примечание |
|---|---|---|---|
| `DealActionState` (+ `DealActionStateStatus`, `RuntimeTarget`, `TargetEntityType`) | СК §6 детально; ЖЦ §7 детально | **СК** | persisted; core/other + own lifecycle — ЖЦ-Q3 |
| `InstrumentExternalRules` (+ Status, `InstrumentType`, `ContractType`) | РИ §6.2 / КЛ §6 детально | **РИ** | Auditable |
| `IndicatorValue` (abstract + Atr/Ema/Rsi/Macd/BollingerBands/Stochastic/Obv + Type) | РИ §12 детально; КЛ §8 | **РИ** | Auditable |
| `MarketStructure` (+ Type) | РИ §17 детально; КЛ §9 | **РИ** | Auditable |
| `MarketPriceLevel` (+ Type) | РИ §18 детально; КЛ §9 | **РИ** | вложенный в MarketStructure |
| `MarketPhase` (+ Type) | РИ §23 детально; КЛ §10 | **РИ** | Auditable |
| Strategy settings: `StrategyIndicatorSetting`(+Destiny), `StrategyMarketStructureSetting`(+Destiny), `StrategyMarketPhaseSetting`, `IndicatorParams`(+subclasses), `MarketStructureParams`, `MarketPhaseParams`(+AlgorithmType) | РИ §10/11/15/16/21/22 детально | **РИ** | immutable, расширение `Strategy.md` — РИ-Q3 |
| `TimeFrame` (доменный enum) | РИ §8 детально | **РИ** | размещение — РИ-Q1 |
| `TradeFill` / `TradeFillsArchive` | АУ/СК: «Fill не persisted на 1 этапе» | — | **материализация отложена** (АУ-Q3) vs backlog п.6 |
| `ServiceCommandExecutionHistory`, entity history models | АУ — не финализированы | — | **открытые вопросы** (АУ-Q1) |
| `Exchange`/`ExchangeAccount`/`Instrument` (полные модели) | СТ §7.1–7.2 средне | СТ | вне порядка 6 сущностей — СТ-Q2 (backlog п.9) |

### Тип: сквозное правило (`docs/rules/`)

Новые (кандидаты):

| Кандидат-правило | Упомянут в | Primary |
|---|---|---|
| `risk-validator-scope` (когда RiskValidator вызывается / нет) | ОР §3 / FSM §13.4 / СК §18.1 / КЛ §19 / ЖЦ §14.1 / СТ §6.8 — повсеместно | **ОР** (см. ОР-Q2) |
| `controlled-exchange-exceptions` (ExternalStatus/InvariantViolation/NotFound + реакция entity→ERROR / Deal→ERROR / Exchange→HOLD) | СК §2.5–2.6 / СТ §6 детально | **СТ** (пересечение с `external-status-resolution.md`) |
| `runtime-error-classification` (INTERNAL/EXCHANGE/VALIDATION; EXCHANGE_ERROR вместо UNKNOWN_RESULT/EXCHANGE_TIMEOUT; retryable-политика) | СК §11.4.1 / КЛ §20.1 / FSM §13.8-9 / АУ §7.2 | **СК** (RULE13) |
| `audit-not-runtime-source` (аудит/история не источник runtime-логики FSM) | АУ §1 / СК §2.3 / FSM / ЖЦ повсеместно | **АУ** |
| `command-lifecycle` (CREATE→SUBMIT→REFRESH; ServiceCommand не persisted queue; одна команда за проход) | СК §8/§6.1/§18.2 / ЖЦ §8 | **СК** — *vs раздел RVO `ServiceCommand`* |
| `trading-constraints` (OKX SWAP/FUTURES, isolated, свои средства, без borrow, ≤1 позиция/инструмент, лимиты плеча) | ЖЦ §12 детально | **ЖЦ** |
| `market-data-freshness` (expirationDuration; jobs не меняют Strategy.Status; data-dependent action не по устаревшим данным) | РИ §1/§25 / FSM §2.2 | **РИ** — *vs компонент `MarketDataExpirationChecker`* |

Уже мигрированные (только **расширение/дополнение**, не новый файл):

| Файл | Что добавится |
|---|---|
| `docs/rules/ack-not-runtime-truth.md` | ACK от submit/amend/cancel/close не truth (СК §CANCEL_*, СТ §12.4) |
| `docs/rules/no-partial-close.md` | механизм partial exit через reduce-only Order/AlgoOrder; DIRECT_PARTIAL_*_FORBIDDEN (СК §17, КЛ §инвариант) |
| `docs/rules/external-status-resolution.md` | resolver-result-object, unknown→exception, closeReason candidate не перетирается (СТ §5) |
| `docs/rules/exchange-hold.md` | что HOLD блокирует/разрешает (СТ §6.6–6.7), DISABLED |
| `docs/rules/raw-exchange-dto-boundary.md` | nullable contract ClientService; `*ExternalSnapshot` boundary; balance без normal null (СК §REFRESH) |

### Тип: правило биржи (`docs/client/okx/`)

| Кандидат | Упомянут в | Primary | Примечание |
|---|---|---|---|
| `okx-timeframe-mapping` (+ `TimeFrameMapper`) | РИ §8 детально | РИ | новый файл (backlog п.5) |
| `okx-instrument-mapping` (+ `OkxInstrumentResponse`? GET /public/instruments → InstrumentExternalRules; externalState) | РИ §6 детально | РИ | новый |
| OKX ticker → `MarketPriceDataExternalSnapshot` | КЛ §7 / РИ §7 | КЛ | новый или раздел |
| extends `okx-order-mapping.md` | external order status (live/partially_filled/filled/canceled/mmp_canceled); tdMode=isolated/posSide=net | СТ §8.3 / СК §10.1 | расширение |
| extends `okx-algo-order-mapping.md` | algo external status (live/pause/partially_effective/effective/order_failed/partially_failed) | СТ §8.4 | расширение |
| extends `okx-position-mapping.md` | REFRESH_POSITION endpoint, null=closed, autoCxl=true | СТ §8.5 / СК §13.11/§18.5 | расширение |
| extends `okx-balance-mapping.md` | BalanceContainerExternalSnapshot, settleCurrency обязательна | СК §REFRESH_BALANCE | расширение |

---

## Пересечения с уже мигрированными моделями

Расширения существующих файлов `docs/` (детали — в `tasks-*` соответствующих доков):

- **`docs/models/core/Deal.md`** — `entryReason` (STRATEGY/MANUAL/RECOVERY/
  UNKNOWN), `entryStepType` (ENTRY/GRID_ENTRY/null), closeReason значения
  (RISK_CONTROL, ENTRY_CONDITION_EXPIRED, STRATEGY_EXIT, TAKE_PROFIT,
  STOP_LOSS, LIQUIDATION…), runtime graph (orders/algoOrders/position),
  `resultProfit` через REFRESH_FILLS (правило). [ЖЦ §1.1/§4, СТ §8.1]
- **`docs/lifecycles/Deal.md`** — реакция на BLOCKED risk (PRECHECK→CLOSED/
  RISK_CONTROL; пост-live-risk→ERROR), recovery-переходы
  (ENTRY_SUBMITTED→EXIT_PENDING и т.д.), `FINALIZE_DEAL_ENTRY`. Бóльшая
  часть статусной механики **уже есть** — дополнять, не дублировать.
  [FSM, ЖЦ §9–10, ОР §8]
- **`docs/models/core/Position.md`** — Status (ACTIVE/CLOSED/ERROR), live
  risk формула, externalSize==0 семантика, CloseReason значения,
  refresh-only материализация, неиспользуемые статусы. [СТ §8.5, СК §12.4]
- **`docs/lifecycles/Position.md`** — переходы ACTIVE→CLOSED/ERROR через
  REFRESH_POSITION. [СТ §8.5]
- **`docs/models/core/Order.md`** — Order.Status enum, `AttachedAlgoOrder`
  (+ Status), missing-attached-protection policy по статусу parent. [СТ
  §8.3, СК §12.1/§12.3, FSM §5.4.1]
- **`docs/lifecycles/Order.md`** — переходы; CANCELED заменяет CLOSED. [СТ §8.3]
- **`docs/models/core/AlgoOrder.md`** — Status enum,
  PARTIALLY_COMPLETED/recovery-state. [СТ §8.4, СК §12.2]
- **`docs/lifecycles/AlgoOrder.md`** — переходы. [СТ §8.4]
- **`docs/models/core/Strategy.md`** — `StrategyDetail.stepsByStatus`,
  `StrategyStep` types (ENTRY/GRID_ENTRY/MAIN_PROTECTION/
  PROTECTION_ADJUSTMENT/PARTIAL_EXIT/GRID_MANAGEMENT/EXIT/FAIL_SAFE),
  `StrategyAction`/`key`/`targetActionKey`/правила валидации,
  Order/Algo/Position actions, `marketDataExpiredSetting`,
  `riskPerTradePercent`/`maxLeverage`, immutable settings (indicator/
  structure/phase + params). [СК §7, FSM §2, РИ §10–22]
- **`docs/lifecycles/Strategy.md`** — enforcement ACTIVE/INACTIVE/DELETED
  (блок новых / graceful shutdown открытых). [СТ §7.3, ЖЦ §1.1, РИ §4.2]
- **`docs/models/core/BalanceContainer.md`** — нет Status/lifecycle,
  freshness (expirationDuration), settleCurrency, REFRESH_BALANCE как
  единственный flow обновления, defensive BLOCKED. [СТ §8.6, СК §REFRESH_BALANCE, ОР §2.5]
- **`docs/models/other/AnomalyReport.md` + lifecycle** — связь с
  `AnomalyJob`/`ReconciliationJob` (поставщики report'ов). [ЖЦ §11, СТ §10]

---

## Порядок создания на проходе 2

Топологический порядок (сначала без зависимостей / зависимости уже
мигрированы; затем надстройка). Циклов не обнаружено.

1. **persisted market-data модели + settings** (`InstrumentExternalRules`,
   `IndicatorValue`, `MarketStructure`/`MarketPriceLevel`, `MarketPhase`,
   `TimeFrame`, Strategy settings) — листья, опираются только на
   `Strategy` (мигрирован).
2. **расширения уже мигрированных моделей/lifecycles** (Deal, Position,
   Order, AlgoOrder, Strategy, BalanceContainer) — статусы/enums/формулы,
   на которые ссылаются компоненты и RVO.
3. **RVO** (`MarketPriceData`, `CalculatedPrice`/`CalculatedSize`/
   `CalculatedStrategyAction`/`CalculationError`, `RiskValidationResult`/
   `RiskCheckResult`/`RiskBlockAction`, `DealContext`, `CalculationContext`,
   `ServiceCommand`/payloads, retry-модели, resolver-result'ы) +
   persisted `DealActionState` — структуры данных под компоненты.
4. **сквозные правила** (risk-validator-scope, controlled-exchange-exceptions,
   runtime-error-classification, audit-not-runtime-source, command-lifecycle,
   trading-constraints, market-data-freshness) + расширения существующих
   `docs/rules/` + правила биржи `docs/client/okx/`.
5. **компоненты** (jobs → сервисы данных → калькуляторы → risk-layer →
   command-layer/executors → resolver'ы → FSM handlers → DealStateMachine →
   EntryScanner/DealOpening/Orchestrator → AnomalyJob).
6. **процессы** (market-data-calculation; deal-lifecycle; при решении
   КЛ-Q2/ОР-Q2 — strategy-action-calculation / risk-evaluation;
   audit-execution-history — тонкий каркас).
7. **аудит-открытые-вопросы** (в `open-questions.md`, не файлы `docs/`).

Внутри каждого слоя: где артефакт упомянут полнее (колонка Primary) — там
создаём, в остальных доках только дополняем/ссылаемся.

---

## Открытые вопросы прохода 1

Локальные вопросы каждого дока — в его `tasks-*` (коды ЖЦ-Q*, FSM-Q*,
СК-Q*, СТ-Q*, КЛ-Q*, ОР-Q*, РИ-Q*, АУ-Q*). Здесь — вопросы, которые
влияют на саму карту / порядок и/или явно общие:

1. **Противоречие `PositionContext`** — поле в `CalculationContext` (КЛ §4)
   vs явное исключение из `DealContext` (ЖЦ §5.3). Материализация RVO под
   вопросом. (КЛ-Q1 / ЖЦ-Q2) → кандидат в `open-questions.md`.
2. **Противоречие closeReason `RISK_CONTROL` vs `ENTRY_RISK_BLOCKED`** —
   ОР §8.1 (решено: RISK_CONTROL, lifecycle согласован) vs АУ §7.1 (черновое
   ENTRY_RISK_BLOCKED). Вариант ОР главнее. (ОР-Q1 / АУ-Q2)
3. **`RetryErrorType` (legacy) vs `RuntimeErrorCode` (актуальный)** — СК
   §11.4 vs §11.4.1. Мигрировать только актуальный. (СК-Q1)
4. **`TradeFill` персистить или нет** — процессные доки: «Fill не persisted
   на 1 этапе» vs backlog п.6 планирует `TradeFill`/`TradeFillsArchive`.
   Несинхрон этапности. (АУ-Q3)
5. **Аудит не финализирован** — `ServiceCommandExecutionHistory`, entity
   history, timeline, snapshot format — ~30 нерешённых подвопросов (АУ §5/§8).
   На проходе 2 → блок AUDIT-Q* в `open-questions.md`, не модели. (АУ-Q1)
6. **`DealActionState` core/other + own lifecycle** (ЖЦ-Q3) — влияет на
   целевой путь и шаг 1/2 порядка.
7. **`RiskSettings` материализация** — только name-level, структура
   неизвестна. (КЛ-Q3)
8. **`TimeFrame` размещение** (model/dictionary) (РИ-Q1); **market-data
   модели core/other** (РИ-Q2); **Strategy settings: расширение vs
   отдельные файлы** (РИ-Q3); **`*ExternalSnapshot` RVO vs client** (РИ-Q4).
9. **Процесс vs компоненты** для калькуляторов (КЛ-Q2) и risk-layer (ОР-Q2);
   **процесс vs lifecycle** для «Жизненный цикл сделки» (ЖЦ-Q1).
10. **Гранулярность**: handler-компоненты (FSM-Q1), executor'ы (СК-Q2),
    payload'ы (СК-Q3) — file-per-X vs группировка.
11. **`Exchange`/`Instrument`/`Account` модели** — заводить сейчас или
    вынести в отдельную миграцию (backlog п.9). (СТ-Q2)
12. **Финализационные executor'ы + retry-state** — связано с открытым
    DEAL-Q1 (`open-questions.md`); материализация частично под вопросом.
    (СК-Q4)

Ни один из вопросов не блокирует начало прохода 2 на слоях 1–3 (модели/
расширения/RVO): большинство вопросов — про гранулярность и точное
размещение, разрешимые в момент создания конкретного файла. Блокирующими
для своих узких участков остаются: №1 (PositionContext — не заводить RVO
до решения), №4/№5 аудита (не материализовать модели), №7 (RiskSettings).
