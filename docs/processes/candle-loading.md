# Загрузка свечей (candle-loading)

## На какой вопрос отвечает этот файл

Как устроен процесс добычи и поддержания целостности свечной истории.

## Главная идея

Свечи — базовые рыночные данные, на которые опираются все
производные расчёты (индикаторы / структура / фаза). Их **добыча и
целостность** вынесены в отдельный процесс: `CandleJob` ведёт
каждую `CandleGroup` (инструмент + таймфрейм) по её жизненному
циклу, поддерживает плотность ряда по count и доводит инструмент до
готовности.

Процесс — **поставщик свечей** для
`docs/processes/market-data-calculation.md` (вычисление поверх
загруженных свечей). Выделен из `market-data-calculation` по
двухусловному критерию (своя оркестрация + точка композиции) —
`.claude/decisions/process-materialization-criterion.md`.

## Оркестрация: `CandleJob` и цикл `CandleGroup`

`CandleJob` (CRON, порядка раза в минуту) ведёт свечную историю по
группам `CandleGroup` (одна группа = инструмент + таймфрейм) через
её жизненный цикл (`docs/lifecycles/CandleGroup.md`):

- историческая выкачка «в глубину» (`BACKFILL`) до планового
  горизонта `Instrument.plannedCandleStartDate` (на инструмент)
  либо до пустого ответа биржи;
- регулярная докачка хвоста при новом баре (`SYNC`);
- проверка целостности по count (`CHECK`);
- докачка дыр бинарным поиском (`REPAIR`) до плотного ряда
  (`ACTIVE`).

Идемпотентность — по уникальности `(candleGroupId, openTimestamp)`;
фактические границы — `actualFirstUtcMillis`/`actualLastUtcMillis`,
объём — `count` (на старте реконсилируется реальным `COUNT(*)`). В
историю попадают только закрытые свечи (`confirm=1`), без
look-ahead.

Джоба живёт в пакете `domain.jobs`; кроме CRON, тик запускается вне
расписания через `JobController` (`POST /api/jobs/candle-loading/trigger`)
асинхронно (фасад `CandleJobFacade`, `@Async`) — codestyle.

Контракт пагинации назад и лимиты OKX REST —
`docs/integrations/okx/contracts/candle.md`. Полная политика
загрузки и целостности (глубина, расписание `SYNC`/`CHECK`, докачка
дыр, поведение при постоянной дыре) — `docs/lifecycles/CandleGroup.md`; density-инвариант —
`docs/models/domain/other/CandleGroup.md`.
Граница свечи (OKX-массив → `CandleExternalSnapshot` → domain
`Candle`) — `docs/models/mapping/Candle.md`.

## Онбординг инструмента и готовность свечей

Готовность самого инструмента (онбординг
`CREATED → SYNC → CANDLES_LOADING → ACTIVE`) координируется с
готовностью его групп свечей: инструмент `ACTIVE`, когда **все** его
`CandleGroup` достигли `ACTIVE`. Семантика переходов и координация
`Instrument.Status` ↔ `CandleGroup.Status` —
`docs/lifecycles/Instrument.md`. Это уровень готовности
**инструмента**; готовность данных для активации **стратегии** —
более поздний слой (`docs/processes/market-data-calculation.md`).

## Глубина под прогрев индикаторов — вход от стратегии (запарковано)

Глубину исторической выкачки задаёт `Instrument.plannedCandleStartDate`
(плановый горизонт на инструмент). Стратегия объявляет, сколько истории
нужно для прогрева её индикаторов: эффективный `warmup` + `timeframe`
каждой `StrategyIndicatorSetting`
(`docs/models/domain/aggregate/Strategy.md`) — это
**вход** для расчёта глубины загрузки на стороне рыночных данных. Как
именно warmup-горизонт стратегии конкретно перекладывается в
`plannedCandleStartDate` — cross-cutting strategy ↔ market-data,
**запарковано** (всплывёт вместе с владельцем оркестрации, ORCH-Q1).

## Владелец оркестрации — открытый вопрос

Кто драйвит переходы `Instrument.Status` и `CandleGroup.Status` и
координирует их между собой (отдельный orchestrator-компонент +
per-status handler'ы по образцу FSM сделки, или иной механизм) —
открытый вопрос **ORCH-Q1**
(`.claude/work/questions/open-questions.md`). До решения владелец не
материализуется; семантика переходов и координации зафиксирована в
lifecycle-доках, а оркестрация описана здесь без привязки к
конкретному компоненту-владельцу. `CandleJob` остаётся
производителем свечей (`docs/components/CandleJob.md`).

В коде шага 1 `CandleJob` несёт **провизорную** координацию
готовности (`refreshInstrumentReadiness`: переводит инструменты из
`CANDLES_LOADING` в `ACTIVE`, когда их группы готовы) — минимальный
seam, чтобы шаг работал end-to-end, а не канонический владелец
оркестрации (он определяется в ORCH-Q1).

## Связи

- Производитель — `docs/components/CandleJob.md`.
- Модель и lifecycle групп — `docs/models/domain/other/CandleGroup.md`,
  `docs/lifecycles/CandleGroup.md`; свеча —
  `docs/models/domain/other/Candle.md`.
- Lifecycle онбординга инструмента — `docs/lifecycles/Instrument.md`.
- Потребитель свечей — `docs/processes/market-data-calculation.md`.
- Контракт / формат OKX — `docs/integrations/okx/contracts/candle.md`,
  `docs/models/mapping/Candle.md`.
- Критерий выделения процесса —
  `.claude/decisions/process-materialization-criterion.md`.
- Открытый вопрос владельца оркестрации — ORCH-Q1.
