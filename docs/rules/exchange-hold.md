# Exchange HOLD: что блокируется

## На какой вопрос отвечает этот файл

Какое правило системы определяет, какие команды блокирует статус
`Exchange.HOLD`.

## Правило

`Exchange.HOLD` — safety-состояние биржи, выставляемое safety-каскадом
(см. `docs/rules/external-status-resolution.md`). В состоянии `HOLD`:

**Блокируются** normal trading commands:

```text
SUBMIT_ORDER
SUBMIT_ALGO_ORDER
AMEND_ORDER
AMEND_ALGO_ORDER
```

**Не блокируются** safety / read commands:

```text
REFRESH_*
SEARCH / HISTORY
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
EXECUTE_KILL_SWITCH
```

## Почему

Сквозное правило про gating команд на уровне биржи
(`.claude/decisions/rule-source-of-truth.md`). `HOLD` останавливает
создание нового риска, но оставляет возможность наблюдать состояние
(read/refresh) и снижать риск (cancel/close/kill-switch) до разбора
аномалии.

> Модель/lifecycle самого `Exchange` в текущей серии миграции не
> создаётся (не входит в backlog-порядок из 6 сущностей). Здесь
> зафиксировано только правило gating команд по `HOLD`; полная модель
> `Exchange` — отдельная задача.

## Связанное

- `docs/rules/external-status-resolution.md` (источник перехода в HOLD).
- `docs/lifecycles/Order.md`.
