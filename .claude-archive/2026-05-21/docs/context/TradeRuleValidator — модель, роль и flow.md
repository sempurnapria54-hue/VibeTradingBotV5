# TradeRuleValidator — flow с executor-моделью

## Цель

`TradeRuleValidator` — это отдельный сервис проверки торговых инвариантов перед выполнением обычного sync.

Он нужен для того, чтобы отделить:

* обычную синхронизацию фактов биржи и БД;
* проверку допустимости состояния;
* запуск полного аварийного сценария при нарушении правил.

---

## Место в новой executor-модели

`TradeRuleValidator` используется только в refresh-flow.

Например:

* `RefreshPositionExecutor`
* `RefreshPendingOrdersExecutor`
* `RefreshAlgoOrdersExecutor`

Он не должен использоваться из `KillSwitchService` и не должен вызывать refresh-executors обратно.

---

## Главная цепочка

На примере позиций:

```text
ServiceCommandExecutor
  -> RefreshPositionExecutor
     -> TradeRuleValidator
        -> AnomalyService
        -> KillSwitchService
           -> ClosePositionExecutor
           -> CancelOrderExecutor
           -> CancelAlgoOrderExecutor
           -> DealEmergencyFinalizer
```

Важно:

* `TradeRuleValidator` сам запускает полный аварийный сценарий;
* после завершения аварийного сценария он выбрасывает `TradeRuleViolationException`;
* обычный refresh после этого не продолжается.

---

## Что получает validator

Validator должен принимать только минимально необходимые данные.

Для проверки позиций это обычно:

* `Exchange exchange`
* `Instrument instrument`
* `Long dealId`
* `List<PositionExternalSnapshot> externalSnapshots`
* `List<Position> domainPositions`

Без `DealContext` и без зависимости от FSM.

---

## Что validator делает, если всё ок

Если состояние допустимо:

* validator ничего не меняет;
* не вызывает побочных сервисов;
* просто возвращает управление в refresh-executor.

---

## Что validator делает, если найдено нарушение

Если найдено нарушение, validator выполняет полный аварийный сценарий.

### Шаг 1. Классифицирует нарушение

Определяет:

* `code`
* `severity`

### Шаг 2. Собирает before-снимки

Собирает:

* `internalBefore`
* `externalBefore`

Это должны быть **полные слепки по инструменту**, а не только данные, которые validator прямо сейчас сравнивал.

### Шаг 3. Создаёт `AnomalyReport`

Через `AnomalyService.create(...)`.

### Шаг 4. Переводит репорт в `IN_PROGRESS`

Через `AnomalyService.markInProgress(...)`.

### Шаг 5. Вызывает `KillSwitchService`

Передаёт:

* `exchange`
* `instrument`
* `dealId`
* `reasonCode`

### Шаг 6. Получает `KillSwitchResult`

Из него берёт:

* `success`
* `internalAfter`
* `externalAfter`
* `message`

### Шаг 7. Завершает `AnomalyReport`

Если `success = true`:

* сначала `markKillSwitchExecuted(...)`
* затем `complete(...)`

Если `success = false` или произошла ошибка:

* `markError(...)`

### Шаг 8. Прерывает обычный sync

После этого validator **всегда** выбрасывает `TradeRuleViolationException`.

---

## Что validator не должен делать

`TradeRuleValidator` не должен:

* выполнять обычный sync позиций, ордеров и algo-ордеров;
* менять `DealContext`;
* принимать решения FSM;
* руками закрывать позиции или ордера;
* резолвить внешние статусы биржи в доменные.

Статусы резолвят соответствующие refresh executors.

Аварийные действия выполняют соответствующие close/cancel executors через `KillSwitchService`.

---

## Какие проверки должен уметь делать

Минимально по позициям:

* на бирже не более одной открытой позиции по инструменту;
* в БД не более одной активной позиции по инструменту;
* внешнее и внутреннее состояние не нарушают базовые правила.

Позже — аналогично для orders и algo-orders.

---

## Рекомендуемая структура класса

```java
public class TradeRuleValidator {

    public void validatePositions(...) {
    }

    public void validateOrders(...) {
    }

    public void validateAlgoOrders(...) {
    }
}
```

Внутри допустимы helper-методы:

* `checkExternalPositionsCount(...)`
* `checkInternalPositionsCount(...)`
* `resolveCode(...)`
* `resolveSeverity(...)`
* `buildInternalBefore(...)`
* `buildExternalBefore(...)`
* `handleViolation(...)`

---

## Роль `AnomalyJob`

`AnomalyJob` не является основным обработчиком новой аномалии.

Она используется как recovery-механизм:

* если процесс оборвался после `CREATED`;
* после `IN_PROGRESS`;
* после `KILL_SWITCH_EXECUTED`.

---

## Короткий итог

`TradeRuleValidator` в новой модели:

* используется из refresh-executors;
* при нарушении сам запускает полный аварийный flow;
* делегирует фактическое аварийное закрытие в `KillSwitchService`;
* ведёт `AnomalyReport` через `AnomalyService`;
* всегда выбрасывает `TradeRuleViolationException`, чтобы остановить обычный sync.
