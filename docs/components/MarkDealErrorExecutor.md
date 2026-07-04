# MarkDealErrorExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_ERROR` (компонент-executor): что читает/пишет,
ребро в `ERROR`, идемпотентность, retry-anchor.

## Назначение

Получает `MARK_DEAL_ERROR` — пометку ошибочного состояния сделки. **Читает**
факт аварии/нарушения, обнаруженный handler'ом или execution boundary
(`docs/rules/runtime-error-classification.md`,
`docs/rules/controlled-exchange-exceptions.md`). **Пишет** `Deal.status =
ERROR` (non-terminal runtime status для `ErrorHandler`/safety-flow) и
`DealFinalizationState(MARK_ERROR).status = COMPLETED`. Торговых решений не
принимает; `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Ребро статуса

`* → ERROR` (любой active runtime status). `ERROR` — **не** terminal:
дальнейший разбор ведёт `docs/components/ErrorHandler.md`; аварийный терминал
`ERROR → EMERGENCY_CLOSED` после подтверждённого снятия live risk ставит
**`MARK_DEAL_EMERGENCY_CLOSED`** (`docs/components/MarkDealEmergencyClosedExecutor.md`,
best-effort число, шаг 7 — `docs/decisions/pnl-finalization-mechanics.md` реш.3).
`MARK_DEAL_ERROR` сам терминал не ставит — он переводит сделку под safety-flow.
Сюда же сходится ошибочная тропа неисчислимой финализации
(`MarkDealClosedExecutor` после исчерпания retry — DEAL-Q2,
`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).

## Идемпотентность и retry

- **Retry-anchor** — `DealFinalizationState(deal, MARK_ERROR)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор
  на уже `ERROR`-сделке — no-op → `COMPLETED`.
- Падение записи самой пометки → `RETRY_PENDING`/`FAILED`
  (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
