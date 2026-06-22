# DealFinalizationState lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `DealFinalizationState`, кто и при каких
фактах их меняет.

Структура модели — в `docs/models/domain/other/DealFinalizationState.md`.

## Кто управляет

Статус ведёт command-layer финализационного контура, не FSM напрямую:

- **`ServiceCommandFactory`** — **читает** `status` (+ `type`), чтобы
  выбрать одну актуальную финализационную команду за проход
  (`docs/components/ServiceCommandFactory.md`); сам статус не пишет.
- **Финализационные executor'ы** (`FinalizeDealEntryExecutor`,
  `FinalizeDealExitExecutor`, `MarkDealClosedExecutor`,
  `MarkDealErrorExecutor`) — **пишут** `status` по результату исполнения и
  подтверждённым фактам.
- **`RetryPolicyService`** — при падении executor'а переводит в
  `RETRY_PENDING` (инкремент `attemptCount`, `nextRetryAt`, `lastError`)
  либо в `FAILED` при исчерпании попыток
  (`docs/components/RetryPolicyService.md`).

## Статусы

Значения — в `docs/models/domain/other/DealFinalizationState.md` §Енумы.
Live (не финальные): `PENDING`, `RETRY_PENDING`. Финальные: `COMPLETED`,
`FAILED`.

## Матрица переходов

```text
(нет)          -> PENDING
PENDING        -> COMPLETED | RETRY_PENDING | FAILED
RETRY_PENDING  -> COMPLETED | RETRY_PENDING | FAILED
COMPLETED | FAILED -> (терминальные, переходов нет)
```

- `(нет) → PENDING` — handler решил финализировать; command-layer
  материализует строку `(deal, type)` (upsert), фабрика эмитит
  финализационную команду по её статусу.
- `PENDING → COMPLETED` — финализация подтверждена фактами (терминальное
  ребро сделано / факты консолидированы), идемпотентно (повтор на уже
  сделанной финализации — no-op → `COMPLETED`).
- `* → RETRY_PENDING` — executor упал на retryable `EXCHANGE_ERROR`
  (`docs/rules/runtime-error-classification.md`); ждёт `nextRetryAt`.
- `RETRY_PENDING → COMPLETED` — повтор возобновляет финализацию по фактам;
  если факт уже подтверждён — сразу `COMPLETED`.
- `* → FAILED` — retry исчерпан либо `INTERNAL_ERROR`/`VALIDATION_ERROR`
  (non-retryable). Финализация не доведена → сделка идёт ошибочной тропой
  (`MarkDealErrorExecutor` / `ErrorHandler` / safety-flow); терминальный
  контракт — `docs/lifecycles/Deal.md` §«Терминальный контракт
  финализации». Живым риском сделка не зависает — доходит до терминала.

## Recovery после рестарта

Pending `ServiceCommand` как очередь не восстанавливаются
(`docs/rules/command-lifecycle.md`). Command-layer поднимает
`DealFinalizationState` по `type` + `status`, добирает факты сделки/биржи и
выбирает нужную финализационную команду заново. Идемпотентность — через
`UNIQUE(deal_id, finalization_type)` (повтор не плодит вторую финализацию).
Audit/история источником не является
(`docs/rules/audit-not-runtime-source.md`).

## Связи

- Жизненный цикл команды — `docs/rules/command-lifecycle.md`.
- Retry-механика — `docs/components/RetryPolicyService.md`.
- Классификация runtime-ошибок —
  `docs/rules/runtime-error-classification.md`.
- Терминальный контракт сделки — `docs/lifecycles/Deal.md`.
- Прецедент статусной механики операционной модели —
  `docs/lifecycles/DealActionState.md`.
