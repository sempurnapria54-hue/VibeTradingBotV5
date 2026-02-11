# Task 06C — CandleGroup: lease + selection + scheduled job skeleton

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Сделать scheduled job, который:

* выбирает candle_group по статусам
* захватывает lease атомарно
* фиксирует `nowClosedTs`
* вызывает обработчик группы (пока только логика-скелет)
* корректно release/extend lease

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.job`

* `CandleGroupsSyncJob`

Package: `com.example.tradingbot.domain.service.candlegroup`

* `CandleGroupWorker`

    * `void processGroup(Long candleGroupId)`

* `CandleGroupLeaseService`

    * `List<CandleGroupEntity> pickEligibleGroups(nowMillis, maxGroups)`
    * `boolean acquireLease(groupId)`
    * `void extendLease(groupId)`
    * `void releaseLease(groupId)`

* `CandleGroupRunContextFactory`

    * `CandleGroupRunContext create(CandleGroupEntity group)`

Model:

* `CandleGroupRunContext`

    * `runNowMillis`
    * `tfMillis`
    * `nowClosedTs`
    * `instanceId`

---

## 2) Выбор candle_group

Статусы для обработки:

* `NEW, BACKFILL_RUNNING, REPAIR_RUNNING, SYNC`

Lease:

* брать если `lease_until is null OR lease_until < now`

Acquire lease:

* атомарный update с where-условием по lease_until

---

## 3) Фиксация времени

В начале обработки group:

* `runNow`
* `tfMillis`
* `nowClosedTs`

`nowClosedTs` передавать дальше в S2/S3/S6.

---

## 4) Ошибки/attempt_count

* каждый fail увеличивает `attempt_count`
* если `attempt_count >= maxAttemptsBeforeError` → `status=ERROR`
* сохранять `last_error_*`

---

## 5) Логи

Логи без секретов:

* groupId, instrumentId, timeframe, status
* nowClosedTs
* шаги (S1/S2/S3/S6)

---

## DoD

* Job запускается, выбирает не более maxGroupsPerRun.
* Lease защищает от параллельной обработки.
* Ошибки корректно учитывают attempt_count.
