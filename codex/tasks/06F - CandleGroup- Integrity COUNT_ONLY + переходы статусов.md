# Task 06F — CandleGroup: Integrity COUNT_ONLY + переходы статусов

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Реализовать быстрый integrity check (count) и переходы:

* если mismatch → `status=REPAIR_RUNNING`
* если ок → `status=SYNC` (или `READY`)

Ремонт (binary + repair) — в следующей таске.

---

## 1) Компоненты

Package: `com.example.tradingbot.domain.service.candlegroup.integrity`

* `CandleIntegrityService`

    * `IntegrityResult checkCountOnly(CandleGroupEntity group, CandleGroupRunContext ctx)`

`IntegrityResult`:

* `long startTs`
* `long endTs`
* `long expected`
* `long actual`
* `boolean ok`

---

## 2) Диапазон проверки

* `startTs = coverage_start_ts`
* `endTs = nowClosedTs`

`expected = ((endTs - startTs) / tfMillis) + 1`
`actual = candleDataService.countBetween(groupId, startTs, endTs)`

---

## 3) Действия по результату

* если `ok`:

    * если статус `REPAIR_RUNNING` или `BACKFILL_RUNNING` → перевести в `SYNC`
* если `!ok`:

    * если `integrityCheckMode`:

        * `COUNT_ONLY` → `status=REPAIR_RUNNING` и завершить обработку
        * `COUNT_PLUS_REPAIR` → перевод в REPAIR + запуск repair (делает Task 06G)

Дополнительно:

* если `actual > expected` → логировать как аномалию данных (не repair дыр, а «лишние бары»)

---

## 4) Интеграция в worker

При статусах:

* после backfill завершения (S1) обязательно сделать count check
* при SYNC: опционально раз в N запусков (N конфигом)

---

## DoD

* `COUNT_ONLY` корректно считает expected/actual по фиксированному nowClosedTs.
* При mismatch переводит группу в REPAIR_RUNNING.
