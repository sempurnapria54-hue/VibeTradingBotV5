# DOCS_CHECK_4 — пост-хок концепт-гейт §6a шага 6 фазы 1

## На какой вопрос отвечает этот файл

Каков исход пост-хок концепт-гейта §6a (concept-review по пост-sync докам)
для концепт-инкрементов, въехавших на CODE шага 6.

## Контекст

- **Под-шаг:** §6a (роадмап-процесс `roadmap-step-execution.md`), прогон как
  `DOCS_CHECK_4` (сквозная нумерация; концепт-гейт CODE дошёл до `DOCS_CHECK_3`).
- **Фокус:** `concept-review` — **только доки** (doc↔doc целостность, name-level
  пробелы, неотвеченные вопросы); код не читался, docs↔code не сверялись (их
  закрыл `SYNC_DOCS_FROM_CODE`).
- **Предмет:** 6 концепт-инкрементов, миновавших до-CODE концепт-гейт.
- **Прогон:** независимый ревьюер + независимая верификация блокеров (CC грепом
  подтвердил 6a/3/4/6b/6c на целевых доках).

## Стадия остановки

Прошёл все стадии (стадия 2). Гейт стадии 0 не сработал: HOLD-Q1 закрыт;
INSTR-Q2-остаток помечен non-gating для шага 6.

## Чисто (пробелов нет)

- **Инкремент 2 — Retry re-arm.** Матрица `DealActionState`
  (`RETRY_PENDING → PLANNED|CREATED|FAILED`), `StrategyActionOrchestrator.md`
  (re-arm target==null→PLANNED / target→CREATED) и «кто пишет статус» согласованы.
- **Инкремент 5 — D-B3 recovery-by-clientId.** `SubmitOrderExecutor.md` /
  `SubmitAlgoOrderExecutor.md` / `RetryPolicyService.md` §Опасные команды /
  `ack-not-runtime-truth.md` согласованы.
- **Инкремент 6, часть — L3/L4-классификация + слоистость FSM.**
  `instrument-hold`/`exchange-hold`/`controlled-exchange-exceptions`/
  `risk-creating-entry-protection` согласованы (бесстоповая = L3,
  controlled-violation = безусловный L4 доминирует, HOLD-Q1); слоистость
  петля→handler→orchestrator→executor→command согласована.

## Пробелы (нужен GAPS_CLOSE_4)

### ПРОБЕЛ 6a — kill-switch: команда vs не-команда (несогласованность, БЛОКЕР)
`fsm-execution-layering.md` упраздняет «`KILL_SWITCH` — команда» (тип
`EXECUTE_KILL_SWITCH` убран, kill-switch — side-executor). Но
`action-orchestration-vs-command.md` (владелец CMD-Q6) продолжает: заголовок
«почему REPLACE — действие, а `KILL_SWITCH` — команда» (стр.7), §«`KILL_SWITCH`
— отдельная команда» (стр.55), «оправдывает её статус команды» (стр.70). И
`DealStateMachine.md:57` — «команда-с-внутренними-шагами (`KILL_SWITCH`)…
остаётся командой», со ссылкой на `KillSwitchExecutor.md`, который утверждает
обратное (противоречивая кросс-ссылка). **CC подтвердил грепом.** Прямое
doc↔doc противоречие на таксономии гейтящегося инкремента. Тип: несогласованность.
**Закрыть:** переформулировать «`KILL_SWITCH` — команда» в этих двух доках под
`fsm-execution-layering.md` — сохранив рациональ CMD-Q6 (self-contained
синхронный teardown, не зависит от петли), но как side-executor вне реестра, не
как `ServiceCommandType`.

### ПРОБЕЛ 3 — частичный unique-index `uk_deal_active_instrument` не задан (name-level, БЛОКЕР)
DB-инвариант «одна незакрытая сделка на инструмент» через частичный UNIQUE нигде
не документирован. `idempotency-via-unique.md` делегирует конкретные ключи в
модель, но `Deal.md` (aggregate) **не имеет §Персистентность** и не несёт ни
`uk_deal_active_instrument`, ни предиката «какие статусы = активная», ни
benign-гонки вставки (ср. `Instrument.md`, где §Персистентность есть).
`trading-constraints.md` относит enforcement к app-check + `AnomalyJob`, не к
DB-индексу — расходится с инкрементом. **CC подтвердил: в `Deal.md` §Персистентность
отсутствует.** Тип: name-level (предикат активных статусов — load-bearing для
миграции). **Закрыть:** §Персистентность в `Deal.md` (индекс + предикат +
benign insert-race); выровнять enforcement-историю в `trading-constraints.md`.

### ПРОБЕЛ 4 — set-leverage inline: owner-док молчит + противоречие размещения + лаг INSTR-Q2 (name-level + несогласованность)
`SubmitOrderExecutor.md` (названный owner) **не упоминает set-leverage** (нет
inline-write, idempotency, сужения «только открывающие»). **CC подтвердил.**
`PrecheckHandler.md` размещает set-leverage в рабочей логике PRECHECK и объявляет
представление «нерешённым (деталь CODE): отдельная команда `SET_LEVERAGE` vs
inline» — противоречит инкременту (решено: inline в submit-executor). `INSTR-Q2`
в `open-questions.md` держит остаток открытым, хотя код закрыл (inline). Тип:
name-level + несогласованность. **Закрыть:** специфицировать inline set-leverage в
`SubmitOrderExecutor.md`; устранить противоречие в `PrecheckHandler.md`; закрыть
INSTR-Q2-остаток.

### ПРОБЕЛ 6b — `SafetyHoldCoordinator` / `HoldSignal` не специфицированы (name-level)
Реактивная координация (L3/L4-решение, дёрганье kill-switch, оркестрация
`AnomalyReport`) держится на `SafetyHoldCoordinator` и сигнале `HoldSignal` —
упомянуты по имени в ≥6 доках, но **owner/model-дока нет** (`SafetyHoldCoordinator.md`
не существует — CC подтвердил). `DealOrchestratorJob.md` описывает лишь «реагирует
на hold-сигнал» без структуры `HoldSignal` (scope/код/источник) и без механики
координатора; `KillSwitchService` (`fireInstrument`/`fireExchange`) тоже без дока.
Тип: name-level (центральный компонент инкремента без спеки). **Закрыть:** новый
компонент-док `SafetyHoldCoordinator` (+ при необходимости `KillSwitchService`) и
модель `HoldSignal`.

### ПРОБЕЛ 1 — placeholder-ZERO прибыли не заявлен (name-level + латентная несогласованность, мягкий)
Step 6 пишет `resultProfit = ZERO + settle currency` как placeholder (реальный
PnL — шаг 7; отсрочка помечена — не пробел). Но интерим-значение placeholder
нигде не заявлено: `MarkDealClosedExecutor.md` — «пишет обязательные resultProfit»,
читая «готовый результат» из `FinalizeDealExitExecutor`, а тот (§Граница 6↔7) —
«расчёт resultProfit сюда не входит… шаг 7» (апстрим числа не производит, а
`MARK_DEAL_CLOSED` обязан записать — разрешается placeholder'ом, но не сказано).
`Deal.md`: «resultProfit=0 допустим только как результат расчёта, не как fallback»
— placeholder ZERO не подпадает. Тип: name-level + латентная несогласованность.
**Закрыть:** оговорить step-6 placeholder-ZERO как явный интерим; снять напряжение
с клаузой «ZERO только как результат расчёта».

### ПРОБЕЛ 6c — битая кросс-ссылка «§8.C» (несогласованность, минор)
`EntryFinalizedHandler.md:39,52` — «→ L3-холд (`markErrorStopless`, §8.C
`instrument-hold.md`)». В `instrument-hold.md` **нет §8.C** (секция живёт в
пайплайн-файле `phase-1-step-6-holds-design.md`). **CC подтвердил.** Тип:
несогласованность (неверная атрибуция клаузы). **Закрыть:** поправить ссылку.

## Блокирующие open-questions

Жёстких гейтов нет. `INSTR-Q2`-остаток релевантен Пробелу 4 (лаг доков за кодом,
не самостоятельный гейт); HOLD-Q1 закрыт.

## Сводка

6 пробелов: **2 блокера** (6a таксономия kill-switch, 3 unique-index), 4 не-блокера
(4 set-leverage, 6b координатор, 1 placeholder-ZERO, 6c ссылка). По типам:
несогласованности — 6a, 4(часть), 6c; name-level — 3, 4(часть), 6b, 1. Все —
doc-gaps, закрываемы документированием as-built решений/структур (пользовательских
развилок нет: концепты решены на CODE/holds-design, доки лагают).

**Исход: §6a НЕ пройден → `GAPS_CLOSE_4`**, затем перепрогон concept-review
(`DOCS_CHECK_5`); чисто → шаг 6 `DONE`. Инкременты 2 и 5 + L3/L4 + слоистость —
чисты, повторной проверки не требуют.

## Исход §6a после GAPS_CLOSE_4 / DOCS_CHECK_5 / GAPS_CLOSE_5 — ПРОЙДЕН

- **GAPS_CLOSE_4** (docs←code, документирование as-built): закрыты все 6 пробелов.
  6a — kill-switch переклассифицирован «команда»→side-executor
  (`action-orchestration-vs-command.md`, `DealStateMachine.md`); 3 — §Персистентность
  в `Deal.md` (`uk_deal_active_instrument` предикат `NOT IN (CLOSED, EMERGENCY_CLOSED)`,
  ERROR активен; support-индексы; benign insert-race) + `trading-constraints.md`
  (app-gatekeeper primary + DB partial-unique defense-in-depth); 4 — inline
  set-leverage в `SubmitOrderExecutor.md`, снято противоречие `PrecheckHandler.md`,
  **INSTR-Q2 закрыт**; 6b — новые `SafetyHoldCoordinator.md` / `HoldSignal.md`
  (+`HoldScope`) / `KillSwitchService.md`; 1 — интерим-placeholder ZERO примирён
  (`Deal.md` §Итоговый PnL + `MarkDealClosedExecutor.md`); 6c — ссылка §8.C починена.
- **DOCS_CHECK_5** (перепрогон concept-review): все 6 пробелов подтверждены
  закрытыми; всплыл 1 новый минорный остаток — `HoldSignal.md` заявлял `HoldScope`
  общим с `AnomalyReport`, а `AnomalyReport.md` поля `scope` не нёс (та же
  docs↔code-дивергенция: `AnomalyReport.java` имеет `scope`, док отставал).
- **GAPS_CLOSE_5** (docs←code): в `AnomalyReport.md` добавлено поле
  `scope: HoldScope` (таблица + инвариант локуса); claim `HoldSignal.md` теперь
  двусторонне согласован. **CC верифицировал грепом.**

**Гейт §6a ПРОЙДЕН** — все концепт-инкременты согласованно специфицированы в
пост-sync доках. Совокупно с зафиксированными CODE-фокусами
(`conventions`/`performance`/`disaster`, находки закрыты) и `divergence`-исходом
SYNC — **все гейты `DONE` шага 6 удовлетворены**.

### Follow-up'ы (не блокеры, вне §6a-скоупа)

- **`DealTransition`** — runtime-RVO, несущий `holdSignal`, без своего model-дока
  (`docs/components/models/DealTransition.md`); недокументированный RVO, не битая
  ссылка. Минорный doc-долг.
- **`idempotency-via-unique.md`** формулирует «upsert (вставить/обновить)», а
  `Deal.md` реализует «вставить/пропустить» (benign-skip) — семантический нюанс
  зонтичного правила; ядро («ключ — в модели») удовлетворено.
- **`Instrument.leverage`** — доки называют «потолок/умолчание», код (`ensureLeverage`)
  берёт напрямую как рабочее плечо; тонкая docs↔code-несогласованность характеристики.
