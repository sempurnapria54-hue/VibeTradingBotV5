# Команда REFRESH_PENDING_ORDERS — полное переопределение с нуля

## Цель

Команда `REFRESH_PENDING_ORDERS` нужна для корректной синхронизации обычных ордеров (`Order`) по инструменту между
биржей и локальной БД.

Это не просто “обновить pending snapshot”.

Команда должна:

* получить текущие pending orders по инструменту;
* получить локальные живые `Order` по инструменту;
* передать оба набора в `TradeRuleValidator`;
* если validator не нашёл нарушение — выполнить штатный sync;
* если локальный order пропал из pending snapshot, **не закрывать его вслепую**, а восстановить его финальное состояние
  через другие биржевые endpoint'ы.

---

## Главная проблема старого подхода

Неправильно делать так:

```text
order исчез из orders-pending -> ставим CLOSED
```

Почему это плохо:

* `orders-pending` показывает только незавершённые ордера;
* исчезновение из pending не означает автоматически `CLOSED`;
* ордер мог:

    * полностью исполниться;
    * частично исполниться и потом отмениться;
    * просто отмениться;
    * перейти в другое финальное состояние.

Поэтому для order, который пропал из pending snapshot, нужно отдельно восстанавливать финальное состояние.

---

## Кто выполняет команду

Команда должна выполняться через отдельный executor:

* `ServiceCommandExecutor`
* `RefreshPendingOrdersExecutor`

Фасадный `OrderService` может существовать как точка входа, но use-case команды должен жить именно в executor.

---

## Правильная цепочка вызовов

```text
Handler
  -> ServiceCommandExecutor
     -> RefreshPendingOrdersExecutor
        -> TradeRuleValidator
           -> AnomalyService
           -> KillSwitchService
        -> обычный sync pending orders
        -> отдельное восстановление финального состояния для orders, исчезнувших из pending
```

Важно:

* `RefreshPendingOrdersExecutor` не должен сам запускать kill-switch;
* `TradeRuleValidator` остаётся единственной точкой запуска anomaly / kill-switch flow;
* восстановление финального состояния order — это часть order refresh логики, а не kill-switch логики.

---

## Уровень команды

Команда выполняется по:

* `exchange`
* `instrument`
* при необходимости `dealId`

То есть это команда уровня инструмента.

---

## Входные данные

Рекомендуемая сигнатура:

```java
public void execute(Exchange exchange, Instrument instrument, Long dealId)
```

Если `dealId` не передан, но нужно создать новый order из snapshot — допустимо отдельно резолвить последнюю сделку по
инструменту.

---

## Что считается источниками правды

### 1. Pending источник

`GET /trade/orders-pending`

Используется для получения текущих незавершённых order'ов по инструменту.

### 2. Detail источник

`GET /trade/order`

Используется для восстановления текущего или финального состояния **конкретного** order по `ordId` или `clOrdId`.

### 3. Recent history источник

`GET /trade/orders-history`

Используется для восстановления завершённых order'ов за последние 7 дней.

### 4. Archive history источник

`GET /trade/orders-history-archive`

Используется как fallback для order'ов старше окна recent history.

---

## Общий flow команды

## Шаг 1. Прочитать внешний pending snapshot

Нужно получить:

* `List<OrderExternalSnapshot> externalPendingSnapshots`

Это список обычных незавершённых order'ов по инструменту.

---

## Шаг 2. Прочитать внутренние live orders

Нужно получить из БД:

* только живые `Order` по `instrumentId`
* только по live statuses

Нельзя читать всю историю order'ов по инструменту.

---

## Шаг 3. Передать наборы в `TradeRuleValidator`

Executor должен передать в validator:

* `exchange`
* `instrument`
* `dealId`
* `externalPendingSnapshots`
* `liveOrders`

Если validator выбросил исключение — обычный sync не продолжается.

---

## Шаг 4. Синхронизировать order'ы, которые есть в pending snapshot

Для каждого внешнего pending snapshot:

### Сценарий A. Local order найден

Нужно:

* обновить локальный order из snapshot;
* резолвить доменный статус (`PENDING`, `PARTIALLY_COMPLETED`, и т.д.);
* сохранить.

### Сценарий B. Local order не найден

Нужно:

* создать новый локальный `Order`;
* привязать к instrument;
* при необходимости привязать к `dealId`;
* обновить поля из snapshot;
* сохранить.

---

## Шаг 5. Обработать локальные live orders, которых нет в pending snapshot

Вот здесь и должна быть новая правильная логика.

Если локальный live order не встретился в `orders-pending`, это значит только одно:

* order больше не pending

Но это **не означает**, что он просто `CLOSED`.

Для таких orders нужно выполнить восстановление финального состояния.

---

## Как восстанавливать финальное состояние order

Для каждого локального live order, который не нашёлся в pending snapshot:

### Шаг 5.1. Попробовать `GET /trade/order`

Нужно запросить details конкретного order:

* сначала по `ordId`, если есть `externalId`
* если `externalId` нет, то по `clOrdId` / `internalId`, если это согласовано с текущей моделью

Если detail endpoint вернул order:

* обновить локальный order из detail snapshot;
* резолвить доменный статус;
* сохранить;
* на этом обработка закончена.

### Шаг 5.2. Если detail не помог — попробовать `orders-history`

Если detail endpoint не дал результата или не дал достаточно информации:

* искать order в `orders-history` по инструменту;
* сопоставлять по `externalId` или `clOrdId`.

Если нашли:

* обновить локальный order;
* резолвить финальный доменный статус;
* сохранить.

### Шаг 5.3. Если recent history не помог — попробовать `orders-history-archive`

Если order не найден в `orders-history`:

* искать в `orders-history-archive`;
* сопоставлять по `externalId` или `clOrdId`.

Если нашли:

* обновить локальный order;
* резолвить финальный доменный статус;
* сохранить.

### Шаг 5.4. Только если ничего не найдено

Если order не найден:

* ни в detail;
* ни в recent history;
* ни в archive history;

только тогда применять явно согласованный fallback.

Но fallback должен быть осознанным и отдельным правилом, а не “по умолчанию CLOSED”.

---

## Предпочтительная архитектура восстановления финального состояния

Я бы выделил отдельный компонент, чтобы executor не распухал.

Например:

* `OrderFinalStateResolver`
* или `OrderExternalStateResolver`

Его ответственность:

* принять `exchange`, `instrument`, `Order`
* попытаться восстановить реальное состояние через:

    * details
    * recent history
    * archive history
* вернуть либо найденный `OrderExternalSnapshot`, либо специальный результат “not found”

Тогда executor останется orchestration-классом.

---

## Как сопоставлять orders

Порядок сопоставления должен быть устойчивым:

### Приоритет 1

* `externalId`

### Приоритет 2

* `internalId` / `clOrdId`

Не строить сопоставление по косвенным полям.

---

## Кто резолвит доменный статус

Резолв доменного статуса order должен делать order-layer, а не FSM.

То есть должен существовать единый helper/service, который умеет переводить внешний статус в доменный.

Например:

* `resolveOrderStatus(snapshot)`

Он должен одинаково работать и для pending snapshot, и для detail/history snapshot.

---

## Какие статусы нужно различать

Минимально:

* `live` -> `PENDING`
* `partially_filled` -> `PARTIALLY_COMPLETED`
* `filled` -> `COMPLETED`
* `canceled` -> `CLOSED`

Если в модели есть более точные финальные причины/статусы — использовать их.

---

## Что не должен делать executor

`RefreshPendingOrdersExecutor` не должен:

* сам запускать kill-switch;
* сам создавать `AnomalyReport`;
* сам принимать решения FSM;
* закрывать orders вслепую только на основании отсутствия в pending snapshot.

---

## Persistence слой

Нужны:

* чтение live orders по `instrumentId + live statuses`
* сохранение order
* при необходимости поиск по `externalId`
* при необходимости поиск по `internalId`

История order'ов может читаться с биржи, а не из БД.

---

## Короткий итог

Новая правильная логика команды такая:

* pending snapshot нужен только для текущих незавершённых order'ов;
* order, который исчез из pending, не должен автоматически закрываться;
* для него нужно отдельно восстанавливать финальное состояние через:

    * details
    * recent history
    * archive history
* только после этого локальная модель `Order` может быть корректно финализирована.
