# FinalizeDealExitExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_EXIT` (компонент-executor): что читает/пишет,
терминальное ребро, идемпотентность, retry-anchor, граница 6 ↔ 7.

## Назначение

Получает `FINALIZE_DEAL_EXIT` — консолидацию фактов штатного выхода после
того, как live risk снят. **Читает** подтверждённые факты выхода (`Position`
закрыта/отсутствует по `REFRESH_POSITION`; нет live orders/algo; fills
загружены по `REFRESH_FILLS`, если нужны для PnL; `Deal.CloseReason`
определён). **Пишет** консолидированный результат выхода на runtime graph
сделки (готовит её к терминальному `MARK_DEAL_CLOSED`) и
`DealFinalizationState(FINALIZE_EXIT).status = COMPLETED`. На биржу сам не
ходит; `RiskValidator` не вызывается (`docs/rules/risk-validator-scope.md`).

## Граница 6 ↔ 7 (расчёт прибыли)

**Расчёт `Deal.resultProfit` сюда не входит** — он отнесён к **шагу 7**
(граница 6 ↔ 7, 2026-06-21; `docs/lifecycles/Deal.md` §Терминальный
контракт финализации, `docs/integrations/okx/contracts/account-bills.md`).
Шаг 6 — *механика* финализации (retry-state, терминальное ребро, триггер,
идемпотентность); сам PnL-расчёт (`sum(DealCashFlow.amount)` и т. п.) — шаг
7 (OKX-Q3 / DEAL-Q2).

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
