# Task 05F — Stage 05 (v3): Report foundation + BEFORE snapshots + Buckets (SAFE)

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Реализовать «безопасный» запуск reconcile, который:

* читает managed instruments из БД
* снимает `database_before`
* снимает `exchange_before`
* создаёт и сохраняет `SynchronizeExecutionEnvironmentReport` (первичная запись)
* собирает buckets и логирует counts

**Запрещено:** выполнять cancel/close на OKX, менять presence в БД (SYNC), обновлять атрибуты сущностей.

> Разрешено: только запись отчёта (одна строка + можно anomalies=[], но без обновлений P/O/A и instrument/exchange статусов).

---

## Что нужно сделать

### 1) Config

* `reconcile.enabled`

### 2) Модели снапшотов (domain/service layer)

Package: `com.example.tradingbot.domain.service.reconcile.model`

* `DatabaseSnapshot`
* `DatabaseInstrumentSnapshot`
* `ExchangeSnapshot`
* `ExchangeInstrumentSnapshot`

Минимум в каждом instrument snapshot:

* `instId`
* counts: `positionsCount`, `ordersCount`, `algoOrdersCount`
* ids (если доступны):

    * orders: `ordId`, `clOrdId`
    * algo: `algoId`, `algoClOrdId`
* DB snapshot дополнительно: `instrumentMode`, `instrumentStatus`, `positionMode`

### 3) Снятие database_before

Компонент: `DatabaseSnapshotBuilder`

* берёт managed instruments из БД
* по каждому instId читает active P/O/A
* формирует `DatabaseSnapshot`

### 4) Снятие exchange_before

Компонент: `OkxExchangeSnapshotProvider`

* получает positions + orders pending + algoOrders pending
* группирует по instId
* формирует `ExchangeSnapshot`

### 5) Report persistence (минимум)

Компонент: `SynchronizeExecutionEnvironmentReportService`

* `createStartedReport(trigger, databaseBefore, exchangeBefore)`
* сохраняет в БД:

    * `startedAt`
    * `trigger`
    * `databaseBeforeJson`
    * `exchangeBeforeJson`
    * `hasAnomalies=false`, `maxSeverity=NONE`

### 6) Buckets

Компонент: `InstrumentBucketBuilder`

* `buildBuckets(dbBefore, exBefore)`

### 7) Orchestrator SAFE run

`SynchronizeExecutionEnvironmentService.runSafe()`
Пайплайн:

1. database_before
2. exchange_before
3. create report
4. build buckets
5. log totals + per bucket counts

---

## Ограничения

* Никаких updates по Exchange/Instrument/status/mode.
* Никаких SYNC (presence).
* Никакого Transfer.

---

## DoD

* После запуска в БД появляется 1 запись `SynchronizeExecutionEnvironmentReport` с before снапшотами.
* Логи содержат totals и per bucket counts.
* При ошибке получения снапшота биржи бросается доменная ошибка, отчёт не создаётся (или создаётся с пометкой FAILED — выбери и зафиксируй в коде).
