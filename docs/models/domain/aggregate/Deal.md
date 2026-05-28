# Deal

## На какой вопрос отвечает этот файл

Что это за торговая модель `Deal` (lifecycle root и runtime graph
сделки): структура, енумы, runtime graph, итоговый PnL.

Статусы и переходы — в `docs/lifecycles/Deal.md`.

## Назначение

`Deal` — lifecycle root и runtime graph торговой сделки. Фиксирует,
что система начала сопровождать торговый сценарий по конкретному
`Instrument`, по pinned `StrategyDetail`, в ожидаемом направлении,
с FSM-статусом, причиной создания, причиной завершения и итоговым
profit/loss.

`Deal` **не** является биржевой сущностью: нет external id, нет
external status, OKX mapping-документ не нужен. `Deal` **не**
отвечает за: сырые exchange responses, историю команд, историю
изменений сущностей, подробный entry context, risk-check details,
свежие market/calculation data, raw fills archive, полную финансовую
отчётность.

## Структура

Java-класс `com.example.tradingbot.domain.model.core.deal.Deal`,
расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `internalId` | `String` | Безопасный внешний/межсервисный id (API, логи, timeline). |
| `instrumentId` | `Long` | Инструмент (полный `Instrument` — в `DealContext`). |
| `strategyDetailId` | `Long` | Pinned `StrategyDetail`: даже если `Strategy` изменится / станет INACTIVE / DELETED, открытая сделка ведётся по этой pinned-версии. |
| `status` | `Status` | FSM-статус (см. lifecycle). |
| `direction` | `StrategyTradeDirection` | Expected direction (`LONG`/`SHORT`), фиксируется при создании; `Position.direction` должен ему соответствовать. |
| `entryReason` | `EntryReason` | Короткая причина создания (не управляет FSM). |
| `entryStepType` | `EntryStepType` | Тип entry-step (`ENTRY`/`GRID_ENTRY`/null; не управляет FSM). |
| `shutdownReason` | `ShutdownReason` | Причина graceful shutdown / controlled close (если запущен). Не заменяет `closeReason`. |
| `closeReason` | `CloseReason` | Итоговая бизнес-причина завершения. |
| `resultProfit` | `BigDecimal` | Итоговый PnL (см. ниже). |
| `resultProfitCurrency` | `String` | Валюта результата (для `ETH-USDT-SWAP` обычно `USDT`). |
| `orders` | `List<Order>` | Ordinary orders сделки (attached protection — внутри `Order`). |
| `algoOrders` | `List<AlgoOrder>` | Standalone algo-orders сделки. |
| `position` | `Position` | Текущая позиция (≤1 на `Deal`). |

`Deal.direction` имеет тип `StrategyTradeDirection` (енум Strategy;
мигрируется с `Strategy` — форвард-заметка в
`.claude/work/questions/tasks/deal.md`).

## Енумы

- **`Status`**: `PRECHECK`, `ENTRY_SUBMITTED`, `ENTRY_FINALIZED`,
  `PROTECTION_SWITCHED`, `MANAGING`, `EXIT_PENDING`, `CLOSED`,
  `ERROR`, `EMERGENCY_CLOSED`. Описывает бизнес-этап сделки, **не**
  статус `Order`/`AlgoOrder`/`Position`/command execution/exchange
  ACK. Значения, группы, переходы — в `docs/lifecycles/Deal.md`.
- **`EntryReason`**: `STRATEGY` (создана `EntryScannerJob` по
  условиям), `MANUAL`, `RECOVERY` (восстановление существующего
  runtime risk), `UNKNOWN` (fallback, не для normal flow).
- **`EntryStepType`**: `ENTRY`, `GRID_ENTRY` (или null, если создана
  не через strategy entry-step). Комбинации с `entryReason` — в
  lifecycle/справке. Подробный entry context — в аудите, не в `Deal`.
- **`ShutdownReason`**: `STRATEGY_DELETED`, `MARKET_DATA_EXPIRED`
  (только если policy решила завершать сделку controlled-exit, не
  при любом stale), `MANUAL_STOP`, `RISK_POLICY`, `EXCHANGE_HOLD`,
  `UNKNOWN`. Заполняется только при реальном запуске graceful
  shutdown (см. lifecycle).
- **`CloseReason`**: `ENTRY_CONDITION_EXPIRED` (candidate закрыт в
  PRECHECK до live risk), `STRATEGY_EXIT`, `TAKE_PROFIT`,
  `STOP_LOSS` (включая fixed и trailing SL; конкретный механизм — в
  `Order`/`AlgoOrder`/`DealActionState`/audit), `TIME_STOP`,
  `RISK_CONTROL` (штатное risk-control завершение, включая risk-block
  в PRECHECK), `MANUAL_CLOSE`, `EMERGENCY_CLOSE` (только для
  `EMERGENCY_CLOSED`), `UNKNOWN`. Не используются:
  `ENTRY_RISK_BLOCKED`, `TRAILING_STOP`. Описывает бизнес-причину, не
  технический механизм закрытия позиции.

`entryReason`/`entryStepType` не управляют FSM. `shutdownReason`
(почему перевели в graceful shutdown — не значит, что закрылась) и
`closeReason` (итоговая причина завершения) — разные поля.

## Итоговый PnL (resultProfit)

Первоисточник правила — здесь (`Deal` владеет полем,
`.claude/decisions/rule-source-of-truth.md`):

- `resultProfit` считается через `REFRESH_FILLS` / `TradeFill` facts,
  **не** через `BalanceContainer` diff. `REFRESH_BALANCE` после
  выхода нужен для актуального account snapshot, не для PnL сделки.
- Для terminal statuses `CLOSED` / `EMERGENCY_CLOSED` `resultProfit`
  и `resultProfitCurrency` обязательны.
- `resultProfit = 0` допустим только как результат расчёта, **не**
  как fallback при ошибке. Если временно нельзя посчитать —
  финализация retry-ится по общей retry-policy. (Поведение при
  исчерпании retry — открытый вопрос, см.
  `.claude/work/questions/open-questions.md`.)

Детальный breakdown (fees, fundingFee, gross/netProfit, entry/exit
fills, average prices, partial exits) в `Deal` не хранится —
восстанавливается через `TradeFill` facts / финализационный расчёт /
audit (`TradeFill` и `REFRESH_FILLS` — форвард-заметка в
task-вопросах).

## Runtime graph

`Deal` содержит runtime graph: `orders` (см.
`docs/models/domain/core/Order.md`), `algoOrders` (см.
`docs/models/domain/core/AlgoOrder.md`), `position` (см.
`docs/models/domain/core/Position.md`, ≤1 на `Deal`). Live risk сделки —
вычисляемо (см. lifecycle), отдельным boolean-полем не хранится.

В runtime graph **не** входят и в `Deal` не хранятся:
`DealActionState`, `Exchange`, `Instrument`, `StrategyDetail`,
`BalanceContainer`, `TradeFill` archive, raw exchange facts,
`CalculationContext`, `MarketPriceData`, `IndicatorValue`,
`MarketStructure`, `MarketPhase` runtime data, audit/history, pending
`ServiceCommand`. Также не хранятся `marketPhaseId` (фаза входа
выводится через `StrategyDetail.marketPhaseType`), `openedAt`/
`closedAt`/`errorAt` (даты записи — `Auditable`; торговые моменты —
через `Order`/`Position`/`TradeFill`/audit).

## Границы с DealActionState / DealContext

- `DealActionState` **не** поле `Deal`: persisted operational
  FSM-state конкретного `StrategyAction` (recovery/retry/idempotency/
  связь `StrategyAction → runtime target`). Связь: `Deal.id →
  DealActionState.dealId → strategyActionId →
  RuntimeTarget(entityType, entityId)`.
- `DealContext` **не** часть модели `Deal`: процессный runtime-context
  одного прохода FSM (добавляет `Exchange`, `Instrument`, pinned
  `StrategyDetail`, последний persisted `BalanceContainer`,
  `DealActionState` list).

Полные модели `DealActionState` / `DealContext`, FSM-handlers и
правила сборки — в кластере процессов Deal management (форвард-
заметки в `.claude/work/questions/tasks/deal.md`).
