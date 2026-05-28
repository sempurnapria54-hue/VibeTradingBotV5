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
| `dealActionStateId` | `Long` | Runtime-состояние action стратегии (связь с `StrategyAction`; структура/размещение `DealActionState` — DEAL-Q3, `.claude/work/questions/open-questions.md`). |
| `payload` | `ServiceCommandPayload` | Параметры выполнения (см. `docs/components/models/ServiceCommandPayload.md`). |

Не обязан хранить `strategyId` / `strategyDetailId`: происхождение
команды восстанавливается через `DealActionState` и историю исполнения.
Аудит/история не источник runtime-логики FSM (см.
`docs/rules/audit-not-runtime-source.md`).

## Енум `ServiceCommandType`

`REFRESH_BALANCE`, `REFRESH_POSITION`, `CLOSE_POSITION`, `CREATE_ORDER`,
`SUBMIT_ORDER`, `AMEND_ORDER`, `CANCEL_ORDER`, `REFRESH_ORDER`,
`REFRESH_PENDING_ORDERS`, `REFRESH_ORDER_HISTORY`, `CREATE_ALGO_ORDER`,
`SUBMIT_ALGO_ORDER`, `AMEND_ALGO_ORDER`, `CANCEL_ALGO_ORDER`,
`REFRESH_ALGO_ORDER`, `REFRESH_ALGO_ORDERS`, `REFRESH_ALGO_ORDER_HISTORY`,
`REFRESH_FILLS`, `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`,
`MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.

Graceful shutdown, protection switch и safety-flow собираются из
существующих команд — отдельных типов под них нет. `entryReason` /
`entryStepType` — поля `Deal`/audit, не `ServiceCommandType`.
