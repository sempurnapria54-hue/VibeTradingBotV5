# Task 050A (v2) — Stage 05: Orchestrator + Snapshot + Buckets (DRY-RUN)

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Сделать безопасный DRY-RUN режим reconcile:

* снять snapshot OKX (positions/orders/algoOrders)
* сгруппировать по `instId` в buckets
* залогировать counts и базовую статистику

**Запрещено:** менять БД, выполнять cancel/close.

---

## Что нужно сделать

### 1) Config

Добавь `@ConfigurationProperties(prefix = "reconcile")`:

* `boolean enabled` (default true/false по твоему решению)

DRY-RUN запускаем вручную из теста/CommandLineRunner (без REST).

### 2) Компоненты

Создай в `com.example.tradingbot.domain.service.reconcile`:

* `OkxExchangeSnapshotProvider`

    * `ExchangeSnapshot captureSnapshot()`

* `InstrumentBucketBuilder`

    * `List<InstrumentBucket> buildBuckets(ExchangeSnapshot snapshot)`

* `SynchronizeExecutionEnvironmentService`

    * `void runDryRun()`
    * внутри: captureSnapshot → buildBuckets → log

Модели в `com.example.tradingbot.domain.service.reconcile.model`:

* `ExchangeSnapshot`
* `ExternalPosition`
* `ExternalOrder`
* `ExternalAlgoOrder`
* `InstrumentBucket`

### 3) Источник данных (OKX)

Snapshot получаем через уже реализованные OKX client services (не контроллеры):

* positions
* orders (open/pending)
* algoOrders

Если в проекте есть несколько эндпоинтов (history/pending/etc), выбирай те, которые соответствуют сценарию «execution environment».

### 4) Минимальные поля внешних DTO

Заполни только то, что нужно для bucket/counts:

* `instId`
* id (ordId/algoId) если есть
* client id (clOrdId/algoClOrdId) если есть

### 5) Логирование

Логи без секретов.

Формат логов:

* начало dry-run: exchangeName, capturedAt
* totals: P/O/A
* по каждому bucket:

    * `instId` + `Positions.count`, `Orders.count`, `AlgoOrders.count`

---

## Ограничения

* Не писать в БД.
* Не выполнять CancelFlow.
* Не добавлять REST endpoints.

---

## DoD

* `runDryRun()` выполняется и логирует buckets.
* При ошибке OKX логирует `code/msg` и продолжает/завершает предсказуемо.
