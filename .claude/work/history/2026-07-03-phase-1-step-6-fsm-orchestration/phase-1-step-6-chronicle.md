# Хроника шага 6 Фазы 1 — FSM и живая оркестрация

## На какой вопрос отвечает этот файл

Какова хроника прохождения шага 6 Фазы 1 по под-шагам
(перенесена из phase-1.md при расщеплении 2026-07-06).

## Хроника

- **Уточнение границы шага 6 ↔ 7 (2026-06-21, до старта под-шагов):**
  живая оркестрация отнесена к шагу 6, не 7. Шаг 6 — «FSM + живая
  оркестрация»: помимо статусной механики и handler'ов в него вошли
  оркестрационная петля (`DealOrchestratorJob` driving),
  REPLACE-оркестрация, per-deal concurrency-guard (D-M1) и механика
  финализации (финализационные executor'ы, терминальные рёбра,
  retry-state финализации). Шаг 7 «Сделки и P&L» сужен до расчёта
  `resultProfit` и агрегации `Deal` (DEAL-Q2 закрыт как терминальный
  контракт на шаге 6; на шаге 7 остаётся *число* прибыли на ошибочном
  терминале); формулировка «он же оркестрирует торговый цикл» из
  строки шага 7 снята (петля уехала в шаг 6). Жёсткие гейты `DONE`
  D-B3/D-M1 (деньги-дубли из разбора ревью шага 4, 2026-06-12:
  SUBMIT recovery-by-clientId; concurrency-guard исполнения команды)
  привязаны к шагу 6 — петлю нельзя включать и шаг не уходит в
  `DONE`, пока оба не закрыты (определения — `backlog.md`
  §Хвост шага 4).
- **Шаг 6 → `DOCS_CHECK_1` (2026-06-22):** стартован шаг «FSM + живая
  оркестрация» (`TOOLING` без новых артефактов — фокусы `concept`/`trading`
  активны). Статусный костяк (процесс `deal-management`, `DealStateMachine`,
  7 handler'ов, lifecycles, command-layer) **в основном материализован**
  миграцией из архива; пробелы сосредоточены на **петле, финализации и
  операционной оболочке оркестратора**. **Не чисто** — 15 пробелов
  (N1-N15), 8 эскалаций (Э1-Э8); торговый блокер — TR1 (бесстоповый
  risk-creating вход). Гейтят `CODE`: N1 (error-политика), N2-N4/DEAL-Q1
  (финализационная под-спина), N5-N6/CMD-Q5-Q6 (REPLACE-владелец / «действие
  vs команда»), N9/TR1 (защита входа), N8 (оболочка джоба), N10 (set-leverage),
  N11 (`maxAttempts`), N12 (Precheck-чистота); N7/D-M1 — жёсткий гейт `DONE`.
  Нужен `GAPS_CLOSE_1`. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-1.md`.
- **Шаг 6 → `GAPS_CLOSE_1` (2026-06-22):** пробелы `DOCS_CHECK_1` закрыты.
  **N1** — error-политика (`docs/rules/error-handling-policy.md` +
  `docs/rules/instrument-hold.md`; TBD `codestyle` снят). **N2-N4/DEAL-Q1**
  — финализационная под-спина: дом retry-state `DealFinalizationState`
  (модель + lifecycle + `docs/decisions/deal-finalization-state-materialization.md`),
  4 executor-дока (`FINALIZE_*`/`MARK_*`), путь эмиссии (`ServiceCommand`
  +`dealFinalizationStateId`, `ServiceCommandFactory`). **N9/TR1** — инвариант
  `docs/rules/risk-creating-entry-protection.md` + снят fail-open `RiskValidator`
  (код `RISK_CREATING_ENTRY_WITHOUT_STOP`). **N5-N6/CMD-Q5-Q6** —
  `docs/decisions/action-orchestration-vs-command.md` (REPLACE-владелец —
  петля/`DealStateMachine`; `KILL_SWITCH` — команда). **N7-N8/D-M1** —
  `DealOrchestratorJob` оболочка + concurrency-guard (БД-блок на весь проход).
  **N10** — set-leverage перед ордером в `PRECHECK` (INSTR-Q2 продвинут).
  **N11** — авторитет `maxAttempts` = policy. **N12/CMD-Q4** —
  инструмент-скоупный read вне command-layer (Precheck-часть закрыта). **DEAL-Q2**
  — терминальный контракт (`Deal.md`). **N13-N15** — гигиена (стале-ссылки,
  finalization-список, scope-нота `account-bills`). Закрыты DEAL-Q1/DEAL-Q2/
  CMD-Q5/CMD-Q6; продвинуты INSTR-Q2/CMD-Q4. Далее — подтверждающий
  `DOCS_CHECK_2`. Закрытие — `.claude/work/progress/phase-1-step-6-docs-check-1.md`
  §Закрытие.
- **Шаг 6 → `DOCS_CHECK_2` (2026-06-22):** подтверждающий прогон после
  `GAPS_CLOSE_1` — три независимых ревьюер-субагента (concept ×2 + trading).
  **Все 15 пробелов `DOCS_CHECK_1` (N1-N15) + торговый блокер TR1 +
  DEAL-Q1/DEAL-Q2/CMD-Q5/CMD-Q6 подтверждены закрытыми чисто** (верификация
  атрибуции по каждому целевому доку; ripple-проверки финализации (a/b/c)
  пройдены; гейтящих open-questions нет; TR2-TR4 остаются forward, не
  регрессировали). **Почти чисто — одна минорная негейтящая гигиена-рябь R1:**
  `deal-management.md:63-64` несёт устаревший безусловный инвариант
  `resultProfit` для всех terminal — расходится с DEAL-Q2-контрактом
  (обязателен только для чистого `CLOSED`, `EMERGENCY_CLOSED` освобождён);
  правки DEAL-Q2 не пробросились в обзорный процесс-док (исполнительные доки
  контракт несут верно). По строгому гейту «чистый `DOCS_CHECK`» R1 держит
  прогон формально не-чистым. Нужен микро-`GAPS_CLOSE_2` (одна строка) +
  подтверждающий `DOCS_CHECK_3` (либо принять R1 как гигиену в составе
  `GAPS_CLOSE_2`); затем гейт `CODE` чист. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-2.md`.
- **Шаг 6 → `GAPS_CLOSE_2` (2026-06-22):** закрыта единственная находка
  `DOCS_CHECK_2` — **R1** (реконсиляция формулировки). `deal-management.md`
  §«Статусная механика и recovery»: обязательность `resultProfit`/
  `resultProfitCurrency` ограничена **чистым** terminal `CLOSED`, ошибочный
  `EMERGENCY_CLOSED` — по терминальному контракту (`docs/lifecycles/Deal.md`
  §«Терминальный контракт финализации», DEAL-Q2). Правка-cleanup (выводима из
  принятого DEAL-Q2-контракта, без вариантов). Далее — подтверждающий
  `DOCS_CHECK_3`.
- **Шаг 6 → `DOCS_CHECK_3` (2026-06-22):** узкий подтверждающий прогон после
  `GAPS_CLOSE_2` (независимый ревьюер-субагент, concept-фокус) — **чисто**.
  **R1** подтверждён закрытым чисто (`deal-management.md` согласован с
  DEAL-Q2-контрактом `Deal.md`); sweep по `docs/` — других стале-копий
  безусловного `resultProfit`-инварианта нет; новой ряби нет. **Гейт `CODE`
  пройден** (`roadmap-step-execution.md` §«Гейт `CODE` — чистый `DOCS_CHECK`»):
  concept — этот прогон, trading — чисто на `DOCS_CHECK_2` (поверхность не
  менялась). Шаг 6 готов к `CODE`; перевод за пользователем. Жёсткие гейты
  `DONE` (D-B3 / реализация D-M1) — на `CODE`/`DONE`. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-3.md`.
- **Шаг 6 → `CODE` (2026-06-22):** написан код по утверждённой концепции
  (~50 файлов в working tree, staged; `mvn clean compile` чисто на JDK 25,
  без deprecation/warnings). Материализованы: финализационная под-спина
  (`DealFinalizationState` + entity/repo/dataservice/mapper, миграция `V9`;
  4 финализационных executor'а; эмиссия через фабрику + retry-anchor в
  диспетчере), FSM (`DealStateMachine` + 7 handler'ов + `DealFsmSupport`/
  `DealActionPlanner`/`MarketConditionContextFactory`), оболочка петли
  (`DealOrchestratorJob` + `EntryScannerJob` + фасады + конфиг + триггеры),
  **D-M1** (`OrchestratorPassLock` — БД advisory lock на проход), **D-B3**
  (recovery-by-clientId в submit-executor'ах), N9/TR1 (защита бесстопового
  входа), set-leverage, error-политика (`@RestControllerAdvice` +
  `ErrorApiResponse`). **Аппрув-гейт:** три независимых адверсариальных
  фокуса (`conventions` 0/2/9, `performance` 0/2/3, `disaster` 2/4/3;
  `security` деактивирован до шага 9) + независимая верификация фиксов.
  Закрыты оба disaster-blocker'а (B1 RETRY_PENDING зависание action-команд;
  B2 несоблюдение `nextRetryAt`), major'ы M3 (финализация FAILED → ошибочная
  тропа), M4 (терминальный гейт по live orders/algo), M5 (частичный
  unique-index «одна сделка на инструмент»), perf-M1 (индексы `deals`), все
  conventions-находки. Форвард (осознанно, фаза 1): REPLACE-leg-оркестрация,
  биржевой REST в `@Transactional` (M6), перф M2-M5, tech-radar-запись по
  raw-JDBC advisory lock. Отчёт и концепт-инкременты для пост-хок гейта §6a —
  `.claude/work/progress/phase-1-step-6-code.md`. Финальный аппрув `CODE` и
  переход к `SYNC_DOCS_FROM_CODE` — за пользователем.
- **Шаг 6 → сверка scope `CODE` на полноту (2026-06-22):** поставленный `CODE`
  сверён построчно со scope (роадмап-строка + граница 6↔7 + закрытия
  `GAPS_CLOSE_1` N1-N15 + жёсткие гейты), не только на качество. **Весь scope —
  built**, кроме двух **обоснованных deferral'ов** (зафиксированы с владельцами и
  треком, не молча): **D1** REPLACE-leg-оркестрация (фабрика ног возвращает
  empty; самостоятельный refinement, базовой петле фазы 1 не нужен; `backlog.md`
  §Хвост шага 4) и **D2** error-градация уровни 3-4 — реактивный enforcement
  холдов instrument/exchange + `AnomalyReport`-реакция (зависит от `AnomalyReport`
  ops шага 8 и status-lifecycle backlog п.9; порог серии неудач — провизорный;
  `backlog.md` §Шаг 6). Внешняя поверхность + уровни 1-2 + `KillSwitchExecutor` +
  преконтроль N9/TR1 — построены. На сверке снят один дефект: орфан-метод
  `DealFsmSupport.killSwitchCommand()` (эмиссия `EXECUTE_KILL_SWITCH` без
  вызовов; конвенц-фокус пропустил) удалён. **set-leverage** — намеренное сужение
  «каждый ордер → открывающий» в submit-executor'е подтверждено как
  §6a-инкремент (доки сами отнесли тайминг к шагу 6). Жёсткие гейты `DONE`
  (D-B3 / D-M1) — оба built. Сверка — `phase-1-step-6-code.md` §Сверка scope;
  финальный аппрув `CODE` за пользователем.
- **Шаг 6 → `SYNC_DOCS_FROM_CODE` → §6a → `DONE` (2026-07-01…03):** аппрув
  `CODE` дан; `SYNC_DOCS_FROM_CODE` (фокус `divergence`, docs←code) выровнял 52
  дока под as-built (Stage 2/3-рефактор + фиксы ревью): `ServiceCommandFactory`
  распилен на `StrategyActionOrchestrator`+per-type executor'ы +
  `DealFinalizationCommandFactory`; `OrchestratorPassLock`→`JobExecutionGuard`;
  kill-switch package-move + реактивность через `HoldSignal`→`SafetyHoldCoordinator`
  (снапшот v64). **Пост-хок концепт-гейт §6a** (концепт-инкременты на CODE):
  `DOCS_CHECK_4` — 6 пробелов (2 блокера: таксономия kill-switch «команда» vs
  side-executor, частичный unique-index `uk_deal_active_instrument`; 4 не-блокера:
  inline set-leverage у owner-дока, спека `SafetyHoldCoordinator`/`HoldSignal`,
  placeholder-ZERO, ссылка §8.C). `GAPS_CLOSE_4` закрыл все 6 docs←code
  (kill-switch→side-executor; §Персистентность `Deal.md` + `trading-constraints.md`
  app-gatekeeper+DB defense-in-depth; inline set-leverage, **INSTR-Q2 закрыт**;
  новые `SafetyHoldCoordinator.md`/`HoldSignal.md`/`KillSwitchService.md`;
  placeholder-ZERO примирён; §8.C). `DOCS_CHECK_5` — подтверждено, 1 остаток
  (`AnomalyReport.scope` docs↔code-лаг); `GAPS_CLOSE_5` — `scope: HoldScope`
  добавлено. **§6a ПРОЙДЕН** — все гейты `DONE` (CODE-фокусы / `divergence` /
  §6a; жёсткие D-B3/D-M1 built) удовлетворены с зафиксированным исходом. Ролляп
  фазы без изменений (`IN_PROGRESS`: шаги 1-6 `DONE`, 7-11 `HOLD`). Отчёт §6a —
  `.claude/work/progress/phase-1-step-6-docs-check-4.md`. Дельта `GAPS_CLOSE_4/5`
  — staged для коммита в IDEA.
