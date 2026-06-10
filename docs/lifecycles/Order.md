# Order lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходят `Order` и embedded `AttachedAlgoOrder`,
кто и при каких фактах их меняет.

Структура моделей — в `docs/models/domain/core/Order.md`.

## Кто управляет

Статусы меняет refresh/search/history flow по exchange facts: сырой
внешний статус нормализуется через `OrderExternalStatusResolver`
(ordinary order) и `AttachedAlgoOrderStateResolver` (attached, по
фактам), затем executor применяет результат. FSM/handlers **не**
используют `externalStatus` напрямую (см.
`docs/rules/external-status-resolution.md`).

> Resolver'ы, refresh-executors и команды (`REFRESH_ORDER`,
> `REFRESH_FILLS`, `SUBMIT_ORDER`, `AMEND_ORDER`, `CANCEL_ORDER`) —
> command-подсистема (шаг 4): `docs/components/` (executors, resolver'ы),
> `docs/rules/command-lifecycle.md`. Здесь — статусная механика,
> которой владеет сам `Order`.

## `Order.Status`

| Статус | Runtime-active | Final | Live risk | Смысл |
|---|---|---|---|---|
| `CREATED` | да | нет | нет на бирже | Локальная запись создана, нужен submit/recovery. |
| `PENDING` | да | нет | неизвестно | Submit был/мог быть, нужен refresh/search. ACK не runtime truth (`docs/rules/ack-not-runtime-truth.md`). |
| `ACTIVE` | да | нет | да | Ордер live, может исполниться. |
| `PARTIALLY_COMPLETED` | да | нет | да | Частично исполнен, есть live-остаток. |
| `COMPLETED` | нет | да | нет | Полностью исполнен. |
| `CANCELED` | нет | да | нет | Отменён. |
| `ERROR` | — | problem-final | неизвестно | Problem state; сделка идёт через safety/recovery. |

`isLive()` = CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED. ACTIVE
ставится только после refresh/search факта, не по ACK.

## `AttachedAlgoOrder.Status` и матрица переходов

| Статус | Active-like | Final | Смысл |
|---|---|---|---|
| `CREATED` | да | нет | Local intent вместе с parent Order. |
| `PENDING` | да | нет | Parent отправлен/мог быть, active-факт не подтверждён. |
| `ACTIVE` | да | нет | Подтверждена refresh-фактами, может сработать. |
| `COMPLETED` | нет | да | Сработала. |
| `CANCELED` | нет | да | Отменена/снята. |
| `ERROR` | — | problem-final | Ошибочное состояние. |

`isActiveLike()` = PENDING/ACTIVE; `isTerminal()` = COMPLETED/CANCELED/
ERROR. Допустимые переходы (`canTransitionTo`):

```text
null     -> CREATED
CREATED  -> PENDING | ERROR
PENDING  -> ACTIVE | CANCELED | ERROR
ACTIVE   -> COMPLETED | CANCELED | ERROR
COMPLETED | CANCELED | ERROR -> (терминальные, переходов нет)
```

Недопустимый переход → `IllegalStateException`.

### PENDING vs ACTIVE

```text
PENDING -> после SUBMIT_ORDER parent order (attached могла быть
           отправлена вместе с parent, active-факт не подтверждён)
ACTIVE  -> только после REFRESH_ORDER, если
           attached найдена в OrderExternalSnapshot.attachedAlgoOrders
           по internalId и нет failCode / failReason
```

Заполненные `failCode`/`failReason` → `ERROR`.

## Attached protection resolving (по фактам)

У OKX `attachAlgoOrds` нет полноценного `state`, поэтому attached
обновляется не простым status-resolver'ом, а по набору фактов.
Матчинг: `AttachedAlgoOrder.internalId ==
AttachedAlgoOrderExternalSnapshot.internalId` (OKX `attachAlgoClOrdId`).

## Missing attached protection policy

Отсутствие `AttachedAlgoOrderExternalSnapshot` в одном
`OrderExternalSnapshot.attachedAlgoOrders` — **не** финальный факт.
Решение зависит от статуса parent `Order`:

```text
parent CREATED / PENDING
  -> attached остаётся PENDING; ждём refresh / retry / recovery.

parent ACTIVE / PARTIALLY_COMPLETED
  -> дополнительный search-cycle (REFRESH_ORDER — внутр. pending/history,
     REFRESH_FILLS, REFRESH_POSITION);
     не делаем вывод по одному snapshot.

parent COMPLETED
  -> если позиция active и standalone main protection отсутствует:
       attached -> ERROR, closeReason = PROTECTION_LOST, Deal -> ERROR.
  -> если позиция закрыта — анализ fills/history:
       attached сработал -> COMPLETED / TRIGGERED;
       закрыта иначе      -> CANCELED / UNKNOWN;
       непонятно          -> ERROR / UNKNOWN.

parent CANCELED
  -> attached -> CANCELED, closeReason = PARENT_ORDER_CANCELED.

parent ERROR
  -> attached -> ERROR, closeReason = UNKNOWN.
```

## Exchange facts, обновляющие Order

- **`REFRESH_ORDER`** — обновляет `Order` из `OrderExternalSnapshot`
  (externalId, externalStatus, status через resolver, side, price, size,
  accumulatedFillSize, averagePrice, fee, attachedAlgoOrders), проходя
  evidence-cycle **внутри команды**
  (`docs/decisions/refresh-evidence-cycle-ownership.md`):
  - `GET /trade/order` — конкретный parent `Order`;
  - `orders-pending` — список live/pending по инструменту; не найден среди
    pending — **не** финальный факт отмены/исполнения;
  - `orders-history` (+ archive) — terminal-факт (COMPLETED / CANCELED /
    ERROR при нераспознанном статусе), когда не найден среди pending.
- **`REFRESH_FILLS`**: уточняет execution facts (accumulatedFillSize,
  averagePrice, fee); `Deal` напрямую не обновляет.

## ERROR-переходы (safety cascade)

Оба сценария — cross-cutting safety-каскад `Order -> ERROR`,
`Deal -> ERROR`, `Exchange -> HOLD` (см.
`docs/rules/external-status-resolution.md` и `docs/rules/exchange-hold.md`):

- **Unknown external status**: resolver бросает
  `ExternalStatusException(UNKNOWN_EXTERNAL_STATUS)` (не возвращает
  `Status.ERROR` как обычный mapping-результат) → boundary ловит →
  `Order.ERROR` + `closeReason = UNKNOWN_EXTERNAL_STATUS` → каскад.
- **Not found после полного evidence-cycle**:
  `ExternalNotFoundException` → `Order.ERROR` + `closeReason =
  MISSING_AFTER_REFRESH` → каскад. Пустой ответ одного endpoint —
  **не** основание для `MISSING_AFTER_REFRESH`.
- **Exchange invariant violation** (`reduceOnly` mismatch и пр.):
  `Order.ERROR` + `closeReason = EXCHANGE_INVARIANT_VIOLATION` →
  каскад (детали — `docs/models/mapping/Order.md`).
