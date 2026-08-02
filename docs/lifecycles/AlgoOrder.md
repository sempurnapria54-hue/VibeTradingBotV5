# AlgoOrder lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `AlgoOrder`, кто и при каких фактах их
меняет.

Структура модели — в `docs/models/domain/core/AlgoOrder.md`.

## Кто управляет

Статусы меняет refresh/service layer по exchange facts: сырой
внешний статус нормализуется через `AlgoOrderExternalStatusResolver`
(per-exchange, OKX — `OkxAlgoOrderExternalStatusResolver`), затем
executor применяет результат через строгие transition-методы. FSM/
handlers **не** используют `externalStatus` напрямую (см.
`docs/rules/external-status-resolution.md`).

> Resolver'ы, executors и команды (`CREATE_ALGO_ORDER_COMMAND`,
> `SUBMIT_ALGO_ORDER_COMMAND`, `CANCEL_ALGO_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`) —
> command-подсистема (шаг 4): `docs/components/` (executors,
> resolver'ы), `docs/rules/command-lifecycle.md`. Амендной команды
> нет — ремоделирование через REPLACE-оркестрацию
> (`docs/decisions/replace-not-amend.md`).

## Статусы и live semantics

| Статус | Runtime-live | Live on exchange | Смысл |
|---|---|---|---|
| `CREATED` | да | нет | Локальный intent, на бирже не подтверждён. |
| `PENDING` | да | неизвестно | Submit/cancel мог быть, нужен refresh/search/history. ACK не runtime truth (`docs/rules/ack-not-runtime-truth.md`). |
| `ACTIVE` | да | да | Активен или ожидает срабатывания. |
| `PARTIALLY_COMPLETED` | да | требует выяснения | Частично сработал — exchange-driven recovery-status, не цель стратегии. |
| `COMPLETED` | нет | нет | Сработал. |
| `CANCELED` | нет | нет | Отменён. |
| `ERROR` | — | problem-final | Ошибочное состояние; сделка → safety/error-flow. |

`isLive()` = CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED. ACTIVE
ставится только после refresh-факта, не по ACK.

## Матрица переходов

Строгий `transitTo` (недопустимый переход → `IllegalStateException`):

```text
null                 -> CREATED
CREATED              -> PENDING | ERROR
PENDING              -> ACTIVE | COMPLETED | CANCELED | ERROR
ACTIVE               -> PARTIALLY_COMPLETED | COMPLETED | CANCELED | ERROR
PARTIALLY_COMPLETED  -> COMPLETED | CANCELED | ERROR
COMPLETED | CANCELED | ERROR -> (терминальные)
```

`toComplete()` ставит `COMPLETED` + `closeReason = TRIGGERED`;
`toCancel(reason)`/`toError(reason)` требуют ненулевой reason.

## Резолвинг статуса (OKX state → domain)

`AlgoOrderExternalStatusResolver` отвечает только за `external status
→ domain status`; не сохраняет сущность, не принимает FSM-решения, не
создаёт команды, не запускает cleanup/kill-switch.

| OKX `state` | Доменная реакция |
|---|---|
| `live` | `ACTIVE` |
| `pause` | `ACTIVE` (active-like: ещё существует, влияет на risk/cleanup) |
| `partially_effective` | `PARTIALLY_COMPLETED` (recovery-status, добрать факты) |
| `effective` | `COMPLETED`, `closeReason = TRIGGERED` |
| `canceled` | `CANCELED`, `closeReason` из cancel intent (не из state) |
| `order_failed` | `ExternalStatusException(ORDER_FAILED)` |
| `partially_failed` | `ExternalStatusException(PARTIALLY_FAILED)` (problem-state, часть могла выполниться) |
| unknown | `ExternalStatusException(UNKNOWN_EXTERNAL_STATUS)` |

Resolver не возвращает `Status.ERROR` как обычный mapping-результат —
problem/unknown статусы идут через `ExternalStatusException` и
safety-каскад (`docs/rules/external-status-resolution.md`).

## ERROR-переходы (safety cascade)

`AlgoOrder -> ERROR`, `Deal -> ERROR`, `Exchange -> HOLD`
(`docs/rules/external-status-resolution.md`, `docs/rules/exchange-hold.md`):

- **Problem/unknown external status** (`order_failed` →
  `ORDER_FAILED`; `partially_failed` → `PARTIALLY_FAILED`; unknown →
  `UNKNOWN_EXTERNAL_STATUS`) — через `ExternalStatusException`.
- **Exchange invariant violation** (`tdMode`/`posSide`/`side`/
  `ordType`/`reduceOnly` mismatch) → `ExternalInvariantViolationException`
  → `closeReason = EXCHANGE_INVARIANT_VIOLATION` (детали — в
  `docs/models/mapping/AlgoOrder.md`).
- **Not found после полного algo evidence-cycle** →
  `ExternalNotFoundException` → `closeReason = MISSING_AFTER_REFRESH`.
  Пустой `data=[]` одного endpoint — **не** основание; нужен полный
  цикл algo-sources (details + pending + history).

Размеры **не** валидируются как hard invariant: `AlgoOrder.size` —
рассчитанный intent, `externalSize` (`actualSz`) — внешний факт,
могут отличаться из-за partial trigger/execution.

## Cancel / replace (по фактам, не по ACK)

`CANCEL_ALGO_ORDER_COMMAND` не финализирует `AlgoOrder`: ACK не runtime
truth. FSM сначала делает refresh, потом при необходимости создаёт
cancel; executor отправляет команду, сохраняет ACK, не финализирует.
После cancel intent верим exchange fact: `effective` →
`COMPLETED`/`TRIGGERED`; `partially_effective` →
`PARTIALLY_COMPLETED`; `canceled` → `CANCELED` + `closeReason` из
cancel intent (`CANCELED_BY_STRATEGY` / `REPLACED_BY_STRATEGY` /
`KILL_SWITCH` / `MANUAL_CANCEL`); `order_failed`/`partially_failed` →
`ExternalStatusException`. После рестарта: собрать `DealContext`,
refresh/history, верить фактам; если всё ещё live и cancel нужен —
повторить.

Ремоделирование (REPLACE) algo-order амендной команды не имеет:
новая сущность ставится первой (protective-порядок), старая
отменяется с `closeReason = REPLACED_BY_STRATEGY` после
подтверждения новой фактом; цепочка — `replacesInternalId`
(`docs/decisions/replace-not-amend.md`).

## Граница refresh-executor

`RefreshAlgoOrderExecutor` обновляет **только** `AlgoOrder` (по
algo-order endpoints): `externalId`, `externalStatus`, `failCode`,
`externalSize`, `externalPrice`, `externalTriggerTime`, condition
external fields, `linkedOrderExternalIds`, `status` через resolver.
Не ходит в ordinary orders / fills / positions — эти команды
выбирает FSM/DealOrchestrator после анализа `DealContext`.
`linkedOrderExternalIds` только сохраняются: на первом этапе не
создают `Order`/`DealActionState`, не являются FSM-target.
