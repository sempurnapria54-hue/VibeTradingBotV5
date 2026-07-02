# DealActionState

## На какой вопрос отвечает этот файл

Что это за модель `DealActionState`: структура, вложенный
`RuntimeTarget`, енумы, retry-состояние, инварианты, персистентность.

Статусы и переходы — в `docs/lifecycles/DealActionState.md`.

## Назначение

`DealActionState` — **persisted** операционная модель runtime-состояния
выполнения одного `StrategyAction` в рамках `Deal`. Отвечает на вопрос:
«на каком шаге исполнения находится это действие сделки и какую
runtime-сущность оно породило». Несёт идемпотентность/recovery/retry
command-layer'а и связь `StrategyAction ↔ Order/AlgoOrder/Position`.

Не торговая бизнес-сущность (не про PnL и не про бизнес-цикл сделки —
тем владеет `Deal`), а операционное состояние сопровождения исполнения
— поэтому `docs/models/domain/other/`, а не `aggregate`, по аналогии с
`AnomalyReport` (см. `docs/decisions/deal-action-state-materialization.md`,
`.claude/decisions/model-layer-ontology.md`).

`DealActionState` — единственный держатель связи `StrategyAction ↔
runtime-сущность`: `Order`/`AlgoOrder`/`Position` **не** хранят
`strategyActionId`/`strategyActionKey`/`role`/`level` (см. их модели и
`docs/rules/audit-not-runtime-source.md`).

## Структура

Java-модель, наследует retry-состояние от базового `Retryable` (см.
`docs/components/RetryPolicyService.md`).

| Поле | Тип | Обязательно | Назначение |
|---|---|---|---|
| `id` | `Long` | да | Внутренний идентификатор в БД. |
| `dealId` | `Long` | да | Сделка, в рамках которой выполняется action. |
| `strategyActionId` | `Long` | да | Действие стратегии, чьё исполнение отслеживается. |
| `target` | `RuntimeTarget` | нет | Куда нацелено действие — какую runtime-сущность оно породило/затрагивает (jsonb). `null`, пока сущность не создана (`PLANNED`). |
| `status` | `DealActionStateStatus` | да | Статус исполнения action (см. lifecycle). |

Retry-поля из базы `Retryable` (`docs/components/RetryPolicyService.md`):
`attemptCount`, `maxAttempts`, `nextRetryAt`, `lastError` (`RetryError`,
jsonb). Авторитет предела повторов — **policy (читается живьём)**;
`maxAttempts` на сущности — снимок для истории, не операторное значение
(см. `docs/components/RetryPolicyService.md` §«Авторитет `maxAttempts`»).

## `RuntimeTarget` (раздел `DealActionState`)

«Куда нацелено действие» — вложенный value-объект, не самостоятельная
сущность (раздел модели по `.claude/decisions/model-granularity.md`): без
своего `DealActionState` смысла не имеет.

| Поле | Тип | Назначение |
|---|---|---|
| `entityType` | `TargetEntityType` | Тип runtime-сущности, на которую нацелено действие. |
| `entityId` | `Long` | Локальный `id` этой сущности; `null` для `NONE`. |

Executor'ы `CREATE_*` заполняют `target` при создании сущности
(`RuntimeTarget(ORDER, orderId)` / `RuntimeTarget(ALGO_ORDER,
algoOrderId)`); `REFRESH_POSITION` — `RuntimeTarget(POSITION, positionId)`
по факту материализации позиции.

## Енумы

### `DealActionStateStatus`

- `PLANNED` — action выбран, команды ещё не было (`target == null`).
- `CREATED` — `CREATE_*` создал локальную сущность; `target` заполнен.
- `SUBMITTED` — `SUBMIT_*`/`CANCEL_*`/`CLOSE_POSITION`
  отправлен на биржу; факт ещё не подтверждён (ACK не runtime truth).
- `COMPLETED` — факт исполнения подтверждён `REFRESH_*`-контуром.
- `RETRY_PENDING` — executor упал на retryable-ошибке; ждёт повтора по
  `nextRetryAt` (см. `docs/components/RetryPolicyService.md`).
- `FAILED` — retry исчерпан либо ошибка non-retryable
  (`INTERNAL_ERROR`/`VALIDATION_ERROR`, см.
  `docs/rules/runtime-error-classification.md`).
- `SKIPPED` — action стал неактуален и не исполняется (условие
  истекло, состояние сделки изменилось).

### `TargetEntityType`

- `ORDER` — ordinary order.
- `ALGO_ORDER` — standalone algo-order.
- `POSITION` — позиция.
- `DEAL` — сама сделка (lifecycle/system action на самой сделке).
  **Финализация** (`FINALIZE_DEAL_*`/`MARK_DEAL_*`) с шага 6 ведётся
  отдельной сущностью `DealFinalizationState`
  (`docs/models/domain/other/DealFinalizationState.md`), **не**
  `DealActionState` (финализация не привязана к `StrategyAction`; см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- `BALANCE` — баланс (`REFRESH_BALANCE`).
- `NONE` — action без runtime-target-сущности (`entityId == null`).

## REPLACE-действия (две ноги, одна запись)

`StrategyActionType.REPLACE` (`docs/decisions/replace-not-amend.md`)
исполняется как CREATE-надмножество: действие порождает **новую**
runtime-сущность (`target` = новая, `replacesInternalId` = `internalId`
замещаемой) плюс cancel-ногу по старой. Новых статусов
`DealActionStateStatus` нет — `StrategyActionOrchestrator` (через per-type
`StrategyActionExecutor`) выводит следующую команду **из фактов** («одна
актуальная команда за проход»):

- protective (`positionReducingOnly = true`): новой нет →
  `CREATE_*`; не отправлена → `SUBMIT_*`; новая подтверждена ACTIVE
  фактом и старая жива → `CANCEL_*` старой
  (`REPLACED_BY_STRATEGY`); старая терминальна → `COMPLETED`;
- entry (не reduce-only): зеркально — cancel-нога первой, place
  после подтверждения терминала старой (fill-race разбирается по
  фактам: исполнена → действие `SKIPPED`; частично — место
  пересчёта остатка).

Замещаемая сущность резолвится из `DealContext.actionStates` по
цепочке замещений от target-action (последнее живое звено по
`replacesInternalId`). Представление последовательности ног
(вывод из фактов vs явные фазы) — деталь `CODE`; концептуально
статусной машине хватает существующих значений.

## Инварианты

- **`UNIQUE(deal_id, strategy_action_id)`** — на одно действие стратегии
  в рамках сделки приходится ровно одно состояние исполнения. Ключ
  фиксируется здесь (модель — место истины ключа уникальности, см.
  `docs/rules/idempotency-via-unique.md`); идемпотентность исполнения —
  через upsert по этому ключу.
- `strategyActionId` хранится **только** здесь, не в
  `Order`/`AlgoOrder`/`Position`.
- `target` заполняется executor'ом при создании/материализации
  сущности, не FSM напрямую.
- `DealActionState` переживает рестарт: после падения command-layer
  пересобирает нужную команду по `status` + `target` + exchange facts,
  pending `ServiceCommand` как очередь не восстанавливаются (см.
  `docs/rules/command-lifecycle.md`).

## Персистентность

- `target` (`RuntimeTarget`) и `lastError` (`RetryError`) — вложенные
  объекты → **jsonb** на строке `DealActionState` (по
  `docs/rules/persistence-representation.md`: вложенные объекты в БД —
  jsonb на строке владельца, пока на них нет внешних FK-ссылок).
- Скалярные поля (`dealId`, `strategyActionId`, `status`, retry-скаляры)
  — обычные колонки.

## Чего не хранит

- Историю исполнения команд (audit/timeline — отдельный слой, не runtime,
  см. `docs/rules/audit-not-runtime-source.md`).
- `role`/`level`/`strategyActionKey` стратегии (контекст — через
  `StrategyAction` по `strategyActionId`).
- Сами параметры команды (живут в `ServiceCommandPayload` runtime,
  не персистятся).

## Связи

- Держатель связи для `Order` / `AlgoOrder` / `Position`
  (`docs/models/domain/core/`).
- Читается `StrategyActionOrchestrator` (выбор команды по `status` через
  per-type `StrategyActionExecutor`) и `DealContext.actionStates`; пишется
  executor'ами и `RetryPolicyService`.
- Retry-база — `docs/components/RetryPolicyService.md` (`Retryable`,
  `RetryError`).
- Решение о материализации/представлении —
  `docs/decisions/deal-action-state-materialization.md`.
