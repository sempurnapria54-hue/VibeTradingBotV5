# FinalizeDealExitExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_EXIT` (компонент-executor): что читает/пишет,
терминальное ребро, идемпотентность, retry-anchor, граница 6 ↔ 7.

## Назначение

Получает `FINALIZE_DEAL_EXIT` — консолидацию фактов штатного выхода после
того, как live risk снят, **и расчёт итогового `resultProfit`** (шаг 7).
**Читает** подтверждённые факты выхода (`Position` закрыта/отсутствует по
`REFRESH_POSITION`; нет live orders/algo; `Deal.CloseReason` определён) плюс
P&L-факты, добытые **до него** командами (реш.1
`docs/decisions/pnl-finalization-mechanics.md`): `PositionCloseResultExternalSnapshot`
(готовый net `realizedPnl` — `REFRESH_POSITIONS_HISTORY`) и `DealCashFlow`
(категорийная разбивка — `REFRESH_BILLS`). **Вычисляет** net-число + сверяет
сумму `DealCashFlow` с net (`docs/decisions/result-profit-source.md`); **пишет
`resultProfit`/`resultProfitCurrency` прямо на `Deal`** (persisted) в **одной
транзакции** с `DealFinalizationState(FINALIZE_EXIT).status = COMPLETED` (N7 —
durable-носитель числа = поле `Deal`, рестарт-safe). На биржу **сам не ходит**
— P&L-факты приходят готовыми снапшотами от refresh-команд; `RiskValidator` не
вызывается (`docs/rules/risk-validator-scope.md`).

## Расчёт прибыли (шаг 7) и сверка

**Расчёт `Deal.resultProfit` — здесь.** Шаг 6 поставил *механику* финализации
(retry-state, терминальное ребро, триггер, идемпотентность) и
интерим-placeholder ZERO; шаг 7 наделяет `FINALIZE_DEAL_EXIT` **расчётом и
записью числа** на `Deal`: net из `PositionCloseResultExternalSnapshot` +
разбивка из `DealCashFlow` + сверка. `MARK_DEAL_CLOSED`
(`MarkDealClosedExecutor`) число **не пишет** — читает готовое `Deal.resultProfit`,
ассертит непустоту и ставит терминал `CLOSED` (N7). Placeholder ZERO снят.
- **Сверка bills ↔ net (N10):** число **всегда** = positions-history net (bills
  его не подменяют). Расхождение **сверх epsilon** или cross-ccy движение
  (`ccy ≠ resultProfitCurrency`, напр. комиссия в OKB) → **`AnomalyReport`**
  (аудит-аномалия, `scope = INSTRUMENT`) — **не блокирует** финализацию, сделка
  идёт в `CLOSED` с net-числом (`docs/decisions/pnl-finalization-mechanics.md`
  реш.5).
- Внутренняя декомпозиция расчёта (выделять ли отдельный калькулятор) — деталь
  CODE шага 7. Структуры носителей —
  `docs/models/mapping/PositionCloseResult.md`,
  `docs/models/domain/other/DealCashFlow.md`.

## Терминальное ребро

Не терминал сделки. Поддерживает выходную проверку `EXIT_PENDING → CLOSED`
(`docs/components/ExitPendingHandler.md`), готовя факты к `MARK_DEAL_CLOSED`.

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, FINALIZE_EXIT)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже консолидированном выходе — no-op → `COMPLETED`.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
