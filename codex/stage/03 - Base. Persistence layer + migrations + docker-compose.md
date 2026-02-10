# Stage 03 — Persistence: Exchange + Instrument + Candle (+ migrations + docker-compose)

## Цель

Внедрить слой хранения данных для базовых сущностей:

* `Exchange`
* `Instrument`
* `Candle`

В этом этапе добавляем:

* `@Entity` модели
* Spring Data репозитории
* `DataService` слой с базовыми методами
* миграции для создания таблиц
* `docker-compose.yml` для развёртывания PostgreSQL

---

## Пакеты (фиксировано)

* `com.example.tradingbot.persistence.model.*`
* `com.example.tradingbot.persistence.repository.*`
* `com.example.tradingbot.persistence.service.*`

---

## Модели и поля (фиксировано)

### Exchange

* `Long id`
* `String name` (например `OKX`)
* `String baseUrl`
* `String status`
* `Auditable`: `createdAt`, `createdBy`, `modifiedAt`, `modifiedBy`

Ограничения:

* `UNIQUE(name)`

### Instrument

* `Long id`
* `Long exchangeId` (FK → Exchange)
* `String name` (например `ETH-USDT-SWAP`)
* `String positionMode` (`OPEN | NONE`)
* `String status` (`CREATED | ACTIVE | SYNC | HOLD | CLOSED`)
* `Auditable`

Ограничения:

* `UNIQUE(exchange_id, name)`

### Candle

* `Long id`
* `Long instrumentId` (FK → Instrument)
* `String timeframe` (строго значения `OkxTimeframes.*`, case-sensitive)
* `Long timestamp` (UTC, millis)
* `BigDecimal open`
* `BigDecimal high`
* `BigDecimal low`
* `BigDecimal close`
* `BigDecimal volume` (nullable)
* `String status` (nullable)
* `Auditable`

Ограничения:

* `UNIQUE(instrument_id, timeframe, timestamp)`
* Индекс для быстрых выборок: `(instrument_id, timeframe, timestamp)`

---

## Миграции

* Используем **Flyway**.
* SQL миграции храним в `src/main/resources/db/migration/`.
* Минимум:

  * `V1__init.sql` — создаёт таблицы, индексы, FK.

---

## docker-compose

* PostgreSQL
* внешний порт: `5440:5432`
* volume для данных
* env:

  * `POSTGRES_DB=tradingbot`
  * `POSTGRES_USER=postgres`
  * `POSTGRES_PASSWORD=password`

---

## Вне скоупа (запрещено)

* Не менять OKX proxy слой (controllers/services/clientservice/restclient/dto/mappers).
* Не добавлять бизнес-логику.
* Не добавлять новые REST endpoints.

---

## Definition of Done

1. `docker compose up -d` поднимает Postgres на `localhost:5440`.
2. Приложение стартует, Flyway применяет `V1__init.sql` без ошибок.
3. Таблицы `exchange`, `instrument`, `candle` созданы.
4. Есть `@Entity` + `Repository` + `DataService` для всех 3 сущностей.
5. Проект компилируется и собирается.
