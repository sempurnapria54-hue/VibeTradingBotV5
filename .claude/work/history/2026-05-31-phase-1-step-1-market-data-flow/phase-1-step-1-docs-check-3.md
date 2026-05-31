# DOCS_CHECK_3 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы в третьей итерации проверки целостности концепции
доков под шаг 1 и какие пробелы найдены (gap-отчёт для возможного
`GAPS_CLOSE_3`).

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных (коннект к
  OKX, инструменты, цены/свечи, свежесть)».
- Под-шаг: `DOCS_CHECK_3` (третья итерация),
  `.claude/processes/roadmap-step-execution.md`; стадийный обход
  `concept-review` (`.claude/skills/concept-review.md`), роль
  `reviewer`.
- **Проверка — только по докам**: doc↔doc несогласованности,
  name-level пробелы, неотвеченные/отложенные вопросы. Код не
  читался, с кодом не сверялось.
- Вход: `snapshot-v18`, `phase-1-step-1-gaps-close-2.md` (закрыты
  Н1, Н3, N1, N2, Q1; Н2 осознанно не трогаем; новые доки
  `candle-loading.md`, `lifecycles/Instrument.md`,
  `mapping/Instrument.md`).
- Особый фокус (что менялось в `GAPS_CLOSE_2`): связки
  `Instrument` ↔ lifecycle ↔ mapping ↔ `CandleGroup`;
  `count`/`actualFirst`/`actualLast` + density по всей свечной
  подсистеме; разделение `candle-loading` vs
  `market-data-calculation`.

## Охват

### Проверено (доки)

- **Модели (domain):** `domain/core/Instrument.md`;
  `domain/other/Candle.md`, `CandleGroup.md`,
  `InstrumentExternalRules.md`.
- **Lifecycle:** `lifecycles/Instrument.md`, `lifecycles/CandleGroup.md`.
- **Mapping:** `mapping/Instrument.md`, `mapping/Candle.md`,
  `mapping/InstrumentExternalRules.md`.
- **External-snapshot:** `models/externalSnapshot/README.md`.
- **Инвентарь источника (OKX):** `OkxInstrumentResponse.md`.
- **Компоненты:** `CandleJob.md`, `InstrumentExternalRulesSyncJob.md`.
- **Процессы:** `processes/candle-loading.md` (новый),
  `processes/market-data-calculation.md`.
- **Правила:** `raw-exchange-dto-boundary.md`.
- **Контракт OKX:** `contracts/candle.md`.
- **Open-questions:** проход по всем 14.
- **Кросс-скан доков:** `coverage*` (стало `actual*`) —
  не осталось ни одного вхождения; ссылки `candle-loading` /
  `market-data-calculation` — переведены корректно.

### Вне охвата (помечено, не проверялось)

- Потребители рыночных данных поздних шагов: индикаторы (шаг 3),
  структура/фаза рынка (шаги 4-8), стратегия/сделки/риск/FSM
  (шаги 2,4-7). Цепочка вычислений в `market-data-calculation.md`
  (`IndicatorJob`/`MarketStructureJob`/`MarketPhaseJob`,
  активация стратегии, `deal-management`) — проверена только на
  «не вносит несогласованность в загрузочную часть шага 1».
- `InstrumentExternalRules` (модель/mapping/sync-job) — отложена за
  пределы шага 1; проверена только на консистентность деферрала и
  как контр-источник по составу снапшота инструмента.

## Стадия остановки

Обход **прошёл все стадии** (на гейте не остановлен).

- **Стадия 0 (гейтящие технические / скоуп) — чиста.** REST-first
  закреплён (OKX-Q4 для шага 1 разблокирован); владелец
  оркестрации онбординга/загрузки осознанно не материализуется
  (ORCH-Q1) — по решению не гейтит; снапшот-концепция rules
  отложена (INSTR-Q1) — по решению не гейтит. Гейтящих вопросов нет.
- **Стадия 1 (процессы / lifecycles) — чиста.** См. фокус 1 и 3.
- **Стадия 2 (компоненты + модели) — две несогласованности** в
  граничной модели снапшота инструмента (Н(3-1), Н(3-2)).

## Проверка фокусов `GAPS_CLOSE_2`

### Фокус 1 — `Instrument` ↔ lifecycle ↔ mapping ↔ `CandleGroup`

Связки **согласованы**, с двумя оговорками по составу снапшота
(ниже Н(3-1), Н(3-2)):

- Онбординг-путь `CREATED → SYNC → CANDLES_LOADING → ACTIVE`
  изложен одинаково в `Instrument.md` (енум + ссылка),
  `lifecycles/Instrument.md` (статусы/переходы/триггеры) и
  `candle-loading.md` (§«Онбординг инструмента»). Расхождений нет.
- Координация `Instrument.Status` ↔ `CandleGroup.Status`
  непротиворечива: инструмент `ACTIVE` ⟺ **все** его `CandleGroup`
  в `ACTIVE`; пока хотя бы одна в рабочем статусе загрузки —
  инструмент в `CANDLES_LOADING`. Утверждение совпадает в
  `lifecycles/Instrument.md` и `candle-loading.md`.
- Плановый горизонт `plannedCandleStartDate` — на `Instrument`
  (общий для всех ТФ), фактические границы — на `CandleGroup`
  (per-ТФ); 1:many без промежуточного объекта. Согласовано в
  `Instrument.md`, `CandleGroup.md`, обоих lifecycles, `CandleJob.md`,
  `candle-loading.md`, `contracts/candle.md` (стоп пагинации →
  `plannedCandleStartDate`).
- Идентичность на границе snapshot↔domain (шаг 1 = только
  идентичность) — `mapping/Instrument.md` согласован с
  `Instrument.md` (справочные поля транзиентны, персистентного дома
  нет, INSTR-Q1). **Исключение — состав снапшота:** Н(3-1), Н(3-2).

### Фокус 2 — `count` / `actualFirst` / `actualLast` + density

**Полностью согласовано.** Поля `actualFirstUtcMillis`/
`actualLastUtcMillis`/`count` и density-инвариант
`count == (actualLast − actualFirst) / step + 1` изложены
одинаково в `CandleGroup.md` (модель, §«Целостность по count»),
`lifecycles/CandleGroup.md` (§«Политика загрузки и целостности»),
`CandleJob.md`, `candle-loading.md`. Везде совпадает: плотность
меряется на `[actualFirst, actualLast]`; дотягивание нижней границы
до планового горизонта — забота `BACKFILL`, не density; на старте
`count` реконсилируется `COUNT(*)`; идемпотентность по
`(candleGroupId, openTimestamp)`; дефицит → бинарный поиск по count
→ `REPAIR`; постоянная дыра после исчерпания попыток → `ERROR`.
Старого нейминга `coverage*` в докаx не осталось. Числа/тайминги
осознанно отнесены на `CODE`.

### Фокус 3 — `candle-loading` vs `market-data-calculation`

**Разделение чистое, дублей нет, владельцы непротиворечивы.**

- `candle-loading.md` = добыча + целостность: оркестрация
  `CandleJob`, цикл `CandleGroup`, density-политика, координация
  онбординга инструмента. `market-data-calculation.md` = вычисление
  (индикаторы/структура/фаза) поверх загруженных свечей; явно
  «свечи не добывает», берёт из `candle-loading`.
- Кросс-ссылки переведены: `market-data-calculation.md`
  делегирует загрузку в `candle-loading.md`/`CandleJob.md`, не
  претендует на оркестрацию загрузки. `CandleJob.md`,
  `CandleGroup.md`/lifecycle, `lifecycles/Instrument.md`,
  `mapping/Candle.md` указывают на `candle-loading` как процесс.
- Двойного владения нет: оркестрацию свечей держит только
  `candle-loading` (через `CandleJob`); готовность инструмента —
  там же; готовность данных для активации **стратегии** — отдельный
  поздний слой в `market-data-calculation` (не дублирует
  онбординг инструмента).
- `InstrumentExternalRulesSyncJob` убран из активной оркестрации
  `market-data-calculation` и помечен отложенным (backlog п.9)
  согласованно в трёх местах (`market-data-calculation.md`,
  `InstrumentExternalRulesSyncJob.md`, `InstrumentExternalRules.md`).

## Пробелы по типам

### 1. Несогласованности между доками

**Н(3-1). `mapping/Instrument.md` приписывает `lever`/`state`
граничному `InstrumentExternalSnapshot`; два других дока относят их
к rules-снапшоту.**

`mapping/Instrument.md` (§«Что персистится в шаге 1» и §OKX
«Справочные поля OKX в шаге 1») перечисляет среди полей, которые
«приходят в `InstrumentExternalSnapshot`», в т.ч. `lever` и `state`.
Но:

- `OkxInstrumentResponse.md` (инвентарь источника) держит `lever`/
  `state` в разделе «поля, которые **НЕ входят** в этот DTO» и
  говорит, что они «потребляются отдельной моделью
  `InstrumentExternalRules`»; DTO `InstrumentResponse` →
  `InstrumentExternalSnapshot` их не несёт;
- `mapping/InstrumentExternalRules.md` явно маппит `lever` →
  `externalMaxLeverage` и `state` → `Status` через
  `InstrumentExternalRulesExternalSnapshot` (rules-снапшот), а в
  §«Не маппимые поля OKX» относит к `InstrumentExternalSnapshot`
  только `baseCcy`/`quoteCcy`/`settleCcy`.

Итог: три дока расходятся в составе `InstrumentExternalSnapshot` —
`lever`/`state` приписаны и идентичному снапшоту (шаг 1), и
rules-снапшоту (поздние шаги). `base/quote/settle` + sizes
(`lotSz`/`minSz`/`ctVal`/`ctMult`/`tickSz`) в снапшоте — согласованы
во всех трёх; расходятся только `lever`/`state`. Тип:
несогласованность. Источник дрейфа — `mapping/Instrument.md`
(переписан в `GAPS_CLOSE_2`): он один приписал `lever`/`state`
идентичному снапшоту. **Не блокер шага 1** (`lever`/`state` в
шаге 1 в домен не персистятся ни через какой снапшот), но влияет на
состав DTO `InstrumentExternalSnapshot`, который шаг 1 конструирует.
Фикс чистый и без содержательного решения: убрать `lever`/`state`
из перечней снапшота в `mapping/Instrument.md` (они принадлежат
rules-снапшоту; два дока уже согласны). Связь с INSTR-Q1 — только
по соседству (INSTR-Q1 про персистентный дом и ренейм rules, не про
состав идентичного снапшота).

### 2. Name-level без структуры (где структура нужна шагу)

**Н(3-2). Сквозное правило DTO-границы не перечисляет
`InstrumentExternalSnapshot` среди граничных снапшотов.**

`raw-exchange-dto-boundary.md` (§«Граничные `*ExternalSnapshot`»)
перечисляет: `InstrumentExternalRulesExternalSnapshot`,
`MarketPriceDataExternalSnapshot`, `BalanceContainerExternalSnapshot`,
`CandleExternalSnapshot`, order/algo/position. В списке есть снапшот
**отложенной** rules-модели, но **нет** активного для шага 1
`InstrumentExternalSnapshot` (граница онбординга идентичности). При
этом `mapping/Instrument.md` ссылается на это правило как на
авторитет: «`InstrumentExternalSnapshot` — единственное, что
выходит за `ClientService`/adapter; см.
`raw-exchange-dto-boundary.md`», — а перейдя по ссылке, снапшот в
перечне не находим. В `GAPS_CLOSE_2` правило правилось (добавлен
`CandleExternalSnapshot` по Н3), но `InstrumentExternalSnapshot` не
был добавлен. Тип: name-level / несогласованность (перечень
граничных снапшотов неполон относительно реально заданных шагом 1).
**Не блокер шага 1** (принцип правила и состав снапшота заданы в
`mapping/Instrument.md`), фикс чистый: добавить
`InstrumentExternalSnapshot` в перечень. Решения не требует.

### 3. Неотвеченные / отложенные вопросы

Новых не выявлено. Ранее отложенные по решению — INSTR-Q1, ORCH-Q1,
Н2 (см. ниже) — на шаг 1 не влияют как блокеры.

## Блокирующие открытые вопросы (проход по `open-questions.md`, 14)

Гейтящих нет. По релевантности шагу 1:

- **INSTR-Q1** — открыт по решению `GAPS_CLOSE_2` (снапшот-концепция
  на `InstrumentExternalRules`, возможный ренейм); шаг 1 не
  блокирует (персистентный дом справочных полей в шаге 1 осознанно
  отсутствует). Не всплыл как блокер.
- **ORCH-Q1** — открыт по решению (владелец оркестрации онбординга/
  загрузки); до решения владелец не материализуется, семантика
  переходов и координации зафиксирована в lifecycle-доках и
  `candle-loading.md`. Шаг 1 не блокирует.
- **OKX-Q4** (WS-каналы) — разблокирован для шага 1 (REST-first);
  не блокер.
- **TIME-Q1** — сужен (canon enum `TimeFrame` в `CandleGroup.md`);
  не блокер. Хвост (свёртка раздела в `Strategy.md`) — шаг 2.
- Остальные 10 (DEAL-Q1/2/3, PROC-Q1, RISK-Q1, ENUM-Q1, CMD-Q1,
  OKX-Q1/2/3) — шаги 2-8, шаг 1 не блокируют.

## Н2 (тикер `instType`) — статус

Осознанно открыт по решению `GAPS_CLOSE_2` (не блокер; тикер-фетч
отложен в зону FSM/поздних шагов). В проверенных доках шага 1
(онбординг инструмента + загрузка свечей) **как блокер не всплыл**:
маппинг тикера / `MarketPriceData` в загрузочную часть шага 1 не
входит. Подтверждено — не эскалируем.

## Эскалации

**Нет.** Обе несогласованности (Н(3-1), Н(3-2)) — чистые
doc-alignment фиксы без содержательного решения: другие доки уже
дают согласованную картину, `mapping/Instrument.md` и
`raw-exchange-dto-boundary.md` приводятся к ней. В чат выносить
нечего.

## Сводка

- **Несогласованности:** 2 — Н(3-1) `lever`/`state` приписаны
  идентичному `InstrumentExternalSnapshot` в `mapping/Instrument.md`
  вопреки `OkxInstrumentResponse.md` / `mapping/InstrumentExternalRules.md`;
  Н(3-2) перечень граничных снапшотов в `raw-exchange-dto-boundary.md`
  не содержит `InstrumentExternalSnapshot`. Обе низкой важности,
  **не блокеры** шага 1, оба фикса чистые.
- **Name-level:** 0 новых (Н(3-2) учтён как несогласованность/
  name-level перечня).
- **Неотвеченные:** 0 новых.
- **Эскалаций:** 0.
- **Фокусы `GAPS_CLOSE_2`:** фокус 2 (count/actual/density) и
  фокус 3 (candle-loading vs market-data-calculation) — **чисто**;
  фокус 1 (связки инструмента) — чисто, кроме состава снапшота
  (Н(3-1), Н(3-2)).
- **Стадия остановки:** прошёл все стадии (0-1 чисты, на стадии 2 —
  две несогласованности в граничной модели снапшота инструмента).
- **Итог: почти чисто.** Остались два мелких doc-alignment фикса в
  одной области (граничный `InstrumentExternalSnapshot`). Рекомендую
  лёгкий `GAPS_CLOSE_3` (две правки без обсуждения), затем —
  быстрая подтверждающая проверка этой области и переход к `CODE`.

## Размещение знания — не здесь

`concept-review` помечает пробелы, но не закрывает их. Приведение
`mapping/Instrument.md` (убрать `lever`/`state` из состава
`InstrumentExternalSnapshot`) и `raw-exchange-dto-boundary.md`
(добавить `InstrumentExternalSnapshot` в перечень граничных) — на
`GAPS_CLOSE_3` штатным потоком.
