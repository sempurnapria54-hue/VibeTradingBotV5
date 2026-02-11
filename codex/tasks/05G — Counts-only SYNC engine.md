# Task 05G — Stage 05 (v3): Counts-only sync engine (SYNC-1..SYNC-4)

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Реализовать SYNC (counts-only) для presence:

* positions
* orders
* algoOrders

Только наличие сущностей (presence). Атрибуты — в Task 05E.

---

## Что нужно сделать

### 1) Компоненты

Package: `com.example.tradingbot.domain.service.reconcile`

* `CountsOnlySyncEngine`

    * `void syncPresence(InstrumentBucket bucket, ExchangeInstrumentSnapshot exchangeState)`

* `ReconcilePlanBuilder`

    * `ReconcilePlan buildPlan(InstrumentBucket bucket, ExchangeInstrumentSnapshot exchangeState)`

Package: `...reconcile.model`

* `ReconcilePlan`

    * `List<CreateUnknownAction> createUnknown`
    * `List<MarkClosedAction> markClosed`
    * `List<MarkAnomalyAction> markAnomaly`

### 2) Источники идентификации

* На бирже “наши” объекты определяются по `clOrdId/algoClOrdId`, которые равны нашим internalId.
* Если у объекта на бирже нет clientId → считаем объект внешним/unknown.

### 3) Правила SYNC

Реализуй SYNC-1..SYNC-4 по сценарию.
Общий принцип:

* DB=0, EX>0 → создать UNKNOWN записи в БД (status=UNKNOWN или SYNC, как ты зафиксировал в домене)
* DB>0, EX=0 → закрыть/пометить отсутствующие
* DB!=EX → привести presence к консистентности, не создавая дублей

### 4) Идемпотентность

* UNIQUE ключи в таблицах обязательны
* повторный запуск не создаёт новые UNKNOWN, если уже есть

### 5) Слой доступа к данным

* Из domain/service слоя нельзя использовать Repository напрямую.
* Используй только `persistence.service.*`.

---

## Ограничения

* Не выполнять CancelFlow.
* Не переносить атрибуты.

---

## DoD

* Для каждого bucket после syncPresence counts в БД соответствуют exchangeState.
* Повторный запуск не создаёт дублей.
