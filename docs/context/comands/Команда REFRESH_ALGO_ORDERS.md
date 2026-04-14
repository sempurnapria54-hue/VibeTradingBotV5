# REFRESH_ALGO_ORDERS — полная спецификация команды

## Цель команды

`REFRESH_ALGO_ORDERS` — это сервисная команда, которая сопровождает:

* самостоятельные `AlgoOrder`;
* `AttachedAlgoOrder`, которые уже вышли из ordinary order snapshot-контура и должны подтверждаться через algo
  endpoints.

Команда работает только в algo-truth layer.

Она не занимается ordinary order truth layer. Это зона ответственности `REFRESH_PENDING_ORDERS`.

---

# 1. Где команда находится в общей архитектуре

```text
Handler
  -> ServiceCommandExecutor
     -> RefreshAlgoOrderExecutor
        -> TradeRuleValidator.validateRefreshAlgoOrders(...)
        -> normal algo refresh flow
```

После выполнения orchestrator заново перечитывает `DealContext`.

---

# 2. Что именно делает команда

Команда должна:

1. получить внешний snapshot live algo-ордеров по инструменту;
2. получить local live `AlgoOrder` из БД по инструменту;
3. получить `AttachedAlgoOrder`, которые уже требуют algo-подтверждения;
4. проверить торговые инварианты именно для algo refresh-команды;
5. если инварианты не нарушены — синхронизировать live algo сущности;
6. для каждой local live algo-сущности, которой нет во внешнем live algo snapshot, восстановить final algo state через
   algo endpoints;
7. сразу сохранять любые изменения `AlgoOrder` и `AttachedAlgoOrder` в БД.

---

# 3. Что команда НЕ делает

Команда `REFRESH_ALGO_ORDERS` не должна:

* сопровождать ordinary `Order`;
* читать `orders-pending`;
* читать `trade/order`;
* сопровождать ordinary order attached stage;
* вызывать `RefreshAttachedAlgoOrderExecutor`;
* создавать / обновлять parent `Order`;
* самостоятельно запускать kill-switch;
* самостоятельно формировать `AnomalyReport`.

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

# 5. Какие сущности сопровождает команда

## 5.1. `AlgoOrder`

Полноценный самостоятельный algo-ордер.

## 5.2. `AttachedAlgoOrder`

Но только в той фазе, когда child уже должен подтверждаться через algo endpoints.

То есть здесь мы работаем не с attached child как частью order snapshot, а с attached child как с сущностью, которая уже
живёт в algo-truth layer.

---

# 6. Какие `AttachedAlgoOrder` должна читать эта команда

Команда должна читать из БД только те child entities, которые уже имеют смысл сопровождать через algo endpoints.

Минимально это:

* `ATTACHED`
* `ACTIVE`

Почему:

* `ATTACHED` — child уже подтверждён в order snapshot, но ещё не подтверждён как самостоятельный live algo;
* `ACTIVE` — child уже подтверждён как live algo и должен продолжать сопровождаться в algo layer.

`CREATED` сюда попадать не должен.
Это ещё ordinary order / pre-confirmation стадия.

---

# 7. Источники правды и какие методы вызываем

## 7.1. Живой algo snapshot

### Методы

1. `GET /api/v5/trade/orders-algo-pending`
2. при необходимости `GET /api/v5/trade/order-algo`

### Что важно понимать

`orders-algo-pending` — это стартовый список live algo-ордеров. Он нужен как основной внешний snapshot live algo
состояния по инструменту.

`order-algo` — точечный detail endpoint. Его используем для восстановления состояния конкретной algo-сущности.

---

## 7.2. Final algo state

Если local live algo сущность отсутствует во внешнем live snapshot, final state восстанавливаем по цепочке:

### Шаг 1

`GET /api/v5/trade/order-algo`

### Шаг 2

Если detail не помог:
`GET /api/v5/trade/orders-algo-history`

### Важно

Нельзя делать:

```text
missing from orders-algo-pending -> CLOSED
```

Это неверно.

Пропажа из pending algo snapshot означает только, что algo больше не live/pending. Его final state нужно доказать через
algo detail/history.

---

# 8. Какие ordType и состояния надо учитывать

## 8.1. Для pending algo snapshot

`GET /orders-algo-pending` возвращает не сработавшие / ещё активные algo orders.

Для покрытия команды нужно уметь читать ordType:

* `conditional`
* `oco`
* `trigger`
* `move_order_stop`

Нужно использовать ровно те типы, которые реально есть в проекте, плюс live local algo сущности.

## 8.2. Для algo history

`GET /orders-algo-history` использовать с состояниями истории:

* `effective`
* `canceled`
* `order_failed`

Именно эти состояния нужны для finalization.

---

# 9. Что читаем из БД

## 9.1. Local live AlgoOrder

Из БД читаем только live `AlgoOrder` по инструменту.

## 9.2. Local AttachedAlgoOrder в algo-layer

Из БД читаем `AttachedAlgoOrder` в статусах:

* `ATTACHED`
* `ACTIVE`

Нельзя читать всю историю без фильтра по статусам.

---

# 10. Проверка торговых правил для команды

### Кто делает

`TradeRuleValidator`

### Метод

Рекомендуемое имя:

```java
validateRefreshAlgoOrders(exchange,
                          instrument,
                          dealId,
                          externalLiveAlgoSnapshots,
                          internalLiveAlgoOrders,
                          internalAttachedAlgoOrders)
```

---

## 10.1. Какие инварианты обязан проверить validator

## Правило 1. Во внешнем live algo snapshot не должно быть дублей

Нельзя, чтобы во внешнем algo snapshot были два live algo с одинаковым:

* `algoId`
* или `algoClOrdId`

Если такие дубли есть — это anomaly.

---

## Правило 2. В локальной БД не должно быть дублей live `AlgoOrder`

Нельзя, чтобы среди local live `AlgoOrder` были дубли по:

* `externalId`
* `internalId`

Если есть — это anomaly.

---

## Правило 3. В локальной БД не должно быть дублей live `AttachedAlgoOrder` algo-слоя

Нельзя, чтобы среди `AttachedAlgoOrder`, попавших в algo-команду, были дубли по:

* `externalAttachedId`
* `externalId`
* `internalId`

Если есть — это anomaly.

---

## Правило 4. Каждый внешний live algo должен однозначно сопоставляться с одной local algo-сущностью

Порядок matching должен быть таким:

1. `externalId` (`algoId`)
2. `internalId` (`algoClOrdId`)
3. для attached child допускается дополнительно `externalAttachedId`, если именно он используется как биржевой attached
   identifier

Если внешний live algo:

* не сопоставился ни с одной local algo-сущностью,
* или сопоставился сразу с несколькими,

это anomaly.

### Почему это anomaly, а не normal create case

В проекте базовый инвариант тот же: сначала локальная БД, потом биржа.

Значит live algo на бирже без local сущности — это нарушение базового инварианта, а не happy path refresh.

---

## Правило 5. Local algo-сущности должны относиться к текущему инструменту

Если в выборке оказалась algo-сущность другого инструмента — это anomaly.

---

## Правило 6. При любой ambiguous ситуации normal algo sync запрещён

Если есть хотя бы одна ambiguous ситуация:

* дубли;
* неизвестный внешний live algo;
* неоднозначное сопоставление;

validator должен остановить normal flow.

---

# 11. Что делает validator при нарушении

Если правило нарушено, validator выполняет полный trade-rule violation flow:

1. создаёт initial `AnomalyReport`;
2. пишет `internal_before` и `external_before`;
3. при необходимости через `InstrumentService` блокирует инструмент;
4. вызывает `KillSwitchService`;
5. получает `internal_after` и `external_after`;
6. доводит report до `KILL_SWITCH_EXECUTED`, затем `COMPLETED` или `ERROR`;
7. выбрасывает `TradeRuleViolationException`.

После этого normal algo sync продолжаться не должен.

---

# 12. Normal flow команды по шагам

## Шаг 1. Получить внешний live algo snapshot

Основной метод:

* `GET /api/v5/trade/orders-algo-pending`

Так как endpoint фильтруется по `ordType`, нужно прочитать все релевантные ordType для текущего инструмента.

Рекомендуемая логика:

1. собрать набор ordType из local live `AlgoOrder` и `AttachedAlgoOrder`;
2. добавить project default ordType, если это нужно;
3. пройтись по ordType и собрать единый deduplicated внешний snapshot.

## Шаг 2. Прочитать local live algo сущности

Из БД:

* live `AlgoOrder`
* `AttachedAlgoOrder` в статусах `ATTACHED` и `ACTIVE`

## Шаг 3. Вызвать validator

`validateRefreshAlgoOrders(...)`

Если validator выбросил исключение — команда заканчивается аварийно.

## Шаг 4. Обновить matched live algo сущности

Для каждого matched external live algo:

* найти local algo entity (`AlgoOrder` или `AttachedAlgoOrder`);
* обновить её из snapshot;
* перевести в `ACTIVE`, если algo подтверждён live;
* сохранить в БД.

## Шаг 5. Обработать local algo сущности, которых нет в live algo snapshot

Для каждой такой сущности:

### 5.1. Вызвать `GET /trade/order-algo`

Ищем по:

* `algoId`
* если его нет, то по `algoClOrdId`

Если найден detail snapshot:

* обновляем сущность;
* если state live/pause -> `ACTIVE`
* если уже есть достаточное final proof, то переводим в final status;
* сохраняем.

### 5.2. Если detail не помог — вызвать `GET /trade/orders-algo-history`

Ищем по:

* `algoId`
* или по инструменту + ordType + history state

Если history показывает:

* `effective` -> final state, обычно `CLOSED`
* `canceled` -> `CLOSED`
* `order_failed` -> `FAILED`

Сохраняем в БД.

### 5.3. Если algo не найден ни в detail, ни в history

Это не normal sync case.

Нельзя молча ставить `CLOSED`.

Это unresolved algo state, который должен идти либо в retryable flow, либо в anomaly / exception — по общей политике
проекта.

---

# 13. Как команда должна работать с `AttachedAlgoOrder`

## 13.1. Что именно меняется по сравнению с `REFRESH_PENDING_ORDERS`

В этой команде `AttachedAlgoOrder` уже не считается child только ordinary order snapshot.

Здесь он рассматривается как algo-layer сущность, которой нужно доказать live/final state через algo endpoints.

---

## 13.2. Какие статусы child может получать здесь

### `ACTIVE`

Если algo detail или algo pending подтвердили живой algo.

### `CLOSED`

Если algo history доказал финальное закрытие / отмену / срабатывание.

### `FAILED`

Если algo history показывает `order_failed` или equivalent failure proof.

### Что важно

Команда не должна возвращать child назад в `ATTACHED`.

`ATTACHED` — это входной статус на границе между order-layer и algo-layer.

---

## 13.3. Какие поля child обновляем здесь

Минимально:

* `externalId` (`algoId`)
* `externalStatus`
* `externalType`
* `size`
* trigger prices
* final status
* failure markers

Каждое изменение — сразу в БД.

---

# 14. Кто за что отвечает внутри этой команды

## `RefreshAlgoOrderExecutor`

Отвечает за orchestration algo refresh flow.

## `TradeRuleValidator`

Отвечает за инварианты команды и аварийный сценарий.

## `AlgoOrderRefreshService` / `AlgoOrderSyncService`

Если такой слой есть, он должен:

* применять algo snapshot к `AlgoOrder` и `AttachedAlgoOrder`;
* сохранять сущности.

## `RefreshAttachedAlgoOrderExecutor`

В этой команде использоваться не должен.

Он относится только к order snapshot stage.

---

# 15. Граница между `REFRESH_PENDING_ORDERS` и `REFRESH_ALGO_ORDERS`

## `REFRESH_PENDING_ORDERS`

Сопровождает `AttachedAlgoOrder` до тех пор, пока truth source — это order snapshot.

## `REFRESH_ALGO_ORDERS`

Подхватывает `AttachedAlgoOrder` после этого и сопровождает его через algo endpoints.

Коротко:

* до algo-активации — `REFRESH_PENDING_ORDERS`
* после — `REFRESH_ALGO_ORDERS`

---

# 16. Чего в этой команде делать нельзя

Нельзя:

* читать `orders-pending` и `trade/order` для algo-truth refresh;
* вызывать `RefreshAttachedAlgoOrderExecutor`;
* закрывать missing-from-pending algo сущность без `order-algo` / `orders-algo-history`;
* молча создавать local algo-сущность на основании неизвестного внешнего live algo;
* обновлять parent `Order` из algo-команды.

---

# 17. Короткий итог

`REFRESH_ALGO_ORDERS` — это algo truth layer.

Она:

* читает live algo snapshot через `orders-algo-pending`;
* читает local live `AlgoOrder` и attached children, которые уже вошли в algo-layer;
* валидирует инварианты;
* восстанавливает final algo state через `order-algo -> orders-algo-history`;
* подтверждает `ACTIVE / CLOSED / FAILED`;
* сразу пишет любые изменения в БД;
* не лезет в ordinary order truth layer.
