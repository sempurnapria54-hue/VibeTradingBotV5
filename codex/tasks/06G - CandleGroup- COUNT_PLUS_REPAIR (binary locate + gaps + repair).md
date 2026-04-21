# Task 06G — CandleGroup: COUNT_PLUS_REPAIR (binary locate + gaps + repair)

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Реализовать полный сценарий S6:

* бинарная локализация дыр по count
* точное определение missing timestamps в leaf-окнах
* repair окон докачкой history
* повторный count и перевод в SYNC

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.service.candlegroup.repair`

* `CandleRepairService`

    * `RepairResult repair(CandleGroupEntity group, CandleGroupRunContext ctx)`

* `CandleGapLocator`

    * `List<TimeWindow> locateLeafWindows(Long groupId, long startTs, long endTs, long tfMillis, int leafBars)`

* `MissingTimestampsResolver`

    * `List<Long> findMissingTimestamps(Long groupId, TimeWindow window, long tfMillis)`
    * `List<TimeWindow> groupIntoGapWindows(List<Long> missing, long tfMillis)`

* `GapWindowDownloader`

    * `GapRepairResult repairWindow(CandleGroupEntity group, CandleGroupRunContext ctx, TimeWindow gap)`

Models:

* `TimeWindow { long fromTs; long toTs; }` (inclusive)

---

## 2) Binary locate (по count)

`checkRange(a,b)`:

* `bars = ((b-a)/tfMillis)+1`
* `expected = bars`
* `actual = countBetween(a,b)`
* если ok → return
* если `bars <= repairLeafBars` → add window
* иначе:

    * `mid = a + (((bars/2)-1) * tfMillis)`
    * left: `[a..mid]`
    * right: `[mid+tfMillis..b]`

Важно:

* mid всегда должен попадать на сетку tf
* правое окно начинается с `mid + tfMillis`

---

## 3) Точное missing timestamps в leaf

Для leaf `[a..b]`:

1. `dbTs = SELECT timestamp ... ORDER BY timestamp`
2. `expectedTs = a..b step tfMillis`
3. missing = expected \ db
4. сгруппировать missing в gap-окна

---

## 4) Repair окна

Для gap `[gapStart..gapEnd]`:

* `cursor = gapEnd + tfMillis`
* пока `cursor >= gapStart`:

    * batch = fetchHistoryBackward(instId, timeframe, batchLimit, cursor)
    * filter `gapStart <= ts <= gapEnd`
    * upsert
    * `minTs = min(tsBatch)`
    * guard empty/minTs>=cursor
    * `cursor = minTs`
    * extend lease

После repair:

* `actualWindow = countBetween(gapStart,gapEnd)`
* `expectedWindow = ((gapEnd-gapStart)/tfMillis)+1`
* если mismatch → увеличить attempt_count и пометить как «не починилось в этом проходе»

---

## 5) Финализация REPAIR_RUNNING

Алгоритм `repair()`:

1. `countOnly` на полном диапазоне
2. если ok → `status=SYNC` и return
3. locate leaf windows
4. resolve missing → gap windows
5. repair gap windows
6. повторить `countOnly`
7. если ok → `status=SYNC` иначе оставить `REPAIR_RUNNING` + attempts

---

## DoD

* При mismatch repair локализует окна и докачивает историю.
* После успешного repair группа возвращается в `SYNC`.
* Весь процесс идемпотентен (upsert).
