# Шаг 6 фазы 1 — FSM + живая оркестрация — DONE (2026-07-03)

## На какой вопрос отвечает этот файл

Что сделано на шаге 6 фазы 1 (FSM + живая оркестрационная петля) и где
детальные артефакты.

## Итог

Материализованы конечный автомат сделки (FSM: состояния/переходы +
handler'ы) и живая оркестрационная петля (`DealOrchestratorJob` driving +
`EntryScannerJob`), REPLACE-оркестрация, per-deal concurrency-guard и
механика финализации (финализационные executor'ы, терминальные рёбра,
retry-state финализации). Шаг прошёл весь docs-first процесс, оба жёстких
гейта `DONE` (D-B3 recovery-by-clientId, D-M1 сериализация прохода) —
built, и закрыт в `DONE`; все гейты §7 — с зафиксированным исходом.

## Под-шаги

- `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2` → `GAPS_CLOSE_2` →
  `DOCS_CHECK_3` (концепт + торговый фокусы, до `CODE` — чисто): закрыты
  N1-N15 (в т.ч. N9/TR1 бесстоповый вход, N11 `maxAttempts`, N12
  Precheck-чистота); граница 6↔7 зафиксирована.
- **Дизайн реактивных холдов L3/L4-CRITICAL** (отдельный заход): рефрейм
  deferral'а D2 — реактивный контур холда (триггерится проходом
  оркестратора: handler классифицировал CRITICAL → координация холд +
  журнал + kill-switch) отделён от ops-контура шага 8; материализован в
  `SafetyHoldCoordinator`/`HoldSignal`.
- `CODE`: написан код (~50 файлов), прогнаны три независимых
  адверсариальных фокуса (`conventions` / `performance` / `disaster`;
  `security` деактивирован до шага 9) + независимая верификация фиксов.
  Закрыты оба disaster-blocker'а (B1 RETRY_PENDING-зависание action-команд,
  B2 несоблюдение `nextRetryAt`), major'ы M3-M5, perf-M1.
- **Сверка scope `CODE` на полноту**: построчно со scope; весь scope built,
  кроме двух обоснованных deferral'ов (D1 REPLACE-leg-оркестрация → backlog
  §Хвост шага 4; D2 error-градация уровни 3-4 ops-часть → backlog §Шаг 6).
  Снят орфан-метод `DealFsmSupport.killSwitchCommand()`.
- `SYNC_DOCS_FROM_CODE` (`divergence`, docs←code): выровнены 52 дока под
  as-built (Stage 2/3-рефактор: `ServiceCommandFactory` распилен на
  `StrategyActionOrchestrator` + per-type executor'ы +
  `DealFinalizationCommandFactory`; `OrchestratorPassLock`→`JobExecutionGuard`;
  kill-switch package-move + реактивность через
  `HoldSignal`→`SafetyHoldCoordinator`; снапшот v64).
- `§6a` (пост-хок концепт-гейт, прогнан как `DOCS_CHECK_4` →
  `GAPS_CLOSE_4` → `DOCS_CHECK_5` → `GAPS_CLOSE_5`): 6 концепт-инкрементов,
  миновавших до-CODE гейт; закрыты 2 блокера (таксономия kill-switch как
  side-executor, а не «команда»; частичный unique-index
  `uk_deal_active_instrument` — app-gatekeeper + DB defense-in-depth) и
  4 не-блокера (inline set-leverage у owner-дока — **INSTR-Q2 закрыт**;
  спеки `SafetyHoldCoordinator`/`HoldSignal`/`KillSwitchService`;
  placeholder-ZERO; ссылка §8.C; `AnomalyReport.scope: HoldScope`). **§6a
  ПРОЙДЕН.**

## Что построено (код)

- **FSM**: `DealStateMachine` + 7 handler'ов + `DealFsmSupport` /
  `DealActionPlanner` / `MarketConditionContextFactory`.
- **Финализационная под-спина**: `DealFinalizationState` (+ entity / repo /
  dataservice / mapper, миграция `V9`); 4 финализационных executor'а;
  эмиссия через `DealFinalizationCommandFactory` + retry-anchor в
  диспетчере.
- **Оболочка петли**: `DealOrchestratorJob` (driving) + `EntryScannerJob` +
  фасады + конфиг + ручные триггеры; per-deal concurrency-guard.
- **Оркестрация действий**: `StrategyActionOrchestrator` + per-type
  executor'ы (Stage 2/3-рефактор `ServiceCommandFactory`).
- **Жёсткие гейты**: **D-B3** (recovery-by-clientId в submit-executor'ах),
  **D-M1** (сериализация прохода — as-built через `JobExecutionGuard`,
  raw-JDBC advisory lock `OrchestratorPassLock` снят из фазы 1).
- **Безопасность/холды**: kill-switch как side-executor + `KillSwitchService`;
  реактивный CRITICAL-холд через `HoldSignal` → `SafetyHoldCoordinator`;
  N9/TR1 защита бесстопового входа; set-leverage.
- **Ошибки**: `@RestControllerAdvice` + `ErrorApiResponse` (внешняя
  поверхность).

## Ключевые решения

- Kill-switch — **side-executor**, не «команда» (таксономия §6a).
- «Одна активная сделка на инструмент» — **частичный unique-index**
  `uk_deal_active_instrument` (DB defense-in-depth) + app-gatekeeper.
- D-M1 сериализация прохода в фазе 1 — через in-process `JobExecutionGuard`;
  raw-JDBC advisory lock отложен на фазу 3 (мультиинстанс; `tech-radar`).
- **INSTR-Q2** (тайминг set-leverage) закрыт: inline у owner-дока,
  «каждый ордер → открывающий» — намеренное сужение фазы 1.

## Открытые хвосты (non-gating форвард)

- **D1 REPLACE-leg-оркестрация** (фабрика ног возвращает empty) → backlog
  §Хвост шага 4.
- **D2 error-градация уровни 3-4, ops-часть** (`AnomalyReport`-реакция,
  зависит от шага 8 + status-lifecycle backlog п.9) → backlog §Шаг 6.
- Биржевой REST в `@Transactional` (M6), перф M2-M5 → backlog.
- **placeholder-ZERO** `resultProfit` на терминале → шаг 7 (расчёт числа
  P&L заменяет заглушку).

## Детальные артефакты

Подпапка `2026-07-03-phase-1-step-6-fsm-orchestration/`:
`phase-1-step-6-docs-check-1.md`, `-docs-check-2.md`, `-docs-check-3.md`
(до-CODE концепт-гейт), `phase-1-step-6-holds-design.md` (дизайн реактивных
холдов L3/L4), `phase-1-step-6-code.md` (CODE + сверка scope + концепт-
инкременты для §6a), `phase-1-step-6-docs-check-4.md` (пост-хок §6a:
`DOCS_CHECK_4/5`, `GAPS_CLOSE_4/5`).
