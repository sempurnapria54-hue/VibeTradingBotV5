# Task 05J — Stage 05 (v3): Cleanup job для отчётов (retention)

Опирайся на stage: `codex/stage/05 — Synchronize Execution Environment.md`.

## Цель

Добавить job очистки `SynchronizeExecutionEnvironmentReport` по TTL:

* удалять только отчёты без аномалий
* только завершённые

---

## Что нужно сделать

### 1) Config

* `synchronize-execution-environment.reports.retention-days` (int)

### 2) Job

Package: `com.example.tradingbot.domain.job`

* `CleanupSynchronizeExecutionEnvironmentReportsJob`

Поведение:

* если retentionDays <= 0 → job ничего не делает
* threshold = nowUtc - retentionDays
* удалить записи, где:

    * `hasAnomalies=false`
    * `finishedAt < threshold`

### 3) Service

Package: `com.example.tradingbot.domain.service.reconcile`

* `CleanupSynchronizeExecutionEnvironmentReportsService`

    * `int cleanup()`

Используй `SynchronizeExecutionEnvironmentReportDataService.deleteFinishedNoAnomaliesBefore(threshold)`.

### 4) Планировщик

* либо @Scheduled cron (частота раз в сутки)
* либо ручной запуск через CommandLineRunner (на время разработки)

---

## Ограничения

* Не удалять отчёты, где `hasAnomalies=true`.
* Не трогать незавершённые отчёты (`finishedAt is null`).

---

## DoD

* Job запускается и удаляет только подходящие записи.
* Логи показывают: threshold + сколько удалено.
