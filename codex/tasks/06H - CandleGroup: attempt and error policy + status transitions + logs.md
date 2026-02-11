# Task 06H — CandleGroup: attempt/error policy + status transitions + logs

Опирайся на stage: `codex/stage/06 — candle_group: загрузка свечей и проверка целостности.md`.

## Цель

Собрать «сквозную» политику ошибок и статусов для всех сценариев S1–S6:

* единое увеличение `attempt_count`
* перевод в `ERROR` по порогу
* единый формат логов
* обновление `last_success_at` / `last_error_*`

---

## 1) Политика attempts

Правило:

* любая ошибка обработки группы увеличивает `attempt_count` на 1
* если `attempt_count >= maxAttemptsBeforeError` → `status=ERROR`

Сброс attempts:

* при успешной обработке (tail/backfill/repair) можно сбрасывать `attempt_count=0` (выбери и зафиксируй правило)

---

## 2) last_success / last_error

При успехе:

* `last_success_at = now`
* `last_error_* = null`

При ошибке:

* `last_error_at = now`
* `last_error_code`, `last_error_message`

---

## 3) Status transitions

Единые правила:

* `NEW` при старте → `BACKFILL_RUNNING`
* `BACKFILL_RUNNING` после completed backfill → `REPAIR_RUNNING`
* `REPAIR_RUNNING` после ok integrity → `SYNC`
* `SYNC` при mismatch → `REPAIR_RUNNING`
* `*` при attempts exceeded → `ERROR`

---

## 4) Observability (логи)

Для каждой группы логировать:

* `groupId`, `instrumentId`, `timeframe`, `status`
* `nowClosedTs`, `coverage_start_ts`, `cursor`
* результаты:

    * tail: fetched/saved
    * backfill: batches, cursor moves
    * integrity: expected/actual
    * repair: leaf windows count, gap windows count, repaired count

Запрещено:

* логировать raw ответы OKX целиком
* логировать секреты

---

## 5) Lease extend policy

Во всех циклах backfill/repair:

* каждые N батчей (или каждые M секунд) делай `extendLease()`

---

## DoD

* После любой ошибки группа либо остаётся в корректном статусе для продолжения, либо уходит в ERROR.
* В логах видно, что джоба делала, и почему приняла решение.
