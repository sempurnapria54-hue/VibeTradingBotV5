# Snapshot v41

**Дата:** 2026-06-09.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (тема **шаг 3 — концепт закрыт + CODE
написан**: концепт-петля сошлась `DOCS_CHECK_6`→`GAPS_CLOSE_5`→`DOCS_CHECK_7`
чисто; реализация шага 3 написана и прошла адверсариальное ревью; торговая
валидация ТВ1-ТВ5 закрыта; батч торговых решений D1-D3 + fork-A реализован).
Плановое завершение темы. Новый чат поднимает **финал шага 3** (аппрув CODE →
`SYNC_DOCS_FROM_CODE` → пост-хок концепт-гейт → `DONE`) без пересказа.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-2 — `DONE`; шаги 4-11 — `HOLD`. Ветка —
`claude-audit`.

**Шаг 3 — нюанс статуса.** Роадмап-строка `phase-1.md` **всё ещё
`DOCS_CHECK_7`** (концепт-гейт чист — «перевод за пользователем» по отчёту
`DOCS_CHECK_7`). **CODE написан в этой сессии** по прямому указанию
пользователя, но строка статуса на `CODE` **не переводилась** (перевод
статуса — оркестрация чата/пользователя; CC её не делал). Т. е. код в
working tree (staged) опережает статус-строку. Прогрессия
`CODE → SYNC_DOCS_FROM_CODE → DONE` — в хвосте (ниже).

Всё staged, **не коммичено** (CC не коммитит). Кодовая база приведена в
соответствие: forward-debt Java step-2 (старая скоринговая модель фазы,
`structureType`, `MarketPhaseParams`) **ресинкнут** на этом CODE.

Путь к точке: вход — snapshot v40 (тема `GAPS_CLOSE_4` закрыта, шаг 3 на
`CODE`, новый чат стартовал с `DOCS_CHECK_6`). Эта сессия: добила концепт-
петлю (`DOCS_CHECK_6`/`GAPS_CLOSE_5`/`DOCS_CHECK_7`), написала CODE,
прогнала ревью + торговую валидацию, реализовала батч решений.

## Что закрыто в этой сессии (шаг 3)

1. **Концепт-петля сошлась.** `DOCS_CHECK_6` нашёл Н11/Н12 →
   `GAPS_CLOSE_5` закрыл (**Н11** — `TREND_CHANGED` убран из whitelist
   правил фазы: темпоральное несовместимо со stateless-классификатором;
   **Н12** — `confirmedAt` фазы = консервативный `max` по гейт-операндам
   сработавшей клаузы) → `DOCS_CHECK_7` **чисто**, гейт CODE пройден
   (правило «гейт CODE = чистый `DOCS_CHECK`»). Отчёты —
   `progress/phase-1-step-3-docs-check-6.md` / `-7.md`.
2. **Реализация шага 3 (CODE).** Полный слой производных данных (детали —
   §Staged ниже): модели (`IndicatorValue` abstract + 8 наследников,
   `MarketStructure`+`MarketPriceLevel`+`MarketBreakoutEvent`,
   `MarketPhase`); реестры конфигов (`indicator_configs`/
   `market_structure_configs`, ключевание по идентичности); persistence
   (SINGLE_TABLE индикаторы, структура+уровни bidirectional, фаза по
   контейнеру) + repos + data services + MapStruct + Flyway **V3**;
   8 калькуляторов (EMA/RSI/ATR/MACD/Stochastic/Bollinger/OBV/ER);
   `MarketStructureResolver` (свинги/уровни/тип/пробой);
   `MarketPhaseClassifier` + `StrategyConditionEvaluator` (грамматика в
   контексте фазы) + `ConditionEvaluationContext`; read-сервисы +
   `MarketDataExpirationChecker` (read-side `isFresh`); jobs
   (`IndicatorJob`/`MarketStructureJob`/`MarketPhaseJob`) + фасады +
   триггеры. Сборка чистая (JDK 25 + SB4, без deprecation).
3. **Адверсариальное ревью (conventions/perf/disaster).** Блокеров нет.
   Фиксы: `saveNewValues`→`saveValues` (правило «нет `New`»); убраны
   неиспользуемые параметры резолвера; **NPE-фикс** —
   `MarketStructureResolver` разыменовывал nullable params → guard
   `hasRequiredParams` → консервативный `UNKNOWN`. Конвенции записаны в
   `codestyle.md` (джобы: выключатель+CRON+concurrency-guard; фасады в
   `domain.jobs.facade`; `@UtilityClass` в `util`; большие выборки —
   окном/пагинацией).
4. **Торговая валидация ТВ1-ТВ5** (фокус `trading-review`, грунт
   дистиллят). **ТВ5** (`referencePoint` свежести) — **Н6-консистентен**
   (структура `windowEndAt`, индикатор/фаза `candleTimestamp`), фикса
   **нет**; open-vs-close — не торговый вопрос, docs-кларификация. **ТВ4**
   (окно 1500) — покрывает warmup каталога (глубочайший канонический ~256
   → warmup ~512, запас 3×), конфигурируем; рекомендация — create-guard
   `warmup ≤ окно`. **ТВ1-ТВ3** → батч решений (ниже).
5. **Батч решений D1-D3 + fork-A** реализован (CODE, staged) +
   форвард-вопрос **STRUCT-Q1**.

## Рациональ решений D1-D3 + fork-A (the WHY — для decision-record на SYNC)

- **D1 / адресный компонент индикатора-операнда.** Абсолютный compare
  MACD-линии с `CONSTANT` масштаб-зависим межинструментно (BTC vs ETH) —
  тот же класс нестабильности, что OBV. Адресный компонент
  (`indicatorComponent`: MACD line/signal/histogram; Stochastic %K/%D;
  Bollinger 5 компонентов) даёт автору сравнивать осмысленную часть
  (зеркало OBV-принципа). Выбран **полный** адресный компонент (а не
  минимальный запрет MACD-абсолюта), свёрнут в шаг 3 ради консистентности.
  Контракт: компонент обязателен для многокомпонентных, запрещён для
  одно-компонентных, проверка совместимости с типом. **Концепт-инкремент
  — вошёл через CODE → требует пост-хок концепт-гейта** (новое правило).
- **D2 / ER-порог тренда.** Канон-якоря у **бинарной** отсечки нет (корпус
  использует ER **континуально**, KAMA-style [Kaufman гл. 1/17]); бинарный
  порог — допустимое инженерное упрощение для **дискретного**
  классификатора. Порог вынесен в `MarketStructureParams`
  (`trendEfficiencyThreshold`), значение — на бэктест фазы 2; провизорный
  дефолт 0.30 (числом в канон не зашит). Континуальный редизайн **отвергнут**
  (не оправдан для дискретного выхода).
- **D3 / толеранс кластеризации.** Фикс-% непереносим (волатильность-
  зависим); **ATR-относительный** (`levelToleranceAtrMultiplier`·ATR) сам
  адаптируется и задействует каталожный ATR. `k` — param, значение — фаза 2;
  fallback на долю цены, когда ATR не объявлен.
- **fork-A / резолвер ест каталожный ER+ATR.** Подтянут ради консистентности
  с уже зафиксированным каноном (резолвер потребляет готовые каталожные
  индикаторы, не пересчитывает); закрывает запаркованное расхождение
  (низкий риск, **не новый концепт**). Следствие: разрешение структуры
  зависит от готовности ER/ATR-входа → **объявлен, но не готов/устарел →
  консервативный `UNKNOWN`** (не proxy, не падение). `efficiencyRatioKey`/
  `atrKey` — soft-ссылки на индикаторы того же контейнера; ER-прокси только
  когда не объявлено.

## Хвост работы (для нового чата, по порядку)

1. **Рантайм на dev-БД** (пользователь, IDEA) — на шаге тестов фазы:
   Flyway V3 + подъём контекста (Hibernate валидирует SINGLE_TABLE/
   bidirectional/FK) + тик джоб. CC прогнать не мог (нет Postgres; старт
   контекста дёргает OKX).
2. **Аппрув CODE** (оркестрация) + перевод статус-строки на `CODE`.
3. **`SYNC_DOCS_FROM_CODE`** (доки←код, фокус `divergence` + реконсиляция).
   Список расхождений CC накопил: operand `indicatorComponent`;
   `MarketStructureParams` +2 поля (`trendEfficiencyThreshold`/
   `levelToleranceAtrMultiplier`); структурная настройка +2 ключа
   (`efficiencyRatioKey`/`atrKey`); вернувшийся ATR-вход резолвера (теперь
   **используется**); поле `breakoutEvent` на `MarketStructure`; реестры
   конфигов (`indicator_configs`/`market_structure_configs`,
   `params_canonical`); скалярная проекция → адресный компонент;
   минимальная семантика `VOLUME_FILTER_PASSED`/`CROSSOVER` в фазе;
   `JobExecutionGuard`/`candle-window-bars`; **краевой случай** `config_id`
   при разных ER/ATR-ключах на одинаковых `timeframe+params`.
4. **Decision-record** (docs) — зафиксировать рациональ D1-D3 + fork-A
   (the WHY выше), чтобы доки получили замысел, а не только поля.
5. **Пост-хок `concept-review`** по синканным докам (новое правило §6a
   `roadmap-step-execution.md`) — для **D1** (концепт-инкремент через CODE)
   — **до `DONE`**.
6. **Статус `phase-1.md`:** провести шаг 3 `CODE → SYNC_DOCS_FROM_CODE →
   DONE` по процессу.
7. **Форвард:** STRUCT-Q1 (калибровка ER-порога/k на бэктесте фазы 2);
   богаче-авторинг (%K/%D-кроссовер и пр.); deal-cluster (`CROSSOVER`/
   `VOLUME_FILTER_PASSED` полная семантика, `DealContext`,
   `MarketDataExpirationChecker.checkForEntry`/`checkForStep`) — шаги 4-7;
   ТР1-крипто (IND-Q1) — фаза 4; ТВ4-guard (`warmup ≤ окно`).

## Правила, добавленные в эту сессию (хэндоффы прогнаны пользователем)

- **Гейт CODE = чистый `DOCS_CHECK`** (`roadmap-step-execution.md`).
- **Минимальный промпт `DOCS_CHECK`** (`chat-project-instructions.md` →
  Settings).
- **Пост-хок концепт-гейт для CODE-инкрементов**
  (`roadmap-step-execution.md` §6a): концепт/контракт-инкремент, въехавший
  через CODE, минует концепт-гейт (`DOCS_CHECK` — до CODE, на
  `SYNC_DOCS_FROM_CODE` активен только `divergence`); remedy — `concept-review`
  по синканным докам до `DONE`.

## Активные принципы (для подхвата)

- **Резолвер ест каталожные индикаторы:** `MarketStructureResolver`
  потребляет готовые ER (тренд/шум) и ATR (толеранс, D3), не пересчитывает;
  прокси ER — только когда не объявлено; вход не готов → `UNKNOWN`.
- **Масштаб-зависимые операнды — относительно/адресно:** OBV — только
  относительные формы; MACD-линия — через адресный компонент (D1), не
  голый абсолют (оба — ценовые/кумулятивные единицы, межинструментно
  непереносимы).
- **Численные пороги структуры/фазы — хвост пользователя**, калибровка на
  **бэктест-гейте фазы 2** (STRUCT-Q1); корпус даёт подход, не значения; в
  коде — провизорные дефолты, «value: бэктест» в доках.
- **`referencePoint` свежести** (Н6): структура `windowEndAt`,
  индикатор/фаза `candleTimestamp`; `confirmedAt` — гейт без look-ahead, не
  точка отсчёта. `expiredAt` — производная на чтение.
- **Джобы:** выключатель + CRON в конфиге + `JobExecutionGuard`
  (anti-overlap); фасады в `domain.jobs.facade`; большие выборки —
  ограниченным окном (`candle-window-bars`)/пагинацией (`codestyle.md`).
- **Концепт-инкремент через CODE → пост-хок концепт-гейт** до `DONE`.

## Режим работы

**Содержательное закрытие** (не отладка пайплайна). Финал шага 3 —
оркестрация под-шагов CODE→SYNC→DONE. Развилки со штатным владельцем —
через CC по ступеням автономии (`question-delegation.md`). Численные
торговые хвосты — пользователю/бэктесту, не инженерный дефолт.

## Следующее действие (явно)

Новый чат поднимает **финал шага 3**: аппрув CODE → перевод статуса на
`CODE` → `SYNC_DOCS_FROM_CODE` (divergence-список выше) + **decision-record**
рационали D1-D3+fork-A → **пост-хок `concept-review`** для D1 → `DONE`.
Рантайм dev-БД — на шаге тестов (не блокирует аппрув). Коммит — за
пользователем.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v41`**
  (заменяет v40 в префлайте — обновить PK после коммита). **Затронутые
  PK-файлы** (обновить после коммита, если в PK): `codestyle.md`,
  `roadmap-step-execution.md`, `open-questions.md` (+STRUCT-Q1),
  `chat-project-instructions.md` (минимальный `DOCS_CHECK`-промпт —
  отдельным хэндоффом). `CLAUDE.md`/`structure.md`/`naming.md`/
  `place-knowledge.md` — не менялись. `docs/`-модели в этой сессии **не
  правились** (это был CODE по уже утверждённому канону) — доки синкаются
  на `SYNC_DOCS_FROM_CODE`.
- **Custom Instructions (`chat-project-instructions.md`):** правился
  отдельным хэндоффом (минимальный `DOCS_CHECK`-промпт) — **вставить в
  Settings → Custom Instructions** после коммита.
- **Staged к коммиту (эта сессия):**
  - **CODE шага 3** (src/, ~72 новых + ~37 правок): модели производных
    данных; реестры конфигов; persistence (entities/repos/data services/
    мапперы) + Flyway `V3__create_derived_market_data_tables.sql`;
    8 калькуляторов + `IndicatorMath` (util); `MarketStructureResolver`;
    `MarketPhaseClassifier`+`StrategyConditionEvaluator`+
    `ConditionEvaluationContext`; read-сервисы + `MarketDataExpirationChecker`;
    jobs + фасады (`domain.jobs.facade`) + `JobExecutionGuard` +
    `MarketDataJobsProperties`; правки step-2 (drift-ресинк:
    `MarketPhaseParams`→`phaseRules`/`StrategyMarketPhaseRule`,
    `structureType` убран, `EFFICIENCY_RATIO`/`MARKET_STRUCTURE_IS` enum,
    валидатор, `StrategyMapper`/`StrategyJsonConverter`); батч D1-D3+forkA
    (`IndicatorComponent`/`IndicatorComponents`, operand-поле,
    `MarketStructureParams`/структурная настройка +поля, резолвер/job);
    пример `trend-following-ema.json`; `application.yaml`.
  - **Правила/процесс:** `codestyle.md` (конвенции джоб/util/выборок),
    `roadmap-step-execution.md` (гейт CODE = чистый `DOCS_CHECK` +
    пост-хок концепт-гейт §6a), `open-questions.md` (STRUCT-Q1).
  - **Пред-существующее (не эта сессия):** `.claude-archive/2026-05-21/...`
    — 5 файлов в staged с прошлой темы (не трогались, к батчу не относятся).

## Незакрытое по гигиене (пользователю)

- **Закоммитить staged** (батч CODE шага 3 + правки правил/процесса).
- **Обновить PK** по изменённым PK-файлам (`codestyle.md`,
  `roadmap-step-execution.md`, `open-questions.md`,
  `chat-project-instructions.md`) + снапшот v41.
- **Вставить `chat-project-instructions.md`** в Settings → Custom
  Instructions (минимальный `DOCS_CHECK`-промпт).
