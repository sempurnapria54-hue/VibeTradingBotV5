# Task 06E — CandleGroup: Backfill (S1/S3/S4) + cursor checkpoint

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Реализовать первичную загрузку (S1) и backfill (S3) с чекпоинтом `backfill_cursor_ts`.

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.service.candlegroup`

* `BackfillService`

    * `BackfillResult backfillToCoverage(CandleGroupEntity group, CandleGroupRunContext ctx)`

`BackfillResult`:

* `boolean completed`
* `long newCursorTs`
* `int fetched`
* `int saved`

---

## 2) Инициализация cursor

Если `backfill_cursor_ts` null:

* установить `backfill_cursor_ts = nowClosedTs + tfMillis`
* сохранить сразу (чтобы рестарт не начал «с нуля»)

---

## 3) Пагинация назад

Псевдокод:

* `cursor = group.backfillCursorTs`
* while `cursor > coverage_start_ts`:

    * batch = `fetchHistoryBackward(instId, timeframe, batchLimit, cursor)`
    * filter by `ts >= coverage_start_ts` и `ts <= nowClosedTs`
    * upsert
    * `minTs = min(tsBatch)`
    * guard: if batch empty OR `minTs >= cursor` → break
    * `cursor = minTs`
    * **checkpoint**: `updateBackfillCursor(groupId, cursor)` (после сохранения)
    * extend lease (если долго)

Completed:

* если `cursor <= coverage_start_ts` → completed=true

---

## 4) Интеграция в S1

В `CandleGroupWorker`:

* если статус `NEW`:

    1. `status=BACKFILL_RUNNING`
    2. tail sync
    3. backfill
    4. если completed → `status=REPAIR_RUNNING`

Если статус `BACKFILL_RUNNING`:

* продолжить backfill с cursor

---

## 5) Защита от вечного цикла

* если batch пустой → break
* если `minTs >= cursor` → break

В таком случае:

* увеличить attempt_count
* оставить статус как есть

---

## DoD

* Backfill двигает cursor только назад.
* После рестарта backfill продолжается с чекпоинта.
* Когда cursor <= coverage_start_ts — backfill считается завершённым.
