# DealActionState lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит исполнение действия (`DealActionState`, оба
вида — STRATEGY и SYSTEM), кто и при каких фактах их меняет.

Структура модели — в `docs/models/domain/other/DealActionState.md`.

## Кто управляет

Статус ведёт command-layer, не FSM напрямую:

- **STRATEGY-строки:** `StrategyActionOrchestrator` (диспетчер) —
  **читает** `status` (+ target) и через per-type
  `StrategyActionExecutor` выбирает одну актуальную команду за проход;
  статус не пишет, кроме re-arm `RETRY_PENDING` на стадию пере-эмиссии.
- **SYSTEM-строки:** `SystemActionExecutor` — **читает** `status` и
  подтверждённые факты звеньев, эмитит следующую команду системного
  действия за проход (`docs/components/SystemActionExecutor.md`); re-arm
  `RETRY_PENDING` — аналогично.
- **Executor'ы команд** — **пишут** `status` и target по результату
  исполнения и подтверждённым фактам
  (`docs/components/ServiceCommandExecutor.md`). Звено, пишущее в `Deal`,
  двигает исполнение **той же транзакцией** (клауза —
  `docs/decisions/command-action-boundary.md` §5).
- **`RetryPolicyService`** — при падении звена переводит в
  `RETRY_PENDING` (инкремент `attemptCount`, `nextRetryAt`, `lastError`)
  либо в `FAILED` при исчерпании бюджета исполнения
  (`docs/components/RetryPolicyService.md`).

## Статусы

Значения — в `docs/models/domain/other/DealActionState.md` §Енумы.
Live (не финальные): `PLANNED`, `CREATED`, `SUBMITTED`, `RETRY_PENDING`.
Финальные: `COMPLETED`, `FAILED`, `SKIPPED`. **`COMPLETED` жёстко
терминален** — строка-исполнение не переиспользуется; новая надобность в
том же действии — новое исполнение (частичные ключи держат только живые
строки).

## Матрица переходов (STRATEGY)

```text
(нет)          -> PLANNED
PLANNED        -> CREATED | SKIPPED
CREATED        -> SUBMITTED | RETRY_PENDING | FAILED | SKIPPED
SUBMITTED      -> COMPLETED | RETRY_PENDING | FAILED
RETRY_PENDING  -> PLANNED | CREATED | FAILED
COMPLETED | FAILED | SKIPPED -> (терминальные, переходов нет)
```

- `PLANNED → CREATED` — `CREATE_*` создал локальную сущность, target
  заполнен.
- `CREATED → SUBMITTED` — `SUBMIT_*` (для `CLOSE_POSITION_COMMAND` —
  отправка close).
- `SUBMITTED → COMPLETED` — факт подтверждён `REFRESH_*`-контуром (не по
  ACK, `docs/rules/ack-not-runtime-truth.md`).
- `RETRY_PENDING → PLANNED|CREATED` — re-arm к последнему
  durable-свидетельству (`target == null` → `PLANNED`, target есть →
  `CREATED`); факт уже подтверждён — executor сразу пишет `COMPLETED`.
  Опасные команды перед повтором делают refresh/search
  (`docs/components/RetryPolicyService.md`).
- `* → FAILED` — бюджет исчерпан либо non-retryable; сделка идёт в
  `ERROR`/safety-flow.
- `* → SKIPPED` — исполнение стало неактуальным до подтверждения.

## Матрица переходов (SYSTEM)

Системное действие сущностей не создаёт — стадий `CREATED`/`SUBMITTED`
у него нет; «где мы» выводится из подтверждённых фактов звеньев
(`docs/components/SystemActionExecutor.md`), статус строки несёт только
исполнение целиком:

```text
(нет)          -> PLANNED
PLANNED        -> COMPLETED | RETRY_PENDING | FAILED | SKIPPED
RETRY_PENDING  -> PLANNED | FAILED
COMPLETED | FAILED | SKIPPED -> (терминальные, переходов нет)
```

- `(нет) → PLANNED` — handler затребовал действие; `SystemActionExecutor`
  материализует строку исполнения и эмитит первое звено по фактам.
- `PLANNED → COMPLETED` — все звенья подтверждены фактами; для
  завершающих действий — в одной транзакции с записью в `Deal`
  (`ENTRY_FINALIZED`; число+терминал — по звеньям, см. клаузу §5
  `command-action-boundary.md`).
- `* → RETRY_PENDING` — звено упало на retryable-ошибке; ждёт
  `nextRetryAt`; re-arm возвращает в `PLANNED` — следующее звено заново
  выводится из фактов (не из памяти прохода).
- `* → FAILED` — бюджет исполнения исчерпан (сквозной, β) либо
  non-retryable; сделка идёт ошибочной тропой; для добычи — плюс холд
  инструмента (`docs/rules/instrument-hold.md` §«Серия неудач»).
- `* → SKIPPED` — действие стало неактуальным (например, добыча при уже
  терминализованной сделке).

## Recovery после рестарта

Pending `ServiceCommand` как очередь не восстанавливаются
(`docs/rules/command-lifecycle.md`). Command-layer поднимает живые строки
исполнений по `status` + target (+ вид/тип действия), добирает exchange
facts и выбирает нужную команду заново; стадия системного действия
выводится из durable-фактов звеньев, поэтому флага «рестарт с нуля» нет.
Audit/история источником не является
(`docs/rules/audit-not-runtime-source.md`).

## Связи

- Жизненный цикл команды — `docs/rules/command-lifecycle.md`.
- Per-pass исполнитель системных действий —
  `docs/components/SystemActionExecutor.md`.
- Retry-механика — `docs/components/RetryPolicyService.md`.
- Классификация runtime-ошибок —
  `docs/rules/runtime-error-classification.md`.
- Решение о границе «команда ↔ действие» —
  `docs/decisions/command-action-boundary.md`.
