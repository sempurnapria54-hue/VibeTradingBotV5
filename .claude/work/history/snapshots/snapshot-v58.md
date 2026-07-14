# Snapshot v58

**Дата:** 2026-06-22.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — разбор `DOCS_CHECK_1` шага 6 (FSM +
живая оркестрация) и применение `GAPS_CLOSE_1` — закрыта:** 15 пробелов
(N1-N15) / 8 эскалаций (Э1-Э8) / торговый блокер TR1 закрыты в доках по
согласованным с пользователем решениям; гейтящие `CODE` пробелы сняты. Заход —
**плановое завершение темы** (не continuation). Снапшот обычного состава; новый
чат стартует с PK-префлайта, затем **`DOCS_CHECK_2`** (подтверждающий прогон,
гейт `CODE`). Сменяет v57 (там был закрыт шаг 5, шаг 6 ещё не стартовал).

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `IN_PROGRESS`**
(под-шаг `GAPS_CLOSE_1` пройден, следующий — `DOCS_CHECK_2`), шаги 7-11 `HOLD`.
Ветка `claude-audit`. **Кода в этой теме не трогали** — шаг 6 идёт docs-first
(концепт-гейт до `CODE`); работа шага 6 — только доки + пайплайн. Дельта
`DOCS_CHECK_1`+`GAPS_CLOSE_1` **уже закоммичена** пользователем (commit
`14a7b23 ROADMAP 1-6-2 DOCS_CHECK_1`, 40 файлов) — **working tree чист**;
единственное staged после этой сессии — сам снапшот v58.

## Путь к точке (от v57)

v57 закрыл шаг 5 (`DONE`). Эта тема стартовала и провела шаг 6 до `GAPS_CLOSE_1`
по `roadmap-step-execution`:

**Граница шага.** После уточнения 6 ↔ 7 (2026-06-21) шаг 6 = «FSM + **живая
оркестрация**»: статусная механика + конструкция handler'ов **плюс** живая
петля (`DealOrchestratorJob` driving), REPLACE-оркестрация, per-deal
concurrency-guard (D-M1), **механика финализации** (финализационные executor'ы,
терминальные рёбра, retry-state). Статусный костяк (`deal-management`,
`DealStateMachine`, 7 handler'ов, lifecycles, command-layer) **в основном уже
материализован** миграцией из архива.

**1. `DOCS_CHECK_1` (2026-06-22) — не чисто.** `TOOLING` без новых артефактов
(фокусы `concept-review`/`trading-review` активны). Найдено **15 пробелов**
(N1-N15), **8 эскалаций** (Э1-Э8), торговый блокер **TR1** (бесстоповый
risk-creating вход). Пробелы сосредоточены на **петле / финализации /
операционной оболочке оркестратора** — на том, что петля «включает». CC в
прогоне только **предлагал** (варианты+крен), не финализировал. Отчёт +
§Закрытие — `.claude/work/progress/phase-1-step-6-docs-check-1.md`.

**2. `GAPS_CLOSE_1` (2026-06-22) — гейтящие `CODE` пробелы закрыты в доках** по
согласованным с пользователем решениям:

- **N1 / Э2 — error-политика.** Новое правило `docs/rules/error-handling-policy.md`
  (внешняя поверхность — единый глобальный `@ControllerAdvice` + единый
  error-DTO; async-фасад ручного триггера джобы: `202` запуск / `409` отказ
  запуска; внутренняя градация **4 уровня** — лог / ретрай / **холд
  инструмента** / **холд биржи**) + `docs/rules/instrument-hold.md` (уровень 3).
  **TBD снят** в `.claude/rules/codestyle.md` §«Обработка ошибок». Набор
  HTTP-кодов / 409-vs-идемпотентность — провизорный хвост пользователя.
- **N2-N4 / DEAL-Q1 / Э1 — финализационная под-спина.** Дом retry-state —
  **отдельная сущность `DealFinalizationState`** (модель + lifecycle + decision
  `deal-finalization-state-materialization.md`; **крен (а)** — не обобщение
  `DealActionState`). 4 executor-дока (`FinalizeDealEntry/Exit`,
  `MarkDealClosed/Error`: чтение/запись, терминальные рёбра, идемпотентность,
  retry-anchor). Эмиссия — `ServiceCommand`+`dealFinalizationStateId`,
  `ServiceCommandFactory` по статусу `DealFinalizationState`.
- **N9 / TR1 / Э5 — обязательная защита risk-creating входа.** Инвариант
  `docs/rules/risk-creating-entry-protection.md` (вход без резолвимого стопа не
  доходит до постановки → `PRECHECK` блок → `CLOSED`+`RISK_CONTROL`; двусторонний
  enforcement). Снят fail-open `RiskValidator` (код
  `RISK_CREATING_ENTRY_WITHOUT_STOP`).
- **N5-N6 / CMD-Q5+Q6 / Э3 — REPLACE-владелец + «действие vs команда».**
  Decision `action-orchestration-vs-command.md`: владелец REPLACE-секвенса —
  петля/`DealStateMachine` (по фактам), фабрика — «одна команда за проход»;
  `KILL_SWITCH` — **команда** (синхронный fire-all teardown, защиту снимает
  последней).
- **N7-N8 / D-M1 / Э4 — оболочка оркестратора + concurrency + `maxAttempts`.**
  `DealOrchestratorJob`: CRON+`enabled`+async-фасад+критерии выборки (active +
  due-for-retry по `nextRetryAt`); **concurrency — БД-блок на весь проход**
  (сериализует проходы; per-deal/in-memory отвергнуты). `maxAttempts` —
  авторитет **policy** (live), поле сущности — снимок для истории.
- **N10 / INSTR-Q2 / Э6 — set-leverage** перед каждым ордером в `PRECHECK`
  (idempotent); `Instrument.leverage` — потолок/умолчание.
- **N12 / CMD-Q4 / Э7 — Precheck-чистота.** Инструмент-скоупный exchange-read
  **вне command-layer** (`IntegrationService`); bulk-команду не возвращаем.
- **N13-N15 / Э8 — гигиена** (стале-ссылки «handler'ы мигрируются отдельно» +
  битый `tasks/deal.md`; `FINALIZE_DEAL_ENTRY` в finalization-списке
  `risk-validator-scope`; scope-нота `account-bills` — расчёт `resultProfit` →
  шаг 7).
- **DEAL-Q2** — терминальный контракт финализации (`Deal.md`): финализация
  всегда доводит до терминала; число на ошибочном терминале — шаг 7.

**Open-questions:** закрыты **DEAL-Q1, DEAL-Q2, CMD-Q5, CMD-Q6**; продвинуты
**INSTR-Q2** (тайминг/владелец решены), **CMD-Q4** (Precheck-часть закрыта).
Cross-cutting форварды TR2/TR3/TR4 — не тронуты (backlog).

## Среда

Без изменений против v57. Кода в этой теме не писали (docs-first до `CODE`).
**Нюанс сборки (на будущий `CODE`):** `java`/`mvn` не на PATH, `mvnw` нет — CC
собирает через JBR-25 (IDEA, `…/idea-2026.1/…/jbr`) как `JAVA_HOME` + wrapper
Maven 3.9.11, offline (`-o`). demo-среда (Vault test + demo + Postgres-test +
сеть OKX) доступна; prod вне контура.

## Следующий шаг

Новый чат — PK-префлайт, затем **`DOCS_CHECK_2`** (подтверждающий прогон по
докам после `GAPS_CLOSE_1`; независимые ревьюер-фокусы `concept`+`trading`).
**Чистый `DOCS_CHECK_2` = гейт `CODE`** (`roadmap-step-execution.md` §«Гейт
`CODE` — чистый `DOCS_CHECK`»). Если чисто → `CODE` шага 6 (сшивка петли); если
остались пробелы → узкий `GAPS_CLOSE_2`.

**Жёсткие гейты `DONE` шага 6** (петлю нельзя включать, пока не закрыты,
`phase-1.md` §гейты): **D-B3** (SUBMIT recovery-by-clientId) и **D-M1**
(concurrency-guard) — спека D-M1 теперь в доках (БД-блок на весь проход),
реализация — гейт `DONE` на `CODE`.

## Принципы

Docs-first; концепт-гейт `CODE` = чистый `DOCS_CHECK`. Шаг 6 **композиционный**:
нижние слои (market-data/calc/risk/command-layer шагов 1-5) сшиваются в
работающую петлю; достраивается то, что без петли было мёртвым кодом (REPLACE,
финализация, concurrency-guard). Зафиксировано на `GAPS_CLOSE_1`:

- **Error-политика:** внешняя поверхность — единый `@ControllerAdvice` + единый
  error-DTO; внутренняя градация 4 уровня (лог / ретрай / холд инструмента /
  холд биржи). FSM/оркестрация наружу не торчат.
- **Финализация** — отдельный дом retry-state `DealFinalizationState` (не
  обобщение `DealActionState`, чей инвариант `UNIQUE(deal_id, strategy_action_id)`
  остаётся жёстким).
- **REPLACE** — оркеструет петля/`DealStateMachine` по подтверждённым фактам
  (protective: place-new → факт → cancel-old; entry: cancel-old → терминал →
  place-new). `KILL_SWITCH` — команда (аварийный синхронный fire-all).
- **Защита risk-creating входа обязательна** — без резолвимого стопа позиция до
  биржи не доходит; reduce-only не трогаем.
- **set-leverage** — перед каждым ордером в `PRECHECK` (idempotent).
- **Concurrency** — БД-блок на весь проход оркестратора (сериализует проходы).
- **Граница 6 ↔ 7:** *механика* финализации (executor'ы, рёбра, retry-state) —
  шаг 6; *расчёт* `resultProfit`/PnL — шаг 7. Числа (HTTP-коды, лимиты, проскок)
  провизорны/отложены, не выдумываются.

## Отложено / на будущее

- **Расчёт `resultProfit` / breakdown PnL / fee-модель** → шаг 7 (механика
  финализации шага 6 оставляет терминальный контракт).
- **HTTP-коды / 409-vs-идемпотентность** — провизорный хвост пользователя
  (error-политика зафиксирована, конкретный набор кодов — за пользователем).
- **TR2** (верхний кэп плеча/нотинала при узком стопе) — cross-cutting forward,
  revisit бэктест/живые прогоны (фаза 2+; `per-trade-risk-policy.md`,
  `backlog.md`).
- **TR3** (буфер на проскок/гэп за стопом) — forward, числа — бэктест.
- **TR4** (placement-наивность к манипуляциям / market-close на неликвиде) —
  forward (фаза 2+).
- **INSTR-Q2 остаток** — CODE-представление write set-leverage (тайминг/владелец
  решены).
- **CMD-Q4 orphan-скан** → `AnomalyJob` шаг 8 (Precheck-часть закрыта).
- **Численный лимит риска на сделку** — provisional (бэктест/пользователь).

## Открытые вопросы

**12 открытых** (было больше; `GAPS_CLOSE_1` закрыл 4: **DEAL-Q1, DEAL-Q2,
CMD-Q5, CMD-Q6**; продвинул INSTR-Q2, CMD-Q4). Остались: **INSTR-Q2**
(продвинут, остаток — CODE), ORCH-Q1, **CMD-Q4** (Precheck-часть закрыта,
orphan-часть → шаг 8), OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4 (WS), STRAT-Q4, IND-Q1,
STRUCT-Q1 (фаза 2), PHASE-Q1, PHASE-Q2. **Ни один не гейтит `DOCS_CHECK_2` /
остаток шага 6** — гейтящие закрыты на `GAPS_CLOSE_1`.

## Гейты делегирования

- **`reviewer`** — `concept-review` + `trading-review` отработали `DOCS_CHECK_1`
  (15 пробелов, TR1) независимыми субагентами, не автор доков.
- **`solution-designer`** — концепт-решения `GAPS_CLOSE_1` (дом финализации,
  error-политика, «действие vs команда», оболочка оркестратора + guard, защита
  входа, тайминг set-leverage, read вне command-layer).
- **`trading-specialist` / `trading-review`** — обоснование/валидация TR1
  (защита risk-creating входа; корпус расколот, но бот стоп-driven).
- **`knowledge-curator`** — размещение/реконсиляция доков `GAPS_CLOSE_1` (новые
  правила/решения/модели/executor-доки + правки компонент/lifecycles/правил).
- **`integrator`** — правка `account-bills.md` — scope-нота потребления
  (`resultProfit` → шаг 7), офдок-факты OKX не менялись.
- **Следующий чат (`DOCS_CHECK_2`):** `reviewer` фокусы `concept`+`trading` —
  независимый подтверждающий прогон по докам после `GAPS_CLOSE_1`.

## Режим работы

**Шаг 6 в работе, под-шаг `GAPS_CLOSE_1` пройден.** Новый чат — `DOCS_CHECK_2`
(подтверждающий прогон, гейт `CODE`), docs-first. Не отладка пайплайна, не
продолжение разбора `DOCS_CHECK_1` (тема закрыта плановым завершением).

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v58`** (заменяет
  v57 в префлайте — **обновить PK после коммита**).
- **Закоммичено (не staged) — дельта `DOCS_CHECK_1`+`GAPS_CLOSE_1`**, commit
  `14a7b23 ROADMAP 1-6-2 DOCS_CHECK_1` (40 файлов):
  - **новые доки:** `docs/rules/error-handling-policy.md`,
    `docs/rules/instrument-hold.md`, `docs/rules/risk-creating-entry-protection.md`,
    `docs/models/domain/other/DealFinalizationState.md`,
    `docs/lifecycles/DealFinalizationState.md`,
    `docs/decisions/deal-finalization-state-materialization.md`,
    `docs/decisions/action-orchestration-vs-command.md`, 4 executor-дока
    (`FinalizeDealEntry/ExitExecutor`, `MarkDealClosed/ErrorExecutor`);
  - **правки:** компоненты (`DealOrchestratorJob`, `DealStateMachine`,
    `PrecheckHandler`, `EntryFinalizedHandler`, `ServiceCommandFactory`,
    `ServiceCommandExecutor`, `RetryPolicyService`, `RiskValidator`,
    `KillSwitchExecutor`, `IntegrationService`, `ErrorHandler`, component-models),
    lifecycles `Deal`/`DealActionState`, правила (`risk-validator-scope`,
    `ack-not-runtime-truth`, `exchange-hold`, `runtime-error-classification`),
    `account-bills.md`, `per-trade-risk-policy.md`,
    `deal-action-state-materialization.md`;
  - **пайплайн:** `.claude/rules/codestyle.md` (TBD error-политики снят),
    `open-questions.md` (−4 закрытых), `backlog.md`, `roadmap/phase-1.md`
    (журнал `DOCS_CHECK_1`/`GAPS_CLOSE_1`, статус шага 6 = `GAPS_CLOSE_1`),
    progress-файл с §Закрытие.
- **Staged (эта сессия):** только `snapshot-v58.md`. Working tree до неё был
  чист (дельта темы закоммичена пользователем).
- **Untracked (не наши / транзиентные):** `tradingbot.iml`, `vault.hcl`.
- **`external-source-sync`:** офдок-факты OKX не менялись (правка `account-bills`
  — scope-нота нашей интерпретации) — ре-синхронизация не нужна.
