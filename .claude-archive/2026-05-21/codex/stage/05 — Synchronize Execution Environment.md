# Stage 05 — Synchronize Execution Environment (Reconcile) v3

> Цель: безопасно и воспроизводимо синхронизировать «execution environment» между OKX и БД, с обязательным репортом по каждому запуску.

## 0) Ключевая идея v3

1. **Каждый запуск** сохраняет единый отчёт `SynchronizeExecutionEnvironmentReport`.
2. Отчёт содержит **4 снапшота**:

  * `database_before`
  * `exchange_before`
  * `database_after`
  * `exchange_after`
3. `AnomalyReport` больше не используется (удаляем сущность/таблицу).
4. `Cancel Exchange Flow` остаётся частью job и выполняется **под флагом**.

---

## 1) Границы этапа

### Включено

* Orchestrator job/service
* Снятие снапшотов: БД и биржа (до/после)
* Buckets по `instId` (инструменты)
* Anomaly checks (B1–B8)
* Cancel Exchange Flow (C1–C7) под флагом
* SYNC (counts-only) presence: positions/orders/algoOrders
* Transfer (A4): обновление атрибутов сущностей и инструментов (цены и т.п.)
* Финализация статусов Exchange/Instrument
* Cleanup job отчётов (retention)

### Исключено

* торговая логика/стратегия/кворум/риск
* создание новых торговых действий
* новые REST endpoints (можно отдельным этапом)

---

## 2) Пакеты

### Reconcile (application/ops)

* `com.example.tradingbot.domain.job.*`
* `com.example.tradingbot.domain.service.reconcile.*`
* `com.example.tradingbot.domain.service.reconcile.model.*`

### Persistence

* `com.example.tradingbot.persistence.model.*`
* `com.example.tradingbot.persistence.repository.*`
* `com.example.tradingbot.persistence.service.*`

---

## 3) Конфигурация и переключатели

### 3.1 Запуск job

* `reconcile.enabled` (boolean)

### 3.2 Переключатель опасных действий

* `reconcile.cancel-flow.enabled` (boolean, default `false`)

  * `false`: cancel/close запросы на OKX запрещены
  * `true`: cancel-flow разрешён, но выполняется только при решении AnomalyEngine

### 3.3 Retention отчётов

* `synchronize-execution-environment.reports.retention-days` (int, default 14)

---

## 4) Основные компоненты (точный список)

### 4.1 Orchestrator

* `SynchronizeExecutionEnvironmentJob`

  * single-instance lock (DB)
  * запускает `SynchronizeExecutionEnvironmentService.run()`

* `SynchronizeExecutionEnvironmentService`

  * `run()` — полный pipeline

### 4.2 Snapshot providers

* `OkxExchangeSnapshotProvider`

  * `ExchangeSnapshot captureExchangeSnapshot()`
  * (опционально) `ExchangeInstrumentSnapshot refreshInstrumentSnapshot(String instId)` — для актуализации после cancel-flow

* `DatabaseSnapshotBuilder`

  * `DatabaseSnapshot captureDatabaseSnapshot(Long exchangeId, List<InstrumentEntity> managed)`

### 4.3 Buckets

* `InstrumentBucketBuilder`

  * `List<InstrumentBucket> buildBuckets(DatabaseSnapshot dbBefore, ExchangeSnapshot exBefore)`

* `InstrumentBucket`

  * `String instId`
  * `DbInstrumentState dbState` (instrument + active P/O/A)
  * `ExchangeInstrumentState exchangeState` (P/O/A из exchange_before)

### 4.4 Report

* `SynchronizeExecutionEnvironmentReportService`

  * `ReportEntity createStartedReport(trigger, dbBefore, exBefore)`
  * `void appendAnomaly(reportId, anomalyItem)`
  * `void finalizeReport(reportId, dbAfter, exAfter, maxSeverity, hasAnomalies)`

### 4.5 Engines/flows

* `AnomalyEngine`

  * `Optional<AnomalyDecision> evaluate(InstrumentBucket bucket)`

* `CancelExchangeFlow`

  * `CancelFlowResult execute(InstrumentBucket bucket, AnomalyDecision decision)`
  * при необходимости использует refresh snapshot по инструменту

* `CountsOnlySyncEngine`

  * `void syncPresence(InstrumentBucket bucket, ExchangeInstrumentState currentExchangeState)`

* `ExchangeToDbTransferService`

  * `void transferAttributes(InstrumentBucket bucket, ExchangeInstrumentState currentExchangeState)`
  * `void transferInstrumentPrices(String instId, ExchangeSnapshot exchangeBeforeOrCurrent)`

---

## 5) Persistence (минимальные поля)

### 5.1 Уже существующие

* `ExchangeEntity`
* `InstrumentEntity`

### 5.2 Reconcile сущности

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
* `internalId` (наш id, он же clOrdId)
* `exchangeOrderId` (ordId, nullable)
* `status`
* audit

UNIQUE: `(exchange_id, instrument_id, internal_id)`

#### AlgoOrderEntity (минимум)

* `id`
* `exchangeId` (FK)
* `instrumentId` (FK)
* `internalId` (наш id, он же algoClOrdId)
* `exchangeAlgoOrderId` (algoId, nullable)
* `status`
* audit

UNIQUE: `(exchange_id, instrument_id, internal_id)`

### 5.3 Report сущности (новые)

#### SynchronizeExecutionEnvironmentReportEntity

* `id`
* `exchangeId` (FK)
* `startedAt`
* `finishedAt` (nullable)
* `trigger` (SCHEDULED|MANUAL)
* `hasAnomalies` (bool)
* `maxSeverity` (NONE|NON_CRITICAL|CRITICAL)
* `databaseBeforeJson` (jsonb/text)
* `exchangeBeforeJson` (jsonb/text)
* `databaseAfterJson` (jsonb/text, nullable)
* `exchangeAfterJson` (jsonb/text, nullable)

#### SynchronizeExecutionEnvironmentReportAnomalyEntity

* `id`
* `reportId` (FK)
* `instId` (nullable для глобальных)
* `type` (например B1..B8, либо именованный тип)
* `severity` (NON_CRITICAL|CRITICAL)
* `summary`
* `detailsJson`
* `createdAt`

---

## 6) Формат снапшотов (рекомендованный)

### 6.1 exchange_* (биржа)

По каждому `instId`:

* `Positions.count`, `Orders.count`, `AlgoOrders.count`
* списки ids (если доступны):

  * orders: `ordId`, `clOrdId`
  * algoOrders: `algoId`, `algoClOrdId`

### 6.2 database_* (БД)

По каждому `instId`:

* `Instrument.mode/status/positionMode`
* `activePositions.count`
* `activeOrders.count` + ids: `order.internalId`, `order.exchangeOrderId`
* `activeAlgoOrders.count` + ids: `algo.internalId`, `algo.exchangeAlgoOrderId`

> Снапшоты — для аудита/дебага. SYNC остаётся counts-only.

---

## 7) Pipeline (порядок выполнения)

### Шаги до bucket

1. single-instance lock
2. global HOLD runtime (останавливаем торговые механизмы на время reconcile)
3. прочитать managed instruments из БД
4. `database_before` = capture DB snapshot
5. `exchange_before` = capture Exchange snapshot
6. создать `SynchronizeExecutionEnvironmentReport` (startedAt + trigger + before снапшоты)
7. `Exchange.status = SYNC` (DB)
8. build buckets (dbBefore + exchangeBefore)

### По каждому bucket

A1) `Instrument.status = SYNC` (DB)
A2) anomaly checks B1..B8

* если аномалия:

  * append anomaly item в report
  * если CRITICAL: `Instrument.mode=HOLD`, `Instrument.status=HOLD`, (опционально cancel-flow), завершить bucket
  * если NON_CRITICAL: выполнить cancel-flow (если нужно), вернуть `Instrument.mode=OPEN`, продолжить

A2.1) если выполняли cancel/close на бирже:

* refresh exchange facts по этому `instId` для дальнейших шагов (внутренний refresh, не заменяет `exchange_before` в отчёте)

A3) SYNC (counts-only): привести presence в БД к текущим exchange фактам
A4) Transfer: обновить атрибуты сущностей и инструмента по текущим exchange фактам (цены, размеры, статусы)
A5) финализация bucket:

* `Instrument.positionMode = OPEN|NONE` по фактам
* `Instrument.status = ACTIVE`

### Шаги после bucket

9. `exchange_after` = capture Exchange snapshot (после всех bucket и cancel/close)
10. `database_after` = capture DB snapshot
11. finalize report (finishedAt + after снапшоты + maxSeverity/hasAnomalies)
12. `Exchange.status = ACTIVE`
13. снять global HOLD
14. release lock

---

## 8) Набор задач Stage 05

* **05A(v3)**: report foundation (db_before/ex_before + первичная запись) + buckets + логирование (без изменений presence)
* **05B(v3)**: миграции и persistence для report (замена AnomalyReport)
* **05C(v3)**: counts-only sync engine (SYNC)
* **05D(v3)**: anomaly engine + cancel flow + запись аномалий в report
* **05E(v3)**: transfer (цены + расширенные поля) + after snapshots + finalize report
* **05F(v3)**: cleanup job отчётов

---

## 9) DoD Stage 05

* Каждый запуск сохраняет отчёт с before/after снапшотами.
* При cancel-flow последующие шаги используют актуальные exchange факты (refresh по инструменту).
* SYNC остаётся counts-only.
* После reconcile:

  * Exchange.status=ACTIVE
  * каждый Instrument.status=ACTIVE или HOLD (при CRITICAL)
* Cleanup job удаляет только отчёты без аномалий по TTL.
