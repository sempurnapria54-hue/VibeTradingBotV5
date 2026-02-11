# Task 06D — CandleGroup: Tail Sync (S2) + overlap

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Реализовать сценарий S2 (регулярная докачка хвоста) для candle_group:

* загрузка последних `overlapBars`
* upsert
* обновление `last_tail_sync_ts = nowClosedTs`

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.service.candlegroup`

* `TailSyncService`

    * `TailSyncResult syncTail(CandleGroupEntity group, CandleGroupRunContext ctx)`

`TailSyncResult`:

* `int fetched`
* `int saved`
* `long updatedLastTailSyncTs`

Использовать:

* `OkxCandleFetcher.fetchTail(instId, timeframe, overlapBars)`
* `CandleDataService.upsertBatch(groupId, mappedCandles)`
* `CandleGroupDataService.updateLastTailSync(groupId, nowClosedTs)`

---

## 2) Overlap конфиг

`candle-groups.job.tailOverlapBars` — map timeframe → bars.

Если TF нет в мапе:

* fallback на разумный дефолт (зафиксировать константой)

---

## 3) Валидации

* отфильтровать свечи:

    * `timestamp <= nowClosedTs` (не тянуть формирующийся бар)
    * `timestamp % tfMillis == 0`

---

## 4) Интеграция в worker

В `CandleGroupWorker.processGroup()`:

* при статусах `NEW, BACKFILL_RUNNING, SYNC, REPAIR_RUNNING` можно вызывать tail sync как «первый шаг»

---

## DoD

* Для группы в SYNC хвост всегда догоняется и `last_tail_sync_ts` обновляется.
* Повторный запуск не меняет данные неконсистентно.
