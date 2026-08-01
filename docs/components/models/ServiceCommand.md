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
можно ли после результата перейти дальше». Flow action-команды:
`StrategyAction → StrategyActionOrchestrator → StrategyActionExecutor
(per type, зовёт StrategyActionCalculator) → ServiceCommand →
ServiceCommandExecutor → конкретный Executor`. Финализационные команды
эмитит `DealFinalizationCommandFactory` (см.
`docs/components/StrategyActionOrchestrator.md`,
`docs/components/DealFinalizationCommandFactory.md`).

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
`docs/decisions/deal-finalization-state-materialization.md`). Системная/
cleanup-команда без action-state (`REFRESH_*` / `CANCEL_*` / `CLOSE_POSITION`,
эмитируемые как safety/cleanup вне strategy-action) может не нести ни того,
ни другого. **Оговорка не распространяется на добывающие команды штатной
тропы** (H15, `GAPS_CLOSE_7`): `REFRESH_POSITION` и `REFRESH_BILLS` в
`EXIT_PENDING` влияют на **число** сделки, поэтому идут под анкером
`DealActionState` — иначе `applyFailureAccounting` на них no-op, и
устойчивый отказ не даёт ни попыток, ни `FAILED`
(`docs/decisions/pnl-finalization-mechanics.md` §5a). Kill-switch командой не является — он реактивен (`HoldSignal` →
`SafetyHoldCoordinator`, см. `docs/components/KillSwitchExecutor.md`).

Не обязан хранить `strategyId` / `strategyDetailId`: происхождение
команды восстанавливается через `DealActionState` / `DealFinalizationState`
и историю исполнения. Аудит/история не источник runtime-логики FSM (см.
`docs/rules/audit-not-runtime-source.md`).

## Енум `ServiceCommandType`

`REFRESH_BALANCE`, `REFRESH_POSITION`,
`REFRESH_BILLS`, `CLOSE_POSITION`, `CREATE_ORDER`, `SUBMIT_ORDER`,
`CANCEL_ORDER`, `REFRESH_ORDER`, `CREATE_ALGO_ORDER`, `SUBMIT_ALGO_ORDER`,
`CANCEL_ALGO_ORDER`, `REFRESH_ALGO_ORDER`, `FINALIZE_DEAL_ENTRY`,
`FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`, `MARK_DEAL_EMERGENCY_CLOSED`,
`MARK_DEAL_ERROR`.

**Амендных команд нет:** `AMEND_ORDER` / `AMEND_ALGO_ORDER` сняты из
enum'а (снятие AMEND: 19 → 17; после снятия `EXECUTE_KILL_SWITCH` — 16;
на шаге 7 снимается `REFRESH_FILLS` и добавляются `REFRESH_BILLS` /
`MARK_DEAL_EMERGENCY_CLOSED` → **17** целевых (в коде пока 16),
`docs/decisions/pnl-finalization-mechanics.md`) решением
`docs/decisions/replace-not-amend.md` — AMEND ушёл из доменного
словаря целиком. Ремоделирование — действие стратегии
`StrategyActionType.REPLACE`, исполняемое **оркестрацией
существующих атомарных команд** (protective: `CREATE_*`/`SUBMIT_*`
новой → подтверждение `REFRESH_*` → `CANCEL_*` старой с
`REPLACED_BY_STRATEGY`; entry: cancel-нога первой); отдельного
`ServiceCommandType` под REPLACE нет.

**Refresh-набор — ровно по одной команде на сущность:** `REFRESH_ORDER`,
`REFRESH_ALGO_ORDER`, `REFRESH_POSITION`, `REFRESH_BALANCE`,
`REFRESH_BILLS` (`DealCashFlow` — разбивка). `REFRESH_FILLS` **снимается** на
`CODE` шага 7 (его order-fill-метрики покрыты `REFRESH_ORDER`;
`docs/decisions/pnl-finalization-mechanics.md` реш.1) — состав выше
**целевой**, в коде enum пока несёт `REFRESH_FILLS` и не несёт двух новых
команд (H15, `GAPS_CLOSE_6`). Внутри исполнителя
допускается несколько вызовов биржи (evidence-cycle,
`docs/decisions/refresh-evidence-cycle-ownership.md`).

**`REFRESH_POSITIONS_HISTORY` командой не вводится** (H1/H3,
`GAPS_CLOSE_7`; ревизует реш.1 `pnl-finalization-mechanics`).
positions-history описывает **ту же сущность** `Position` после закрытия,
а не новую, поэтому по принципу «одна команда на сущность» это **вторая
нога evidence-cycle `REFRESH_POSITION`** (live → history), а не отдельный
тип. Промежуточная редакция (`GAPS_CLOSE_6`, H13) вводила её как команду и
исполняла **вложенным шагом** финализирующего действия — конструкции
«команда внутри команды» канон не знает (`docs/rules/command-lifecycle.md`
ослабляет атомарность только до «несколько **эндпоинтов** внутри
команды»), и канала возврата снапшота у `ServiceCommandExecutionResult`
нет. С приземлением факта на `Position` обе проблемы отпадают вместе.
Bulk-команды `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` /
`REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY` сняты — их эндпоинты
живут только звеньями цикла (CMD-Q3 закрыт). Перечисление **неизвестных**
сущностей по инструменту (orphan / чужой live risk) — CMD-Q4.

**Финализационные команды** `FINALIZE_DEAL_ENTRY` / `FINALIZE_DEAL_EXIT` /
`MARK_DEAL_CLOSED` / `MARK_DEAL_EMERGENCY_CLOSED` / `MARK_DEAL_ERROR` —
lifecycle/system actions без `StrategyAction`: их retry-state живёт в
`DealFinalizationState` (не `DealActionState`), а эмитятся они по статусу
`DealFinalizationState` (`MARK_DEAL_EMERGENCY_CLOSED` — терминал аварийной
тропы `ERROR → EMERGENCY_CLOSED`, симметричен `MARK_DEAL_CLOSED`,
`docs/decisions/pnl-finalization-mechanics.md` реш.3)
(`docs/decisions/deal-finalization-state-materialization.md`,
`docs/components/DealFinalizationCommandFactory.md`). Семантика executor'ов —
`docs/components/FinalizeDealEntryExecutor.md` и др.

Graceful shutdown, protection switch, REPLACE-ремодел и safety-flow
собираются из существующих команд — отдельных типов под них нет.
`entryReason` / `entryStepType` — поля `Deal`/audit, не
`ServiceCommandType`.
