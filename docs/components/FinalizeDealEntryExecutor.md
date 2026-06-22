# FinalizeDealEntryExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `FINALIZE_DEAL_ENTRY` (компонент-executor): что читает/пишет,
терминальное ребро, идемпотентность, retry-anchor.

## Назначение

Получает `FINALIZE_DEAL_ENTRY` — консолидацию результата входа после того,
как entry order финализирован и позиция подтверждена. **Читает**
подтверждённые факты входа (entry `Order` финализирован; `Position` активна
и соответствует сделке/инструменту/направлению; при необходимости цена
входа / fills через уже выполненный `REFRESH_FILLS`). **Пишет**
консолидированный результат входа на runtime graph сделки (фиксирует, что
вход завершён) и `DealFinalizationState(FINALIZE_ENTRY).status = COMPLETED`.
На биржу сам не ходит — опирается на уже добытые факты; новых торговых
решений не принимает (`RiskValidator` не вызывается, см.
`docs/rules/risk-validator-scope.md`).

## Терминальное ребро

Не терминал сделки. Поддерживает выходную проверку
`ENTRY_SUBMITTED → ENTRY_FINALIZED` (`docs/components/EntrySubmittedHandler.md`):
сам статус `Deal` двигает FSM по фактам, executor лишь консолидирует
результат входа и закрывает свою финализационную строку.

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, FINALIZE_ENTRY)` (база
  `Retryable`; не `DealActionState` — финализация не привязана к
  `StrategyAction`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже консолидированном входе — no-op → `COMPLETED`, второй финализации
  не плодит.
- Падение → `RETRY_PENDING`/`FAILED` по
  `docs/components/RetryPolicyService.md` и
  `docs/rules/runtime-error-classification.md`.

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
