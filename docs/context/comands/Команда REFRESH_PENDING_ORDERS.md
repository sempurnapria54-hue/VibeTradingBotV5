# Команда REFRESH_PENDING_ORDERS — executor flow

## Цель

Команда `REFRESH_PENDING_ORDERS` нужна для синхронизации **обычных незавершённых ордеров** по инструменту между биржей и
локальной БД.

Под обычными ордерами здесь понимаются именно `Order`, без algo-ордеров.

Команда должна:

* прочитать pending orders с биржи;
* прочитать живые локальные `Order` по инструменту;
* передать оба набора данных в `TradeRuleValidator`;
* если validator не нашёл нарушения — выполнить обычный sync;
* если validator нашёл нарушение — прервать обычный sync через anomaly/kill-switch flow.

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
              -> CancelOrderExecutor
              -> CancelAlgoOrderExecutor
              -> ClosePositionExecutor
              -> DealEmergencyFinalizer
        -> обычный sync orders, если validator ничего не выявил
```

Важно:

* `RefreshPendingOrdersExecutor` не должен сам вызывать `KillSwitchService`;
* `RefreshPendingOrdersExecutor` не должен сам создавать `AnomalyReport`;
* `KillSwitchService` не должен вызывать `RefreshPendingOrdersExecutor` обратно.

---

## Уровень команды

Команда выполняется **по инструменту**.

То есть рабочий ключ:

* `exchange`
* `instrument`
* при необходимости `dealId` как контекст

Но источник правды для pending orders — это именно pending snapshot биржи по инструменту.

---

## Входные данные

Для команды нужны:

* `Exchange exchange`
* `Instrument instrument`
* `Long dealId` — если validator / anomaly flow должны знать текущую сделку

Рекомендуемая сигнатура:

```java
public void execute(Exchange exchange, Instrument instrument, Long dealId)
```

---

## Что считается источником правды

### Внешняя сторона

Источник правды — `GET /trade/orders-pending` по инструменту.

То есть executor должен получить список `OrderExternalSnapshot`, отражающий все текущие незавершённые обычные ордера по
инструменту.

### Внутренняя сторона

Нужно читать из БД только **живые** локальные `Order` по инструменту.

Для операционного refresh нельзя читать всю историю по `instrumentId` без фильтра по статусам.

Базовое правило:

* для действий читаем по `instrumentId + live statuses`

---

## Общий flow выполнения

### Шаг 1. Прочитать внешние pending orders

Нужно запросить у client service pending orders по инструменту.

Ожидаемый результат:

* список `OrderExternalSnapshot`
* либо пустой список, если pending orders по инструменту сейчас нет

---

### Шаг 2. Прочитать внутренние живые orders

Нужно загрузить из БД только живые локальные обычные ордера по инструменту.

То есть не всю историю, а только orders в live-статусах.

---

### Шаг 3. Передать данные в `TradeRuleValidator`

Executor не должен сам определять аномалию.

Он должен передать в validator:

* `exchange`
* `instrument`
* `dealId`
* внешние pending orders
* внутренние живые orders

Если validator ничего не выявил — управление возвращается в executor.

Если validator выявил нарушение, он сам:

* создаёт и ведёт `AnomalyReport`;
* запускает `KillSwitchService`;
* выбрасывает `TradeRuleViolationException`.

После этого обычный sync не продолжается.

---

### Шаг 4. Выполнить обычный sync orders

Если validator не выбросил исключение, executor должен синхронизировать локальные `Order` с биржевым snapshot.

Основные сценарии:

#### Сценарий A. Ордер есть на бирже и есть в БД

Нужно:

* найти соответствующий локальный order;
* обновить его из snapshot;
* резолвить доменный статус;
* сохранить.

#### Сценарий B. Ордер есть на бирже, но в БД его нет

Нужно:

* создать новый локальный `Order`;
* привязать к инструменту;
* при необходимости привязать к `dealId`;
* заполнить поля из snapshot;
* сохранить.

Важно:

* обычный ордер может существовать на бирже раньше, чем локальная БД о нём узнала;
* локальная система должна уметь это отразить.

#### Сценарий C. Ордера нет на бирже, но локальный order ещё живой

Нужно:

* обновить локальный order;
* перевести его в закрытый/финальный доменный статус;
* сохранить.

#### Сценарий D. Нет ни внешних pending orders, ни внутренних живых orders

Ничего не делать.

---

## Что значит “сопоставить ордер”

Сопоставление должно идти по устойчивому идентификатору.

Предпочтительный порядок:

* `externalId`
* если его нет, то `internalId` / `clOrdId`, если это соответствует текущей модели

Сопоставление не должно строиться на случайных косвенных полях.

---

## Кто резолвит доменный статус order

Резолв статуса должен делать сам refresh executor или выделенный sync-компонент order-слоя.

FSM не должна знать детали внешних статусов биржи.

Пример helper-метода:

```java
private Order.Status resolveOrderStatus(OrderExternalSnapshot snapshot)
```

---

## Что не должен делать RefreshPendingOrdersExecutor

`RefreshPendingOrdersExecutor` не должен:

* сам создавать `AnomalyReport`;
* сам запускать `KillSwitchService`;
* сам блокировать инструмент;
* сам принимать решения FSM;
* сам сопровождать `DealContext`.

Он только:

* читает внешние pending orders;
* читает внутренние live orders;
* вызывает validator;
* при отсутствии аномалии делает sync;
* сохраняет результаты в БД.

---

## Работа с persistence

Для команды нужны repository/data service методы чтения живых orders по инструменту.

Ожидаемый контракт, например:

```java
List<Order> findAllByInstrumentIdAndStatuses(Long instrumentId, Set<String> statuses)
```

Если в проекте уже есть такой метод — использовать его.

Также нужны обычные методы:

* `save(...)`
* при необходимости поиск по `externalId` / `internalId`

---

## Инварианты команды

### До выполнения

* известны `exchange` и `instrument`;
* доступен client service;
* доступен persistence слой;
* определены live-статусы для `Order`.

### После успешного выполнения

* локальные `Order` по инструменту отражают актуальный pending snapshot биржи;
* отсутствующие на бирже live orders локально финализированы;
* повторный запуск команды не создаёт хаоса и дубликатов.

---

## Короткий итог

`REFRESH_PENDING_ORDERS` — это обычная sync-команда по инструменту.

Она:

* получает pending orders с биржи;
* читает живые локальные orders;
* передаёт оба набора в `TradeRuleValidator`;
* если всё в порядке — синхронизирует локальные orders;
* если найдено нарушение — validator запускает anomaly / kill-switch flow и прерывает команду.
