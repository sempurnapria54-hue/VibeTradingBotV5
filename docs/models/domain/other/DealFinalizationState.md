# DealFinalizationState

## На какой вопрос отвечает этот файл

Что это за модель `DealFinalizationState`: структура, енумы,
retry-состояние, инварианты, персистентность — где живёт persisted
retry-state финализации сделки.

Статусы и переходы — в `docs/lifecycles/DealFinalizationState.md`.
Почему отдельная сущность, а не обобщение `DealActionState` —
`docs/decisions/deal-finalization-state-materialization.md` (закрытие
DEAL-Q1).

## Назначение

`DealFinalizationState` — **persisted** операционная модель
runtime-состояния выполнения одной **финализационной команды**
(lifecycle/system action) в рамках `Deal`: `FINALIZE_DEAL_ENTRY`,
`FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`, `MARK_DEAL_EMERGENCY_CLOSED`,
`MARK_DEAL_ERROR` (`docs/components/models/ServiceCommand.md`). Несёт
идемпотентность/recovery/retry финализационного контура там, где
`DealActionState` не подходит: финализация **не привязана к
`StrategyAction`** (нет `strategyActionId`), а её команды многокомандны и
ретраятся **по-командно**.

Не торговая бизнес-сущность (PnL и бизнес-цикл — у `Deal`), а операционное
состояние финализационного исполнения — поэтому
`docs/models/domain/other/`, по аналогии с `DealActionState` /
`AnomalyReport` (`docs/decisions/deal-finalization-state-materialization.md`,
`.claude/decisions/model-layer-ontology.md`).

Цель финализации — всегда **сама `Deal`** (`dealId`); отдельного
`RuntimeTarget` нет (в отличие от `DealActionState`, у которого target —
порождённая `Order`/`AlgoOrder`/`Position`).

## Структура

Java-модель, наследует retry-состояние от базового `Retryable` (см.
`docs/components/RetryPolicyService.md`).

| Поле | Тип | Обязательно | Назначение |
|---|---|---|---|
| `id` | `Long` | да | Внутренний идентификатор в БД. |
| `dealId` | `Long` | да | Сделка, чья финализация отслеживается (она же — цель). |
| `type` | `DealFinalizationType` | да | Какая финализационная команда отслеживается (дискриминатор). |
| `status` | `DealFinalizationStateStatus` | да | Статус исполнения финализации (см. lifecycle). |

Retry-поля из базы `Retryable` (`docs/components/RetryPolicyService.md`):
`attemptCount`, `maxAttempts`, `nextRetryAt`, `lastError` (`RetryError`,
jsonb). Авторитет предела повторов — policy (живьём), поле `maxAttempts` —
снимок для истории (см. `docs/components/RetryPolicyService.md`
§«Авторитет `maxAttempts`»).

## Енумы

### `DealFinalizationType`

- `FINALIZE_ENTRY` — консолидация результата входа (`FINALIZE_DEAL_ENTRY`).
- `FINALIZE_EXIT` — консолидация фактов штатного выхода **и расчёт числа
  `resultProfit`** (`FINALIZE_DEAL_EXIT`; шаг 7 — см. §«Чего не хранит»).
- `MARK_CLOSED` — терминальное ребро штатного закрытия (`MARK_DEAL_CLOSED`).
- `MARK_EMERGENCY_CLOSED` — терминальное ребро аварийного закрытия
  `ERROR → EMERGENCY_CLOSED` (`MARK_DEAL_EMERGENCY_CLOSED`, симметрично
  `MARK_CLOSED`; `docs/decisions/pnl-finalization-mechanics.md` реш.3).
- `MARK_ERROR` — пометка ошибочного состояния сделки (`MARK_DEAL_ERROR`).

(1:1 с финализационными значениями `ServiceCommandType`.)

### `DealFinalizationStateStatus`

- `PENDING` — финализация выбрана, команды ещё не было / не подтверждена.
- `COMPLETED` — финализация подтверждена (терминальное ребро сделано /
  факты консолидированы).
- `RETRY_PENDING` — executor упал на retryable-ошибке; ждёт повтора по
  `nextRetryAt` (`docs/components/RetryPolicyService.md`).
- `FAILED` — retry исчерпан либо ошибка non-retryable
  (`INTERNAL_ERROR`/`VALIDATION_ERROR`,
  `docs/rules/runtime-error-classification.md`). Финализация не доведена →
  сделка идёт ошибочной тропой (см. lifecycle и
  `docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).

## Инварианты

- **`UNIQUE(deal_id, finalization_type)`** — на одну финализационную
  команду сделки приходится ровно одно состояние исполнения. Ключ
  фиксируется здесь (модель — место истины ключа уникальности, см.
  `docs/rules/idempotency-via-unique.md`); идемпотентность исполнения —
  через upsert по этому ключу. По-командный ретрай: счётчик на `(deal,
  type)`, а не один на сделку.
- Цель финализации — всегда сама `Deal` (`dealId`); `RuntimeTarget` не
  заводится.
- `DealFinalizationState` переживает рестарт: после падения command-layer
  пересобирает нужную финализационную команду по `type` + `status` +
  фактам сделки/биржи; pending `ServiceCommand` как очередь не
  восстанавливаются (`docs/rules/command-lifecycle.md`).

## Персистентность

- `lastError` (`RetryError`) — вложенный объект → **jsonb** на строке (по
  `docs/rules/persistence-representation.md`).
- Скалярные поля (`dealId`, `type`, `status`, retry-скаляры) — обычные
  колонки.
- Таблица — `deal_finalization_states` (множественное число, см.
  `.claude/rules/codestyle.md` §«Схема БД»).

## Чего не хранит

- Расчёт `resultProfit`/breakdown PnL — у `FinalizeDealExitExecutor` (шаг 7,
  `docs/decisions/result-profit-source.md`). **Число durable-хранится полем
  `Deal.resultProfit`** — `FINALIZE_DEAL_EXIT` пишет его на `Deal` в **одной
  транзакции** с `DealFinalizationState(FINALIZE_EXIT) = COMPLETED` (носитель
  staged-числа = само поле `Deal`, рестарт-safe; N7,
  `docs/decisions/pnl-finalization-mechanics.md` реш.2).
  `DealFinalizationState` P&L-число **не несёт** — только retry-state механики
  финализации.
- Историю исполнения команд (audit/timeline — отдельный слой, не runtime,
  `docs/rules/audit-not-runtime-source.md`).
- Параметры команды (живут в `ServiceCommandPayload` runtime, не
  персистятся).

## Связи

- Читается `DealFinalizationCommandFactory` (выбор финализационной команды
  по `status`) и финализационными executor'ами; пишется ими и
  `RetryPolicyService`.
- Финализационные executor'ы — `docs/components/FinalizeDealEntryExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/MarkDealErrorExecutor.md`.
- Retry-база — `docs/components/RetryPolicyService.md` (`Retryable`,
  `RetryError`).
- Решение о доме retry-state —
  `docs/decisions/deal-finalization-state-materialization.md`.
- Связанный action-контур — `docs/models/domain/other/DealActionState.md`.
