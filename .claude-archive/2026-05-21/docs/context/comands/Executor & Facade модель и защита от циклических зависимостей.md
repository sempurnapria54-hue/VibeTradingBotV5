# Executor / Facade модель и защита от циклических зависимостей

## Цель

Эта дока фиксирует новую модель сервисного слоя:

* команды выполняются через отдельные `Executor` классы;
* фасадные `Service` классы объединяют use-case исполнители;
* `KillSwitchService` не должен вызывать refresh-исполнители;
* архитектура не должна образовывать циклические зависимости.

---

## Главная идея

Нужно разделить:

* **refresh / sync use-case**;
* **close / cancel / emergency use-case**;
* **validator**;
* **kill-switch orchestration**.

Один и тот же сервис не должен одновременно:

* синкать сущность;
* валидировать правила;
* участвовать в kill-switch через обратную зависимость.

---

## Базовая схема

### Верхний уровень

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

---

## Правило по `Executor`

### Refresh executors

Отвечают только за обычные sync-команды.

Примеры:

* `RefreshPositionExecutor`
* `RefreshPendingOrdersExecutor`
* `RefreshAlgoOrdersExecutor`
* `RefreshBalanceExecutor`
* `RefreshEntryOrderExecutor`

Они:

* читают биржу;
* читают БД;
* вызывают `TradeRuleValidator`;
* при отсутствии аномалии выполняют обычный sync.

Они не должны:

* делать kill-switch;
* вести `AnomalyReport`;
* аварийно закрывать сущности.

### Command / Close executors

Отвечают за адресные действия над сущностями.

Примеры:

* `ClosePositionExecutor`
* `CancelOrderExecutor`
* `CancelAlgoOrderExecutor`
* `CreateEntryOrderExecutor`
* `CreateMainProtectionExecutor`
* `AmendMainProtectionExecutor`

Они:

* выполняют конкретное действие;
* ходят на биржу;
* обновляют локальное состояние сущности в БД.

Они не должны:

* валидировать торговые правила;
* запускать kill-switch сами;
* работать с `AnomalyReport`.

---

## Роль фасадных сервисов

Фасадные сервисы нужны как удобная точка входа для application-слоя.

Примеры:

* `PositionService`
* `OrderService`
* `AlgoOrderService`
* `BalanceService`

### Что делает фасад

Фасад:

* объединяет use-case исполнители;
* предоставляет понятный публичный API;
* маршрутизирует вызов к нужному executor.

### Что важно

`KillSwitchService` не должен зависеть от фасада, если этот фасад внутри refresh-flow зависит от `TradeRuleValidator`.

Иначе легко получить цикл.

---

## Пример для позиции

### Правильная структура

* `RefreshPositionExecutor` — обычный `REFRESH_POSITIONS`
* `ClosePositionExecutor` — аварийное/явное закрытие позиции
* `PositionService` — фасад над этими use-case

### Важное правило

`KillSwitchService` должен вызывать **`ClosePositionExecutor` напрямую**, а не `PositionService`, если `PositionService`
участвует в refresh-flow через validator.

Иначе возможен цикл:

```text
RefreshPositionExecutor -> TradeRuleValidator -> KillSwitchService -> PositionService -> RefreshPositionExecutor
```

Это недопустимо.

---

## Та же модель для остальных сущностей

### Orders

* `RefreshPendingOrdersExecutor`
* `RefreshEntryOrderExecutor`
* `CreateEntryOrderExecutor`
* `CancelOrderExecutor`
* `OrderService` как фасад

### Algo orders

* `RefreshAlgoOrdersExecutor`
* `CreateMainProtectionExecutor`
* `CancelAlgoOrderExecutor`
* `AmendMainProtectionExecutor`
* `AlgoOrderService` как фасад

### Balance

* `RefreshBalanceExecutor`
* `BalanceService` как фасад

### Deal

* `DealEmergencyFinalizer` или аналогичный executor
* `DealService` как фасад агрегата

---

## Где живёт `TradeRuleValidator`

`TradeRuleValidator` должен использоваться только в refresh-flow.

То есть:

* `RefreshPositionExecutor` -> `TradeRuleValidator`
* `RefreshPendingOrdersExecutor` -> `TradeRuleValidator`
* `RefreshAlgoOrdersExecutor` -> `TradeRuleValidator`

Но не наоборот.

`TradeRuleValidator` не должен вызывать refresh executors.

---

## Где живёт `KillSwitchService`

`KillSwitchService` — это оркестратор аварийного сценария по инструменту.

Он:

* собирает начальное состояние;
* блокирует инструмент;
* вызывает аварийные executors;
* собирает итоговое состояние;
* формирует `KillSwitchResult`.

Он не должен:

* вызывать refresh executors;
* вызывать фасады, если это создаёт обратную зависимость;
* руками закрывать сущности внутри себя.

---

## Как избежать циклических зависимостей

### Нельзя

```text
RefreshPositionExecutor -> TradeRuleValidator -> KillSwitchService -> PositionService -> RefreshPositionExecutor
```

### Можно

```text
RefreshPositionExecutor -> TradeRuleValidator -> KillSwitchService -> ClosePositionExecutor
```

И аналогично для orders / algo orders.

---

## Правило по зависимости слоёв

### Разрешено

* `ServiceCommandExecutor` -> refresh executors
* refresh executors -> `TradeRuleValidator`
* `TradeRuleValidator` -> `AnomalyService`
* `TradeRuleValidator` -> `KillSwitchService`
* `KillSwitchService` -> command/close executors
* command/close executors -> client + persistence

### Не разрешено

* `KillSwitchService` -> refresh executors
* `TradeRuleValidator` -> refresh executors
* refresh executors -> `KillSwitchService`
* `AnomalyService` -> `KillSwitchService`
* FSM / handlers -> executors напрямую

---

## Короткий итог

Правильная модель такая:

* обычные команды выполняются через `Refresh*Executor`;
* аварийные действия выполняются через `Close* / Cancel*Executor`;
* фасадные `Service` классы удобны для application-слоя, но не должны создавать циклы;
* `TradeRuleValidator` участвует только в refresh-flow;
* `KillSwitchService` оркестрирует аварийный сценарий и вызывает аварийные executors напрямую.
