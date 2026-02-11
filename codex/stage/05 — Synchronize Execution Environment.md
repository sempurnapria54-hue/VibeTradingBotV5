# Stage 05 — Synchronize Execution Environment (Reconcile) v2

Источник требований: `docs/scenario/01 - synchronize_execution_environment.md`.

## 0) Цель

Сделать безопасный и воспроизводимый процесс синхронизации «execution environment» между:

* **Биржей OKX** (фактическое состояние: positions/orders/algoOrders)
* **Локальной БД** (наши сущности и их статусы)

Процесс должен:

1. снять snapshot биржи
2. разложить данные по инструментам (buckets)
3. выполнить **counts-only** синхронизацию (presence)
4. выявить аномалии по правилам B1–B8
5. при включённом флаге и необходимости выполнить **Cancel/Close flow** (C1–C7)
6. выполнить перенос атрибутов (п.10 сценария)
7. сформировать `AnomalyReport`

> В этом этапе нет торговой стратегии и нет исполнения «новых трейдов». Это ops/reconcile.

---

## 1) Границы этапа

### Включено

* Orchestrator job/service
* Snapshot provider (OKX)
* Bucketing по `instId`
* Counts-only sync (SYNC-1..SYNC-4)
* AnomalyEngine (B1–B8) + отчёты
* CancelExchangeFlow (C1–C7) **под флагом**
* Transfer (п.10)
* Persistence сущности, репозитории и DataService для reconcile

### Исключено (запрещено)

* торговая логика (кворум, стратегия, риск)
* новые REST endpoints (ручной запуск можно добавить отдельным этапом)
* изменение OKX proxy контрактов

---

## 2) Пакеты (ориентир)

### Reconcile (application/ops слой)

* `com.example.tradingbot.domain.job.*`
* `com.example.tradingbot.domain.service.reconcile.*`

### Persistence слой

* `com.example.tradingbot.persistence.model.*`
* `com.example.tradingbot.persistence.repository.*`
* `com.example.tradingbot.persistence.service.*`

---

## 3) Конфигурация и переключатели (обязательно)

### 3.1 Основной запуск

* `reconcile.enabled` (boolean)

    * `true`: job/service может выполняться
    * `false`: job не запускается

### 3.2 Переключатель опасных действий

* `reconcile.cancel-flow.enabled` (boolean, default `false`)

    * `false`: **никогда** не выполнять Cancel/Close запросы на OKX
    * `true`: CancelFlow разрешён, но запускается только если `AnomalyEngine` вернул `shouldCancelFlow=true`

> Даже при `enabled=true` CancelFlow не выполняется без явного решения `AnomalyEngine`.

---

## 4) Компоненты (точный список)

### 4.1 Orchestrator

* `SynchronizeExecutionEnvironmentJob`

    * расписание/триггер (в рамках stage достаточно сервиса; расписание можно включить)
    * single-instance lock (DB)

* `SynchronizeExecutionEnvironmentService`

    * `runDryRun()` — безопасный сбор + логирование
    * `run()` — полный pipeline stage

### 4.2 Snapshot

* `OkxExchangeSnapshotProvider`

    * `captureSnapshot()` → `ExchangeSnapshot`

Модели:

* `ExchangeSnapshot`

    * `exchangeName`
    * `capturedAtUtcMillis`
    * `List<ExternalPosition>`
    * `List<ExternalOrder>`
    * `List<ExternalAlgoOrder>`

* `ExternalPosition` / `ExternalOrder` / `ExternalAlgoOrder`

    * минимальные поля: `instId`, внешние id (ordId/algoId), client ids (clOrdId/algoClOrdId), статус/side/type если доступно

### 4.3 Bucketing

* `InstrumentBucketBuilder`

    * `buildBuckets(snapshot)` → `List<InstrumentBucket>`

* `InstrumentBucket`

    * `instrumentName` (instId)
    * `positions/orders/algoOrders` списки + counts

### 4.4 Counts-only sync

* `CountsOnlySyncEngine`

    * `syncInstrumentBucket(snapshot, bucket)`

* `ReconcilePlanBuilder`

    * вычисляет план действий counts-only для bucket

### 4.5 Anomaly

* `AnomalyEngine`

    * `evaluate(snapshot, bucket, dbState)` → `Optional<AnomalyDecision>`

* `AnomalyDecision`

    * `category` (B1..B8)
    * `severity` (INFO|WARN|CRITICAL)
    * `shouldHold` (bool)
    * `shouldCancelFlow` (bool)
    * `summary`
    * `detailsJson`

### 4.6 Cancel/Close flow

* `CancelExchangeFlow`

    * `execute(instId, snapshot, bucket, decision)`
    * реализует C1–C7, идемпотентно

### 4.7 Transfer (п.10)

* `ExchangeToDbTransferService`

    * переносит атрибуты внешних сущностей в БД (ordId/algoId/side/type и т.д. — минимально)

---

## 5) Persistence (минимальные поля)

> Поля статусов и их смысл — описываем позже в доменном слое. Здесь фиксируем только наличие полей.

### 5.1 Используемые базовые сущности

* `ExchangeEntity` (из Stage 03)
* `InstrumentEntity` (из Stage 03)

### 5.2 Новые сущности reconcile

#### PositionEntity (минимум)

* `id`
* `exchangeId` (FK)
* `instrumentId` (FK)
* `side` (nullable)
* `status` (ACTIVE|SYNC|HOLD|CLOSED|ANOMALY|UNKNOWN)
* audit

#### OrderEntity (минимум)

* `id`
* `exchangeId` (FK)
* `instrumentId` (FK)
* `clientOrderId` (clOrdId)
* `exchangeOrderId` (ordId, nullable)
* `status`
* `type` (nullable)
* `side` (nullable)
* audit

UNIQUE: `(exchange_id, instrument_id, client_order_id)`

#### AlgoOrderEntity (минимум)

* `id`
* `exchangeId` (FK)
* `instrumentId` (FK)
* `clientAlgoOrderId` (algoClOrdId)
* `exchangeAlgoOrderId` (algoId, nullable)
* `status`
* `algoType` (nullable)
* audit

UNIQUE: `(exchange_id, instrument_id, client_algo_order_id)`

#### AnomalyReportEntity

* `id`
* `exchangeId` (FK)
* `instrumentId` (nullable)
* `severity`
* `category`
* `summary`
* `detailsJson`
* `createdAt/createdBy`

---

## 6) Pipeline (порядок выполнения)

### 6.1 DRY-RUN (`runDryRun`)

1. capture snapshot
2. build buckets
3. log counts per bucket + totals

### 6.2 FULL (`run`)

1. single-instance lock
2. capture snapshot
3. build buckets
4. для каждого bucket:

    * `CountsOnlySyncEngine.syncInstrumentBucket(...)`
    * `decision = AnomalyEngine.evaluate(...)`
    * если decision есть:

        * сохранить `AnomalyReport`
        * если `shouldHold` → перевести инструмент в HOLD (через persistence.service)
        * если `cancelFlowEnabled && shouldCancelFlow` → `CancelExchangeFlow.execute(...)`
    * `ExchangeToDbTransferService.transfer(...)`

---

## 7) Идемпотентность и безопасность

* Counts-only sync использует UNIQUE ключи и не создаёт дублей.
* CancelFlow должен быть идемпотентным:

    * повторный запуск не ломает состояние и не плодит UNKNOWN
* При `reconcile.cancel-flow.enabled=false` гарантируется отсутствие cancel/close запросов.

---

## 8) Набор задач (как будем реализовывать)

* **Task 050A**: Orchestrator + Snapshot + Buckets (DRY-RUN)
* **Task 050B**: Persistence для reconcile (V2 миграции + Entity/Repo/DataService)
* **Task 050C**: Counts-only sync engine (SYNC-1..SYNC-4)
* **Task 050D**: AnomalyEngine (B1–B8) + CancelFlow (C1–C7) + Transfer (п.10) + переключатель

---

## 9) DoD (Stage 05)

1. DRY-RUN собирает snapshot и логирует buckets.
2. Persistence сущности reconcile добавлены, миграции применяются.
3. Counts-only sync приводит БД к «presence» состоянию биржи.
4. AnomalyEngine пишет отчёты.
5. CancelFlow выполняется только при включённом флаге и решении engine.
6. Transfer переносит атрибуты из snapshot в БД идемпотентно.
