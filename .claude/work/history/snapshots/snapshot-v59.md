# Snapshot v59

**Дата:** 2026-06-22.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — концепт-проработка шага 6 (FSM +
живая оркестрация) — закрыта:** `DOCS_CHECK_1` → `GAPS_CLOSE_1` →
`DOCS_CHECK_2` (нашёл R1) → `GAPS_CLOSE_2` (закрыл R1) → `DOCS_CHECK_3`
(**чисто**); **концепт-гейт `CODE` пройден**. Заход — **плановое завершение
темы**; новый чат стартует с PK-префлайта, затем **под-шаг `CODE`** (пишем
код шага 6). Снапшот обычного состава. Сменяет v58 (промежуточный, никогда не
коммитился — фиксировал точку «next = `DOCS_CHECK_2`», которую сессия
обогнала; можно не коммитить).

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `DOCS_CHECK_3`**
(концепт целостен, гейт `CODE` чист — готов к `CODE`), шаги 7-11 `HOLD`.
Ветка `claude-audit`. **Кода шага 6 ещё нет** — вся тема прошла docs-first
(концепт-гейт до `CODE`). Дельта `DOCS_CHECK_1`+`GAPS_CLOSE_1` **закоммичена**
пользователем (commit `14a7b23`); дельта этой сессии (`DOCS_CHECK_2`/3-отчёты,
R1-фикс, журнал роадмапа, снапшоты) — **staged, не закоммичено**.

## Путь к точке (от v57)

v57 закрыл шаг 5 (`DONE`). Шаг 6 («FSM + живая оркестрация» — после уточнения
границы 6 ↔ 7 от 2026-06-21: живая петля `DealOrchestratorJob`,
REPLACE-оркестрация, per-deal concurrency-guard, **механика финализации**)
прошёл концепт-петлю docs-first до чистого `DOCS_CHECK`:

**1. `DOCS_CHECK_1` — не чисто.** 15 пробелов (N1-N15), 8 эскалаций (Э1-Э8),
торговый блокер TR1 (бесстоповый risk-creating вход). Статусный костяк
(`deal-management`, `DealStateMachine`, 7 handler'ов, lifecycles,
command-layer) **в основном уже материализован** миграцией из архива; пробелы
— на **петле / финализации / операционной оболочке оркестратора**.

**2. `GAPS_CLOSE_1` — гейтящие `CODE` пробелы закрыты в доках** (по
согласованным с пользователем решениям):
- **N1** — error-политика (`error-handling-policy.md` + `instrument-hold.md`;
  внешняя поверхность `@ControllerAdvice`+единый DTO; async-фасад 202/409;
  внутренняя градация 4 уровня; TBD `codestyle` снят).
- **N2-N4/DEAL-Q1** — финализационная под-спина: дом retry-state **отдельная
  сущность `DealFinalizationState`** (модель+lifecycle+decision; крен (а) — не
  обобщение `DealActionState`); 4 executor-дока (`FinalizeDealEntry/Exit`,
  `MarkDealClosed/Error`); эмиссия (`ServiceCommand.dealFinalizationStateId`,
  фабрика по статусу).
- **N9/TR1** — инвариант `risk-creating-entry-protection.md` + снят fail-open
  `RiskValidator` (код `RISK_CREATING_ENTRY_WITHOUT_STOP`).
- **N5-N6/CMD-Q5-Q6** — `action-orchestration-vs-command.md` (REPLACE-владелец
  — петля/`DealStateMachine` по фактам; `KILL_SWITCH` — команда).
- **N7-N8/D-M1** — `DealOrchestratorJob` оболочка + concurrency-guard (БД-блок
  на весь проход). **N10** — set-leverage перед ордером в `PRECHECK`. **N11** —
  авторитет `maxAttempts` = policy. **N12/CMD-Q4** — read вне command-layer.
  **DEAL-Q2** — терминальный контракт. **N13-N15** — гигиена.
- Закрыты DEAL-Q1/DEAL-Q2/CMD-Q5/CMD-Q6; продвинуты INSTR-Q2/CMD-Q4.

**3. `DOCS_CHECK_2` — почти чисто.** Три независимых ревьюер-субагента
(concept ×2 + trading). Все 15 пробелов + TR1 + закрытые вопросы подтверждены
закрытыми чисто (верификация атрибуции; ripple-проверки финализации пройдены;
гейтящих OQ нет; TR2-TR4 — forward, не регрессировали). **Одна минорная
негейтящая гигиена-рябь — R1:** `deal-management.md` несла безусловный
инвариант «для terminal обязательны `resultProfit`» (включал ошибочный
`EMERGENCY_CLOSED`), расходясь с DEAL-Q2-контрактом.

**4. `GAPS_CLOSE_2` — R1 закрыт.** `deal-management.md`: обязательность
`resultProfit`/`resultProfitCurrency` ограничена **чистым** `CLOSED`,
`EMERGENCY_CLOSED` — по терминальному контракту (правка-cleanup).

**5. `DOCS_CHECK_3` — чисто.** Узкий независимый прогон: R1 подтверждён
закрытым; sweep по `docs/` — других стале-копий инварианта нет; новой ряби
нет. **Концепт-гейт `CODE` пройден** (concept — этот прогон; trading — чисто
на `DOCS_CHECK_2`, поверхность не менялась).

## Среда

Без изменений по контуру. Кода ещё не писали. **Нюанс сборки (актуален для
`CODE`):** `java`/`mvn` не на PATH, `mvnw` нет — CC собирает через JBR-25
(IDEA, `…/idea-2026.1/…/jbr`) как `JAVA_HOME` + wrapper Maven 3.9.11, offline
(`-o`). demo-среда (Vault test + demo + Postgres-test + сеть OKX) доступна;
prod вне контура.

## Следующий шаг

Новый чат — PK-префлайт, затем **под-шаг `CODE` шага 6** по
`roadmap-step-execution`: `code-writer` пишет → ревью-итерации независимого
ревьюера по фокусам `conventions`/`performance`/`disaster` → аппрув
(зафиксированный исход фокусов, автор код не аппрувит). Затем
`SYNC_DOCS_FROM_CODE`, при концепт-инкременте — пост-хок гейт §6a, → `DONE`.

**Что `CODE` материализует** (спека — в доках, приведённых `GAPS_CLOSE_1/2`):
- **Живая петля** `DealOrchestratorJob` (driving): оболочка
  CRON+`enabled`+async-фасад+критерии выборки (active + due-for-retry по
  `nextRetryAt`); **D-M1 — БД-блок на весь проход** (сериализует проходы).
- **Финализационная под-спина:** сущность `DealFinalizationState`
  (+persistence, миграция), 4 executor'а, эмиссия через
  `ServiceCommand.dealFinalizationStateId` / фабрику по статусу /
  `DealContext.finalizationStates`.
- **REPLACE-оркестрация** в петле/`DealStateMachine` (по фактам);
  **KILL_SWITCH** executor.
- **Error-политика:** глобальный `@ControllerAdvice` + единый error-DTO;
  внутренняя градация 4 уровня (лог/ретрай/**холд инструмента**/холд биржи) +
  механизм instrument-hold.
- **Защита risk-creating входа:** блок в `PRECHECK`,
  `RISK_CREATING_ENTRY_WITHOUT_STOP`, снятый fail-open.
- **set-leverage** перед каждым ордером в `PRECHECK`; **`maxAttempts`** —
  авторитет policy (live), поле сущности — снимок.
- **Подключить** `RiskValidator`/`StrategyActionCalculator` (написаны на шаге
  5 как точки композиции, в FSM ещё не подключены — форвард с шага 5).

**Жёсткие гейты `DONE` шага 6** (петлю нельзя включать, пока не закрыты,
`phase-1.md` §гейты): **D-B3** (SUBMIT recovery-by-clientId) и реализация
**D-M1** (concurrency-guard; спека в доках, код — на `CODE`).

## Принципы

Docs-first; концепт-гейт `CODE` = чистый `DOCS_CHECK` (пройден). Шаг 6
**композиционный**: нижние слои (market-data/calc/risk/command-layer шагов
1-5) сшиваются в работающую петлю; достраивается то, что без петли было
мёртвым кодом (REPLACE, финализация, concurrency-guard). Зафиксировано:

- Error-политика: внешняя поверхность — единый `@ControllerAdvice`+error-DTO;
  внутренняя градация 4 уровня. FSM/оркестрация наружу не торчат.
- Финализация — отдельный дом retry-state `DealFinalizationState`.
- REPLACE оркеструет петля/`DealStateMachine` по фактам; `KILL_SWITCH` —
  команда (аварийный синхронный fire-all, защиту снимает последней).
- Защита risk-creating входа обязательна (без резолвимого стопа — не доходит
  до биржи); reduce-only не трогаем.
- set-leverage перед каждым ордером в `PRECHECK` (idempotent).
- Concurrency — БД-блок на весь проход оркестратора.
- Граница 6 ↔ 7: *механика* финализации — шаг 6; *расчёт* `resultProfit`/PnL —
  шаг 7. Числа провизорны/отложены, не выдумываются.

## Отложено / на будущее

- **Расчёт `resultProfit` / breakdown PnL / fee-модель** → шаг 7.
- **HTTP-коды / 409-vs-идемпотентность** — провизорный хвост пользователя.
- **INSTR-Q2 остаток** — CODE-представление write set-leverage (отдельная
  команда vs inline-адаптер — деталь `CODE`).
- **CMD-Q4 orphan-скан** → `AnomalyJob` шаг 8 (Precheck-часть закрыта).
- **TR2** (кэп плеча/нотинала при узком стопе), **TR3** (буфер на проскок за
  стопом), **TR4** (placement-наивность) — cross-cutting forward (фаза 2+).
- **Численный лимит риска на сделку** — provisional (бэктест/пользователь).

## Открытые вопросы

**12 открытых** (без изменений против v58; `GAPS_CLOSE_2` закрыл только
гигиену R1, не вопрос): INSTR-Q2 (продвинут, остаток — CODE), ORCH-Q1,
CMD-Q4 (Precheck-часть закрыта, orphan → шаг 8), OKX-Q1, OKX-Q2, OKX-Q3,
OKX-Q4, STRAT-Q4, IND-Q1, STRUCT-Q1, PHASE-Q1, PHASE-Q2. **Ни один не гейтит
`CODE` шага 6** (гейтящие закрыты на `GAPS_CLOSE_1`).

## Гейты делегирования

- **`reviewer`** — `concept`+`trading` отработали `DOCS_CHECK_1` (15 пробелов,
  TR1) и `DOCS_CHECK_2`/`DOCS_CHECK_3` (подтверждающие) независимыми
  субагентами, не авторы доков.
- **`solution-designer`** — концепт-решения `GAPS_CLOSE_1`.
- **`knowledge-curator`** — размещение/реконсиляция доков `GAPS_CLOSE_1` +
  R1-реконсиляция `GAPS_CLOSE_2`.
- **`trading-specialist`/`trading-review`** — обоснование/валидация TR1.
- **Следующий чат (`CODE`):** `code-writer` пишет код шага 6; `reviewer`
  фокусы `conventions`/`performance`/`disaster` в ревью-итерациях
  (независимо); аппрув — зафиксированный исход фокусов.

## Режим работы

**Шаг 6 концептно закрыт, гейт `CODE` чист.** Новый чат — под-шаг `CODE`
(пишем код). Не отладка пайплайна, не концепт-петля (она пройдена).

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v59`** (заменяет
  v57/v58 в префлайте — **обновить PK после коммита**).
- **Закоммичено (не staged):** дельта `DOCS_CHECK_1`+`GAPS_CLOSE_1` — commit
  `14a7b23 ROADMAP 1-6-2 DOCS_CHECK_1` (40 файлов; новые правила/решения/
  модели/executor-доки + правки + `codestyle` TBD снят + open-questions +
  backlog + roadmap + progress).
- **Staged, не закоммичено (дельта этой сессии):**
  - **доки:** `docs/processes/deal-management.md` (R1-фикс `GAPS_CLOSE_2`);
  - **пайплайн:** `roadmap/phase-1.md` (статус шага 6 + журнал
    `DOCS_CHECK_2`/`GAPS_CLOSE_2`/`DOCS_CHECK_3`); progress-отчёты
    `phase-1-step-6-docs-check-2.md`, `…-docs-check-3.md`;
  - **снапшоты:** `snapshot-v59` (этот) + `snapshot-v58` (**промежуточный,
    можно не коммитить** — обогнан в этой же сессии).
- **Untracked (не наши / транзиентные):** `tradingbot.iml`, `vault.hcl`.
- **`external-source-sync`:** офдок-факты OKX не менялись — ре-синхронизация
  не нужна.
