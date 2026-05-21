# REFRESH_PENDING_ORDERS — полная спецификация команды

## Цель команды

`REFRESH_PENDING_ORDERS` — это сервисная команда, которая сопровождает **обычные незавершённые ордера** (`Order`) по
конкретному инструменту и синхронизирует их с биржей.

Эта команда также сопровождает `AttachedAlgoOrder`, **пока attached-защита ещё живёт в контуре snapshot обычного ордера
** и не подтверждена как самостоятельный live algo на бирже.

Команда не занимается полноценным algo-truth refresh. Это зона ответственности `REFRESH_ALGO_ORDERS`.

---

# 1. Где команда находится в общей архитектуре

```text
Handler
  -> ServiceCommandExecutor
     -> RefreshPendingOrdersExecutor
        -> TradeRuleValidator.validateRefreshPendingOrders(...)
        -> normal order refresh flow
        -> RefreshAttachedAlgoOrderExecutor
```

После выполнения orchestrator заново перечитывает `DealContext`.

---

# 2. Что именно делает команда

Команда должна:

1. получить внешний snapshot обычных pending-ордеров по инструменту;
2. получить local live `Order` из БД по инструменту;
3. проверить торговые инварианты именно для ordinary order refresh;
4. если инварианты не нарушены — синхронизировать ordinary `Order`;
5. для каждого обработанного external order snapshot синхронизировать дочерние `AttachedAlgoOrder` через
   `RefreshAttachedAlgoOrderExecutor`;
6. если local live order пропал из pending snapshot — восстановить его final state через ordinary order endpoints;
7. после получения final state снова вызвать child refresh для attached-защиты, если snapshot ордера её содержит.

---

# 3. Что команда НЕ делает

Команда `REFRESH_PENDING_ORDERS` не должна:

* ходить в algo endpoints;
* подтверждать `ACTIVE / CLOSED / FAILED` для algo-truth layer;
* сопровождать `AlgoOrder`;
* самостоятельно запускать kill-switch;
* самостоятельно создавать `AnomalyReport`;
* принимать FSM-решения;
* обновлять позицию или баланс.

`RefreshAttachedAlgoOrderExecutor` внутри этой команды тоже не должен ходить в algo endpoints.

---

# 4. Входные данные

Рекомендуемая сигнатура:

```java
public void execute(Exchange exchange, Instrument instrument, Long dealId)
```

Где:

* `exchange` — биржа;
* `instrument` — инструмент;
* `dealId` — текущая сделка как контекст команды.

---

# 5. Источники правды и какие методы вызываем

## 5.1. Стартовый внешний snapshot ordinary orders

### Метод

`GET /api/v5/trade/orders-pending`

### Когда вызываем

Всегда в начале команды.

### Зачем

Это единственный штатный snapshot незавершённых ordinary order'ов по инструменту. Эндпоинт возвращает именно live /
partially_filled ордера. Attached-поля ордера тоже приходят именно здесь.

### Как вызываем

По инструменту:

* `instType=SWAP`
* `instId=<instrumentExternalId>`
* `state=live,partially_filled`

### Что получаем

`List<OrderExternalSnapshot>`

---

## 5.2. Восстановление final state пропавшего order

Если local live order отсутствует в `orders-pending`, вызываем строго по цепочке:

### Шаг 1

`GET /api/v5/trade/order`

Ищем по:

* `ordId` (`externalId`)
* если его нет, то по `clOrdId` (`internalId`)

### Шаг 2

Если detail не помог:
`GET /api/v5/trade/orders-history`

### Шаг 3

Если recent history не помог:
`GET /api/v5/trade/orders-history-archive`

### Важно

Нельзя делать:

```text
missing from orders-pending -> CLOSED
```

Это неверно. Пропажа из pending означает только, что ордер больше не pending. Его фактическое состояние нужно доказать
через ordinary order truth endpoints.

---

# 6. Что читаем из БД

Команда должна читать только:

* live ordinary `Order` по текущему `instrumentId`

То есть нужен метод уровня data service:

```java
List<Order> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses)
```

Нельзя читать всю историю order'ов по инструменту без фильтра по live statuses.

---

# 7. Проверка торговых правил для команды

Проверка должна происходить до normal sync.

### Кто делает

`TradeRuleValidator`

### Метод

Рекомендуемое имя:

```java
validateRefreshPendingOrders(exchange,
                             instrument,
                             dealId,
                             externalPendingOrders,
                             internalLiveOrders)
```

---

## 7.1. Какие инварианты обязан проверить validator

## Правило 1. Во внешнем pending snapshot не должно быть дублей

Нельзя, чтобы во внешнем snapshot были два live order'а с одинаковым:

* `ordId`
* или `clOrdId`

Если такие дубли есть — это anomaly.

---

## Правило 2. В локальной БД не должно быть дублей live ordinary orders

Нельзя, чтобы в `internalLiveOrders` были два live `Order` с одинаковым:

* `externalId`
* или `internalId`

Если есть — это anomaly.

---

## Правило 3. Каждый внешний live order должен однозначно сопоставляться с local live order

Порядок сопоставления:

1. `externalId`
2. `internalId`

Если внешний pending order:

* не сопоставился ни с одним local live order,
* или сопоставился сразу с несколькими,

это anomaly.

### Почему это anomaly, а не normal create case

В проекте зафиксирован инвариант: сначала сохраняем сущность в БД, потом идём на биржу.

Значит live order на бирже без local order — это нарушение базового инварианта, а не happy path refresh.

---

## Правило 4. Все local live orders должны относиться к текущему инструменту

Если в выборке оказался order другого инструмента — это anomaly.

---

## Правило 5. При любой ambiguous ситуации normal sync запрещён

Если есть хотя бы одна ambiguous ситуация:

* дубль;
* неизвестный внешний live order;
* неоднозначное сопоставление;

validator должен остановить normal flow.

---

# 8. Что делает validator при нарушении

Если правило нарушено, validator должен выполнять полный trade-rule violation flow:

1. создать initial `AnomalyReport`;
2. записать `internal_before` и `external_before` в разрезе инструмента;
3. при необходимости через `InstrumentService` поставить блокирующий статус инструмента;
4. вызвать `KillSwitchService` по инструменту;
5. получить `internal_after` и `external_after`;
6. довести `AnomalyReport` до `KILL_SWITCH_EXECUTED`, а затем до `COMPLETED` или `ERROR`;
7. выбросить `TradeRuleViolationException`.

После этого normal sync команды продолжаться не должен.

---

# 9. Normal flow команды по шагам

## Шаг 1. Получить external pending orders

`GET /api/v5/trade/orders-pending`

## Шаг 2. Получить local live orders

Из БД по `instrumentId + LIVE_ORDER_STATUSES`

## Шаг 3. Вызвать validator

`validateRefreshPendingOrders(...)`

Если validator выбросил исключение — команда заканчивается аварийно.

## Шаг 4. Обновить matched pending orders

Для каждого external pending order:

* найти local `Order`;
* применить snapshot к `Order`;
* статус резолвить через `OrderStatusResolver`;
* сохранить `Order`;
* вызвать `RefreshAttachedAlgoOrderExecutor`.

## Шаг 5. Обработать local live orders, которых нет в pending snapshot

Для каждого такого `Order`:

### 5.1. Вызвать `GET /trade/order`

Если order найден:

* обновить `Order`;
* резолвить финальный статус;
* сохранить;
* вызвать `RefreshAttachedAlgoOrderExecutor`.

### 5.2. Если detail не помог — `GET /trade/orders-history`

Если order найден:

* обновить `Order`;
* резолвить финальный статус;
* сохранить;
* вызвать `RefreshAttachedAlgoOrderExecutor`.

### 5.3. Если history не помог — `GET /trade/orders-history-archive`

Если order найден:

* обновить `Order`;
* резолвить финальный статус;
* сохранить;
* вызвать `RefreshAttachedAlgoOrderExecutor`.

### 5.4. Если order не найден нигде

Это уже не normal sync case.

Нельзя молча ставить `CLOSED`.

Это либо anomaly восстановления final state, либо отдельное доменное исключение восстановления — по общей политике
проекта. Но не silent close.

---

# 10. Роль `OrderStatusResolver`

`OrderStatusResolver` — отдельный чистый resolver для ordinary order.

### Что делает

Переводит `OrderExternalSnapshot` в доменный `Order.Status`.

### Что НЕ делает

* не сохраняет order;
* не ходит на биржу;
* не запускает validator;
* не создаёт побочных эффектов.

---

# 11. Как `RefreshAttachedAlgoOrderExecutor` участвует в этой команде

## Важное правило

`RefreshAttachedAlgoOrderExecutor` — это **внутренний дочерний executor команды `REFRESH_PENDING_ORDERS`**, а не
отдельная сервисная команда.

Его надо вызывать из `RefreshOrderExecutor`:

* при обычном pending snapshot update;
* при final snapshot update, полученном через `trade/order`;
* при final snapshot update, полученном через `orders-history`;
* при final snapshot update, полученном через `orders-history-archive`.

---

## Что он должен делать

Он получает:

* `Order order`
* `OrderExternalSnapshot snapshot`

И должен:

1. извлечь attached protection из order snapshot;
2. найти existing local `AttachedAlgoOrder` по `orderId`;
3. сопоставить children;
4. создать / обновить / закрыть / зафейлить `AttachedAlgoOrder`;
5. **каждое изменение сразу записать в БД**.

---

## Какие поля order snapshot являются для него truth source

Минимально:

* `attachAlgoClOrdId`
* `attachAlgoOrds[]`
* top-level `tp/sl` поля order snapshot
* `attachAlgoId`, если биржа его вернула внутри attached объекта
* `failCode`, `failReason`, если биржа их вернула

---

## Matching rules для `AttachedAlgoOrder`

Рекомендуемый порядок:

1. `externalAttachedId`
2. `externalId`
3. `internalId`
4. fallback по `type`, если по данной модели для order допустим только один child такого типа

Где:

* `externalAttachedId` = `attachAlgoId` из order snapshot
* `internalId` = `attachAlgoClOrdId`
* `externalId` = основной algoId, если позже child подтвердился в algo layer

---

## Какие статусы child могут выставляться в этой команде

### `ATTACHED`

Если protection подтверждён в snapshot обычного order, но ещё не подтверждён через algo endpoints.

### `FAILED`

Если snapshot attached protection явно содержит `failCode/failReason`.

### `CLOSED`

Только если есть достаточное доказательство, что child protection действительно больше не существует.

### Ключевое правило

Нельзя закрывать `AttachedAlgoOrder` только потому, что он пропал из позднего order snapshot после финализации parent
order.

Почему:

* protection может уже жить в algo-контуре;
* отсутствие в позднем order snapshot после fill не является достаточным доказательством `CLOSED`.

В таком кейсе child должен остаться в промежуточном состоянии и быть дальше подхвачен командой `REFRESH_ALGO_ORDERS`.

---

# 12. Граница между `REFRESH_PENDING_ORDERS` и `REFRESH_ALGO_ORDERS`

## Эта команда отвечает за `AttachedAlgoOrder`

До момента, пока attached protection сопровождается как часть order snapshot.

## Эта команда НЕ отвечает за algo-truth подтверждение

После того как attached child уже требует подтверждения через:

* `GET /api/v5/trade/order-algo`
* `GET /api/v5/trade/orders-algo-pending`
* `GET /api/v5/trade/orders-algo-history`

его дальше должен сопровождать `REFRESH_ALGO_ORDERS`.

---

# 13. Кто за что отвечает внутри этой команды

## `RefreshOrderExecutor`

Отвечает за orchestration ordinary order flow.

## `TradeRuleValidator`

Отвечает за проверку инвариантов команды и запуск аварийного сценария.

## `OrderStatusResolver`

Отвечает только за ordinary order status resolution.

## `OrderRefreshService` / `OrderSyncService`

Если такой слой есть, он должен:

* применять snapshot к доменной модели `Order`;
* сохранять order.

## `RefreshAttachedAlgoOrderExecutor`

Отвечает только за child sync attached protection из order snapshot.

---

# 14. Чего в этой команде делать нельзя

Нельзя:

* ходить в algo endpoints;
* использовать `RefreshAttachedAlgoOrderExecutor` как algo-truth resolver;
* молча создавать local `Order` на основании неизвестного внешнего live order;
* закрывать missing-from-pending `Order` без восстановления final state;
* закрывать `AttachedAlgoOrder` только по факту его отсутствия в позднем final order snapshot;
* держать изменения attached child только “в памяти до конца команды” без записи в БД.

---

# 15. Короткий итог

`REFRESH_PENDING_ORDERS` — это ordinary order truth layer.

Она:

* читает `orders-pending`;
* читает local live `Order`;
* валидирует инварианты;
* восстанавливает final state missing ordinary order'ов через `trade/order -> orders-history -> orders-history-archive`;
* вызывает `RefreshAttachedAlgoOrderExecutor` для сопровождения `AttachedAlgoOrder` как child order snapshot;
* не подтверждает algo-truth layer;
* не лезет в algo endpoints.
