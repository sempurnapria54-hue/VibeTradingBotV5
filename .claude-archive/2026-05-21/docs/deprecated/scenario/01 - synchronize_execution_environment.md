# Синхронизация среды выполнения (Synchronize execution environment)

> Назначение документа: **максимально подробная спецификация** процесса синхронизации состояния между **биржей OKX** и *
*БД**.
> Документ используется как “контракт/ТЗ” для генерации задач (Codex) и последующей реализации доменного слоя.

---

## 1. Контекст и границы процесса

### 1.1. Когда запускается

Процесс запускается в следующих ситуациях:

* **Scheduled**: По расписанию через CRON.
* **Manual**: ручной запуск (по необходимости).

### 1.2. Что делает

Процесс выполняет **синхронизацию окружения исполнения** для каждого `instId` (инструмента):

1. Получает “факты” с биржи (snapshot).
2. На их основе:

* выявляет **аномалии** (критичные/некритичные),
* при необходимости запускает **Cancel Exchange Flow**,
* выполняет **SYNC (counts-only)**: синхронизирует **только наличие сущностей** (positions / orders / algoOrders) между
  биржей и БД.

3. После SYNC выполняет следующий “мелкий шаг”:

* **переносит данные с биржи в БД** (обновление полей),
* и **продолжает торговлю** (если режимы позволяют).

#### Дополнение по отчётности (обязательное правило)

* **Каждый запуск** процесса сохраняет `SynchronizeExecutionEnvironmentReport` (даже если аномалий не было).
* Для отчёта нужны **2 снапшота биржи**: `exchange_before` (до синхронизации) и `exchange_after` (после синхронизации).
* И **2 снапшота БД**: `database_before` (до синхронизации) и `database_after` (после синхронизации).
* Важно: **SYNC остаётся counts-only** (мы синхронизируем только наличие сущностей). Снапшоты — для
  аудита/дебага/аналитики.

### 1.3. Что НЕ делает

* НЕ рассчитывает стратегию, НЕ двигает SL/TP по правилам сопровождения.
* НЕ принимает решение об открытии новых позиций (это отдельный механизм).
* НЕ проводит расследование “fills с момента lastReconcileAt” (пока не нужно; можно добавить позже отдельной веткой).

---

## 2. Цель и гарантия результата (Postconditions)

После завершения Reconcile для каждого `instId` (например, `ETH-USDT-SWAP`) должно быть выполнено **одно из двух**: *
*Target NONE** или **Target OPEN**.

### 2.1. Target NONE

**На бирже:**

* нет позиции по инструменту;
* нет активных обычных ордеров по инструменту;
* нет активных algo-ордеров по инструменту (SL/TP/trigger/trailing).

**В БД (для этого инструмента):**

* нет активных позиций;
* нет активных обычных ордеров;
* нет активных algo-ордеров.

### 2.2. Target OPEN

**На бирже:**

* есть ровно **одна** позиция по инструменту;
* есть **защитный SL**, который закрывает/уменьшает позицию (reduce-only/close-mode);
* нет лишних pending-ордеров (не-наши отменены, наши — только те, что нужны для сопровождения).

**В БД (для этого инструмента):**

* есть одна активная позиция;
* есть ссылки на защитный SL в ордерах или в algo-ордерах.

---

## 3. Инварианты (правила, которые всегда должны соблюдаться)

1. Если позиция **OPEN** → должна быть защита (SL) на бирже. Если защиты нет — **удаляем сущности, формируем репорт**.
2. Если позиции нет → не должно остаться “висячих” ордеров бота.
3. Никаких “двойных входов”: если обнаружили `>1` активного entry-ордера бота по инструменту — это аномалия.
4. Если биржа показывает состояние, которое бот не умеет интерпретировать (например одновременно long+short по одному
   `instId`) → **HOLD + алерт**, пока не разрулишь вручную.

---

## 4. Словарь и источники данных (обязательное правило)

**Правило:** если в описании используется поле — **всегда указывается источник**: `Config`, `Database`, `Exchange`.

### 4.1. Основные сущности (домен)

#### Exchange (домен)

* `Exchange.status` — статус биржи в контексте исполнения процесса.

    * Источник: `Database` (мы его пишем в БД).
    * Примеры значений: `SYNC`, `ACTIVE`.

#### Instrument (домен)

* `Instrument.instId` — идентификатор инструмента на OKX (например, `ETH-USDT-SWAP`).

    * Источник: `Config` (managed instruments) и `Database` (список обслуживаемых инструментов).
* `Instrument.mode` — режим торговли:

    * `OPEN` — торговля разрешена,
    * `HOLD` — торговля приостановлена.
    * Источник: `Database`, может временно меняться процессом.
* `Instrument.positionMode` — индикатор наличия позиции (консистентный итог процесса):

    * `OPEN` — есть открытая позиция,
    * `NONE` — нет открытой позиции.
    * Источник: **вычисляется** по результату reconcile и сохраняется в `Database`.
* `Instrument.status` — служебный статус процесса/жизненного цикла:

    * `SYNC` — инструмент в процессе синхронизации,
    * `ACTIVE` — инструмент готов к дальнейшим механизмам (сопровождение/торговля),
    * `HOLD` — инструмент остановлен из-за критичной аномалии (служебно).
    * Источник: `Database`, меняется процессом.

#### Position (домен)

* Активная позиция в БД по инструменту (в рамках правила: максимум 1 позиция на инструмент).
* `Position.status` (примерная линия): `CREATED | ACTIVE | SYNC | HOLD | CLOSED`

    * Источник: `Database` (меняем в БД).

#### Order (обычный ордер, домен)

* `order.internalId` — наш id, который мы отправляем на биржу как `clOrdId`.

    * Источник: `Database` (генерим и сохраняем до вызова биржи).
* `order.exchangeOrderId` — id биржи `ordId`.

    * Источник: `Exchange` (получаем после создания/из snapshot).
* `order.status` — `CREATED | ACTIVE | SYNC | HOLD | CLOSED | ANOMALY` (примерная линия).

    * Источник: `Database`.

#### AlgoOrder (algo-ордер, домен: SL/TP/trigger/trailing)

* `algoOrder.internalId` — наш id, который мы отправляем на биржу как `algoClOrdId`.

    * Источник: `Database`.
* `algoOrder.exchangeOrderId` — id биржи `algoId`.

    * Источник: `Exchange`.
* `algoOrder.status` — `CREATED | ACTIVE | SYNC | HOLD | CLOSED | ANOMALY` (примерная линия).

    * Источник: `Database`.

#### SynchronizeExecutionEnvironmentReport (домен)

* Единый отчёт **по каждому запуску** процесса `Synchronize execution environment`.
* Отчёт заменяет собой старый `AnomalyReport`: теперь **все аномалии** пишутся внутрь одного отчёта.
* Источник: `Database` (формируется и сохраняется процессом).

Поля:

* `startedAt`, `finishedAt`, `trigger` (`SCHEDULED | MANUAL`)
* `hasAnomalies` (bool), `maxSeverity` (`CRITICAL | NON_CRITICAL | NONE`)
* `database_before`, `exchange_before`, `database_after`, `exchange_after` (4 снапшота)
* `anomalies[]` — список аномалий (то, что раньше было в `AnomalyReport`: `type`, `severity`, `instId`, `details`,
  `createdAt`)

Рекомендуемый формат снапшотов (чтобы совпадало с “counts-only”):

* `exchange_*`: по каждому `instId`: `Positions.count`, `Orders.count`, `AlgoOrders.count`, плюс списки `ordId/clOrdId`,
  `algoId/algoClOrdId` (если доступны).
* `database_*`: по каждому `instId`: `activePositions.count`, `activeOrders.count`, `activeAlgoOrders.count`, плюс
  списки `order.internalId/exchangeOrderId`, `algoOrder.internalId/exchangeOrderId` (если доступны), а также
  `Instrument.mode/status/positionMode`.

### 4.2. Snapshot биржи (источник Exchange)

В рамках reconcile используем **два снапшота биржи**: `exchange_before` (до синхронизации) и `exchange_after` (после
синхронизации).

* `Positions snapshot` — все позиции (`Exchange`).
* `Orders pending snapshot` — все активные обычные ордера (`Exchange`).
* `Orders algo pending snapshot` — все активные algo-ордера (`Exchange`).
* `Balance snapshot` — не обязателен для reconcile, но полезен для health/risk (`Exchange`).

---

## 5. Высокоуровневый алгоритм (глобальный)

### Шаг 0. Захватить single instance lock

* Источник: `Database` (или другой механизм блокировки, но фиксируем факт в БД/логах).
* Цель: гарантировать, что синхронизация выполняется **в единственном экземпляре**.

### Шаг 1. Глобальный HOLD на торговлю

* Источник: внутренний runtime state приложения (не обязательно хранить в БД), но логируем.
* Цель: пока идёт reconcile, торговые механизмы (сопровождение/входы) не должны вмешиваться.

### Шаг 2. Прочитать Config

* Источник: `Config`.
* Нужно: список обслуживаемых инструментов (`managedInstruments`), политики, ограничения.

### Шаг 3. Прочитать DB state + known ids

* Источник: `Database`.
* Нужно:

    * текущие `Instrument` записи,
    * активные `Position/Order/AlgoOrder` по инструментам,
    * known ids (как минимум `order.internalId`, `algoOrder.internalId`) для матчинга “наше/не наше”.

### Шаг 3.1. Снять `database_before` (снапшот БД до синхронизации)

* Источник: `Database`.

### Шаг 4. Снять `exchange_before` (снапшот биржи до синхронизации)

* Источник: `Exchange`.
* Результат: 3 (или 4) набора данных: positions / orders / algoOrders (/ balanceExternalSnapshot).
* Если snapshot неполный/ошибка → аномалия `EXCHANGE_SNAPSHOT_FAILED` (severity=CRITICAL).

### Шаг 4.1. Создать `SynchronizeExecutionEnvironmentReport` и сохранить (первичная запись)

* Источник: `Database`.
* Заполняем: `startedAt`, `trigger`, `database_before`, `exchange_before`, `hasAnomalies=false`, `maxSeverity=NONE`,
  `anomalies=[]`.
* Важно: этот шаг выполняется **всегда**, даже если потом аномалий не будет.

### Шаг 5. Установить Exchange.status = SYNC и сохранить Exchange

* Источник: `Database` (мы меняем статус).
* Цель: в БД видно, что сейчас идёт reconcile.

### Шаг 6. Сгруппировать всё в bucket’ы по `instId`

* Bucket = агрегат данных для одного инструмента:

    * `bucket.instId` (из `Config/Database/Exchange snapshot`),
    * `bucket.exchangePositions`, `bucket.exchangeOrders`, `bucket.exchangeAlgoOrders`,
    * `bucket.dbInstrument`, `bucket.dbPositions`, `bucket.dbOrders`, `bucket.dbAlgoOrders`.

### Шаг 7. Для каждого bucket выполнить синхронизацию

* Источник: `batch` (внутренний процесс).
* Важно: синхронизация по инструментам **независима**, но выполняется в общем глобальном HOLD.

### Шаг 8. После обработки всех bucket

### Шаг 8.1. Снять `exchange_after` (снапшот биржи после синхронизации)

* Источник: `Exchange`.
* Снимается **после** обработки всех bucket и всех cancel/close/cancelAlgo операций.

### Шаг 8.2. Снять `database_after` (снапшот БД после синхронизации)

* Источник: `Database`.
* Снимается **после** всех обновлений БД в рамках reconcile.

### Шаг 8.3. Финализировать `SynchronizeExecutionEnvironmentReport`

* Источник: `Database`.
* Заполняем: `finishedAt`, `database_after`, `exchange_after`, `hasAnomalies/maxSeverity`.
* Сохраняем финальную запись отчёта.

### Шаг 9. Финализация статусов

* Установить `Exchange.status = ACTIVE` (Источник: `Database`).
* Сохранить актуальное состояние (Источник: `Database`).
* Снять глобальный HOLD на торговлю.
* Освободить single instance lock.

---

## 6. Алгоритм по одному инструменту (bucket)

### Шаг A1. Перевести инструмент в состояние SYNC

* Действие: `Instrument.status = SYNC`.
* Источник: `Database` (сохраняем).
* Примечание: торговые действия по инструменту не выполняются до конца bucket.

### Шаг A2. Последовательно выполнить проверки аномалий (B1…B8)

* Источник: `batch.exchange` и/или `batch.database` (строго указано ниже).
* При срабатывании аномалии:

    * запускаем **Cancel Exchange Flow**,
    * добавляем аномалию в `SynchronizeExecutionEnvironmentReport.anomalies[]` (и обновляем `hasAnomalies/maxSeverity`),
    * по severity:

        * `CRITICAL` → `Instrument.mode` остаётся `HOLD`, торговля не продолжается до ручного вмешательства,
        * `NON_CRITICAL` → после очистки возвращаем `Instrument.mode=OPEN` и продолжаем.

### Шаг A3. Выполнить SYNC (counts-only)

* **Только наличие сущностей** (counts):

    * `Positions.count`, `Orders.count`, `AlgoOrders.count` — на бирже (Источник: `Exchange`),
    * активные `Position/Order/AlgoOrder` — в БД (Источник: `Database`).
* Цель: привести **наличие сущностей** в консистентность.

### Шаг A4. Перенос данных с биржи в БД (после counts-only)

* Источник: `exchange_before` → пишем в `Database`.
* Это следующий шаг после SYNC: обновление атрибутов сущностей (не про наличие).
* После переноса можно запускать следующие механизмы (сопровождение/торговля), если `Instrument.mode=OPEN`.

### Шаг A5. Завершение bucket

* `Instrument.status = ACTIVE` (Источник: `Database`).
* Обновить `Instrument.positionMode`:

    * если на бирже есть позиция → `positionMode=OPEN`,
    * иначе → `positionMode=NONE`.
* Сохранить `Instrument` (только если изменения были).
* Перейти к следующему инструменту.

---

## 7. Проверки аномалий (B1…B8) — детально

> В диаграмме проверки идут строго последовательно: **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8**.
> Каждая проверка либо пропускает поток дальше, либо запускает **Cancel Exchange Flow** (и пишет аномалию в report).

### B1 — UNKNOWN_INSTRUMENT_OBJECT_EXISTS

* Условие: в `exchange_before` есть `instrumentId`, которого нет в `managedInstruments`.
* Источник: `Exchange` + `Config`.
* Severity: `CRITICAL`.
* Действие: Cancel Exchange Flow по “unknown instrument bucket”, записать аномалию в report.

### B2 — HOLD_BUT_EXCHANGE_HAS_ACTIVE_OBJECTS

* Условие: `Instrument.mode = HOLD` и при этом на бирже есть активные сущности (например, позиции/ордера/algo).
* Источник: `Database` + `Exchange`.
* Severity: `CRITICAL`.
* Действие: Cancel Exchange Flow, `Instrument` остаётся в HOLD, записать аномалию в report.

### B3 — MULTIPLE_POSITIONS_ON_INSTRUMENT

* Условие: `Positions.count > 1`.
* Источник: `Exchange`.
* Severity: `CRITICAL`.
* Действие:
  `mode=HOLD → закрыть все позиции → отменить все Orders и AlgoOrders → записать аномалию в report → ждать ручного разбора`.

### B4 — POSITION_EXISTS_ON_EXCHANGE_BUT_MISSING_IN_DB

* Условие: на бирже есть позиция (`Positions.count > 0`), а в БД нет активной `Position`.
* Источник: `Exchange` + `Database`.
* Severity: `CRITICAL` (считаем, что кто-то вне приложения создал сущность).
* Действие: Cancel Exchange Flow, `Instrument.mode=HOLD`, записать аномалию в report.

### B5 — POSITION_WITHOUT_PROTECTIVE_SL

* Условие: позиция есть (`Positions.count == 1`) и нет protective SL (в терминах наличия: `AlgoOrders.count == 0`).
* Источник: `Exchange`.
* Severity: `CRITICAL`.
* Действие: Cancel Exchange Flow, `Instrument.mode=HOLD`, записать аномалию в report.

### B6 — DB ↔ Exchange mismatch (не аномалия по бирже, но требует SYNC)

* Условие: в БД есть активные сущности, а на бирже по `exchange_before` их нет.
* Источник: `Database` + `Exchange`.
* Комментарий: часто означает, что сущность была закрыта/отменена, пока приложение простаивало.
* Действие: НЕ Cancel Flow по умолчанию — трактуем через SYNC-3/SYNC-4 (counts-only) и приводим БД в консистентность.

### B7 — HOLD_AND_EXCHANGE_EMPTY_BUT_DB_HAS_ACTIVE

* Условие: `Instrument.mode=HOLD`, на бирже `Positions.count=0, Orders.count=0, AlgoOrders.count=0`, но в БД есть
  активные объекты.
* Источник: `Database` + `Exchange`.
* Severity: `NON_CRITICAL`.
* Действие: привести БД в консистентность (stale/close), записать аномалию в report, затем вернуть
  `Instrument.mode=OPEN`.

### B8 — DANGLING_ORDERS_OR_ALGOS_WITHOUT_POSITION

* Условие: найдены ордера/алго-ордера без позиции (или без привязки).
* Источник: `Exchange`.
* Severity: `NON_CRITICAL` (по твоему решению: “UNKNOWN → аномалия → Cancel Flow”, но не критичная).
* Действие: Cancel Exchange Flow, записать аномалию в report, после очистки вернуть `Instrument.mode=OPEN` и продолжить.

---

## 8. Cancel Exchange Flow — детально (очистка + запись аномалии в report)

> Этот flow запускается из любой проверки аномалии.

### Шаг C1. Установить `Instrument.mode = HOLD`

* Источник: `Database`.
* Цель: сразу остановить торговлю по инструменту.

### Шаг C2. Для каждой отдельной записи по инструменту из биржи

Источник: `exchange_before` (`Exchange`).

**Итерация включает:**

* позиции,
* обычные ордера,
* algo-ордера.

#### C2.1. Отменить/закрыть на бирже

* Источник: `Exchange`.
* Действия:

    * позиции → закрыть (market close),
    * обычные ордера → cancel,
    * algo-ордера → cancel.
* Идемпотентность: повторные cancel/close не должны ломать процесс.

#### C2.2. Запись была в БД?

* Источник: `Database`.
* Если **нет записи в БД**:

    * создать объект по данным из биржи,
    * добавить признак `UNKNOWN`.
* Далее для любого случая:

    * установить `status = ANOMALY`,
    * сохранить запись в БД,
    * добавить в список для записи аномалии.

### Шаг C3. Когда записи закончились → добавить аномалию в `SynchronizeExecutionEnvironmentReport.anomalies[]`

* Источник: `batch`.
* В запись об аномалии кладём:

    * `instId`,
    * `severity`,
    * `type`,
    * список объектов (позиции/ордера/algo) с флагом UNKNOWN и их идентификаторами,
    * кусок снапшота (минимально: counts и ids),
    * ошибки отмены/закрытия (если были).

### Шаг C4. Обновить и сохранить `SynchronizeExecutionEnvironmentReport` (промежуточно)

* Источник: `Database`.
* Обновить:

    * `hasAnomalies=true`,
    * `maxSeverity` (если стало хуже),
    * добавить запись в `anomalies[]`.

### Шаг C5. Если severity текущей аномалии = `NON_CRITICAL`

* Действие: **вернуть OPEN режим для инструмента** (т.е. `Instrument.mode=OPEN`).
* Источник: `Database`.
* Далее: продолжить синхронизацию (SYNC + перенос данных).

### Шаг C6. Обновить `Instrument.positionMode` по факту

* Ветка:

    * если остались консистентные открытые позиции и ордера → `positionMode=OPEN`,
    * иначе → `positionMode=NONE`.
* Источник: `Exchange` (факты) + `Database` (сохраняем).

### Шаг C7. `instrument изменился ?` → `Сохранить Instrument`

* Источник: `Database`.

---

## 9. SYNC (counts-only) — синхронизация наличия сущностей

> Здесь **НЕ** анализируем “правильность параметров” SL/TP и т.п.
> Только **наличие/отсутствие** сущностей на бирже и в БД.

### 9.1. Входные “counts”

* Биржа (Источник: `exchange_before`):

    * `Positions.count`
    * `Orders.count`
    * `AlgoOrders.count`
* БД (Источник: `database_before` или прямые запросы на момент SYNC):

    * `activePositions.count`
    * `activeOrders.count`
    * `activeAlgoOrders.count`

### 9.2. Классы SYNC (из диаграммы)

#### SYNC-1: пусто ↔ пусто

* Условие:

    * Биржа: `Positions.count=0, Orders.count=0, AlgoOrders.count=0`
    * БД: `activePositions.count=0, activeOrders.count=0, activeAlgoOrders.count=0`
* Действие:

    * ничего не делать по наличию,
    * `Instrument.positionMode=NONE`,
    * перейти к “переносу данных” (no-op).

#### SYNC-2: позиция и algo есть на бирже и в БД

* Условие:

    * Биржа: `Positions.count=1, AlgoOrders.count>0`
    * БД: активная позиция есть, algo-ордера есть.
* Действие:

    * по наличию — консистентно,
    * `Instrument.positionMode=OPEN`,
    * перейти к “переносу данных”.

#### SYNC-3: DB “позиция есть”, биржа “позиции нет”

* Условие:

    * Биржа: `Positions.count=0`
    * БД: `activePositions.count>0`
* Реальность:

    * позиция закрылась во время простоя,
    * БД не успела это зафиксировать.
* Действие:

    * закрыть/завершить позицию в БД как stale (без действий на бирже),
    * закрыть/завершить связанные ордера/algo-ордера в БД,
    * `Instrument.positionMode=NONE`,
    * перейти к “переносу данных”.

#### SYNC-4: DB активные Orders/Algo, биржа пустая

* Условие:

    * Биржа: `Positions.count=0, Orders.count=0, AlgoOrders.count=0`
    * БД: `activeOrders.count>0` и/или `activeAlgoOrders.count>0`
* Реальность:

    * “залипшие” активные записи в БД после сбоев/падений.
* Действие:

    * закрыть/пометить эти объекты в БД как stale/closed/anomaly (по доменной политике),
    * `Instrument.positionMode=NONE`,
    * перейти к “переносу данных”.

---

## 10. Перенос данных с биржи в БД (после SYNC)

Это следующий шаг после SYNC (counts-only), который делает “финальную нормализацию” БД по фактам биржи.

### 10.1. Источник данных

* `exchange_before` (positions/orders/algoOrders) → `Database`.

### 10.2. Основная идея

* Если Target NONE → обновить БД так, чтобы **все сущности стали закрыты**, а `Instrument.positionMode=NONE`.
* Если Target OPEN → обновить БД так, чтобы:

    * активная позиция в БД отражала биржевую позицию,
    * защитный SL был отражён в БД (ссылка на algo-ордер),
    * все лишние/неактуальные объекты в БД были закрыты/помечены.

> Сопровождение позиции (пересчёт “границ SL”, трейлинг-логика и т.п.) — отдельный механизм, **не часть** этого
> процесса.

---

## 11. Завершение и продолжение торговли

После того как по инструменту:

* аномалии проверены/обработаны,
* SYNC (counts-only) завершён,
* данные биржи перенесены в БД,

процесс:

1. устанавливает `Instrument.status = ACTIVE` (Источник: `Database`);
2. выставляет `Instrument.positionMode` (`OPEN` или `NONE`) по факту биржи;
3. если была некритичная аномалия и всё очищено → возвращает `Instrument.mode = OPEN`;
4. переходит к следующему инструменту.

Глобально в конце:

* `Exchange.status = ACTIVE` (Источник: `Database`);
* снимается глобальный HOLD.

---

## 12. Требования к идемпотентности и перезапуску

1. Процесс может быть запущен повторно в любой момент:

* все cancel/close операции на бирже должны быть “безопасны при повторе”.

2. В БД не должно появляться дублей:

* входные ключи идентификации: `order.internalId`, `algoOrder.internalId`, `exchangeOrderId` (когда он известен),
* UNKNOWN-объекты создаются **только если** записи реально не было в БД.

3. Важные гарантии:

* если случилась CRITICAL аномалия, инструмент остаётся в HOLD до ручного разбора,
* если NON_CRITICAL — после очистки возвращаем OPEN.

---

## 13. Рекомендации по логированию и наблюдаемости

Минимум логов на один bucket:

* `instId`, `Instrument.mode`, `Instrument.status` (до/после)
* `Exchange counts`: `Positions.count`, `Orders.count`, `AlgoOrders.count`
* `DB counts`: `activePositions`, `activeOrders`, `activeAlgoOrders`
* какая проверка аномалий сработала (B1..B8)
* `SynchronizeExecutionEnvironmentReport.id`, `hasAnomalies`, `maxSeverity`, а для каждой аномалии: `severity`, `type`
* сколько объектов отменили/закрыли на бирже, сколько создали UNKNOWN в БД

---

## 14. Примечание по расширению (на будущее)

Позже можно добавить отдельную ветку расследования:

* “fills с момента lastReconcileAt”,
* восстановление точной причинности (что было исполнено, что отменено),
* и более точная логика “order есть в БД, но нет на бирже”.

Сейчас это намеренно не входит в процесс.

---

## 15. Job очистки отчётов (Cleanup job)

Ты теперь сохраняешь отчёт **на каждый запуск**, поэтому нужен отдельный job для чистки.

### 15.1. Что удаляем

Удаляем записи `SynchronizeExecutionEnvironmentReport`, которые удовлетворяют всем условиям:

1. `hasAnomalies = false`
2. `finishedAt < now() - retentionTtl`

Где `retentionTtl` задаётся в `Config` (например, `synchronizeExecutionEnvironment.reports.retentionDays`).

### 15.2. Что НЕ удаляем

* Отчёты, где `hasAnomalies = true` (любой severity).
* Отчёты, которые ещё “свежие” (младше retentionTtl).

### 15.3. Реализация джоба (рекомендация)

* Отдельный scheduled job, например `CleanupSynchronizeExecutionEnvironmentReportsJob`.
* Шедул по CRON (Config), например раз в сутки ночью.
* Логировать количество удалённых отчётов и фактический cutoff timestamp.
