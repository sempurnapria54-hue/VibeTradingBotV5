# ServiceCommand

## На какой вопрос отвечает этот файл

Что это за runtime value object `ServiceCommand`: структура, енум
`ServiceCommandType`, ключевой инвариант «не persisted queue».

## Назначение

`ServiceCommand` — атомарная runtime-команда над runtime-сущностью,
которую можно передать executor'у. RVO, **не** persisted entity и **не**
durable command queue (см. `.claude/decisions/runtime-value-object.md` и
`docs/rules/command-lifecycle.md`). Критерий атомарности («одна
ответственность за состояние», три клаузы) —
`docs/rules/command-lifecycle.md`; решение —
`docs/decisions/command-action-boundary.md`.

Отвечает на вопрос «какую простую операцию выполнить над runtime-
сущностью?»; executor — «как технически выполнить»; FSM — «зачем сейчас и
можно ли после результата перейти дальше». Flow strategy-команды:
`StrategyAction → StrategyActionOrchestrator → StrategyActionExecutor
(per type, зовёт StrategyActionCalculator) → ServiceCommand →
ServiceCommandExecutor → конкретный Executor`. Команды **системных
действий** (добыча, финализация) эмитит `SystemActionExecutor`
(`docs/components/SystemActionExecutor.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `type` | `ServiceCommandType` | Тип атомарной операции. |
| `dealId` | `Long` | Сделка, в рамках которой выполняется команда. |
| `dealActionStateId` | `Long` | Строка **исполнения действия** (`docs/models/domain/other/DealActionState.md`) — retry-anchor команды; один на оба вида действий (STRATEGY и SYSTEM). `null` только у cleanup-команд (см. ниже). |
| `payload` | `ServiceCommandPayload` | Параметры выполнения (см. `docs/components/models/ServiceCommandPayload.md`). |

**Retry-anchor один** — строка исполнения действия в
`deal_action_states` (прежний второй анкер `dealFinalizationStateId`
упразднён вместе с `DealFinalizationState`;
`docs/decisions/command-action-boundary.md` §3). Правила:

- **strategy-команды** (`CREATE_*`/`SUBMIT_*`/`CANCEL_*` ног действий
  стратегии) — анкер = исполнение STRATEGY-вида;
- **звенья системных действий** — добывающие `REFRESH_*` и
  финализационные `FINALIZE_*`/`MARK_*` — анкер = исполнение SYSTEM-вида
  (`REFRESH_DEAL_CONTEXT_ACTION` / `FINALIZE_DEAL_*_ACTION`);
- **cleanup-команды** (`CANCEL_*` / `CLOSE_POSITION_COMMAND`,
  эмитируемые handler'ом как дочистка/safety вне действия) анкера **не
  несут**: их серия неудач считается на инструмент-scope
  (`docs/rules/instrument-hold.md` §«Серия неудач»), второй deal-scoped
  счётчик дал бы двойной учёт.

Kill-switch командой не является — он реактивен (`HoldSignal` →
`SafetyHoldCoordinator`, см. `docs/components/KillSwitchExecutor.md`) и
действием тоже не материализуется
(`docs/decisions/command-action-boundary.md` §2).

Не обязан хранить `strategyId` / `strategyDetailId`: происхождение
команды восстанавливается через строку исполнения. Аудит/история не
источник runtime-логики FSM (`docs/rules/audit-not-runtime-source.md`).

## Енум `ServiceCommandType`

Все значения несут суффикс `_COMMAND` (`.claude/rules/naming.md`
§«Разведение уровней абстракции»; групповые маски в прозе — короткие:
`REFRESH_*`, `MARK_*`):

`REFRESH_BALANCE_COMMAND`, `REFRESH_POSITION_COMMAND`,
`REFRESH_BILLS_COMMAND`, `CLOSE_POSITION_COMMAND`, `CREATE_ORDER_COMMAND`,
`SUBMIT_ORDER_COMMAND`, `CANCEL_ORDER_COMMAND`, `REFRESH_ORDER_COMMAND`,
`CREATE_ALGO_ORDER_COMMAND`, `SUBMIT_ALGO_ORDER_COMMAND`,
`CANCEL_ALGO_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`,
`FINALIZE_DEAL_ENTRY_COMMAND`, `FINALIZE_DEAL_EXIT_COMMAND`,
`MARK_DEAL_CLOSED_COMMAND`, `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`,
`MARK_DEAL_ERROR_COMMAND`.

**Состав целевой — 17** (в коде enum пока без суффиксов, несёт
`REFRESH_FILLS` и не несёт `REFRESH_BILLS_COMMAND` /
`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`; переименование + дельта — `CODE`,
`.claude/work/backlog.md` §Шаг 7). История состава: AMEND-команды сняты
(`docs/decisions/replace-not-amend.md` — ремоделирование — действие
`REPLACE_ACTION`, оркестрация существующих атомарных команд);
`EXECUTE_KILL_SWITCH` убран (kill-switch — не команда); `REFRESH_FILLS`
снимается (метрики покрыты `REFRESH_ORDER_COMMAND`,
`docs/decisions/pnl-finalization-mechanics.md` реш.1).

**Refresh-набор — ровно по одной команде на сущность:**
`REFRESH_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`,
`REFRESH_POSITION_COMMAND`, `REFRESH_BALANCE_COMMAND`,
`REFRESH_BILLS_COMMAND` (`DealCashFlow` — разбивка). Внутри исполнителя
допускается несколько вызовов биржи (evidence-cycle,
`docs/decisions/refresh-evidence-cycle-ownership.md`).

**`REFRESH_POSITIONS_HISTORY` командой не вводится** (H1/H3,
`GAPS_CLOSE_7`): positions-history описывает **ту же сущность**
`Position` после закрытия — это **вторая нога evidence-cycle
`REFRESH_POSITION_COMMAND`** (live → history), не отдельный тип.
Bulk-команды `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` /
`REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY` сняты — их эндпоинты
живут только звеньями цикла (CMD-Q3 закрыт). Перечисление **неизвестных**
сущностей по инструменту (orphan / чужой live risk) — CMD-Q4.

**Финализационные команды** `FINALIZE_DEAL_ENTRY_COMMAND` /
`FINALIZE_DEAL_EXIT_COMMAND` / `MARK_DEAL_CLOSED_COMMAND` /
`MARK_DEAL_EMERGENCY_CLOSED_COMMAND` / `MARK_DEAL_ERROR_COMMAND` — звенья
**системных действий** `FINALIZE_DEAL_ENTRY_ACTION` /
`FINALIZE_DEAL_EXIT_ACTION` / `FINALIZE_DEAL_ERROR_ACTION`; эмитятся
`SystemActionExecutor` по статусу строки исполнения и подтверждённым
фактам звеньев. Семантика executor'ов —
`docs/components/FinalizeDealEntryExecutor.md` и др.

Graceful shutdown, protection switch, REPLACE-ремодел и safety-flow
собираются из существующих команд — отдельных типов под них нет.
`entryReason` / `entryStepType` — поля `Deal`/audit, не
`ServiceCommandType`.
