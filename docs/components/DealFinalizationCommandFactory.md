# DealFinalizationCommandFactory

## На какой вопрос отвечает этот файл

Кто эмитит финализационную команду сделки за проход (компонент): что
делает, материализация retry-state, gate по повтору.

## Назначение

`DealFinalizationCommandFactory` эмитит **одну** финализационную команду
(`FINALIZE_DEAL_*` / `MARK_DEAL_*`) по статусу
`DealFinalizationState(deal, type)`. Только финализация — strategy-action
команды эмитят per-type `StrategyActionExecutor`'ы
(`docs/decisions/fsm-execution-layering.md`,
`docs/components/StrategyActionExecutor.md`). Зовётся из
`DealFsmSupport.finalizationCommand(type, dealContext)`.

## Контракт

```java
Optional<ServiceCommand> finalizationCommand(DealFinalizationType type, DealContext dealContext);
```

Материализует строку `DealFinalizationState` (upsert по `UNIQUE(deal_id,
type)`) при первом обращении и привязывает команду к
`dealFinalizationStateId`. По статусу состояния:

```text
отсутствует / PENDING -> команда (по type)
RETRY_PENDING         -> команда, только если наступил nextRetryAt (иначе ждём backoff)
COMPLETED             -> empty (сделано)
FAILED                -> empty (исчерпано; сделку на ошибочную тропу выводит handler, DEAL-Q2)
```

Маппинг type → команда: `FINALIZE_ENTRY → FINALIZE_DEAL_ENTRY`,
`FINALIZE_EXIT → FINALIZE_DEAL_EXIT`, `MARK_CLOSED → MARK_DEAL_CLOSED`,
`MARK_EMERGENCY_CLOSED → MARK_DEAL_EMERGENCY_CLOSED` (терминал аварийной
тропы, `docs/decisions/pnl-finalization-mechanics.md` реш.3),
`MARK_ERROR → MARK_DEAL_ERROR`. Статус состояния сам не пишет (паритет с
`DealActionState`) — его двигают executor'ы / retry-учёт
(`docs/decisions/deal-finalization-state-materialization.md`,
`docs/lifecycles/DealFinalizationState.md`).
