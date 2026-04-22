# candle_group — сценарии загрузки свечей и проверка целостности

Документ описывает **полный набор бизнес‑сценариев** для:

* хранения свечей через сущность **`candle_group`** (инструмент × таймфрейм);
* scheduled‑job, который **качает историю и хвост**, умеет **переживать рестарты**, и **чинит дыры**;
* проверки целостности сначала через **быстрый `count`**, а затем локализации дыр через **бинарный поиск по `count`**.

> Контекст: OKX, таймфреймы чувствительны к регистру — используем **только константы** `OkxTimeframes.*`. Свеча больше не хранит текстовый TF — только ссылку `candle_group_id`.

---

## 1. Доменная модель и инварианты

### 1.1 candle_group

Одна запись `candle_group` соответствует **одному инструменту и одному таймфрейму**.

Рекомендуемые поля:

* `id`
* `instrument_id` — FK на инструмент
* `timeframe` — значение из `OkxTimeframes.*` (строго как есть)
* `status` — состояние процесса (см. ниже)
* `coverage_start_ts` — UTC millis: **с какого времени мы обязаны иметь полное покрытие**
* `backfill_cursor_ts` — UTC millis: чекпоинт для исторической загрузки «в глубину»
* `last_tail_sync_ts` — UTC millis: до какого **закрытого бара** хвост точно синхронизирован
* `last_success_at`, `last_error_at`, `last_error_code`, `last_error_message`
* `attempt_count` — счётчик попыток
* `lease_owner` — идентификатор инстанса (hostname/pod)
* `lease_until` — UTC millis: до какого момента арендован `candle_group`

Уникальность:

* `UNIQUE(instrument_id, timeframe)`

### 1.2 candles

Свечи храним как факты:

* `id`
* `candle_group_id` — FK → `candle_group`
* `timestamp` — UTC millis (граница бара)
* `open`, `high`, `low`, `close`
* `volume*`
* `created_at`

Уникальность:

* `UNIQUE(candle_group_id, timestamp)`

### 1.3 Инварианты

Должны выполняться всегда:

1. **Idempotency**: повторная загрузка тех же свечей не ломает данные (уникальный ключ + upsert/ignore).
2. **TF сетка**: для закрытых баров `ts` кратен `tfMillis`.
3. **No look-ahead**: при оценке expected используем только **последний закрытый бар**.
4. **Monotonic stop**: `backfill_cursor_ts` движется только в сторону прошлого.
5. **Lease safety**: одновременно один `candle_group` не обрабатывается двумя воркерами.

---

## 2. Статусы candle_group (state machine)

Статусы описывают «что делать джобе дальше», а не «что уже сделано».

Рекомендуемый набор:

* `NEW` — нет истории или нет подтверждённого покрытия.
* `BACKFILL_RUNNING` — идёт историческая загрузка «в глубину» до `coverage_start_ts`.
* `REPAIR_RUNNING` — найдены несоответствия по count, идёт локализация дыр и ремонт.
* `SYNC` — регулярная докачка хвоста (scheduled).
* `READY` — всё ок (можно трактовать как `SYNC`, но полезно как «зелёный статус»).
* `ERROR` — превышен лимит попыток/фатальная ошибка; требуется ручное вмешательство.

### 2.1 Переходы

* `NEW → BACKFILL_RUNNING` — начинаем первичную загрузку.
* `BACKFILL_RUNNING → REPAIR_RUNNING` — backfill завершён, нужна проверка целостности.
* `REPAIR_RUNNING → SYNC` — после ремонта и успешной проверки.
* `SYNC → REPAIR_RUNNING` — если регулярная проверка обнаружила mismatch.
* `* → ERROR` — если попытки исчерпаны или ошибка неустранима автоматически.
* `ERROR → SYNC/REPAIR_RUNNING/BACKFILL_RUNNING` — ручной перевод после исправлений.

---

## 3. Основная scheduled‑job: общий алгоритм

### 3.1 Общие параметры (конфиг)

* `job.batchLimit` — сколько свечей в одном запросе/сохранении
* `job.timeframesOrder` — порядок обхода TF (обычно старшие → младшие)
* `job.tailOverlapBars` — overlap для хвоста (по TF)
* `job.maxGroupsPerRun` — сколько candle_group обрабатывать за один запуск
* `job.leaseDurationSec` — TTL лиза
* `job.maxAttemptsBeforeError` — порог для ERROR
* `job.integrityCheckMode`:

    * `NONE` — только tail/backfill
    * `COUNT_ONLY` — быстрый `count`
    * `COUNT_PLUS_REPAIR` — `count` + бинарная локализация + repair
* `job.repairLeafBars` — порог «малого окна», где вместо бинарного делаем точный список пропусков

### 3.2 Выбор candle_group на обработку

На каждом запуске job:

1. Выбирает `candle_group` с `status IN (NEW, BACKFILL_RUNNING, REPAIR_RUNNING, SYNC)`.
2. Фильтрует по lease:

    * берём, если `lease_until IS NULL` или `lease_until < now`.
3. Ставит lease атомарно:

    * `lease_owner = <instanceId>`
    * `lease_until = now + leaseDuration`

> Важно: lease обновлять (extend) во время долгих операций, иначе другой инстанс может перехватить группу.

### 3.3 «Фиксация времени» для расчётов

В начале обработки candle_group фиксируем:

* `runNow = nowUtcMillis`
* `tfMillis = timeframeMillis(timeframe)`
* `nowClosedTs = floor(runNow / tfMillis) * tfMillis - tfMillis`

И используем `nowClosedTs` во всех expected‑расчётах для этого TF.

---

## 4. Сценарии загрузки свечей

### Сценарий S1 — Первичная загрузка (NEW)

**Цель:** построить покрытие от `coverage_start_ts` до `nowClosedTs`.

**Порядок действий:**

1. Перевести статус в `BACKFILL_RUNNING`.
2. Инициализировать `backfill_cursor_ts`:

    * если `backfill_cursor_ts` пустой → поставить `nowClosedTs + tfMillis` (курсор «чуть правее хвоста»).
3. Выполнить **Tail Sync** (см. S2) с overlap, чтобы хвост был актуален.
4. Выполнить **Backfill в глубину** до `coverage_start_ts` (см. S3).
5. Перевести статус в `REPAIR_RUNNING`.
6. Запустить `COUNT_PLUS_REPAIR` (см. раздел 5):

    * если ок → `SYNC`
    * если не удалось починить → `ERROR` или оставить `REPAIR_RUNNING` с увеличенным `attempt_count`.

**Примечание:** первичная загрузка может быть тяжёлой. Держим батчи маленькими, делаем паузы и ретраи.

---

### Сценарий S2 — Регулярная докачка хвоста (SYNC)

**Цель:** всегда иметь актуальные последние свечи и переживать краткие сбои без сложного ремонта.

**Порядок действий:**

1. Определить overlapBars для TF (например, 300 для 1m, 200 для 3m, 100 для 15m, 50 для 1h, 30 для 4h, 10 для 1d).
2. Запросить последние `overlapBars` свечей (либо `market/candles`, либо `history-candles` одинаковым способом).
3. Upsert в `candles` по `(candle_group_id, timestamp)`.
4. Обновить `last_tail_sync_ts = nowClosedTs`.
5. (Опционально) раз в N запусков выполнить `COUNT_ONLY` (быстрый контроль), если mismatch → `REPAIR_RUNNING`.

**Почему overlap важен:**

* закрывает «микро‑дыры» без бинарного поиска;
* не зависит от того, сколько времени бот был выключен (в разумных пределах);
* безопасен при повторах.

---

### Сценарий S3 — Backfill «в глубину» (BACKFILL_RUNNING)

**Цель:** дойти курсором до `coverage_start_ts`.

**Входные данные:**

* `backfill_cursor_ts` — текущая правая граница запроса
* `coverage_start_ts`

**Алгоритм (пагинация назад):**

1. `cursor = backfill_cursor_ts`.
2. Пока `cursor > coverage_start_ts`:

    * запросить батч свечей «назад» от `cursor`.
    * отфильтровать свечи: `ts >= coverage_start_ts` (всё, что левее — не нужно)
    * upsert батча
    * вычислить `minTsBatch = min(ts)`
    * если батч пустой или `minTsBatch >= cursor` → stop (защита от вечного цикла)
    * `cursor = minTsBatch`
    * **commit checkpoint**: записать `backfill_cursor_ts = cursor` сразу после успешного сохранения батча.
3. Когда `cursor <= coverage_start_ts` → backfill завершён.

**Критично:** чекпоинт пишем **после** сохранения свечей, чтобы после рестарта мы не «перескочили» пропуски.

---

### Сценарий S4 — Рестарт/падение во время backfill

**Проблема:** backfill остановился на середине. В БД могут быть «две половинки» покрытия.

**Решение:** статус + курсор.

**Что происходит при следующем запуске job:**

1. Группа остаётся в `BACKFILL_RUNNING` (или будет такой выставлена).
2. Job берёт lease и читает `backfill_cursor_ts`.
3. Продолжает backfill с того же `backfill_cursor_ts`.
4. После завершения backfill — `REPAIR_RUNNING` и контроль целостности.

**Почему это разруливает «две половинки»:**

* backfill продолжает докачивать «середину» тем же механизмом, а duplicates не страшны.
* если всё же остаются дыры (например, backfill ограничен глубиной/окном) — их поймает count‑проверка и repair.

---

### Сценарий S5 — Рестарт в SYNC (обычная регулярка)

**Суть:** хвост мог не докачаться.

**Решение:** overlap + count.

1. Делается S2 (tail overlap).
2. Если простоя было много — `COUNT_ONLY` почти сразу покажет mismatch.
3. Тогда `REPAIR_RUNNING` (S6/S7).

---

### Сценарий S6 — Найдены несоответствия по count (переход в REPAIR_RUNNING)

**Триггер:** `actualCount != expectedCount` на диапазоне `start..nowClosedTs`.

Job делает:

1. Устанавливает `status = REPAIR_RUNNING`.
2. Запускает бинарную локализацию дыр (раздел 5.2).
3. Чинит найденные окна (раздел 5.4).
4. Повторяет count‑проверку.
5. Если ок → `SYNC`, иначе — повтор/ERROR.

---

## 5. Целостность: count → бинарный поиск → repair

### 5.1 Быстрый integrity check по count

**Цель:** понять, что покрытие полное, не читая всё.

Определения:

* `startTs = max(coverage_start_ts, oldestTsInDbOrCoverage)`
* `endTs = nowClosedTs`

Считаем:

* `expected = ((endTs - startTs) / tfMillis) + 1`
* `actual = COUNT(candles where candle_group_id=? and timestamp between startTs and endTs)`

Результаты:

* если `actual == expected` → покрытие вероятно целое
* если `actual < expected` → есть дыры
* если `actual > expected` → аномалия (скорее всего неверные границы/ts/двойные данные до введения уникального ключа)

**Важно:** count не ловит «битые значения OHLC», он ловит пропуски/лишние бары.

---

### 5.2 Бинарная локализация дыр (по count)

Идея: мы умеем быстро проверять любой диапазон `(a..b)` через count.

Функция `checkRange(a,b)`:

1. `expected = ((b-a)/tfMillis)+1`
2. `actual = countBetween(a,b)`
3. если `actual == expected` → диапазон целый
4. иначе:

    * если диапазон маленький (`bars <= repairLeafBars`) → в лист (точное определение пропусков)
    * иначе split:

        * `mid = a + floor(((b-a)/(2*tfMillis))) * tfMillis`
        * рекурсивно `checkRange(a, mid)` и `checkRange(mid+tfMillis, b)`

Выход: список «подозрительных листовых окон», где есть пропуски.

**Плюсы:** дешёвые запросы `COUNT` вместо чтения всех timestamps.

---

### 5.3 Точное определение пропущенных timestamp в листовом окне

Когда окно маленькое, делаем точно:

1. Считать все `timestamp` в окне:

    * `SELECT timestamp FROM candles WHERE candle_group_id=? AND timestamp BETWEEN a AND b ORDER BY timestamp`
2. Построить ожидаемую сетку timestamps (в памяти):

    * `a, a+tf, a+2tf, ... b`
3. Разница множеств = список пропусков.
4. Сгруппировать пропуски в окна ремонта:

    * последовательные `ts` объединяем в `[gapStart..gapEnd]`.

Это даёт минимальный набор repair‑окон.

---

### 5.4 Repair окон через загрузку с биржи

Для каждого окна `[gapStart..gapEnd]`:

1. Подготовить курсор для пагинации назад:

    * `cursor = gapEnd + tfMillis`
2. Пока `cursor >= gapStart`:

    * запросить батч истории
    * отфильтровать `gapStart <= ts <= gapEnd`
    * upsert
    * `cursor = min(tsBatch)`
3. После докачки окна:

    * `actualWindow = countBetween(gapStart,gapEnd)`
    * `expectedWindow = ((gapEnd-gapStart)/tfMillis)+1`
    * если не сошлось → повторить 1 раз или пометить «не чинится автоматически».

**Ремонт может идти в несколько проходов:** это ок, потому что upsert идемпотентен.

---

## 6. Дополнительные сценарии и edge cases

### E1 — Появился новый timeframe у инструмента

1. Создать новый `candle_group(instrument, timeframe)` со статусом `NEW`.
2. Задать `coverage_start_ts` (по твоим правилам покрытия).
3. Job в следующем прогоне выполнит S1.

---

### E2 — Изменили coverage_start_ts назад/вперёд

* Если сдвинули **вперёд** (меньше данных требуется):

    * просто обновляем `coverage_start_ts`, можно пересчитать expected.
* Если сдвинули **назад** (нужно больше истории):

    * ставим `status = BACKFILL_RUNNING`, инициализируем/не трогаем `backfill_cursor_ts`.

---

### E3 — Дубликаты, пересохранение, «повтор батча»

Это нормально при:

* ретраях
* рестарте
* overlap

Решение:

* уникальный ключ `(candle_group_id, timestamp)`
* insert‑ignore или upsert

---

### E4 — «expected > actual» постоянно прыгает

Частые причины:

1. ты считаешь expected до **текущего формирующегося бара**;
2. `now` берётся по‑разному внутри одной обработки;
3. неверный `tfMillis` или TF строка нормализована.

Решение:

* фиксировать `nowClosedTs` один раз (раздел 3.3)
* использовать `OkxTimeframes.*`

---

### E5 — Lease и многопоточность

Если джоб может выполняться параллельно (несколько инстансов):

* lease обязателен
* lease нужно продлевать во время долгих backfill/repair
* при аварии lease истекает, и другой инстанс продолжает с чекпоинта

---

### E6 — Когда переводить в ERROR

Если:

* `attempt_count >= maxAttemptsBeforeError`, или
* биржа регулярно возвращает фатальные ошибки (не ретраимые), или
* после repair mismatch сохраняется несколько прогонов подряд.

Тогда:

* `status = ERROR`
* бот может либо пропускать этот TF, либо уходить в HOLD для данного инструмента.

---

## 7. Рекомендуемый порядок обхода таймфреймов

1. Старшие TF быстрее дают «скелет покрытия» и устойчивее к лимитам.
2. Младшие TF тяжёлые — их лучше докачивать после того, как старшие уже целы.

Рекомендация:

* `ONE_DAY → FOUR_HOURS → TWO_HOURS → ONE_HOUR → FIFTEEN_MINUTES → FIVE_MINUTES → THREE_MINUTES → ONE_MINUTE`

(Использовать константы `OkxTimeframes.*`.)

---

## 8. Чек‑лист: «что джоб гарантирует»

После успешного прогона для `candle_group`:

* `last_tail_sync_ts == nowClosedTs` (хвост догнан)
* `count(start..nowClosedTs) == expected` (нет дыр)
* `status == SYNC` (или READY)
* `backfill_cursor_ts <= coverage_start_ts` (если первичная загрузка завершена)

---

## 9. Мини‑псевдо‑блок‑схема (для переноса в диаграмму)

1. Pick candle_group by status + lease
2. Fix nowClosedTs
3. switch(status)

    * NEW → BACKFILL_RUNNING
    * BACKFILL_RUNNING → TailSync → BackfillStep (batched) → REPAIR_RUNNING
    * SYNC → TailSync → (optional CountCheck) → if mismatch → REPAIR_RUNNING
    * REPAIR_RUNNING → CountCheck → if mismatch → BinaryLocate → RepairWindows → recheck
4. Persist state (cursor, last_sync, status)
5. Release lease

---

## 10. Что дальше

Следующий шаг — зафиксировать:

1. конкретные поля таблиц `candle_group` и `candles` (DDL),
2. точные SQL для:

    * `min/max/count between`
    * выборки timestamps в окне
3. параметры по умолчанию overlap/leafBars/lease

И после этого перейти к **отладке каждого запроса OKX** для свечей (tail + history) и проверить пагинацию/лимиты/ретраи.
