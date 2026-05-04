# OKX AlgoOrder mapping

> Статус документа: exchange-specific mapping-дока для standalone `AlgoOrder` на OKX.
>
> Документ описывает, как OKX request/response DTO и external snapshot превращаются в доменный `AlgoOrder`.
>
> Документ не заменяет `AlgoOrder.md`. Доменная модель, статусы и runtime-семантика `AlgoOrder` описаны в `AlgoOrder.md`.
>
> Command-flow `CREATE_ALGO_ORDER -> SUBMIT_ALGO_ORDER -> REFRESH_*` описан в `Сервисные команды.md`.

---

# 1. Назначение

Эта дока отвечает на вопрос:

```text
как данные OKX по standalone algo-order попадают в доменную модель AlgoOrder
и какие поля OKX request/response используются client-layer / mapper-layer.
```

Документ нужен для:

* `OkxClientService`;
* `OkxRestClient`;
* OKX request DTO;
* OKX response DTO;
* `AlgoOrderMapper`;
* `AlgoOrderExternalSnapshot`;
* `AlgoOrderExternalStatusResolver`;
* refresh/search/history executor'ов по standalone algo-order.

---

# 2. Приоритет источников

Если документы или классы противоречат друг другу, использовать такой приоритет:

```text
1. Текущие договорённости по runtime-движку.
2. Актуальная доменная модель `AlgoOrder.md`.
3. Java-классы:
   - AlgoOrder.java;
   - AlgoOrderExternalSnapshot.java;
   - Condition.java;
   - Trigger.java;
   - TriggerPrice.java;
   - Trailing.java.
4. OKX client DTO / OkxClientService как пример текущей реализации.
5. Endpoint-доки OKX.
```

Важно:

```text
client DTO могут быть не финальными;
эта дока описывает целевую mapping-политику, а не утверждает, что текущий код уже полностью корректен.
```

---

# 3. Границы ответственности

## 3.1. Что описывает эта дока

Эта дока описывает:

* какие OKX endpoints используются для algo-order-flow;
* какие OKX request DTO нужны;
* какие OKX response fields используются;
* как OKX response превращается в `AlgoOrderExternalSnapshot`;
* как external snapshot обновляет domain `AlgoOrder`;
* как работает mapping external status -> domain status;
* какие значения задаются константами в `OkxClientService`;
* какие OKX-specific invariant checks выполняет client/adapter-layer.

## 3.2. Что не описывает эта дока

Эта дока не описывает подробно:

* бизнес-смысл статусов `AlgoOrder` — см. `AlgoOrder.md`;
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
  -> AlgoOrderResponse
  -> AlgoOrderExternalSnapshot
  -> AlgoOrderExternalStatusResolver
  -> domain AlgoOrder update
```

Важное правило:

```text
FSM и handlers не используют OKX raw status напрямую.
Сначала OKX data проходит через mapper/resolver.
```

---

# 5. OKX endpoints standalone algo-order

## 5.1. Create algo-order

```text
POST /api/v5/trade/order-algo
```

Используется в `SUBMIT_ALGO_ORDER`.

Назначение:

```text
отправить локально созданный AlgoOrder на OKX
или восстановить факт отправки через stable client id.
```

Runtime-правило:

```text
ответ create algo-order — это ACK / operation result, но не runtime-truth.
После successful submit всё равно нужен refresh/search/history факт.
```

## 5.2. Amend algo-order

```text
POST /api/v5/trade/amend-algos
```

Используется в `AMEND_ALGO_ORDER`.

Runtime-правило:

```text
ответ amend algo-order не считается финальным состоянием AlgoOrder.
Фактическое состояние и параметры подтверждаются refresh/search/history.
```

## 5.3. Cancel algo-order

```text
POST /api/v5/trade/cancel-algos
```

Используется в `CANCEL_ALGO_ORDER`.

Runtime-правило:

```text
ответ cancel algo-order не является финальным фактом отмены.
Фактический AlgoOrder.Status.CANCELED ставится только после refresh/search/history фактов.
```

## 5.4. Algo-order details

```text
GET /api/v5/trade/order-algo
```

Используется в `REFRESH_ALGO_ORDER`.

Основной источник для точечного обновления конкретного `AlgoOrder`.

Идентификаторы:

```text
AlgoOrder.externalId -> algoId
AlgoOrder.internalId -> algoClOrdId
```

Правило поиска:

```text
если externalId есть:
  искать по algoId

если externalId нет:
  искать по algoClOrdId
```

Если OKX поддерживает передачу обоих и приоритет `algoId`, adapter должен учитывать этот приоритет.

## 5.5. Pending algo-orders

```text
GET /api/v5/trade/orders-algo-pending
```

Используется в `REFRESH_ALGO_ORDERS`.

Назначение:

```text
получить текущие active/pending algo-orders по инструменту / типу.
```

Если `AlgoOrder` не найден среди pending, это не является финальным фактом отмены/срабатывания.
Нужно проверить details/history по ситуации.

## 5.6. Algo-order history

```text
GET /api/v5/trade/orders-algo-history
```

Используется в `REFRESH_ALGO_ORDER_HISTORY`.

Назначение:

```text
подтвердить terminal/problem-состояние algo-order:
effective / canceled / order_failed / partially_failed / partially_effective и другие исторические состояния.
```

Для OKX history query часто требует `ordType`, поэтому `ordType` вычисляется через client-layer resolver:

```text
AlgoOrder.conditionType -> OKX ordType
```

---

# 6. OKX request mapping

## 6.1. Create algo-order request

Целевой mapping:

| Domain / runtime source | OKX field | Комментарий |
|---|---|---|
| `Instrument.externalId` | `instId` | Например `ETH-USDT-SWAP`. |
| constant | `tdMode` | `isolated`. В domain не храним. |
| constant | `posSide` | `net`. В domain не храним. |
| `AlgoOrder.direction` | `side` | `BUY/SELL -> buy/sell`. |
| `AlgoOrder.conditionType` | `ordType` | Через `OkxAlgoOrderTypeResolver`. |
| `AlgoOrder.size` | `sz` | Для SWAP/FUTURES — контракты. |
| `AlgoOrder.internalId` | `algoClOrdId` | Stable client id. |
| `AlgoOrder.positionReducingOnly` | `reduceOnly` | Если OKX поддерживает; для closing/protective обычно `true`. |
| `Condition.trigger.stopLoss.value` | `slTriggerPx` | Для SL/OCO/PARTIAL_SL. |
| `Condition.trigger.stopLoss.type` | `slTriggerPxType` | `last/index/mark`. |
| constant / first-stage policy | `slOrdPx` | `-1`, market execution after trigger. |
| `Condition.trigger.takeProfit.value` | `tpTriggerPx` | Для TP/OCO/PARTIAL_TP. |
| `Condition.trigger.takeProfit.type` | `tpTriggerPxType` | `last/index/mark`. |
| constant / first-stage policy | `tpOrdPx` | `-1`, market execution after trigger. |
| `Condition.trailing.trailingPercents` | `callbackRatio` | Для `TRAILING_PERCENTS`. |
| `Condition.trailing.trailingStepValue` | `callbackSpread` | Для `TRAILING_VALUE`. |
| `Condition.trailing.activationPrice.value` | `activePx` | Если activationPrice задан. |

На первом этапе не используем OKX `closeFraction` как основной механизм.

Правило:

```text
StrategyAlgoOrderAction.closeFractionPercents
  + Position
  + InstrumentExternalRules
  -> SizeCalculator
  -> AlgoOrder.size
  -> OKX sz
```

## 6.2. `conditionType -> OKX ordType`

| `ConditionType` | OKX `ordType` |
|---|---|
| `STOP_LOSS` | `conditional` |
| `TAKE_PROFIT` | `conditional` |
| `PARTIAL_STOP_LOSS` | `conditional` |
| `PARTIAL_TAKE_PROFIT` | `conditional` |
| `OCO_FULL` | `oco` |
| `TRAILING_PERCENTS` | `move_order_stop` |
| `TRAILING_VALUE` | `move_order_stop` |

`trigger` как OKX `ordType` на первом этапе не используется, потому что текущая модель не описывает opening trigger algo-order.

## 6.3. Amend algo-order request

`AMEND_ALGO_ORDER` обновляет существующий `AlgoOrder` на бирже.

Mapping зависит от того, какие параметры меняются:

| Runtime source | OKX field | Комментарий |
|---|---|---|
| `Instrument.externalId` | `instId` | Обязательный инструмент. |
| `AlgoOrder.externalId` | `algoId` | Основной биржевой id, если известен. |
| `AlgoOrder.internalId` | `algoClOrdId` | Stable client id, fallback/search id. |
| new calculated size | `newSz` / related field | Если меняется размер. |
| new trigger values | `newSlTriggerPx` / `newTpTriggerPx` / related fields | Зависит от OKX amend DTO. |
| new trailing values | related fields | Зависит от OKX amend DTO. |

Runtime-правило:

```text
amend response / ACK не является runtime-truth.
После AMEND_ALGO_ORDER состояние и параметры подтверждаются только refresh-фактами.
```

## 6.4. Cancel algo-order request

Mapping:

| Runtime source | OKX field | Комментарий |
|---|---|---|
| `Instrument.externalId` | `instId` | Обязательный инструмент. |
| `AlgoOrder.externalId` | `algoId` | Основной id для cancel, если известен. |
| `AlgoOrder.internalId` | `algoClOrdId` | Fallback/search id, если endpoint/DTO поддерживает. |

Политика:

```text
если externalId известен:
  cancel по algoId

если externalId неизвестен:
  сначала refresh/search по algoClOrdId,
  затем cancel по найденному algoId, если OKX endpoint требует именно algoId.
```

`cancel-algos` ACK не финализирует `AlgoOrder`.

---

# 7. Search params DTO

## 7.1. `GetAlgoOrderDetailsSearchParams`

| Runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| `AlgoOrder.externalId` | `externalId` | `algoId` |
| `AlgoOrder.internalId` | `internalId` | `algoClOrdId` |

Правило:

```text
если externalId есть — искать по algoId;
если externalId нет — искать по algoClOrdId.
```

## 7.2. `GetAlgoOrdersPendingSearchParams`

| Runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalType` | `instrumentExternalType` | `instType` |
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| `AlgoOrder.conditionType` | `externalType` | `ordType` |
| optional OKX raw state | `externalStatus` | `state` |
| paging | `afterAlgoOrderExternalId` | `after` |
| paging | `beforeAlgoOrderExternalId` | `before` |
| paging | `limit` | `limit` |

`externalType / ordType` не берётся из `AlgoOrder.externalType`, потому что такого поля в domain нет.

Он вычисляется:

```text
AlgoOrder.conditionType -> OkxAlgoOrderTypeResolver -> ordType
```

## 7.3. `GetAlgoOrdersHistorySearchParams`

| Runtime source | Search param | OKX query field |
|---|---|---|
| `Instrument.externalType` | `instrumentExternalType` | `instType` |
| optional instrument family | `instrumentExternalFamily` | `instFamily` |
| `Instrument.externalId` | `instrumentExternalId` | `instId` |
| `AlgoOrder.conditionType` | `externalType` | `ordType` |
| optional OKX final state | `externalStatus` | `state` |
| `AlgoOrder.externalId` | `externalId` | `algoId` |
| paging | `afterAlgoOrderExternalId` | `after` |
| paging | `beforeAlgoOrderExternalId` | `before` |
| time from | `begin` | `begin` |
| time to | `end` | `end` |
| paging | `limit` | `limit` |

Для OKX history `ordType` обязателен в ряде сценариев. Его вычисляет client-layer resolver.

---

# 8. OKX response -> `AlgoOrderExternalSnapshot`

Целевой mapping:

| OKX response field | `AlgoOrderExternalSnapshot` | Комментарий |
|---|---|---|
| `algoClOrdId` | `internalId` | Stable client id. |
| `algoId` | `externalId` | Биржевой algo-order id. |
| `state` | `externalStatus` | Raw status. FSM напрямую не использует. |
| `failCode` | `failCode` | Диагностика failed/problem status. |
| `actualSz` | `externalSize` | Фактический размер срабатывания. |
| `actualPx` | `externalPrice` | Фактическая цена срабатывания. |
| `triggerTime` | `externalTriggerTime` | Время срабатывания. |
| `ordId` | `linkedOrderExternalIds` | Если есть один linked ordinary order. |
| `ordIdList` | `linkedOrderExternalIds` | Если OKX вернул список. |
| `slTriggerPx` | `condition.trigger.stopLoss.externalValue` | Внешнее значение SL trigger. |
| `slTriggerPxType` | `condition.trigger.stopLoss.externalType` | last/index/mark. |
| `tpTriggerPx` | `condition.trigger.takeProfit.externalValue` | Внешнее значение TP trigger. |
| `tpTriggerPxType` | `condition.trigger.takeProfit.externalType` | last/index/mark. |
| `activePx` | `condition.trailing.activationPrice.externalValue` | Если activationPrice есть. |
| `moveTriggerPx` | `condition.trailing.externalPrice` | Текущее значение trailing, если OKX вернул. |
| `ordType` | not mapped | Проверяется adapter-layer как invariant. |
| `side` | not mapped | Проверяется adapter-layer как invariant. |
| `actualSide` | not mapped | Не храним; можно проверить или оставить в raw audit. |
| `tdMode` | not mapped | Проверяется adapter-layer как invariant. |
| `posSide` | not mapped | Проверяется adapter-layer как invariant. |
| `reduceOnly` | not mapped | Проверяется adapter-layer как invariant. |
| `closeFraction` | not mapped | На первом этапе не используем как основной domain size mechanism. |

Правила конвертации:

```text
empty string -> null
numeric string -> BigDecimal
timestamp string -> Instant
state stays raw string in AlgoOrderExternalSnapshot.externalStatus
status resolution happens later in AlgoOrderExternalStatusResolver
```

---

# 9. `AlgoOrderExternalSnapshot` -> domain `AlgoOrder`

Обновление domain `AlgoOrder` по snapshot:

| `AlgoOrderExternalSnapshot` | `AlgoOrder` | Комментарий |
|---|---|---|
| `internalId` | `internalId` | Сверка; обычно не должен меняться. |
| `externalId` | `externalId` | Сохраняется после submit/refresh. |
| `externalStatus` | `externalStatus` | Сохраняется как raw diagnostic fact. |
| `failCode` | `failCode` | Сохраняется как diagnostic fact. |
| `externalSize` | `externalSize` | OKX actualSz. |
| `externalPrice` | `externalPrice` | OKX actualPx. |
| `externalTriggerTime` | `externalTriggerTime` | OKX triggerTime. |
| `condition` | `condition.external*` fields | Обновляет внешние значения condition/trailing. |
| `linkedOrderExternalIds` | `linkedOrderExternalIds` | Просто сохраняем; runtime не опирается. |

Status update:

```text
snapshot.externalStatus
  -> OkxAlgoOrderExternalStatusResolver
  -> AlgoOrder.Status или ExternalStatusException
```

`canceled` closeReason:

```text
OKX state=canceled
  -> AlgoOrder.Status.CANCELED
  -> closeReason берётся из cancel intent,
     а не из OKX state.
```

---

# 10. OKX external status mapping

| OKX state | Resolver reaction | Domain status / reason |
|---|---|---|
| `live` | normal mapping | `ACTIVE` |
| `pause` | normal mapping | `ACTIVE` |
| `partially_effective` | normal mapping | `PARTIALLY_COMPLETED` |
| `effective` | normal mapping | `COMPLETED`, `TRIGGERED` |
| `canceled` | normal mapping | `CANCELED`, reason from cancel intent |
| `order_failed` | throw `ExternalStatusException` | `ORDER_FAILED` |
| `partially_failed` | throw `ExternalStatusException` | `PARTIALLY_FAILED` |
| unknown | throw `ExternalStatusException` | `UNKNOWN_EXTERNAL_STATUS` |

Notes:

```text
pause
  -> active-like external state;
  -> algo-order exists on exchange and may still affect risk/cleanup.

partially_effective
  -> partial success;
  -> not a target strategy state;
  -> maps to PARTIALLY_COMPLETED and requires further FSM analysis.

partially_failed
  -> partial failure;
  -> part of the scenario may have executed;
  -> problem-state and error/safety flow.
```

---

# 11. Adapter invariant validation

Client / adapter-layer validates exchange-specific invariants.

## 11.1. `tdMode`

Expected:

```text
tdMode = isolated
```

Domain `AlgoOrder` does not store this field.

Mismatch:

```text
ExternalInvariantViolationException
closeReason = EXCHANGE_INVARIANT_VIOLATION
Exchange HOLD
```

## 11.2. `posSide`

Expected:

```text
posSide = net
```

Domain `AlgoOrder` does not store this field.

Mismatch:

```text
ExternalInvariantViolationException
closeReason = EXCHANGE_INVARIANT_VIOLATION
Exchange HOLD
```

## 11.3. `side`

Expected:

```text
AlgoOrder.direction -> OKX side
```

Mismatch:

```text
ExternalInvariantViolationException
```

## 11.4. `ordType`

Expected:

```text
AlgoOrder.conditionType -> OKX ordType
```

Mismatch:

```text
ExternalInvariantViolationException
```

Validation is one-way:

```text
conditionType -> expected ordType
```

Do not reverse-map `ordType -> conditionType`, because `conditional` covers several domain condition types.

## 11.5. `reduceOnly`

Expected:

```text
AlgoOrder.positionReducingOnly -> OKX reduceOnly
```

If OKX response contains `reduceOnly`, adapter may compare expected/actual.

Mismatch:

```text
ExternalInvariantViolationException
```

If another exchange does not support reduce-only / close-only, adapter may ignore `positionReducingOnly`; unsupported exchange does not block first-stage runtime.

## 11.6. Size

Do not hard-validate:

```text
AlgoOrder.size == actualSz
```

Reason:

```text
AlgoOrder.size is calculated intent.
actualSz is external fact after trigger.
actualSz may be different because of partial trigger/execution.
```

---

# 12. ExternalNotFound policy

`ExternalNotFoundException` is thrown only by the refresh/recovery-search boundary.

For `AlgoOrder`, it means:

```text
after checking all relevant algo-order sources,
the expected AlgoOrder was not found
and its final state cannot be explained.
```

Relevant OKX sources:

```text
GET /api/v5/trade/order-algo
GET /api/v5/trade/orders-algo-pending
GET /api/v5/trade/orders-algo-history
```

Not enough:

```text
one empty data=[] response from order-algo
```

Runtime reaction:

```text
AlgoOrder -> ERROR
closeReason = MISSING_AFTER_REFRESH
Deal -> ERROR
Exchange -> HOLD
```

Meaning:

```text
this is likely an integration/API/id-mapping/query/pagination/ordType-filter problem.
Trading must stop until the issue is investigated.
```

---

# 13. Submit semantics

`SUBMIT_ALGO_ORDER` uses stable client id:

```text
AlgoOrder.internalId -> algoClOrdId
```

Before retry submit:

```text
search/refresh by algoClOrdId
```

If algo-order is found:

```text
update local AlgoOrder from external snapshot
```

If not found:

```text
send create algo-order request
```

ACK from create response is not runtime-truth.

Final action completion requires refresh/search/history fact.

---

# 14. Cancel semantics

`CANCEL_ALGO_ORDER` uses common command policy:

```text
ACK is not runtime-truth.
```

Cancel request result:

```text
sCode=0
```

means only:

```text
cancel request accepted / operation acknowledged
```

It does not mean:

```text
AlgoOrder.Status.CANCELED
```

Final cancellation:

```text
OKX state=canceled
  -> AlgoOrder.CANCELED
  -> closeReason from cancel intent
```

If refresh/history shows another fact:

```text
effective
  -> COMPLETED / TRIGGERED

partially_effective
  -> PARTIALLY_COMPLETED

order_failed
  -> ExternalStatusException / ORDER_FAILED

partially_failed
  -> ExternalStatusException / PARTIALLY_FAILED
```

Cancel reason source:

```text
strategy/FSM cleanup  -> CANCELED_BY_STRATEGY
replacement           -> REPLACED_BY_STRATEGY
kill-switch           -> KILL_SWITCH
manual                -> MANUAL_CANCEL
```

---

# 15. Amend semantics

`AMEND_ALGO_ORDER` updates existing `AlgoOrder` on OKX.

Amend response / ACK is not runtime-truth.

After `AMEND_ALGO_ORDER`:

```text
REFRESH_ALGO_ORDER / REFRESH_ALGO_ORDERS / REFRESH_ALGO_ORDER_HISTORY
```

confirms actual state and parameters.

`AmendAlgoOrderExecutor` does not make trading decisions and does not finalize `AlgoOrder` by ACK alone.

---

# 16. Linked ordinary orders

OKX may return:

```text
ordId
ordIdList
```

Mapping:

```text
ordId / ordIdList -> AlgoOrderExternalSnapshot.linkedOrderExternalIds
                  -> AlgoOrder.linkedOrderExternalIds
```

First-stage policy:

```text
store only;
do not create domain Order automatically;
do not create DealActionState;
do not run ordinary order refresh from AlgoOrder refresh executor;
do not use these ids as FSM targets.
```

Future research:

```text
how to use linkedOrderExternalIds for fills / recovery / audit.
```

---

# 17. Refresh executor boundary

`RefreshAlgoOrderExecutor` updates only `AlgoOrder`.

It does not run:

```text
REFRESH_ORDER
REFRESH_PENDING_ORDERS
REFRESH_ORDER_HISTORY
REFRESH_FILLS
REFRESH_POSITION
```

Those commands are chosen by FSM / DealOrchestrator after analyzing `DealContext` and status facts.

---

# 18. Field summary

## 18.1. Stored in domain `AlgoOrder`

```text
internalId
externalId
status
closeReason
conditionType
condition
size
direction
positionReducingOnly
externalStatus
failCode
externalSize
externalPrice
externalTriggerTime
linkedOrderExternalIds
```

## 18.2. Not stored in domain `AlgoOrder`

```text
strategyActionId
strategyActionKey
role
level
externalType / ordType
externalDirection / side
externalPositionSide / posSide
tdMode
reduceOnly
actualSide
closeFraction
```

## 18.3. Stored in `AlgoOrderExternalSnapshot`

```text
internalId
externalId
externalStatus
failCode
externalSize
externalPrice
externalTriggerTime
condition
linkedOrderExternalIds
```

## 18.4. Not stored in `AlgoOrderExternalSnapshot`

```text
externalType / ordType
externalDirection / side
externalPositionSide / posSide
tdMode
posSide
reduceOnly
actualSide
closeFraction
```

---
