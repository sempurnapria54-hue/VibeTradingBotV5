# OKX Order mapping

> Статус документа: exchange-specific mapping-дока для ordinary `Order` на OKX.
>
> Документ описывает, как OKX request/response DTO и external snapshot превращаются в доменные `Order` / `AttachedAlgoOrder`.
>
> Документ не заменяет `Order.md`. Доменная модель, статусы и runtime-семантика `Order` описаны в `Order.md`.
>
> Command-flow `CREATE_ORDER -> SUBMIT_ORDER -> REFRESH_*` описан в `Сервисные команды.md`.

---

# 1. Назначение

Эта дока отвечает на вопрос:

```text
как данные OKX по ordinary order попадают в доменную модель Order
и какие поля OKX request/response используются client-layer / mapper-layer.
```

Документ нужен для:

* `OkxClientService`;
* `OkxRestClient`;
* OKX request DTO;
* OKX response DTO;
* `OrderMapper`;
* `OrderExternalSnapshot`;
* `OrderExternalStatusResolver`;
* refresh/search/history executor'ов по ordinary order.

---

# 2. Приоритет источников

Если документы или классы противоречат друг другу, использовать такой приоритет:

```text
1. Текущие договорённости по runtime-движку.
2. Актуальная доменная модель `Order.md`.
3. Java-классы:
   - Order.java;
   - AttachedAlgoOrder.java;
   - OrderExternalSnapshot.java;
   - AttachedAlgoOrderExternalSnapshot.java.
4. OKX client DTO / OkxClientService как пример текущей реализации.
5. Старые endpoint-доки OKX и старая Order-дока как источник полей.
```

Важно:

```text
приложенные client DTO могут быть не финальными;
эта дока описывает целевую mapping-политику, а не утверждает, что текущий код уже полностью корректен.
```

---

# 3. Границы ответственности

## 3.1. Что описывает эта дока

Эта дока описывает:

* какие OKX endpoints используются для order-flow;
* какие OKX request DTO нужны;
* какие OKX response fields используются;
* как `OrderResponse` превращается в `OrderExternalSnapshot`;
* как `OrderResponse.AttachAlgoOrd` превращается в `AttachedAlgoOrderExternalSnapshot`;
* как external snapshot обновляет domain `Order`;
* как работает mapping external status -> domain status;
* какие значения задаются константами в `OkxClientService`;
* как OKX-specific invariant check сверяет `Order.positionReducingOnly` с `OrderResponse.reduceOnly`, если OKX вернул этот факт.

## 3.2. Что не описывает эта дока

Эта дока не описывает подробно:

* бизнес-смысл статусов `Order` — см. `Order.md`;
* lifecycle `Deal` — см. `Жизненный цикл сделки.md`;
* FSM handlers — см. `FSM этапы сделки.md`;
* retry policy executor'ов — см. `Сервисные команды.md`;
* risk policy — см. `Оценка рисков.md`;
* аудит / timeline — см. `Аудит и история исполнения.md`.

---

# 4. Основной mapping-flow

Целевая цепочка:

```text
OKX REST response
  -> OrderResponse
  -> OrderExternalSnapshot
  -> OrderExternalStatusResolver
  -> domain Order update
```

Для attached protection:

```text
OrderResponse.attachAlgoOrds[*]
  -> AttachedAlgoOrderExternalSnapshot
  -> match with AttachedAlgoOrder by internalId
  -> AttachedAlgoOrderStateResolver
  -> embedded AttachedAlgoOrder update
```

Важное правило:

```text
FSM и handlers не используют OKX raw status напрямую.
Сначала OKX data проходит через mapper/resolver.
```

---

# 5. OKX endpoints ordinary order

## 5.1. Create order

```text
POST /api/v5/trade/order
```

Используется в `SUBMIT_ORDER`.

Назначение:

```text
отправить локально созданный Order на OKX
или восстановить факт отправки через stable client id.
```

Runtime-правило:

```text
ответ create order — это ACK / operation result, но не runtime-truth.
После successful submit всё равно нужен refresh/search/history факт.
```

## 5.2. Amend order

```text
POST /api/v5/trade/amend-order
```

Используется в `AMEND_ORDER`.

Runtime-правило:

```text
ответ amend order не считается финальным состоянием Order.
Фактическое состояние подтверждается refresh/search/history.
```

## 5.3. Cancel order

```text
POST /api/v5/trade/cancel-order
```

Используется в `CANCEL_ORDER`.

Runtime-правило:

```text
ответ cancel order не является финальным фактом отмены.
Фактический `Order.Status.CANCELED` ставится только после refresh/search/history фактов.
```

## 5.4. Order details

```text
GET /api/v5/trade/order
```

Используется в `REFRESH_ORDER`.

Основной источник для обновления конкретного `Order` и embedded `AttachedAlgoOrder`.

## 5.5. Pending orders

```text
GET /api/v5/trade/orders-pending
```

Используется в `REFRESH_PENDING_ORDERS`.

Назначение:

```text
получить текущие live / partially filled ordinary orders по инструменту или типу инструмента.
```

Если `Order` не найден среди pending, это не является финальным фактом отмены/исполнения.
Нужно проверить details/history/fills по ситуации.

## 5.6. Order history

```text
GET /api/v5/trade/orders-history
GET /api/v5/trade/orders-history-archive
```

Используется в `REFRESH_ORDER_HISTORY`.

Назначение:

```text
подтвердить terminal-состояние ordinary order:
filled / canceled / mmp_canceled и другие завершённые состояния.
```

---

# 6. OKX request DTO

## 6.1. `CreateOrderRequest`

Текущий DTO содержит:

```java
public class CreateOrderRequest {

    /** Инструмент OKX: instId, например ETH-USDT-SWAP. */
    private String instrumentId;

    /** Режим торговли OKX: tdMode. Для проекта задаётся константой isolated. */
    private String tradeMode;

    /** Сторона ордера OKX: side, buy / sell. */
    private String side;

    /** Сторона позиции OKX: posSide. Для проекта задаётся константой net. */
    private String positionSide;

    /** Тип ордера OKX: ordType, например optimal_limit_ioc / limit / market. */
    private String orderType;

    /** Размер ордера OKX: sz. Для SWAP/FUTURES — количество контрактов. */
    private String size;

    /** Цена OKX: px. Для market-like order может не отправляться. */
    private String price;

    /** Stable client id OKX: clOrdId. Используется для идемпотентности. */
    private String clientOrderId;

    /** reduceOnly OKX: ордер должен только уменьшать позицию. */
    private String reduceOnly;
}
```

Целевое правило:

```text
CreateOrderRequest — это OKX client DTO.
Он может содержать tradeMode / positionSide,
но domain Order эти поля не хранит.
```

## 6.2. `AmendOrderRequest`

Текущий DTO содержит:

```java
public class AmendOrderRequest {

    /** Инструмент OKX: instId. */
    private String instrumentId;

    /** Биржевой ID ордера OKX: ordId. */
    private String orderId;

    /** Stable client id OKX: clOrdId. */
    private String clientOrderId;

    /** Новый размер OKX: newSz. */
    private String newSize;

    /** Новая цена OKX: newPx. */
    private String newPrice;
}
```

Целевое правило:

```text
AMEND_ORDER работает с уже существующим domain Order.
Executor берёт orderId / clientOrderId из локального Order.
```

## 6.3. `CancelOrderRequest`

Текущий DTO содержит:

```java
public class CancelOrderRequest {

    /** Инструмент OKX: instId. */
    private String instrumentId;

    /** Биржевой ID ордера OKX: ordId. */
    private String orderId;

    /** Stable client id OKX: clOrdId. */
    private String clientOrderId;
}
```

Целевое правило:

```text
Если externalId известен, cancel может использовать ordId.
Если externalId неизвестен, cancel/search/recovery может использовать clOrdId.
```

---

# 7. Search params DTO

## 7.1. `GetOrderDetailsSearchParams`

```java
public class GetOrderDetailsSearchParams {

    /** Инструмент OKX: instId. */
    private String instrumentExternalId;

    /** Биржевой ID ордера OKX: ordId. */
    private String externalId;

    /** Stable client id OKX: clOrdId. */
    private String internalId;
}
```

Mapping:

| Domain / runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| `Order.externalId` | `externalId` | `ordId` |
| `Order.internalId` | `internalId` | `clOrdId` |

## 7.2. `GetOrdersPendingSearchParams`

Mapping:

| Runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalType` | `instrumentExternalType` | `instType` |
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| optional OKX raw state | `externalStatus` | `state` |
| optional OKX order type | `externalType` | `ordType` |
| paging | `afterOrderExternalId` | `after` |
| paging | `beforeOrderExternalId` | `before` |
| paging | `limit` | `limit` |

## 7.3. `GetOrdersHistorySearchParams`

Mapping:

| Runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalType` | `instrumentExternalType` | `instType` |
| optional instrument family | `instrumentExternalFamily` | `instFamily` |
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| optional OKX order type | `externalType` | `ordType` |
| optional OKX final state | `externalStatus` | `state` |
| optional OKX category | `externalCategory` | `category` |
| paging | `afterOrderExternalId` | `after` |
| paging | `beforeOrderExternalId` | `before` |
| time from | `begin` | `begin` |
| time to | `end` | `end` |
| paging | `limit` | `limit` |

## 7.4. `GetOrdersHistoryArchiveSearchParams`

Используется аналогично `GetOrdersHistorySearchParams`, но для archive endpoint.

На первом этапе основной refresh-flow может использовать обычный history, а archive — для более старых данных, если текущая история уже не покрывает нужный период.

---

# 8. `OrderResponse` -> `OrderExternalSnapshot`

`OrderResponse` — OKX client response DTO.

`OrderExternalSnapshot` — normalized external snapshot для domain-layer.

Целевой mapping:

| `OrderResponse` | `OrderExternalSnapshot` | Комментарий |
|---|---|---|
| `clOrdId` | `internalId` | Stable client id бота / OKX clOrdId. |
| `ordId` | `externalId` | Биржевой ordinary order id. |
| `ordType` | `type` | Сырой OKX type, например `optimal_limit_ioc`. |
| `side` | `side` | Сырой OKX side: `buy` / `sell`. |
| `state` | `externalStatus` | Сырой OKX state. Не использовать напрямую в FSM. |
| `px` | `price` | String -> BigDecimal. Пустая строка -> null. |
| `sz` | `size` | String -> BigDecimal. Для SWAP/FUTURES — контракты. |
| `accFillSz` | `accumulatedFillSize` | String -> BigDecimal. |
| `avgPx` | `averagePrice` | String -> BigDecimal. |
| `fee` | `fee` | String -> BigDecimal. |
| `attachAlgoOrds` | `attachedAlgoOrders` | List mapping в `AttachedAlgoOrderExternalSnapshot`. |
| `attachAlgoClOrdId` | `attachedAlgoInternalId` | Top-level attached client id, fallback/diagnostic. |
| `tpTriggerPx` | `takeProfitTriggerPrice` | Сейчас domain attached поддерживает SL; TP может быть future extension. |
| `slTriggerPx` | `stopLossTriggerPrice` | Top-level SL trigger. |
| `reduceOnly` | not mapped | Не сохраняется в `OrderExternalSnapshot`; используется adapter-layer для exchange invariant validation. |

Правила конвертации:

```text
empty string -> null
numeric string -> BigDecimal
state stays raw string in OrderExternalSnapshot.externalStatus
status resolution happens later in OrderExternalStatusResolver
reduceOnly is not stored in OrderExternalSnapshot; OKX adapter may validate it directly from OrderResponse
```

---

# 9. `OrderResponse.AttachAlgoOrd` -> `AttachedAlgoOrderExternalSnapshot`

Целевой mapping:

| `OrderResponse.AttachAlgoOrd` | `AttachedAlgoOrderExternalSnapshot` | Комментарий |
|---|---|---|
| `attachAlgoId` | `externalAttachedId` | OKX attached algo id из embedded attach block. |
| `attachAlgoClOrdId` | `internalId` | Client id attached protection. Основной ключ матчинга. |
| `algoId` | `externalId` | OKX algo id, если появился после trigger/создания. |
| `algoClOrdId` | currently not mapped | Можно использовать как diagnostic/future field, если потребуется. |
| `tpOrdKind` | `externalType` или future field | Для текущего SL-only сценария можно не использовать. |
| `sz` | `size` | String -> BigDecimal/string conversion в mapper-layer. |
| `slTriggerPx` | `stopLossTriggerPrice` | Trigger price attached stop-loss. |
| `failCode` | `failCode` | Если заполнено, attached state resolver может перевести в ERROR. |
| `failReason` | `failReason` | Диагностика ошибки attached protection. |

Важное правило:

```text
attached block в OKX response не имеет полноценного state как ordinary order.
Поэтому AttachedAlgoOrder не резолвится через обычный external status mapping.
Он резолвится по набору фактов.
```

Основной ключ матчинга:

```text
AttachedAlgoOrder.internalId == AttachedAlgoOrderExternalSnapshot.internalId
```

Где:

```text
AttachedAlgoOrderExternalSnapshot.internalId = OKX attachAlgoClOrdId
```

---

# 10. `OrderExternalSnapshot` -> domain `Order`

Обновление domain `Order` по snapshot:

| `OrderExternalSnapshot` | `Order` | Комментарий |
|---|---|---|
| `internalId` | `internalId` | Сверка; обычно не должен меняться. |
| `externalId` | `externalId` | Сохраняется после submit/refresh. |
| `side` | `side` | Сырой OKX side или нормализованное значение по текущей модели. |
| `externalStatus` | `externalStatus` | Сохраняется как raw diagnostic. |
| resolver result | `status` | Через `OrderExternalStatusResolver`. |
| resolver close reason | `closeReason` | Например `FILLED`, `UNKNOWN_EXTERNAL_STATUS`. |
| `price` | `price` | Snapshot price. |
| `size` | `size` | Snapshot size. |
| `accumulatedFillSize` | `accumulatedFillSize` | Факт исполнения. |
| `averagePrice` | `averagePrice` | Факт исполнения. |
| `fee` | `fee` | Факт исполнения. |
| `attachedAlgoOrders` | `attachedAlgoOrders` | Обновляются embedded records по `internalId`. |

Нельзя делать:

```text
externalStatus -> FSM decision напрямую
```

Нужно делать:

```text
externalStatus
  -> OrderExternalStatusResolver
  -> Order.Status / exception
```

---

# 11. OKX external status -> `Order.Status`

Mapping для ordinary order:

| OKX source | OKX raw status | Domain status | closeReason | Комментарий |
|---|---|---|---|---|
| pending/details | `live` | `ACTIVE` | `null` | Ордер live на бирже и ещё может исполниться. |
| pending/details/history | `partially_filled` | `PARTIALLY_COMPLETED` | `null` | Ордер частично исполнен и может иметь live остаток. |
| details/history | `filled` | `COMPLETED` | `FILLED` | Ордер полностью исполнен. |
| details/history | `canceled` | `CANCELED` | context-dependent | Причина отмены определяется runtime-контекстом. |
| history/archive | `mmp_canceled` | `CANCELED` | `UNKNOWN` by default | Можно расширить позже отдельной причиной, если понадобится. |
| any | unknown value | throws `ExternalStatusException(reasonCode = UNKNOWN_EXTERNAL_STATUS)` | `UNKNOWN_EXTERNAL_STATUS` after boundary handling | Resolver бросает controlled exception; refresh-flow переводит локальный Order в `ERROR`. |

## 11.1. Unknown external status policy

Правило:

```text
Resolver не возвращает Order.Status.ERROR как обычный mapping-result.
```

Если OKX вернул неизвестный status:

```text
OrderExternalStatusResolver
  -> throws ExternalStatusException(reasonCode = UNKNOWN_EXTERNAL_STATUS)
```

Дальше refresh boundary / executor boundary выполняет safety-реакцию:

```text
Order.status = ERROR
Order.closeReason = UNKNOWN_EXTERNAL_STATUS
Deal.status = ERROR
Exchange.status = HOLD
```

Причина:

```text
ERROR — это не распознанный биржевой статус,
а локальное safety-состояние Order после невозможности безопасно интерпретировать внешний факт.
```

## 11.2. ExternalNotFoundException / order evidence-cycle

`ExternalNotFoundException` выбрасывает order refresh / recovery-search boundary, если ожидаемый ordinary `Order` не найден после полного order evidence-cycle.

Для OKX order evidence-cycle включает:

```text
GET /api/v5/trade/order
GET /api/v5/trade/orders-pending
GET /api/v5/trade/orders-history
GET /api/v5/trade/orders-history-archive, если обычная history уже не покрывает нужный период
```

Правило поиска:

```text
если Order.externalId есть:
  искать по ordId

если Order.externalId нет:
  искать по clOrdId = Order.internalId
```

Пустой ответ одного endpoint не является основанием для `MISSING_AFTER_REFRESH`.

Если после полного order evidence-cycle order не найден:

```text
throw ExternalNotFoundException
Order.status = ERROR
Order.closeReason = MISSING_AFTER_REFRESH
Deal.status = ERROR
Exchange.status = HOLD
```

Смысл `MISSING_AFTER_REFRESH`:

```text
после полного order evidence-cycle система не смогла найти expected order
и не смогла безопасно объяснить его финал через order sources.
```

Это считается признаком ошибки интеграции / id mapping / query / pagination / history-window и требует остановки normal trading-flow на бирже до разбора.

Если FSM нужны дополнительные факты по сделке, она отдельными командами запрашивает:

```text
REFRESH_FILLS
REFRESH_POSITION
```

Но `RefreshOrderExecutor` не должен сам сопровождать всю сделку целиком.

---

# 12. Domain `Order` -> `CreateOrderRequest`

Целевой mapping:

| Domain / runtime source | `CreateOrderRequest` | OKX field | Комментарий |
|---|---|---|---|
| `Instrument.externalId` | `instrumentId` | `instId` | Например `ETH-USDT-SWAP`. |
| `OkxClientService.TRADE_MODE_ISOLATED` | `tradeMode` | `tdMode` | Константа `isolated`; не хранится в `Order`. |
| `Order.side` | `side` | `side` | `buy` / `sell`. |
| `OkxClientService.POSITION_SIDE_NET` | `positionSide` | `posSide` | Константа `net`; не хранится в `Order`. |
| `Order.type` / execution settings | `orderType` | `ordType` | Например `optimal_limit_ioc`. |
| `Order.size` | `size` | `sz` | BigDecimal -> string. Для SWAP/FUTURES — контракты. |
| `Order.price` | `price` | `px` | Передаётся только если нужен для order type. |
| `Order.internalId` | `clientOrderId` | `clOrdId` | Stable idempotency key. |
| `Order.positionReducingOnly` | `reduceOnly` | `reduceOnly` | Доменное намерение маппится в OKX reduceOnly, если значение задано. |
| `Order.attachedAlgoOrders` | future field if DTO extended | `attachAlgoOrds` | В текущем DTO поля нет; нужно добавить для entry with attached SL. |

## 12.1. Константы `OkxClientService`

Целевое правило:

```java
/** Режим торговли OKX для всех SWAP/FUTURES операций проекта. */
private static final String TRADE_MODE_ISOLATED = "isolated";

/** Сторона позиции OKX для net mode. */
private static final String POSITION_SIDE_NET = "net";
```

На первом этапе:

```text
tdMode = isolated
posSide = net
```

Эти значения не хранятся в domain `Order`.

---

# 13. Create order response handling

`POST /trade/order` response не является runtime-truth.

Целевое правило:

```text
successful submit response
  -> можно сохранить externalId, если OKX вернул ordId;
  -> Order остаётся PENDING, пока refresh/search/history не подтвердит active/final state;
  -> DealActionState остаётся SUBMITTED, пока не подтверждён нужный факт.
```

Если OKX вернул per-item error:

```text
executor / command boundary классифицирует ошибку;
retryable exchange case -> DealActionState.RETRY_PENDING;
non-retryable case -> Order.ERROR / DealActionState.FAILED / Deal.ERROR по ситуации.
```

Важно:

```text
ACK не переводит Order в ACTIVE.
ACTIVE ставится только после refresh/search факта.
```

---

# 14. Domain `Order` -> `AmendOrderRequest`

Целевой mapping:

| Domain / runtime source | `AmendOrderRequest` | OKX field | Комментарий |
|---|---|---|---|
| `Instrument.externalId` | `instrumentId` | `instId` | Инструмент. |
| `Order.externalId` | `orderId` | `ordId` | Предпочтительно, если известен. |
| `Order.internalId` | `clientOrderId` | `clOrdId` | Используется, если нужен client id. |
| calculated action | `newSize` | `newSz` | Новый размер. |
| calculated action | `newPrice` | `newPx` | Новая цена. |

Runtime-правило:

```text
amend response не считается финальным фактом;
после AMEND_ORDER нужен REFRESH_ORDER / REFRESH_PENDING_ORDERS.
```

---

# 15. Domain `Order` -> `CancelOrderRequest`

Целевой mapping:

| Domain / runtime source | `CancelOrderRequest` | OKX field | Комментарий |
|---|---|---|---|
| `Instrument.externalId` | `instrumentId` | `instId` | Инструмент. |
| `Order.externalId` | `orderId` | `ordId` | Если известен. |
| `Order.internalId` | `clientOrderId` | `clOrdId` | Stable client id. |

Runtime-правило:

```text
cancel response не переводит Order в CANCELED как финальный факт;
CANCELED подтверждается refresh/search/history.
```

## 15.1. Общая ACK policy для OKX order operations

Для OKX create / amend / cancel responses действует общее правило:

```text
response ACK / sCode=0 не является runtime-truth.
```

Финальные статусы ordinary `Order` подтверждаются только через exchange facts:

```text
order details
pending orders
order history
order history archive, если нужно
```

`CANCEL_ORDER` не ставит `Order.CANCELED` без refresh/search/history факта.

`AMEND_ORDER` не считается подтверждённым без refresh/search/history факта по новым параметрам.

---

# 16. Attached protection update policy

Attached protection обновляется только через parent order snapshots.

Основной source:

```text
OrderExternalSnapshot.attachedAlgoOrders
```

Матчинг:

```text
AttachedAlgoOrder.internalId == AttachedAlgoOrderExternalSnapshot.internalId
```

## 16.1. Status policy

```text
PENDING ставится после SUBMIT_ORDER.
ACTIVE ставится только после REFRESH_ORDER / REFRESH_PENDING_ORDERS,
когда attached найден в OrderExternalSnapshot.attachedAlgoOrders по internalId
и нет failCode / failReason.
```

Если `failCode / failReason` заполнены:

```text
AttachedAlgoOrder.status = ERROR
```

## 16.2. Missing attached protection policy

Если после `REFRESH_ORDER` в `OrderExternalSnapshot.attachedAlgoOrders` не найден `AttachedAlgoOrderExternalSnapshot` по `internalId`, отсутствие в одном snapshot не считается финальным фактом.

Используется policy C:

```text
parent Order CREATED / PENDING
  -> attached остаётся PENDING;
  -> ждём следующий refresh / retry / recovery.

parent Order ACTIVE / PARTIALLY_COMPLETED
  -> запускаем дополнительный search-cycle;
  -> не делаем финальный вывод по одному snapshot.

parent Order COMPLETED
  -> проверяем позицию и standalone main protection;
  -> если позиция active и main protection отсутствует:
       AttachedAlgoOrder.ERROR / PROTECTION_LOST;
       Deal -> ERROR.

parent Order CANCELED
  -> AttachedAlgoOrder.CANCELED;
  -> closeReason = PARENT_ORDER_CANCELED.

parent Order ERROR
  -> AttachedAlgoOrder.ERROR;
  -> closeReason = UNKNOWN.
```

Дополнительные facts для search-cycle:

```text
REFRESH_PENDING_ORDERS
REFRESH_ORDER_HISTORY
REFRESH_FILLS
REFRESH_POSITION
```

---

# 17. Current code gaps / target refactoring notes

Этот раздел фиксирует расхождения текущих прикреплённых классов с целевой политикой.

## 17.1. `OkxClientService.createOrder` сейчас принимает tradeMode / positionSide аргументами

Текущий код:

```text
createOrder(order, instrumentExternalId, tradeMode, positionSide)
```

Целевое правило:

```text
tradeMode и positionSide не должны приходить из domain Order и не должны прокидываться как произвольные args.
OkxClientService должен сам выставлять константы:
- isolated;
- net.
```

## 17.2. `CreateOrderRequest` должен принимать OKX `reduceOnly`

Если ordinary order используется для partial exit, `Order.positionReducingOnly` должен быть передан в OKX request как `reduceOnly`.

Целевая правка DTO:

```java
/** OKX reduceOnly: ордер может только уменьшать позицию. */
private String reduceOnly;
```

или `Boolean`, если mapper корректно сериализует в ожидаемый OKX формат.

## 17.3. `CreateOrderRequest` не содержит attachAlgoOrds

Если entry order создаётся вместе с attached SL, DTO должен поддерживать `attachAlgoOrds`.

На текущем этапе это нужно для `Order.Type.ENTRY_ATTACHED_STOP_LOSS`.

## 17.4. `OrderResponse.state` comment слишком узкий

В текущем DTO comment говорит, что `state` — `live` или `partially_filled`.

Это верно для pending endpoint, но не для details/history.

Целевой comment:

```text
state — сырой статус OKX order.
Для pending обычно live / partially_filled.
Для details/history может быть filled / canceled / mmp_canceled и другие terminal statuses.
```

## 17.5. `OrderResponse.reduceOnly` не должен маппиться в `OrderExternalSnapshot`

`OrderResponse` уже содержит `reduceOnly`.

Целевое правило:

```text
OrderResponse.reduceOnly -> adapter invariant validation only
```

`OrderResponse.reduceOnly` не маппится в `OrderExternalSnapshot` и не обновляет `Order.positionReducingOnly`.

Если OKX вернул `reduceOnly`, adapter-layer может сравнить его с локальным intent:

```text
expected = Order.positionReducingOnly
actual = OrderResponse.reduceOnly
```

Если значения не совпали, выбрасывается controlled exchange error с кодом `EXCHANGE_INVARIANT_VIOLATION`.

Runtime-реакция: `Order.ERROR`, `Order.closeReason = EXCHANGE_INVARIANT_VIOLATION`, `Deal.ERROR`, `Exchange.HOLD`.

---

# 18. Итоговые правила

1. `Order.md` остаётся источником истины по доменной модели `Order`.
2. `OKX Order mapping.md` описывает только OKX-specific mapping.
3. Domain `Order` не хранит `tdMode` и `posSide`.
4. `tdMode=isolated` и `posSide=net` задаются константами в `OkxClientService`.
5. Domain `Order` хранит `positionReducingOnly` как доменное намерение, а не как внешний факт биржи.
6. `Order.positionReducingOnly` маппится в OKX `reduceOnly` при создании ordinary order.
7. `OrderResponse.reduceOnly` не маппится в `OrderExternalSnapshot`; он используется только для OKX-specific invariant validation.
8. Если биржа не поддерживает reduce-only / close-only механизм, adapter может проигнорировать `positionReducingOnly`; unsupported exchange не блокируем.
9. Если OKX вернул `reduceOnly`, и он не совпал с `Order.positionReducingOnly`, это `EXCHANGE_INVARIANT_VIOLATION`: `Order.ERROR / Deal.ERROR / Exchange.HOLD`.
10. Unknown external status не маппится в обычный domain status.
11. Unknown external status приводит к `ExternalStatusException(reasonCode = UNKNOWN_EXTERNAL_STATUS)` и затем к `Order.ERROR / Deal.ERROR / Exchange.HOLD`.
12. `ExternalNotFoundException` используется только после полного order evidence-cycle, а не после одного пустого response.
13. `MISSING_AFTER_REFRESH` означает `Order.ERROR / Deal.ERROR / Exchange.HOLD` из-за невозможности найти expected order по order sources.
14. Attached protection остаётся embedded частью parent `Order`.
15. Attached protection матчится по `internalId = attachAlgoClOrdId`.
16. Missing attached protection в одном snapshot не считается финальным фактом.
