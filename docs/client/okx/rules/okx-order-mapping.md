# OKX order mapping

## На какой вопрос отвечает этот файл

Как OKX ordinary order (request/response) маппится в доменные
`Order` / `AttachedAlgoOrder`, как резолвится статус и как
проверяется reduce-only invariant.

## Контекст

Exchange-specific mapping для OKX. Доменная модель/статусы — в
`docs/models/core/Order.md` и `docs/lifecycles/Order.md`, эта дока
их не заменяет. Поля raw response — в
`docs/client/okx/models/OkxOrderResponse.md`. Command-flow
(`CREATE_ORDER → SUBMIT_ORDER → REFRESH_*`) — cross-cutting
command-подсистема (форвард-заметки в `.claude/work/questions/tasks/order.md`).

## Endpoints

- **Create** (`SUBMIT_ORDER`): `POST /api/v5/trade/order`.
- **Amend** (`AMEND_ORDER`): `POST /api/v5/trade/amend-order`.
- **Cancel** (`CANCEL_ORDER`): `POST /api/v5/trade/cancel-order`.
- **Order details** (`REFRESH_ORDER`): `GET /api/v5/trade/order`.
- **Pending** (`REFRESH_PENDING_ORDERS`): `GET /api/v5/trade/orders-pending`.
- **History** (`REFRESH_ORDER_HISTORY`): `GET /api/v5/trade/orders-history`,
  `GET /api/v5/trade/orders-history-archive` (archive — для старых
  периодов).

ACK любой create/amend/cancel (`sCode=0`) не является runtime truth
(`docs/rules/ack-not-runtime-truth.md`): финальные статусы
подтверждаются через order details / pending / history / archive.
`CANCEL_ORDER` не ставит `CANCELED`, `AMEND_ORDER` не считается
подтверждённым — без refresh/search/history факта.

## ClientService константы

`OkxClientService` сам выставляет `tdMode = isolated`, `posSide = net`
(не из domain `Order`, не как произвольные аргументы). В `Order` не
хранятся.

## OrderResponse → OrderExternalSnapshot

См. таблицу полей в `docs/client/okx/models/OkxOrderResponse.md`.
Правила: `empty → null`; numeric → `BigDecimal`; `state` остаётся raw
в `externalStatus` (резолвинг позже); `reduceOnly` не маппится в
snapshot.

## Резолвинг статуса (OrderExternalStatusResolver)

`externalStatus` → `Order.Status` (FSM напрямую не использует, см.
`docs/rules/external-status-resolution.md`):

| OKX raw `state` | Domain status | closeReason |
|---|---|---|
| `live` | `ACTIVE` | `null` |
| `partially_filled` | `PARTIALLY_COMPLETED` | `null` |
| `filled` | `COMPLETED` | `FILLED` |
| `canceled` | `CANCELED` | context-dependent |
| `mmp_canceled` | `CANCELED` | `UNKNOWN` (можно расширить) |
| unknown value | — | бросает `ExternalStatusException(UNKNOWN_EXTERNAL_STATUS)` → safety-каскад |

### Order evidence-cycle / not found

`ExternalNotFoundException` — только после **полного** order
evidence-cycle: `GET /trade/order` → `orders-pending` →
`orders-history` → `orders-history-archive` (если history не
покрывает период). Поиск: есть `externalId` → по `ordId`; нет →
по `clOrdId = internalId`. Пустой ответ одного endpoint не даёт
`MISSING_AFTER_REFRESH`. После полного цикла без находки →
`Order.ERROR` + `MISSING_AFTER_REFRESH` → safety-каскад
(`docs/rules/external-status-resolution.md`). Доп. факты сделки
(`REFRESH_FILLS`, `REFRESH_POSITION`) запрашиваются отдельными
командами; `RefreshOrderExecutor` не сопровождает сделку целиком.

## Domain Order → request mapping

**Create** (`CreateOrderRequest`): `Instrument.externalId → instId`;
`isolated → tdMode`; `Order.side → side`; `net → posSide`;
`Order.type/exec settings → ordType`; `Order.size → sz`;
`Order.price → px` (если нужен типу); `Order.internalId → clOrdId`;
`Order.positionReducingOnly → reduceOnly`; `Order.attachedAlgoOrders
→ attachAlgoOrds` (future DTO-поле для entry-with-attached-SL).
После successful submit: можно сохранить `externalId` (если вернулся
`ordId`); `Order` остаётся `PENDING` до refresh/search/history;
per-item error классифицируется (retryable → RETRY_PENDING;
non-retryable → `Order.ERROR`/`Deal.ERROR` по ситуации).

**Amend**: `instId`, `ordId` (предпочтительно), `clOrdId`, `newSz`,
`newPx`. **Cancel**: `instId`, `ordId` (если известен) / `clOrdId`.

## reduce-only invariant

`Order.positionReducingOnly` (доменное намерение) → OKX `reduceOnly`
в create request. `OrderResponse.reduceOnly` **не** маппится в
`OrderExternalSnapshot` и не обновляет `positionReducingOnly` — но
adapter может сравнить:

```text
expected = Order.positionReducingOnly
actual   = OrderResponse.reduceOnly
mismatch -> EXCHANGE_INVARIANT_VIOLATION
         -> Order.ERROR, closeReason = EXCHANGE_INVARIANT_VIOLATION,
            Deal.ERROR, Exchange.HOLD
```

Если биржа не поддерживает reduce-only/close-only — adapter может
проигнорировать `positionReducingOnly`; unsupported exchange на
первом этапе не блокируем.

## Attached protection

`OrderResponse.attachAlgoOrds[*]` →
`AttachedAlgoOrderExternalSnapshot`; матчинг по `internalId`
(`attachAlgoClOrdId`). Status: `PENDING` после `SUBMIT_ORDER`;
`ACTIVE` только после `REFRESH_ORDER`/`REFRESH_PENDING_ORDERS`, если
найден по `internalId` и нет `failCode`/`failReason`; заполненные
`failCode`/`failReason` → `ERROR`. Missing-policy по статусу parent —
в `docs/lifecycles/Order.md`.

## Целевые расхождения с текущим кодом (target refactoring)

- `createOrder` не должен принимать `tradeMode`/`positionSide`
  аргументами — `OkxClientService` сам ставит `isolated`/`net`.
- `CreateOrderRequest` должен принимать `reduceOnly` и
  `attachAlgoOrds` (для `ENTRY_ATTACHED_STOP_LOSS`).
- `OrderResponse.state` комментарий: raw статус OKX; pending —
  `live`/`partially_filled`; details/history — `filled`/`canceled`/
  `mmp_canceled` и др. terminal.
- `OrderResponse.reduceOnly` → только adapter invariant validation,
  не в `OrderExternalSnapshot`.
