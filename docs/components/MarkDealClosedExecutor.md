# MarkDealClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_CLOSED` (компонент-executor): терминальное ребро,
что читает/пишет, идемпотентность, retry-anchor, контракт обязательного
`resultProfit`.

## Назначение

Получает `MARK_DEAL_CLOSED` — **терминальное ребро штатного закрытия**.
**Читает** подтверждённое отсутствие live risk и **уже записанное на `Deal`**
число `resultProfit`/`resultProfitCurrency` (его пишет `FinalizeDealExitExecutor`
на шаге 7 — N7). **Ассертит** непустоту числа (инвариант чистого `CLOSED`) и
**пишет** терминал `Deal.status = CLOSED` + `DealFinalizationState(MARK_CLOSED).status =
COMPLETED`. Само число `MARK_DEAL_CLOSED` **не вычисляет и не пишет** — оно
durable-живёт полем `Deal` от `FINALIZE_EXIT`. `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Терминальное ребро

`EXIT_PENDING → CLOSED` (`docs/lifecycles/Deal.md`). `CLOSED` — terminal:
handler'а не имеет; обязательны `resultProfit`/`resultProfitCurrency`.
`MARK_DEAL_CLOSED` ставит терминал только после подтверждённого отсутствия
live risk (иначе — не терминализирует, остаётся в `EXIT_PENDING`/уходит в
`ERROR`).

## resultProfit на терминальном ребре и контракт неисчислимой прибыли (DEAL-Q2)

- **Расчёт и запись числа** `resultProfit` — **шаг 7**, владелец
  `FinalizeDealExitExecutor` (net из `PositionCloseResultExternalSnapshot` +
  разбивка `DealCashFlow` + сверка; пишет число **на `Deal`**,
  `docs/decisions/result-profit-source.md`,
  `docs/decisions/pnl-finalization-mechanics.md` реш.2). `MARK_DEAL_CLOSED`
  **число не считает и не пишет** — **читает `Deal.resultProfit`, ассертит
  непустоту** и ставит терминал `CLOSED` (N7).
- **Step-6 → step-7 переход (placeholder снят).** Механика шага 6 писала
  на терминале интерим-placeholder `resultProfit = BigDecimal.ZERO`, чтобы
  удовлетворить инвариант «на чистом `CLOSED` число обязательно» до расчёта
  шага 7. **Шаг 7 снимает placeholder**: число считает и пишет `FINALIZE_EXIT`
  (реальный net), а `MARK_DEAL_CLOSED` его лишь ассертит. Инвариант непустоты —
  теперь ассерт на `Deal.resultProfit`, а не запись ZERO.
- Если число временно нельзя получить — финализация **ретраится** по общему
  механизму (`DealFinalizationState`).
- Если после исчерпания retry прибыль всё ещё неисчислима —
  `DealFinalizationState(MARK_CLOSED) = FAILED`: чистый терминал `CLOSED`
  **не** ставится; сделка уходит ошибочной тропой
  (`MarkDealErrorExecutor`/`ErrorHandler`) и доходит до **ошибочного
  терминала** (`EMERGENCY_CLOSED`), не зависает живым риском. Инвариант
  «прибыль обязательна» — про чистое закрытие; ошибочный терминал на нём не
  блокируется, **но число всё равно проставляется** — фактический realized net
  (вкл. `liqPenalty`), не ноль/null (G5). Полный контракт —
  `docs/lifecycles/Deal.md` §«Терминальный контракт финализации».

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, MARK_CLOSED)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже `CLOSED`-сделке — no-op → `COMPLETED`.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
