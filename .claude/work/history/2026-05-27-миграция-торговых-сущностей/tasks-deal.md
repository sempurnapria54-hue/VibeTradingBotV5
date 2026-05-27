# Локальные вопросы: миграция Deal

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности Deal.

## Контекст

Источник: `.claude-archive/2026-05-21/docs/domain/models/Deal.md`.
`Deal` — не биржевая сущность, OKX mapping не нужен. Стратегия:
парковать cross-cutting, создавать владение Deal. Продуктовые
открытые вопросы §15 перенесены в
`.claude/work/questions/open-questions.md` (DEAL-Q1, DEAL-Q2) по
backlog.

## Форвард-заметки (мигрируются с кластером Deal management)

Кластер `.claude-archive/.../docs/domain/processes/Deal management/`
(`Жизненный цикл сделки.md`, `FSM этапы сделки.md`, `Сервисные
команды.md`, `Статусы торговых сущностей.md`) — отдельная миграция
(процессы / компоненты / RVO). Ниже — что туда уходит.

- **DEAL-FW1. `DealContext` (shared RVO).** Процессный runtime-context
  одного прохода FSM: `Deal` + `Exchange` + `Instrument` + pinned
  `StrategyDetail` + последний persisted `BalanceContainer` +
  `DealActionState` list (+ по архиву Balance: `position`, `orders`,
  `algoOrders`, `actionStates`). Полная модель и правила сборки — в
  `Жизненный цикл сделки.md`. Консолидирует BAL-Q3, POS-Q4
  (DealContext). Размещение — `docs/components/models/` (RVO).

- **DEAL-FW2. `DealActionState` (модель) + `RuntimeTarget`.**
  Persisted operational FSM-state `StrategyAction`: recovery, retry,
  idempotency, связь `StrategyAction → runtime target`. Связь
  `Deal.id → dealId → strategyActionId → RuntimeTarget(entityType,
  entityId)`. Статусы (`SUBMITTED`, `RETRY_PENDING`, `FAILED`,
  completed/skipped — встречались в Order/AlgoOrder flow).
  Консолидирует ORD-Q3, ALGO-Q3. Полное описание — в
  `Жизненный цикл сделки.md` / `Сервисные команды.md`.

- **DEAL-FW3. FSM handlers + `DealStateMachine` (компоненты).**
  Per-status handlers (`PrecheckHandler`, `EntrySubmittedHandler`,
  `EntryFinalizedHandler`, `ProtectionSwitchedHandler`,
  `ManagingHandler`, `ExitPendingHandler`, `ErrorHandler`) →
  `docs/components/<Handler>.md`; конструкция handler'а (3 типа
  проверок) → раздел `docs/components/DealStateMachine.md`. Lifecycle
  Deal (создан) ссылается на handler'ы. См.
  `.claude/decisions/fsm-handler-as-component.md`. Источник — `FSM
  этапы сделки.md`.

- **DEAL-FW4. Подсистема ServiceCommand (lifecycle/finalization
  commands).** `REFRESH_FILLS`, `FINALIZE_DEAL_EXIT`,
  `MARK_DEAL_CLOSED`, emergency finalization, `EXECUTE_KILL_SWITCH` и
  пр. `ServiceCommand` — runtime object, не persisted queue (после
  рестарта не ищется). Источник — `Сервисные команды.md`. Retry-state
  финализации — открытый вопрос DEAL-Q1.

- **DEAL-FW5. `TradeFill` (модель) + `REFRESH_FILLS`.** Итоговый
  `Deal.resultProfit` считается через `REFRESH_FILLS`/`TradeFill`
  facts (правило — в `docs/models/core/Deal.md`, владелец Deal).
  `TradeFill`/`TradeFillsArchive` (архив:
  `.claude-archive/.../deprecated/models/domain/old/`) — отдельная
  модель, мигрируется при проработке fills/финализации. Закрывает
  направление BAL-Q5, POS-Q5 (правило resultProfit теперь имеет дом в
  Deal.md; механизм fills — здесь).

- **DEAL-FW6. RiskValidator (shared).** Risk-policy в `PRECHECK`,
  risk-control exit, `RiskValidationResult` (RVO). Источник — `Оценка
  рисков.md`. Консолидирует BAL-Q2, POS-Q3, ORD-Q4, ALGO-Q5.

- **DEAL-FW7. Anomaly / Reconciliation (shared safety).** Live risk
  после terminal status → `AnomalyJob` / `ReconciliationJob`.
  Компоненты — при миграции safety/anomaly-подсистемы. Консолидирует
  POS-Q7.

- **DEAL-FW8. `StrategyDetail` pinned + `StrategyTradeDirection`.**
  `Deal.strategyDetailId` — pinned версия; `Deal.direction` тип
  `StrategyTradeDirection` (енум Strategy). Поведение при
  `Strategy.INACTIVE`/`DELETED` (graceful shutdown) — с миграцией
  Strategy (следующая сущность). `StrategyDetail.marketPhaseType`
  (фаза входа) — там же.

- **DEAL-FW9. Аудит / entry context / timeline.** Подробный entry
  context, `MarketPhase` result/timestamp/confidence, breakdown PnL
  (fees, fundingFee, gross/net, fills, average prices, partial exits)
  — в аудите (`Аудит и история исполнения.md`), не в `Deal`.

## Открытые вопросы

Продуктовые открытые вопросы §15 (retry-state финализации;
недосчитанный `resultProfit` после исчерпания retry) перенесены как
**DEAL-Q1** и **DEAL-Q2** в `.claude/work/questions/open-questions.md`
(общие открытые вопросы) — по инструкции backlog. Здесь не
дублируются.
