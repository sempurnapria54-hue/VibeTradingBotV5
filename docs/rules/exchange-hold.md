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
```

(Амендных команд нет — REPLACE-ремодел блокируется этой же парой:
его place-нога — `SUBMIT_*`; `docs/decisions/replace-not-amend.md`.)

**Не блокируются** safety / read commands:

```text
REFRESH_*
SEARCH / HISTORY
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
```

(Kill-switch teardown командой **не** является — тип `EXECUTE_KILL_SWITCH`
убран; аварийное снятие риска идёт вне реестра команд через
`docs/components/KillSwitchExecutor.md` теми же `CANCEL_*` / `CLOSE_POSITION`
/ `REFRESH_*`, которые здесь и так не блокируются.)

Также `HOLD` блокирует создание новых `ENTRY`/`GRID_ENTRY`, normal-flow
TP/SL/trailing actions, pyramid/scaling и любые действия, увеличивающие
торговое намерение вне safety-flow.

## DISABLED (Exchange / Instrument)

`HOLD` — safety-пауза; `DISABLED` — конфигурационное отключение
`Exchange`/`Instrument`. На первом этапе `DISABLED` трактуется как
запрет новых сделок; разрешение safety/read операций зависит от причины
отключения и задаётся отдельно (в отличие от `HOLD`, где safety/read
всегда разрешены). Статус инструмента — также точка enforcement
блокировки торговли после `AnomalyReport` (severity `CRITICAL` →
торговля остаётся запрещённой; см. `docs/models/domain/other/AnomalyReport.md`).
Полная модель/lifecycle `Exchange`/`Instrument` — backlog п.9.

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
- `docs/rules/error-handling-policy.md` — exchange-HOLD = **уровень 4**
  error-градации (нарушение контракта интеграции / инвариантов системы);
  инструмент-scope холд (уровень 3) — `docs/rules/instrument-hold.md`.
- `docs/lifecycles/Order.md`.
