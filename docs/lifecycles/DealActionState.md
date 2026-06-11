# DealActionState lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `DealActionState`, кто и при каких фактах
их меняет.

Структура модели — в `docs/models/domain/other/DealActionState.md`.

## Кто управляет

Статус ведёт command-layer, не FSM напрямую:

- **`ServiceCommandFactory`** — **читает** `status` (+ `target`), чтобы
  выбрать одну актуальную команду за проход (`docs/components/ServiceCommandFactory.md`);
  сам статус не пишет.
- **Executor'ы** (`CREATE_*`/`SUBMIT_*`/`CANCEL_*`/
  `CLOSE_POSITION`/`REFRESH_*`) — **пишут** `status` и `target` по
  результату исполнения и подтверждённым фактам (см.
  `docs/components/ServiceCommandExecutor.md`).
- **`RetryPolicyService`** — при падении executor'а переводит в
  `RETRY_PENDING` (инкремент `attemptCount`, `nextRetryAt`, `lastError`)
  либо в `FAILED` при исчерпании попыток (`docs/components/RetryPolicyService.md`).

Матрица собрана из установленного: flow выбора команды
(`ServiceCommandFactory`), retry-политика (`RetryPolicyService`),
классификация ошибок (`docs/rules/runtime-error-classification.md`).

## Статусы

Значения — в `docs/models/domain/other/DealActionState.md` §Енумы.
Live (не финальные): `PLANNED`, `CREATED`, `SUBMITTED`, `RETRY_PENDING`.
Финальные: `COMPLETED`, `FAILED`, `SKIPPED`.

## Матрица переходов

```text
(нет)          -> PLANNED
PLANNED        -> CREATED | SKIPPED
CREATED        -> SUBMITTED | RETRY_PENDING | FAILED | SKIPPED
SUBMITTED      -> COMPLETED | RETRY_PENDING | FAILED
RETRY_PENDING  -> CREATED | SUBMITTED | COMPLETED | FAILED
COMPLETED | FAILED | SKIPPED -> (терминальные, переходов нет)
```

- `PLANNED → CREATED` — `CREATE_*` создал локальную сущность, `target`
  заполнен.
- `CREATED → SUBMITTED` — `SUBMIT_*` (для `CLOSE_POSITION` — отправка
  close; для `REFRESH`-only action target-сущность может не создаваться).
- `SUBMITTED → COMPLETED` — факт подтверждён `REFRESH_*`-контуром (не по
  ACK, см. `docs/rules/ack-not-runtime-truth.md`).
- `* → RETRY_PENDING` — executor упал на retryable `EXCHANGE_ERROR`;
  опасные команды (`SUBMIT_*`/`CANCEL_*`/`CLOSE_POSITION`) перед повтором
  делают refresh/search (`docs/components/RetryPolicyService.md`).
- `RETRY_PENDING → CREATED|SUBMITTED|COMPLETED` — повтор возобновляет
  нужную команду по фактам; если факт уже подтверждён — сразу
  `COMPLETED`.
- `* → FAILED` — retry исчерпан либо `INTERNAL_ERROR`/`VALIDATION_ERROR`
  (non-retryable); сделка идёт в `ERROR`/safety-flow через `ErrorHandler`.
- `* → SKIPPED` — action стал неактуален (условие истекло, изменилось
  состояние сделки) до подтверждения исполнения.

## Recovery после рестарта

Pending `ServiceCommand` как очередь не восстанавливаются (см.
`docs/rules/command-lifecycle.md`). Command-layer поднимает
`DealActionState` по `status` + `target`, добирает exchange facts
(`REFRESH_*`/search/history) и выбирает нужную команду заново. Audit/
история источником не является (`docs/rules/audit-not-runtime-source.md`).

## Связи

- Жизненный цикл команды — `docs/rules/command-lifecycle.md`.
- Retry-механика — `docs/components/RetryPolicyService.md`.
- Классификация runtime-ошибок —
  `docs/rules/runtime-error-classification.md`.
