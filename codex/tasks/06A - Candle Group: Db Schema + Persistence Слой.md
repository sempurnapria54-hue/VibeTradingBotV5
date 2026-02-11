# Task 06A — CandleGroup: DB schema + persistence слой

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Добавить в БД и в persistence слой сущности:

* `CandleGroupEntity`
* `CandleEntity` (если ещё нет) с FK на candle_group

Плюс репозитории и DataService.

---

## 1) Миграции Flyway

### 1.1 create table candle_group

Файл: следующий свободный `V__create_candle_group.sql`.

Создать `candle_group`:

* `id BIGSERIAL PK`
* `instrument_id BIGINT NOT NULL` FK → `instrument(id)`
* `timeframe VARCHAR(16) NOT NULL`
* `status VARCHAR(32) NOT NULL`
* `coverage_start_ts BIGINT NOT NULL`
* `backfill_cursor_ts BIGINT NULL`
* `last_tail_sync_ts BIGINT NULL`
* `attempt_count INT NOT NULL DEFAULT 0`
* `last_success_at TIMESTAMPTZ NULL`
* `last_error_at TIMESTAMPTZ NULL`
* `last_error_code VARCHAR(32) NULL`
* `last_error_message VARCHAR(1024) NULL`
* `lease_owner VARCHAR(128) NULL`
* `lease_until BIGINT NULL`
* audit поля (created_at/updated_at/created_by/updated_by — если проект уже использует)

UNIQUE:

* `(instrument_id, timeframe)`

Indexes:

* `(status)`
* `(lease_until)`
* `(instrument_id, timeframe)`

### 1.2 candles

Требование сценария: свеча хранит только `candle_group_id` (без timeframe).

Варианты (выбери один и зафиксируй в миграции):
A) Если таблицы `candles` ещё нет — создать новую:

* `id BIGSERIAL PK`
* `candle_group_id BIGINT NOT NULL` FK → `candle_group(id)` ON DELETE CASCADE
* `timestamp BIGINT NOT NULL`
* OHLC + volume
* audit
* UNIQUE `(candle_group_id, timestamp)`
* INDEX `(candle_group_id, timestamp)`

B) Если `candles` уже существует в проекте с другим FK — создать новую таблицу `candles_v2` и использовать её в этом этапе.

> Не ломаем существующий backtest/исторические таблицы без отдельного решения. В сомнении — выбирай вариант B.

---

## 2) Persistence entities

Package: `com.example.tradingbot.persistence.model`

### 2.1 CandleGroupEntity

* связи: `@ManyToOne InstrumentEntity instrument`
* поля как в DDL
* enum статуса: `CandleGroupStatus`

### 2.2 CandleEntity

* `@ManyToOne CandleGroupEntity candleGroup`
* поля: timestamp, OHLC, volumes

Требования:

* Lombok без `@Data`

---

## 3) Repositories

Package: `com.example.tradingbot.persistence.repository`

* `CandleGroupRepository`

    * `Optional<CandleGroupEntity> findByInstrumentIdAndTimeframe(Long instrumentId, String timeframe)`
    * `List<CandleGroupEntity> findTopNByStatusInOrderByIdAsc(...)` (или paging)

* `CandleRepository`

    * `long countByCandleGroupIdAndTimestampBetween(Long groupId, long from, long to)`
    * `Optional<Long> findMinTimestampByCandleGroupId(Long groupId)`
    * `Optional<Long> findMaxTimestampByCandleGroupId(Long groupId)`
    * `List<Long> findTimestampsByCandleGroupIdAndTimestampBetweenOrderByTimestampAsc(...)`

---

## 4) DataServices

Package: `com.example.tradingbot.persistence.service`

* `CandleGroupDataService`

    * `List<CandleGroupEntity> findEligibleForRun(nowMillis, statuses, maxGroups)`
    * `boolean tryAcquireLease(groupId, owner, nowMillis, leaseUntilMillis)`
    * `void extendLease(groupId, owner, newLeaseUntilMillis)`
    * `void releaseLease(groupId, owner)`
    * `void markSuccess(groupId, now)`
    * `void markError(groupId, code, message, now, attempts)`
    * `void updateStatus(groupId, status)`
    * `void updateBackfillCursor(groupId, cursorTs)`
    * `void updateLastTailSync(groupId, nowClosedTs)`

* `CandleDataService`

    * `void upsertBatch(groupId, List<CandleEntity> candles)` (реализация через saveAll + unique)
    * `long countBetween(groupId, from, to)`
    * `List<Long> loadTimestamps(groupId, from, to)`

---

## DoD

* Миграции применяются.
* Сущности/репозитории/data services компилируются.
* Есть методы для count/min/max/timestamps и lease.
