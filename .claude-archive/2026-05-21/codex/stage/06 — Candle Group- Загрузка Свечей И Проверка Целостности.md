# Stage 06 — candle_group: загрузка свечей и проверка целостности

Основание: `docs/scenario/02 - candle_group — сценарии загрузки свечей и проверка целостности.md`.

## 0) Цель

Построить устойчивый контур хранения и синхронизации свечей OKX:

* свечи храним как факты в `candles`, привязанные к `candle_group` (instrument × timeframe)
* scheduled job:

    * качает хвост (tail sync) с overlap
    * докачивает историю «в глубину» (backfill) до `coverage_start_ts`
    * умеет переживать рестарты за счёт курсоров/статусов
    * проверяет целостность: быстрый `count`, при mismatch — локализация дыр бинарным поиском по `count` + repair
* защита от параллельной обработки через lease

**Критично:** OKX timeframe чувствителен к регистру — используем только `OkxTimeframes.*`, без нормализации.

---

## 1) Доменная модель (DDL + инварианты)

### 1.1 candle_group

`candle_group` = один инструмент × один таймфрейм.

Минимальные поля:

* `id`
* `instrument_id` (FK → instrument)
* `timeframe` (строго значение `OkxTimeframes.*`)
* `status` (state machine)
* `coverage_start_ts` (UTC millis) — с какого ts обязаны иметь полное покрытие
* `backfill_cursor_ts` (UTC millis) — курсор исторической загрузки (движется только назад)
* `last_tail_sync_ts` (UTC millis) — до какого закрытого бара хвост точно синхронизирован
* `attempt_count`
* `last_success_at`, `last_error_at`, `last_error_code`, `last_error_message`
* `lease_owner`, `lease_until` (UTC millis)
* audit поля (created/updated)

Уникальность:

* `UNIQUE(instrument_id, timeframe)`

### 1.2 candles

Факты свечей:

* `id`
* `candle_group_id` (FK → candle_group)
* `timestamp` (UTC millis, граница бара)
* `open`, `high`, `low`, `close`
* `volume*`
* audit

Уникальность:

* `UNIQUE(candle_group_id, timestamp)`

### 1.3 Инварианты

1. Idempotency: повторная загрузка того же диапазона не ломает данные (unique key + upsert/ignore).
2. TF-сетка: для закрытых баров `timestamp % tfMillis == 0`.
3. No look-ahead: expected считаем только до `nowClosedTs`.
4. Monotonic stop: `backfill_cursor_ts` монотонно уменьшается.
5. Lease safety: один `candle_group` обрабатывает один воркер.

---

## 2) State machine candle_group

Статусы описывают «что делать дальше».

Рекомендуемые статусы:

* `NEW`
* `BACKFILL_RUNNING`
* `REPAIR_RUNNING`
* `SYNC`
* `READY` (опционально как «зелёный» статус)
* `ERROR`

Переходы:

* `NEW → BACKFILL_RUNNING`
* `BACKFILL_RUNNING → REPAIR_RUNNING`
* `REPAIR_RUNNING → SYNC`
* `SYNC → REPAIR_RUNNING` (при mismatch)
* `* → ERROR` (attempts исчерпаны / фатал)
* `ERROR → ...` (ручной перевод)

---

## 3) Конфигурация (application.yml)

`candle-groups.job.*`:

* `batchLimit` — размер батча на запрос/сохранение
* `timeframesOrder` — порядок обхода TF (старшие → младшие)
* `maxGroupsPerRun` — сколько групп за запуск
* `leaseDurationSec` — TTL лиза
* `maxAttemptsBeforeError`
* `tailOverlapBars` — overlap по TF (map timeframe → bars)
* `integrityCheckMode` = `NONE | COUNT_ONLY | COUNT_PLUS_REPAIR`
* `repairLeafBars` — окно (bars), где вместо split делаем точный список пропусков
* `sleepMsBetweenBatches` (опционально)

---

## 4) Фиксация времени (ключевой принцип)

В начале обработки *каждого candle_group* фиксируем:

* `runNow = nowUtcMillis`
* `tfMillis = timeframeMillis(timeframe)`
* `nowClosedTs = floor(runNow / tfMillis) * tfMillis - tfMillis`

`nowClosedTs` используем везде для expected и для обновления `last_tail_sync_ts`.

---

## 5) Выбор candle_group + lease (многопоточность)

Алгоритм выбора на запуск job:

1. выбрать группы `status IN (NEW, BACKFILL_RUNNING, REPAIR_RUNNING, SYNC)`
2. фильтр по lease:

    * берём если `lease_until IS NULL OR lease_until < now`
3. атомарно проставить lease:

    * `lease_owner = instanceId`
    * `lease_until = now + leaseDuration`

Во время длительных операций lease нужно продлевать (extend).

---

## 6) Сценарии обработки (S1–S6)

### S1 — Первичная загрузка (NEW)

Цель: построить покрытие `coverage_start_ts..nowClosedTs`.

Шаги:

1. `status=BACKFILL_RUNNING`
2. init `backfill_cursor_ts` если null → `nowClosedTs + tfMillis`
3. Tail Sync (S2)
4. Backfill (S3) до `coverage_start_ts`
5. `status=REPAIR_RUNNING`
6. Integrity:

    * если `COUNT_PLUS_REPAIR` и всё ок → `status=SYNC`
    * иначе попытки/ERROR

### S2 — Регулярная докачка хвоста (SYNC)

Цель: всегда актуальные последние свечи.

Шаги:

1. определить overlapBars для TF
2. загрузить последние `overlapBars` свечей
3. upsert
4. `last_tail_sync_ts = nowClosedTs`
5. (опционально) раз в N запусков сделать `COUNT_ONLY`, при mismatch → `REPAIR_RUNNING`

### S3 — Backfill в глубину (BACKFILL_RUNNING)

Цель: довести `backfill_cursor_ts` до `coverage_start_ts`.

Пагинация назад:

* `cursor = backfill_cursor_ts`
* пока `cursor > coverage_start_ts`:

    * загрузить батч history «назад от cursor»
    * отфильтровать `ts >= coverage_start_ts`
    * upsert
    * `minTsBatch = min(ts)`
    * защита: если батч пустой или `minTsBatch >= cursor` → stop
    * `cursor = minTsBatch`
    * commit checkpoint: `backfill_cursor_ts = cursor` (после сохранения!)

### S4 — Рестарт во время backfill

В следующий запуск:

* группа остаётся `BACKFILL_RUNNING`
* job продолжает с `backfill_cursor_ts`
* после завершения → `REPAIR_RUNNING` → integrity

### S5 — Рестарт в SYNC

* делаем S2 (overlap) и (опционально) `COUNT_ONLY`
* если mismatch → `REPAIR_RUNNING`

### S6 — Найден mismatch по count (REPAIR_RUNNING)

1. `status=REPAIR_RUNNING`
2. `COUNT_ONLY` на `start..nowClosedTs`
3. если mismatch и режим `COUNT_PLUS_REPAIR`:

    * бинарная локализация дыр по count
    * точное определение пропущенных timestamps в листовых окнах
    * repair окон загрузкой с биржи
4. повторный count
5. если ок → `SYNC`, иначе попытки/ERROR

---

## 7) Integrity алгоритм (count → бинарный поиск → repair)

### 7.1 Count check

Диапазон:

* `startTs = coverage_start_ts`
* `endTs = nowClosedTs`

`expected = ((endTs - startTs) / tfMillis) + 1`
`actual = COUNT(candles where candle_group_id=? and timestamp between startTs and endTs)`

Решение:

* `actual == expected` → вероятно цело
* `actual < expected` → есть дыры
* `actual > expected` → аномалия (неконсистентные данные/границы)

### 7.2 Бинарная локализация дыр по count

`checkRange(a,b)`:

* вычислить expected/actual для окна
* если ок → return
* если bars <= repairLeafBars → сохранить окно как leaf
* иначе split по mid и рекурсивно

### 7.3 Точное определение пропусков в leaf

* SELECT timestamps в [a..b]
* построить ожидаемую сетку
* diff = пропущенные ts
* сгруппировать в gap-окна `[gapStart..gapEnd]`

### 7.4 Repair окон

Для каждого `[gapStart..gapEnd]`:

* `cursor = gapEnd + tfMillis`
* пока `cursor >= gapStart`:

    * history batch
    * фильтр `gapStart..gapEnd`
    * upsert
    * `cursor = min(tsBatch)`
* после repair: count window expected==actual

---

## 8) Набор задач Stage 06

* **06A**: миграции + persistence модели `candle_group` и обновление `candles` под `candle_group_id`
* **06B**: OKX candle fetcher (tail + history) + парсер + tfMillis util (OkxTimeframes)
* **06C**: lease + selection + scheduled job skeleton
* **06D**: Tail Sync (S2) + overlap конфиг
* **06E**: Backfill (S1/S3/S4) + курсор + чекпоинт
* **06F**: Count integrity (COUNT_ONLY) + перевод в REPAIR_RUNNING
* **06G**: Binary locate + leaf gaps + repair windows (COUNT_PLUS_REPAIR)
* **06H**: attempt/error policy + статусы + observability (логи)

---

## 9) DoD Stage 06

* Для каждого candle_group джоба корректно:

    * выдерживает рестарты (cursor/lease)
    * tail overlap закрывает микро-дыры
    * count check детектит mismatch
    * repair чинит дыры и возвращает `status=SYNC`
* Invariants соблюдаются (TF сетка, monotonic cursor, idempotency).
