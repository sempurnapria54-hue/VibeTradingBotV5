# Snapshot v40

**Дата:** 2026-06-09.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (тема **`GAPS_CLOSE_4` закрыта целиком**:
Н6/Н8/Н10 + торговый раунд ТР1/ТР2 + переписанная дисциплина грунтовки
`trading-specialist` + Н3-структура — всё в каноне; шаг 3 переведён на
`CODE`). Плановое завершение темы. Новый чат стартует с PK-префлайта и
**`DOCS_CHECK_6`** (концепт-ревью сильно переписанных доков перед `CODE`).

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-2 — `DONE`; **шаг 3 — `GAPS_CLOSE_4`
закрыт, статус переведён на `CODE`** (гейт концепции расчищен); шаги 4-11 —
`HOLD`. Ветка — `claude-audit`. Все правки сессии — в `docs/` и `.claude/`
(staged, не коммичено); **кодовая база отстаёт** — Java step-2 forward-debt
(старая скоринговая модель фазы + `StrategyMarketStructureSetting.structureType`
+ `MarketStructureParams` 7 полей); ресинк — на `CODE` шага 3.

Путь к точке: вход — snapshot v39 (тема «условный редизайн фазы» уже была
закрыта; остаток `GAPS_CLOSE_4` — Н3-структура, Н6, ТР1/ТР2, трекинг). Эта
сессия добила остаток целиком (ниже).

## Закрыто в каноне (тема `GAPS_CLOSE_4` целиком)

1. **Н6/Н8/Н10 → канон.** Retention (Н8 — результаты не чистим до
   потребителя истории + якорь пересмотра); ключевание `MarketPhase`
   контейнером (Н10 — осознанное исключение из шаринга-по-идентичности);
   точка отсчёта свежести (Н6 — структура от `windowEndAt`, фаза/индикатор
   от `candleTimestamp`, `confirmedAt` — гейт без look-ahead, не точка
   отсчёта); `expiredAt` — **производная на чтение**, не колонка (развилка
   D разошлась → сошлась на «считать на чтение» с якорем пересмотра
   «тяжёлые запросы»). Канон: `market-data-freshness.md`,
   `market-data-retention.md` (new), `market-data-result-identity-keying.md`,
   `MarketDataExpirationChecker.md`, `IndicatorValue.md`/`MarketStructure.md`/
   `MarketPhase.md`.
2. **Торговый раунд ТР1/ТР2** — decision `volume-condition-semantics.md`
   (new). **ТР2:** OBV-операнд — только относительные формы; абсолютный
   compare OBV с `CONSTANT` исключён (оконный OBV не спасает — две оси:
   старт/окно и масштаб/нормировка); стабильный абсолютный порог по объёму
   — отдельный **нормированный** операнд по потребности (не заведён).
   **ТР1 ч.1:** объём — подтверждающий фильтр, не единственное основание
   `ENTRY`. **ТР1 крипто:** ∅ в корпусе (проверены сырые книги) →
   эскалация, **запаркована на фазу 4** (IND-Q1 — книжная часть закрыта,
   крипто открыта). Грунт книжный [Kaufman гл. 12, Harris гл. 12];
   самодистилляция в `strategy-patterns.md` §4, `microstructure.md` §9.
3. **Дисциплина грунтовки `trading-specialist` — книжный контур.**
   Лестница: дистиллят → **сырые книги + самодистилляция** → эскалация за
   источником → контур дообучения (подбор книги в чате → докачка PDF →
   дистилляция) → интернет крайним резервом. Новый процесс
   `.claude/processes/trading-library-distillation.md` (выделен из лога
   разовой задачи; лог архивирован в
   `.claude/work/history/2026-06-05-trading-library-distillation-run.md`);
   `trading-specialist.md` переписан под лестницу.
4. **Н3-структура → канон.** Семантика классификации (свинг-пивоты →
   кластеризация по `minTouches` → диапазон/тренд → пробой → `UNKNOWN` →
   `confirmedAt`/валидный уровень) — `MarketStructure.md` §Семантика
   классификации; новая компонент-дока `MarketStructureResolver.md` (имя
   **Resolver**, не Analyzer — разведено с будущим «аналитиком» фазы 4):
   потребляет готовый каталожный ER (единый источник), гибрид-fallback,
   stateless; тонкий `MarketStructureJob`. `MARKET_STRUCTURE_IS` оставлен
   именованным `ruleType` (вердикт sugar-vs-алиас). Развилки приняты:
   **S1** гибрид-fallback, **S2** `breakoutEvent` явным предвычисленным
   событием (`RANGE_BREAKOUT_CONFIRMED` читает готовым), **S3**
   `structureType` удалён с настройки (тип выводит резолвер; идентичность
   конфигурации = `timeframe`+`params`). Остаток `GAPS_CLOSE_4` закрыт,
   гейт `CODE` расчищен.

## Запарковано / хвосты (в новый чат, по приоритету — после `DOCS_CHECK_6`)

1. **ТР1 крипто-надёжность объёма** (IND-Q1 крипто-часть) — якорь на
   **фазу 4** (продуктовый подбор инструмента/площадки + будущий аналитик
   совета). В фазе 1 инструмент/стратегия задаются пользователем вручную
   (REST) → острый случай под ручным контролем + объём = подтверждающий
   фильтр. **Не-блокирующий.** Якоря — `open-questions.md` §IND-Q1,
   `roadmap.md` §Примечания.
2. **Численные пороги** структуры и фазы (`swingLookbackBars`,
   `minTouches`, буфер/подтверждение пробоя, ширина диапазона, пороги
   правил фазы) — хвост пользователя, на этапе настройки стратегии.
3. **Точная арифметика** (толерансы кластеризации, окно/формула ER,
   критерий «наклон EMA согласен») — на `CODE`.
4. **Forward-debt Java step-2** — ресинк под условную модель фазы +
   удаление `structureType` + сверка `MarketStructureParams` на `CODE`
   шага 3.

## Следующее действие (явно)

Новый чат стартует с **`DOCS_CHECK_6`** — концепт-ревью шага 3 по сильно
изменившимся докам (условная фаза + ER каталожным индикатором + объёмные
условия + семантика структуры/резолвер — всё переписало доки). Прогон по
`concept-review` в текущем формате (флаг действия CC + ярлыки исхода/
дефицита). Цель — проверить целостность концепции **перед `CODE`**.

## Открытое наблюдение по пайплайну (не принятое изменение)

**Слепой проход спойлится.** Пользователь сам прогоняет CC и видит крен в
его отчёте → валидация информированная, не слепая → гейт N=3 **не тикает**.
Кандидат на доработку, если хотим, чтобы гейт мог тикать: развести «кто
видит крен CC» (напр. чат прогоняет слепой проход до раскрытия отчёта).
Зафиксировано как наблюдение, не как принятое изменение правил.

## Активные принципы (для подхвата)

- **Книжный контур грунтовки `trading-specialist`:** дистиллят → сырые
  книги + самодистилляция → эскалация → дообучение → интернет крайним
  резервом (`trading-library-distillation.md`). Каждое торговое утверждение
  — «источник говорит» / «предполагаю·инженерное» / «в корпусе ∅».
- **OBV-операнд — относительные формы**, объём — подтверждающий фильтр (не
  единственное основание `ENTRY`); стабильный абсолютный порог по объёму —
  нормированный операнд по потребности (`volume-condition-semantics.md`).
- **Структура — вычисляемый результат-операнд:** `MarketStructureResolver`
  выводит `Type` (выход, не вход), пробой — `breakoutEvent` предвычисленным.
- **`expiredAt` — производная на чтение** (`referencePoint +
  askingSetting.expirationDuration`), не колонка; шаримые результаты
  ключуются реестром конфигураций, фаза — контейнером (осознанное
  исключение).
- **Каталог индикаторов расширяем через стратегию** по потребности, не
  превентивно.
- **Приемлемость остаточного риска** (whipsaw/перескок/просадка) — хвост
  пользователя, не инженерный дефолт.
- **Критерий sugar-vs-алиас грамматики** (`condition-ruletype-granularity.md`):
  `MARKET_STRUCTURE_IS` проверен — оставлен именованным; `MARKET_PHASE_IS`,
  `VOLUME_FILTER_PASSED` — отложенные кандидаты (точечно при фиксации их
  контрактов, не пакетом).

## Режим работы

**Содержательное закрытие** (не отладка пайплайна). Развилки со штатным
владельцем — через CC по ступеням автономии
(`.claude/processes/question-delegation.md`). Расхождения bucket-2 —
диагностика дефицита входа + перепрогон владельцем, не ручной вывод в чате.

## Активные задачи

- `phase-1-step-3-gaps-close-4-design.md` (staged) — проработка Н3 +
  хвост Н6; **закрыта** (баннеры: Н6/Н8/Н10 в канон, §1.1/§1.3 пересверены
  → `n3-structure-rerun.md`).
- `phase-1-step-3-tr1-tr2-volume-conditions.md` (staged, new) — раунд
  ТР1/ТР2, **закрыт** (баннер «зафиксировано в канон»).
- `phase-1-step-3-n3-structure-rerun.md` (staged, new) — пересверка
  Н3-структуры, **закрыта** (баннер «зафиксировано в канон»).
- `phase-1-step-3-fork-a-rerun-er-location.md`,
  `phase-1-step-3-fork-a-bis-rerun-efficiency-ruletype.md`,
  `phase-1-step-3-market-phase-conditional-redesign.md` (staged) —
  **закрыты** (баннеры прошлой темы).
- `phase-1-step-3-docs-check-5.md` — gap-отчёт 5-го прогона (сравнительная
  база для `DOCS_CHECK_6`); `…-docs-check-1.md` … `-4.md` — история прогонов.
- `.claude/processes/trading-library-distillation.md` (new) — процесс
  дистилляции; лог разовой задачи — в `history/`.

## Текущий фронтир / следующее действие

Тема `GAPS_CLOSE_4` **закрыта целиком**, шаг 3 на `CODE`. Следующее —
**`DOCS_CHECK_6`** (концепт-ревью перед `CODE`). Хвосты (после
`DOCS_CHECK_6`, по приоритету): ТР1 крипто (фаза 4, не блокирует) →
численные пороги (настройка) → арифметика (`CODE`) → forward-debt Java.

Коммит — за пользователем (всё staged, CC не коммитит).

## Синхрон / что в работе / PK

- **Project Knowledge:** последний снапшот теперь **`snapshot-v40`**
  (заменяет v39 в префлайте — обновить PK после коммита). **Затронутые
  PK-файлы** (обновить после коммита, если в PK): `trading-specialist.md`,
  новый `.claude/processes/trading-library-distillation.md`,
  `IndicatorValue.md`, `Strategy.md`, `MarketStructure.md`, ER-decision
  (`efficiency-ratio-as-catalog-indicator.md`). `CLAUDE.md` /
  `structure.md` / `naming.md` / `place-knowledge.md` в этой сессии не
  менялись.
- **Custom Instructions (`chat-project-instructions.md`):** в этой сессии
  не правился (источник правды — поле в Settings).
- **Staged к коммиту (эта сессия):**
  - **Н6/Н8/Н10:** `market-data-retention.md` (new),
    `market-data-freshness.md`, `market-data-result-identity-keying.md`,
    `MarketDataExpirationChecker.md`, `MarketPhase.md`, `MarketStructure.md`,
    `IndicatorValue.md`.
  - **ТР1/ТР2:** `volume-condition-semantics.md` (new),
    `microstructure.md`, `strategy-patterns.md` (distilled),
    `condition-ruletype-granularity.md`, `open-questions.md`,
    `roadmap.md`.
  - **Грунтовка:** `trading-specialist.md`,
    `trading-library-distillation.md` (new process),
    rename `progress/trading-library-distillation.md` →
    `history/2026-06-05-trading-library-distillation-run.md`.
  - **Н3-структура:** `MarketStructureResolver.md` (new),
    `MarketStructureJob.md`, `MarketStructure.md`, `Strategy.md`,
    `strategy-condition-authoring-contract.md`,
    `efficiency-ratio-as-catalog-indicator.md`, `phase-1.md` (статус
    `CODE`).
  - Проработки `phase-1-step-3-tr1-tr2-volume-conditions.md` (new),
    `phase-1-step-3-n3-structure-rerun.md` (new), баннеры в
    `phase-1-step-3-gaps-close-4-design.md`; этот снапшот.
- Коммит — за пользователем.
