# Task 030 — Реализовать Stage 03: Persistence (Exchange/Instrument/Candle) + Flyway + docker-compose

## Контекст

Реализуй **Stage 03** из `codex/stage/03 - Base. Persistence layer + migrations + docker-compose.md`.

Фиксированная структура пакетов:

* `com.example.tradingbot.persistence.model.*`
* `com.example.tradingbot.persistence.repository.*`
* `com.example.tradingbot.persistence.service.*`

Соблюдать `codex/Code style.md`.

---

## Важные ограничения (строго)

* **Не трогать** OKX proxy слой: `rest.controller.okxproxy`, `client.okx`, `domain.service.okxproxy`, `mapping.okxproxy`, DTO и модели proxy.
* **Не добавлять** бизнес-логику.
* **Не добавлять** новые REST endpoints.

---

## Что нужно сделать

### 1) docker-compose.yml (PostgreSQL)

Создай/обнови `docker-compose.yml`:

* image: `postgres:16`
* env:

  * `POSTGRES_DB=tradingbot`
  * `POSTGRES_USER=postgres`
  * `POSTGRES_PASSWORD=password`
* ports: **`5440:5432`**
* volume: `pgdata:/var/lib/postgresql/data`
* объяви volume `pgdata`.

### 2) Flyway

1. Добавь Flyway зависимость в `pom.xml`.
2. Создай `src/main/resources/db/migration/V1__init.sql`.

DDL требования (обязательно):

#### Таблица `exchange`

* поля:

  * `id BIGSERIAL PRIMARY KEY`
  * `name VARCHAR(...) NOT NULL`
  * `base_url VARCHAR(...) NOT NULL`
  * `status VARCHAR(...) NOT NULL`
  * `created_at TIMESTAMPTZ NOT NULL`
  * `created_by VARCHAR(...) NULL`
  * `modified_at TIMESTAMPTZ NULL`
  * `modified_by VARCHAR(...) NULL`
* ограничения:

  * `UNIQUE(name)`

#### Таблица `instrument`

* поля:

  * `id BIGSERIAL PRIMARY KEY`
  * `exchange_id BIGINT NOT NULL` (FK)
  * `name VARCHAR(...) NOT NULL`
  * `position_mode VARCHAR(...) NOT NULL`
  * `status VARCHAR(...) NOT NULL`
  * audit поля как в `exchange`
* ограничения:

  * `UNIQUE(exchange_id, name)`
  * FK: `exchange_id → exchange(id)`

#### Таблица `candle`

* поля:

  * `id BIGSERIAL PRIMARY KEY`
  * `instrument_id BIGINT NOT NULL` (FK)
  * `timeframe VARCHAR(...) NOT NULL`
  * `timestamp BIGINT NOT NULL` (UTC millis)
  * `open NUMERIC(50, 30) NOT NULL`
  * `high NUMERIC(50, 30) NOT NULL`
  * `low NUMERIC(50, 30) NOT NULL`
  * `close NUMERIC(50, 30) NOT NULL`
  * `volume NUMERIC(50, 30) NULL`
  * `status VARCHAR(...) NULL`
  * audit поля
* ограничения:

  * `UNIQUE(instrument_id, timeframe, timestamp)`
  * Индекс: `(instrument_id, timeframe, timestamp)`
  * FK: `instrument_id → instrument(id)`

Примечание:

* Схема/имена таблиц и полей должны строго соответствовать указанным именам.
* magic numbers не использовать в коде — только константы.

### 3) application.properties

Убедись, что datasource смотрит на `localhost:5440`:

* `spring.datasource.url=jdbc:postgresql://localhost:5440/tradingbot`

И что Flyway включён (обычно включается автоматически при зависимости).

### 4) @Entity модели

Создай модели в `com.example.tradingbot.persistence.model`:

* `ExchangeEntity`
* `InstrumentEntity`
* `CandleEntity`

Требования:

* Lombok использовать (без `@Data`).
* Поля соответствуют DDL.
* Связи:

  * `InstrumentEntity` → `ExchangeEntity` (`@ManyToOne`)
  * `CandleEntity` → `InstrumentEntity` (`@ManyToOne`)
* Таблица/колонки задаются через `@Table/@Column`.

Audit:

* Если в проекте уже есть JPA Auditing — используй `@CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy`.
* Если нет — добавь минимум нужного, но не усложняй (это persistence этап).

### 5) Repositories

Создай репозитории в `com.example.tradingbot.persistence.repository`:

* `ExchangeRepository extends JpaRepository<ExchangeEntity, Long>`

  * `Optional<ExchangeEntity> findByName(String name)`
* `InstrumentRepository extends JpaRepository<InstrumentEntity, Long>`

  * `Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name)`
  * `List<InstrumentEntity> findAllByExchangeId(Long exchangeId)`
* `CandleRepository extends JpaRepository<CandleEntity, Long>`

  * `boolean existsByInstrumentIdAndTimeframe(Long instrumentId, String timeframe)`
  * `Optional<Long> findOldestTimestampByInstrumentIdAndTimeframe(...)` (через `@Query`)
  * `Optional<Long> findNewestTimestampByInstrumentIdAndTimeframe(...)` (через `@Query`)

### 6) DataService

Создай сервисы в `com.example.tradingbot.persistence.service`:

* `ExchangeDataService`
* `InstrumentDataService`
* `CandleDataService`

Требования:

* DataService инкапсулирует репозиторий.
* Методы минимум:

  * `save`, `saveAll`, `findById`, `exists...` (по необходимости)
  * `findByName` / `findByExchangeIdAndName`
* `@Transactional` на write методах.

---

## Definition of Done

1. `docker compose up -d` поднимает Postgres на `localhost:5440`.
2. При старте приложения Flyway применяет `V1__init.sql` без ошибок.
3. Таблицы `exchange`, `instrument`, `candle` созданы.
4. Entity/Repository/DataService компилируются.
5. Проект собирается.

---

## Не делать

* Не менять существующие OKX proxy endpoint’ы.
* Не добавлять контроллеры.
* Не добавлять бизнес-логику.
