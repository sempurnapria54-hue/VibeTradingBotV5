# ServiceCommand

## На какой вопрос отвечает этот файл

Что это за runtime value object `ServiceCommand`: структура, енум
`ServiceCommandType`, ключевой инвариант «не persisted queue».

## Назначение

`ServiceCommand` — атомарная runtime-команда над runtime-сущностью,
которую можно передать executor'у. RVO, **не** persisted entity и **не**
durable command queue (см. `.claude/decisions/runtime-value-object.md` и
`docs/rules/command-lifecycle.md`).

Отвечает на вопрос «какую простую операцию выполнить над runtime-
сущностью?»; executor — «как технически выполнить»; FSM — «зачем сейчас и
можно ли после результата перейти дальше». Flow: `StrategyAction →
StrategyActionCalculator → ServiceCommandFactory → ServiceCommand →
ServiceCommandExecutor → конкретный Executor`.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `type` | `ServiceCommandType` | Тип атомарной операции. |
| `dealId` | `Long` | Сделка, в рамках которой выполняется команда. |
| `dealActionStateId` | `Long` | Runtime-состояние **action стратегии** (связь с `StrategyAction`; модель — `docs/models/domain/other/DealActionState.md`). `null` для финализационных команд. |
| `dealFinalizationStateId` | `Long` | Runtime-состояние **финализационной** команды (lifecycle/system action без `StrategyAction`; модель — `docs/models/domain/other/DealFinalizationState.md`). `null` для action-команд. |
| `payload` | `ServiceCommandPayload` | Параметры выполнения (см. `docs/components/models/ServiceCommandPayload.md`). |

Retry-anchor команды — ровно один из `dealActionStateId` /
`dealFinalizationStateId`: action-команды (`CREATE_*`/`SUBMIT_*`/`CANCEL_*`/
`CLOSE_POSITION`/`REFRESH_*`) ведутся `DealActionState`; финализационные
(`FINALIZE_DEAL_*`/`MARK_DEAL_*`) — `DealFinalizationState` (нет
`StrategyAction`, поэтому `dealActionStateId` им не подходит; см.
`docs/decisions/deal-finalization-state-materialization.md`). Safety-команда
без retry-state (`EXECUTE_KILL_SWITCH`) может не нести ни того, ни другого.

Не обязан хранить `strategyId` / `strategyDetailId`: происхождение
команды восстанавливается через `DealActionState` / `DealFinalizationState`
и историю исполнения. Аудит/история не источник runtime-логики FSM (см.
`docs/rules/audit-not-runtime-source.md`).

## Енум `ServiceCommandType`

`REFRESH_BALANCE`, `REFRESH_POSITION`, `CLOSE_POSITION`, `CREATE_ORDER`,
`SUBMIT_ORDER`, `CANCEL_ORDER`, `REFRESH_ORDER`, `CREATE_ALGO_ORDER`,
`SUBMIT_ALGO_ORDER`, `CANCEL_ALGO_ORDER`, `REFRESH_ALGO_ORDER`,
`REFRESH_FILLS`, `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`,
`MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.

**Амендных команд нет:** `AMEND_ORDER` / `AMEND_ALGO_ORDER` сняты из
enum'а (19 → 17) решением
`docs/decisions/replace-not-amend.md` — AMEND ушёл из доменного
словаря целиком. Ремоделирование — действие стратегии
`StrategyActionType.REPLACE`, исполняемое **оркестрацией
существующих атомарных команд** (protective: `CREATE_*`/`SUBMIT_*`
новой → подтверждение `REFRESH_*` → `CANCEL_*` старой с
`REPLACED_BY_STRATEGY`; entry: cancel-нога первой); отдельного
`ServiceCommandType` под REPLACE нет.

**Refresh-набор — ровно по одной команде на сущность:** `REFRESH_ORDER`,
`REFRESH_ALGO_ORDER`, `REFRESH_POSITION`, `REFRESH_BALANCE`,
`REFRESH_FILLS`. Внутри исполнителя допускается несколько вызовов биржи
(evidence-cycle, `docs/decisions/refresh-evidence-cycle-ownership.md`).
Bulk-команды `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` /
`REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY` сняты — их эндпоинты
живут только звеньями цикла (CMD-Q3 закрыт). Перечисление **неизвестных**
сущностей по инструменту (orphan / чужой live risk) — CMD-Q4.

**Финализационные команды** `FINALIZE_DEAL_ENTRY` / `FINALIZE_DEAL_EXIT` /
`MARK_DEAL_CLOSED` / `MARK_DEAL_ERROR` — lifecycle/system actions без
`StrategyAction`: их retry-state живёт в `DealFinalizationState` (не
`DealActionState`), а эмитятся они по статусу `DealFinalizationState`
(`docs/decisions/deal-finalization-state-materialization.md`,
`docs/components/ServiceCommandFactory.md`). Семантика executor'ов —
`docs/components/FinalizeDealEntryExecutor.md` и др.

Graceful shutdown, protection switch, REPLACE-ремодел и safety-flow
собираются из существующих команд — отдельных типов под них нет.
`entryReason` / `entryStepType` — поля `Deal`/audit, не
`ServiceCommandType`.
