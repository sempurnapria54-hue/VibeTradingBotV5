# AttachedAlgoOrder — статусы, переходы и правила сопровождения

## Цель

Эта дока фиксирует жизненный цикл `AttachedAlgoOrder`.

Она нужна, чтобы:

* `REFRESH_PENDING_ORDERS` и `REFRESH_ALGO_ORDERS` одинаково трактовали статусы child protection;
* `RefreshAttachedAlgoOrderExecutor` не закрывал child агрессивно;
* `RefreshAlgoOrderExecutor` понимал, какие child уже нужно подхватывать в algo-truth layer;
* Codex не додумывал переходы сам.

---

# 1. Роль сущности

`AttachedAlgoOrder` — это дочерняя доменная сущность `Order`.

Она нужна для сопровождения attached protection, которую OKX возвращает:

* в обычном order snapshot;
* а позже — через algo endpoints, если protection уже живёт как самостоятельный algo.

Это не просто DTO-поле внутри `Order`.

Это отдельная доменная сущность с собственным lifecycle.

---

# 2. Текущие доменные статусы

Используем следующие статусы:

## `CREATED`

Локальная child-запись создана, но биржа ещё не подтвердила attached protection.

## `ATTACHED`

Attached protection подтверждён в snapshot обычного ордера.

## `ACTIVE`

Attached protection подтверждён как live algo в algo-truth layer.

## `CLOSED`

Attached protection завершён и больше не активен.

## `FAILED`

Attached protection не удалось создать / подтвердить / сопроводить.

---

# 3. Смысл каждого статуса

## 3.1. `CREATED`

### Что значит

* child уже создан в БД;
* parent order локально знает, что protection должен быть;
* но внешнего подтверждения от биржи ещё нет.

### Где обычно встречается

* сразу после локального создания entry order с attached stop loss;
* до первого refresh snapshot ордера.

### Кто сопровождает

* ordinary order layer
* `REFRESH_PENDING_ORDERS`

### Кто НЕ должен читать

* `REFRESH_ALGO_ORDERS`

---

## 3.2. `ATTACHED`

### Что значит

* protection найден во внешнем snapshot обычного ордера;
* attached protection существует как часть order snapshot;
* но ещё не подтверждён как самостоятельный live algo в algo-truth layer.

### Где это подтверждается

Через ordinary order endpoints:

* `orders-pending`
* `trade/order`
* `orders-history`
* `orders-history-archive`

### Кто сопровождает

* сначала `REFRESH_PENDING_ORDERS`
* затем, если child уже должен подтверждаться через algo endpoints, его подхватывает `REFRESH_ALGO_ORDERS`

### Важный смысл

`ATTACHED` — это пограничный статус между order-truth layer и algo-truth layer.

---

## 3.3. `ACTIVE`

### Что значит

* attached protection уже подтверждён как live algo;
* truth source теперь algo endpoints.

### Где это подтверждается

Через algo endpoints:

* `order-algo`
* `orders-algo-pending`

### Кто сопровождает

* только `REFRESH_ALGO_ORDERS`

### Кто НЕ должен ставить этот статус

* `RefreshAttachedAlgoOrderExecutor`

Он не должен ходить в algo endpoints.

---

## 3.4. `CLOSED`

### Что значит

* attached protection больше не живой;
* он отменён, снят, сработал или завершён иным образом;
* доказательство этого есть в соответствующем truth layer.

### Важно

`CLOSED` нельзя ставить только по отсутствию child в позднем snapshot ордера после fill родителя.

Нужно различать:

* protection действительно закрыт;
* protection просто переехал в algo-truth layer.

---

## 3.5. `FAILED`

### Что значит

* protection не удалось создать / подтвердить / восстановить;
* есть явное доказательство ошибки.

### Источники доказательства

* `failCode` / `failReason` в attached snapshot обычного ордера;
* `order_failed` в algo history;
* явный проектный failure case, который не является ambiguous.

---

# 4. Кто какие статусы имеет право выставлять

## `RefreshAttachedAlgoOrderExecutor`

### Может выставлять

* `ATTACHED`
* `FAILED`
* `CLOSED` — только если есть достаточное доказательство именно на order-snapshot стадии

### Не должен выставлять

* `ACTIVE`

Почему:
`ACTIVE` подтверждается только algo endpoints.

---

## `RefreshAlgoOrderExecutor`

### Может выставлять

* `ACTIVE`
* `CLOSED`
* `FAILED`

### Не должен выставлять

* `CREATED`
* `ATTACHED` как новый начальный статус

Почему:
В algo-layer child уже должен приходить как минимум в `ATTACHED`.

---

# 5. Таблица допустимых переходов

## 5.1. Основной happy path

```text
CREATED -> ATTACHED -> ACTIVE -> CLOSED
```

Расшифровка:

* `CREATED -> ATTACHED` — protection подтверждён в order snapshot
* `ATTACHED -> ACTIVE` — protection подтверждён как live algo через algo endpoints
* `ACTIVE -> CLOSED` — protection завершён, снят или сработал

---

## 5.2. Happy path без отдельного live algo подтверждения

```text
CREATED -> ATTACHED -> CLOSED
```

Когда допустимо:

* protection был виден только как child order snapshot;
* parent order cancelled / attached был явно снят;
* есть достаточное доказательство, что protection не переехал в algo-truth layer.

Это не default path, а допустимый special case.

---

## 5.3. Failure path до algo-activation

```text
CREATED -> FAILED
CREATED -> ATTACHED -> FAILED
```

Когда допустимо:

* order snapshot вернул attached failure proof;
* создание attached protection явно не удалось.

---

## 5.4. Failure path после algo-activation

```text
ATTACHED -> ACTIVE -> FAILED
ATTACHED -> FAILED
```

Когда допустимо:

* algo history показал `order_failed`;
* protection не смог перейти в рабочий algo-state.

---

# 6. Недопустимые переходы

Ниже переходы, которые делать нельзя.

## Нельзя делать

```text
CREATED -> ACTIVE
```

Почему:
Нельзя перепрыгивать стадию attached confirmation в order snapshot.

---

```text
ACTIVE -> ATTACHED
```

Почему:
Назад из algo-truth layer в order-truth layer child не возвращается.

---

```text
CLOSED -> ACTIVE
CLOSED -> ATTACHED
FAILED -> ACTIVE
FAILED -> ATTACHED
```

Почему:
После terminal state child не должен оживать той же записью.
Нужно создавать новую сущность, если protection создаётся заново.

---

# 7. Когда `RefreshAttachedAlgoOrderExecutor` может ставить `CLOSED`

Это самый опасный момент, поэтому правило фиксируем отдельно.

## Разрешено ставить `CLOSED`, если одновременно выполняется одно из условий:

### Условие A

Parent order явно отменён / финализирован так, что attached protection больше не может существовать.

### Условие B

Order snapshot явно отражает удаление attached protection, и проектно это трактуется однозначно.

### Условие C

Есть прямое проектное правило, что для этого типа parent-final-state attached child не может перейти в algo-layer.

---

## Нельзя ставить `CLOSED`, если:

* parent order завершился fill;
* поздний order snapshot больше не содержит attached fields;
* но нет доказательства, что child действительно закрыт;
* и остаётся возможность, что protection уже живёт через algo endpoints.

### В этом случае

child должен остаться в `ATTACHED` и быть подхвачен `REFRESH_ALGO_ORDERS`.

Это обязательное правило.

---

# 8. Когда `RefreshAlgoOrderExecutor` может ставить terminal status

## `ACTIVE`

Если live algo подтверждён через:

* `order-algo`
* или `orders-algo-pending`

## `CLOSED`

Если algo history доказал final close:

* `effective`
* `canceled`
* или другой согласованный final non-live state

## `FAILED`

Если algo history доказал failure:

* `order_failed`
* или другой согласованный failure state

---

# 9. Что делать в ambiguous cases

## Случай 1

Child пропал из order snapshot после fill parent order.

### Что делать

Не закрывать.
Оставить `ATTACHED`.
Дальше подхватит algo-layer.

---

## Случай 2

Child не найден ни в algo detail, ни в algo pending, ни в algo history.

### Что делать

Не ставить silent `CLOSED`.
Это unresolved state.

Дальше — либо retry, либо anomaly, либо exception по общей политике проекта.

---

## Случай 3

Во внешнем snapshot пришли противоречивые attached данные.

### Что делать

Не делать догадки.
Либо `FAILED`, если есть явный failure proof, либо anomaly / guarded exception.

---

# 10. Правило сохранения в БД

Каждое значимое изменение `AttachedAlgoOrder` должно сразу писаться в БД.

Обязательно сохранять при:

* создании child
* смене статуса
* изменении identifiers
* изменении trigger fields
* закрытии
* failure
* algo activation

Нельзя держать lifecycle child только в памяти до конца команды.

---

# 11. Короткий итог

Жизненный цикл `AttachedAlgoOrder` фиксируем так:

## Основной путь

`CREATED -> ATTACHED -> ACTIVE -> CLOSED`

## Допустимые альтернативы

* `CREATED -> ATTACHED -> CLOSED`
* `CREATED -> FAILED`
* `CREATED -> ATTACHED -> FAILED`
* `ATTACHED -> ACTIVE -> FAILED`

## Главные правила

* `ACTIVE` ставит только algo-layer
* `RefreshAttachedAlgoOrderExecutor` не ставит `ACTIVE`
* нельзя закрывать child только по исчезновению из позднего order snapshot
* terminal statuses не оживают обратно той же записью
