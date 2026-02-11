# Task 050B (v2) — Stage 05: Persistence модели для reconcile (V2 migration)

Опирайся на stage: `codex/stage/codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Добавить persistence слой для reconcile:

* `PositionEntity`
* `OrderEntity`
* `AlgoOrderEntity`
* `AnomalyReportEntity`

Плюс репозитории и DataService.

---

## Что нужно сделать

### 1) Flyway миграция

Добавь `src/main/resources/db/migration/V2__reconcile_entities.sql`:

Создай таблицы:

#### `position`

* `id BIGSERIAL PK`
* `exchange_id BIGINT NOT NULL` FK → `exchange(id)`
* `instrument_id BIGINT NOT NULL` FK → `instrument(id)`
* `side VARCHAR(...) NULL`
* `status VARCHAR(...) NOT NULL`
* audit поля (как в Stage 03)

Индексы:

* `(exchange_id, instrument_id)`

#### `order`

* `id BIGSERIAL PK`
* `exchange_id BIGINT NOT NULL` FK
* `instrument_id BIGINT NOT NULL` FK
* `client_order_id VARCHAR(...) NOT NULL`
* `exchange_order_id VARCHAR(...) NULL`
* `status VARCHAR(...) NOT NULL`
* `type VARCHAR(...) NULL`
* `side VARCHAR(...) NULL`
* audit

UNIQUE:

* `(exchange_id, instrument_id, client_order_id)`

Индексы:

* `(exchange_id, instrument_id)`

#### `algo_order`

* `id BIGSERIAL PK`
* `exchange_id BIGINT NOT NULL` FK
* `instrument_id BIGINT NOT NULL` FK
* `client_algo_order_id VARCHAR(...) NOT NULL`
* `exchange_algo_order_id VARCHAR(...) NULL`
* `status VARCHAR(...) NOT NULL`
* `algo_type VARCHAR(...) NULL`
* audit

UNIQUE:

* `(exchange_id, instrument_id, client_algo_order_id)`

Индексы:

* `(exchange_id, instrument_id)`

#### `anomaly_report`

* `id BIGSERIAL PK`
* `exchange_id BIGINT NOT NULL` FK
* `instrument_id BIGINT NULL` FK
* `severity VARCHAR(...) NOT NULL`
* `category VARCHAR(...) NOT NULL`
* `summary VARCHAR(512) NOT NULL`
* `details_json TEXT NOT NULL`
* `created_at TIMESTAMPTZ NOT NULL`
* `created_by VARCHAR(...) NULL`

Индексы:

* `(exchange_id, instrument_id)`
* `(created_at)`

---

### 2) @Entity

Создай в `com.example.tradingbot.persistence.model`:

* `PositionEntity`
* `OrderEntity`
* `AlgoOrderEntity`
* `AnomalyReportEntity`

Требования:

* Lombok (без `@Data`).
* `@ManyToOne` связи:

    * Position → Exchange, Instrument
    * Order → Exchange, Instrument
    * AlgoOrder → Exchange, Instrument
    * AnomalyReport → Exchange, Instrument(nullable)

### 3) Repositories

В `com.example.tradingbot.persistence.repository`:

* `PositionRepository`

    * `List<PositionEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId)`
* `OrderRepository`

    * `List<OrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId)`
    * `Optional<OrderEntity> findByExchangeIdAndInstrumentIdAndClientOrderId(...)`
* `AlgoOrderRepository`

    * `List<AlgoOrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId)`
    * `Optional<AlgoOrderEntity> findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(...)`
* `AnomalyReportRepository`

### 4) DataServices

В `com.example.tradingbot.persistence.service`:

* `PositionDataService`
* `OrderDataService`
* `AlgoOrderDataService`
* `AnomalyReportDataService`

Методы:

* save/saveAll
* findAllByExchangeIdAndInstrumentId
* findBy...clientId

---

## Ограничения

* Не менять OKX proxy слой.
* Не добавлять бизнес-логику.

---

## DoD

* V2 миграция применяется.
* Сущности/репозитории/data services компилируются.
