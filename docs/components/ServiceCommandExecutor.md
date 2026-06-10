# ServiceCommandExecutor

## На какой вопрос отвечает этот файл

Кто исполняет атомарную команду и маршрутизирует её в конкретный executor
(компонент): контракт, общая семантика групп, обработка controlled
exceptions.

## Назначение

`ServiceCommandExecutor` исполняет одну атомарную `ServiceCommand` (см.
`docs/components/models/ServiceCommand.md`), маршрутизируя её в конкретный
executor по типу payload. Executor не принимает торговых решений.

Контракт (ориентир, не требование к точным именам):

```java
ServiceCommandExecutionResult execute(P payload, DealContext dealContext);
```

## Общая семантика групп executor'ов

Конкретные executor'ы следуют этим правилам и не вводят несовместимую
семантику:

- **`CREATE_*`** — создаёт локальную runtime-сущность в БД (доменный
  статус, рассчитанные параметры, `DealActionState.target`), сохраняет
  сущность и `DealActionState` одной транзакцией; на биржу не ходит.
- **`SUBMIT_*`** — отправляет созданную сущность на биржу или
  восстанавливает факт отправки по stable client id (`internalId →
  clOrdId` / `algoClOrdId`); перед повтором ищет сущность по client id.
  ACK не runtime truth.
- **`AMEND_*`** — отправляет изменение live-сущности; ACK не truth, факт
  новых параметров подтверждается refresh.
- **`CANCEL_*`** — отправляет отмену; ACK не truth; `closeReason` не
  перетирается, если уже установлен.
- **`REFRESH_*`** — читает exchange facts через `IntegrationService`, применяет
  status resolver, обновляет сущность, заполняет `closeReason` только если
  текущий `== null`; торговых решений не принимает, cleanup не запускает,
  audit/history как runtime-source не использует. Для сущностей с
  evidence-cycle (`REFRESH_ORDER` / `REFRESH_ALGO_ORDER` / `REFRESH_FILLS`)
  исполнитель обходит эндпоинты **внутри одной команды** (эскалация
  live → pending → history → archive), обрывается на первом успешном,
  полный обход — только при не-найдено, и сам выносит терминал
  (`MISSING_AFTER_REFRESH`); владение циклом —
  `docs/decisions/refresh-evidence-cycle-ownership.md`.

> **Pending/history эндпоинты** (`orders-pending` / `orders-history` /
> `orders-algo-pending` / `orders-algo-history`) — звенья evidence-cycle,
> который entity-refresh-исполнитель (`RefreshOrderExecutor` /
> `RefreshAlgoOrderExecutor`) обходит **внутри одной команды**
> (`docs/decisions/refresh-evidence-cycle-ownership.md`). Самостоятельных
> `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS`
> / `REFRESH_ALGO_ORDER_HISTORY` **нет** (CMD-Q3 закрыт: refresh-набор —
> ровно по одной команде на сущность). Перечисление **неизвестных**
> live-сущностей по инструменту (orphan / чужой риск; Precheck-cleanliness,
> AnomalyJob) bulk-командой больше не покрыто — **CMD-Q4**.

ACK как runtime truth не считается ни для submit/amend/cancel/close (см.
`docs/rules/ack-not-runtime-truth.md`). Жизненный цикл команды и принцип
«одна команда за проход» — `docs/rules/command-lifecycle.md`.

## Controlled exchange exceptions

Refresh/executor boundary ловит controlled exception, обновляет
runtime-сущность и отдаёт факты FSM/handler'у (таксономия и реакция —
`docs/rules/controlled-exchange-exceptions.md`). Resolver FSM-решение не
принимает и сущность не сохраняет; client/adapter сделку в новый статус
напрямую не переводит.

## Retry

При падении executor'а — через `docs/components/RetryPolicyService.md`:
`attemptCount`++, `nextRetryAt`, `lastError`, `DealActionState =
RETRY_PENDING`; при исчерпании попыток → `FAILED`.
