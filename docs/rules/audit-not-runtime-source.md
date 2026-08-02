# Аудит не источник runtime-логики

## На какой вопрос отвечает этот файл

Какое у нас правило: аудит и история исполнения не являются источником
runtime-логики FSM.

## Правило

Аудит и история объясняют **прошлое** (почему создана сделка, какой
action/команда привели к действию, чем закончилась попытка, были ли
retry, какие факты подтвердили финал). FSM **не** читает историю, чтобы
решить, что делать дальше.

Для runtime-восстановления используется только:

```text
DealContext, DealActionState,
Order / AlgoOrder / Position / BalanceContainer,
exchange snapshots, refresh/search/history facts
```

Аудит **не** должен: управлять FSM; быть источником решения, какие
команды создать; заменять `DealActionState`; быть единственным источником
восстановления после рестарта; хранить operational-lock; быть command
queue. История исполнения команд не становится durable queue; история
изменения сущностей не заменяет `DealActionState`.

### Что аудит фиксирует (инварианты отображения)

- `REFRESH_BALANCE_COMMAND` попадает в историю исполнения команд (баланс влияет на
  risk decisions).
- `shutdownReason` виден в timeline, но **не** заменяет `closeReason` (см.
  `docs/models/domain/aggregate/Deal.md`).
- `CLOSED` и `EMERGENCY_CLOSED` различимы в отчётах (штатный vs аварийный
  terminal-финал); `ENTRY_CONDITION_EXPIRED` отображается как нормальное
  закрытие candidate Deal без live risk, не авария.
- Подробный entry context хранится в истории, а не в `Deal`.
- Partial exit объясним через `Order`/`AlgoOrder` + fills/history/refresh
  + `DealActionState` (direct partial close запрещён, см.
  `docs/rules/no-partial-close.md`).

## Первоисточник и смежное

Правило сквозное по командам/FSM (`.claude/decisions/rule-source-of-truth.md`).
Связанные инварианты живут у владельцев: `strategyActionId` не в
`Order`/`AlgoOrder`/`Position` → их модели +
`docs/models/domain/other/DealActionState.md`;
`ServiceCommand` не persisted queue → `docs/rules/command-lifecycle.md`.
Сами модели истории (`ServiceCommandExecutionHistory`, entity history,
timeline, snapshot-формат) **не** проектируются в этой миграции — архивный
док «Аудит и история исполнения» — рабочий каркас с ~30 открытыми
подвопросами; материализация отложена (backlog п.6, см.
`.claude/decisions/process-materialization-criterion.md`).
