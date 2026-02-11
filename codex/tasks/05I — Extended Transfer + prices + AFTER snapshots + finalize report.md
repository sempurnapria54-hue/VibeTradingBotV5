# Task 05I — Stage 05 (v3): Transfer (атрибуты + цены) + AFTER snapshots + finalize report

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Важно

Всё, что требуется поменять (даже если касается предыдущих тасков), фиксируем здесь.

## Цель

1. Реализовать A4 Transfer: обновление атрибутов сущностей и инструментов по exchange фактам
2. Добавить цены инструмента (last/mark/index)
3. Добавить 2 финальных снапшота: `exchange_after`, `database_after`
4. Финализировать `SynchronizeExecutionEnvironmentReport`

---

## 0) Концепция 2 exchange snapshots

* В отчёте храним **только**:

    * `exchange_before` — в самом начале
    * `exchange_after` — в самом конце

Если в A2 был cancel/close, то для корректного A3/A4 внутри bucket требуется **внутренний refresh** exchange facts по инструменту.

* Этот refresh НЕ записывается в report как отдельный снапшот.

---

## 1) Snapshot: tickers (цены)

### 1.1 Модель

Добавь `ExternalTicker` (минимум):

* `instId`
* `last`
* `markPx` (если доступно)
* `idxPx` (если доступно)
* `ts` (если есть)

### 1.2 ExchangeSnapshot

Добавь в `ExchangeSnapshot`:

* `Map<String, ExternalTicker> tickersByInstId`

### 1.3 Capture

В `OkxExchangeSnapshotProvider.captureExchangeSnapshot()`:

* собери positions/orders/algoOrders
* собери tickers для каждого managed instId

---

## 2) Миграции БД (используй следующий свободный номер)

Файл: `src/main/resources/db/migration/V__reconcile_extended_transfer_fields.sql`

### 2.1 instrument

Добавить поля:

* `last_price VARCHAR(64) NULL`
* `mark_price VARCHAR(64) NULL`
* `index_price VARCHAR(64) NULL`
* `price_updated_at TIMESTAMPTZ NULL`

### 2.2 position

Добавить поля:

* `pos VARCHAR(64) NULL`
* `avg_px VARCHAR(64) NULL`
* `mark_px VARCHAR(64) NULL`
* `liq_px VARCHAR(64) NULL`
* `lever VARCHAR(32) NULL`
* `mgn_mode VARCHAR(16) NULL`
* `upl VARCHAR(64) NULL`
* `u_time BIGINT NULL`

### 2.3 order

Добавить поля:

* `state VARCHAR(32) NULL`
* `ord_type VARCHAR(32) NULL`
* `px VARCHAR(64) NULL`
* `sz VARCHAR(64) NULL`
* `fill_sz VARCHAR(64) NULL`
* `avg_px VARCHAR(64) NULL`
* `fee VARCHAR(64) NULL`
* `c_time BIGINT NULL`
* `u_time BIGINT NULL`

### 2.4 algo_order

Добавить поля:

* `state VARCHAR(32) NULL`
* `algo_type VARCHAR(32) NULL` (если ещё нет)
* `sz VARCHAR(64) NULL`
* `trigger_px VARCHAR(64) NULL`
* `ord_px VARCHAR(64) NULL`
* `tp_trigger_px VARCHAR(64) NULL`
* `tp_ord_px VARCHAR(64) NULL`
* `sl_trigger_px VARCHAR(64) NULL`
* `sl_ord_px VARCHAR(64) NULL`
* `callback_ratio VARCHAR(64) NULL`
* `callback_spread VARCHAR(64) NULL`
* `c_time BIGINT NULL`
* `u_time BIGINT NULL`

---

## 3) Обновление @Entity

Обнови сущности:

* `InstrumentEntity`
* `PositionEntity`
* `OrderEntity`
* `AlgoOrderEntity`

Требования:

* Lombok без `@Data`
* patch-обновление: меняем поля только если реально изменились

---

## 4) Transfer services

### 4.1 Базовый transfer (ссылки ordId/algoId)

Если у тебя уже есть `ExchangeToDbTransferService` из предыдущих тасков — оставь его, но перенеси расширение в отдельный сервис.

### 4.2 Extended transfer

Создай `ExchangeToDbExtendedTransferService`:

* `void transfer(Long exchangeId, InstrumentBucket bucket, ExchangeInstrumentSnapshot currentExchangeState, ExchangeSnapshot exchangeBeforeOrCurrent)`

Что делает:

#### 4.2.1 Instrument prices

Если в `tickersByInstId` есть запись:

* обновить `lastPrice/markPrice/indexPrice/priceUpdatedAt`

#### 4.2.2 Position fields

Если на бирже позиция есть:

* найти активную позицию в БД по (exchangeId,instrumentId)
* обновить `pos/avgPx/markPx/liqPx/lever/mgnMode/upl/uTime`

#### 4.2.3 Orders fields

Для каждого order из exchange facts:

* матчинг по `clOrdId` → `Order.internalId`
* если clientId пустой → fallback по `ordId` только если в БД найден ровно один матч
* обновить поля `exchangeOrderId/state/ordType/px/sz/fillSz/avgPx/fee/cTime/uTime`

#### 4.2.4 AlgoOrders fields

Аналогично Orders:

* матчинг по `algoClOrdId` → `AlgoOrder.internalId`
* fallback по `algoId` только при уникальном матче
* обновить поля `state/algoType/.../cTime/uTime`

---

## 5) AFTER snapshots + finalize report

В `SynchronizeExecutionEnvironmentService.run()` после всех bucket:

1. `exchange_after = captureExchangeSnapshot()`
2. `database_after = captureDatabaseSnapshot(...)`
3. `reportService.finalizeReport(reportId, database_after, exchange_after, ...)`
4. `Exchange.status = ACTIVE`

---

## 6) Рефакторинг по report (если ещё не сделано)

Если где-то остались упоминания `AnomalyReport`:

* удалить/заменить на `reportService.appendAnomaly(...)`

---

## Ограничения

* Не добавлять новые REST endpoints.
* Не выполнять стратегию.

---

## DoD

* После reconcile у отчёта заполнены 4 снапшота.
* Цены инструмента и атрибуты сущностей обновляются.
* Повторный запуск не создаёт дублей и не меняет строки без реальных изменений.
