# Task 05E — Stage 05: Report persistence (replace AnomalyReport)

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

* Полностью убрать `AnomalyReport` (persistence/model/repository/service + таблица)
* Добавить persistence для:

  * `SynchronizeExecutionEnvironmentReport`
  * `SynchronizeExecutionEnvironmentReportAnomaly`

---

## Что нужно сделать

### 1) Flyway миграция (используй следующий свободный номер)

Файл: `src/main/resources/db/migration/V__sync_execution_environment_report.sql`

1. Удалить старую таблицу (если существует):

* `DROP TABLE IF EXISTS anomaly_report;`

2. Создать таблицу `sync_execution_environment_report`:

* `id BIGSERIAL PK`
* `exchange_id BIGINT NOT NULL` FK → `exchange(id)`
* `started_at TIMESTAMPTZ NOT NULL`
* `finished_at TIMESTAMPTZ NULL`
* `trigger VARCHAR(16) NOT NULL`
* `has_anomalies BOOLEAN NOT NULL DEFAULT FALSE`
* `max_severity VARCHAR(16) NOT NULL DEFAULT 'NONE'`
* `database_before_json JSONB NOT NULL`
* `exchange_before_json JSONB NOT NULL`
* `database_after_json JSONB NULL`
* `exchange_after_json JSONB NULL`

Индексы:

* `(exchange_id, started_at DESC)`
* `(finished_at)`
* `(has_anomalies, finished_at)`

3. Создать таблицу `sync_execution_environment_report_anomaly`:

* `id BIGSERIAL PK`
* `report_id BIGINT NOT NULL` FK → `sync_execution_environment_report(id)` ON DELETE CASCADE
* `inst_id VARCHAR(64) NULL`
* `type VARCHAR(64) NOT NULL`
* `severity VARCHAR(16) NOT NULL`
* `summary VARCHAR(512) NOT NULL`
* `details_json JSONB NOT NULL`
* `created_at TIMESTAMPTZ NOT NULL`

Индексы:

* `(report_id)`
* `(inst_id)`
* `(severity)`

---

### 2) @Entity

Package: `com.example.tradingbot.persistence.model`

* `SynchronizeExecutionEnvironmentReportEntity`
* `SynchronizeExecutionEnvironmentReportAnomalyEntity`

Требования:

* Lombok (без `@Data`)
* jsonb поле хранить как `String` или `JsonNode` (выбери один подход и будь консистентен)

Связь:

* `ReportEntity` 1..N `AnomalyEntity` (lazy)

---

### 3) Repository

Package: `com.example.tradingbot.persistence.repository`

* `SynchronizeExecutionEnvironmentReportRepository`

  * `Page<...> findAllByExchangeIdOrderByStartedAtDesc(...)`
  * `List<...> findAllByHasAnomaliesFalseAndFinishedAtBefore(Instant threshold)`

* `SynchronizeExecutionEnvironmentReportAnomalyRepository`

  * базовый `JpaRepository`

---

### 4) DataService

Package: `com.example.tradingbot.persistence.service`

* `SynchronizeExecutionEnvironmentReportDataService`

  * `ReportEntity createStarted(...)`
  * `void appendAnomaly(reportId, ...)`
  * `void finalizeReport(reportId, databaseAfterJson, exchangeAfterJson, finishedAt, hasAnomalies, maxSeverity)`
  * `int deleteFinishedNoAnomaliesBefore(Instant threshold)`

---

## Ограничения

* В этом таске не трогать CancelFlow/SYNC/Transfer.

---

## DoD

* Миграция применяется.
* Старый `AnomalyReport` не используется нигде.
* Новый report сохраняется и может финализироваться.
