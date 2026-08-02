# MarkDealErrorExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_ERROR_COMMAND` (компонент-executor): что читает/пишет,
ребро в `ERROR`, идемпотентность, retry-anchor.

## Назначение

Получает `MARK_DEAL_ERROR_COMMAND` — пометку ошибочного состояния сделки; первое
исполнение системного действия **`FINALIZE_DEAL_ERROR_ACTION`**
(`docs/components/SystemActionExecutor.md`). **Читает** факт
аварии/нарушения, обнаруженный handler'ом или execution boundary
(`docs/rules/runtime-error-classification.md`,
`docs/rules/controlled-exchange-exceptions.md`). **Пишет** `Deal.status =
ERROR` (non-terminal runtime status для `ErrorHandler`/safety-flow) — в
одной транзакции с завершением своего исполнения. Торговых решений не
принимает; `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Ребро статуса

`* → ERROR` (любой active runtime status). `ERROR` — **не** terminal:
дальнейший разбор ведёт `docs/components/ErrorHandler.md`; аварийный терминал
`ERROR → EMERGENCY_CLOSED` после подтверждённого снятия live risk ставит
**`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`** (`docs/components/MarkDealEmergencyClosedExecutor.md`,
best-effort число, шаг 7 — `docs/decisions/pnl-finalization-mechanics.md` реш.3).
`MARK_DEAL_ERROR_COMMAND` сам терминал не ставит — он переводит сделку под safety-flow.
Сюда же сходится ошибочная тропа неисчислимой финализации
(`MarkDealClosedExecutor` после исчерпания retry — DEAL-Q2,
`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»).

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_ERROR_ACTION`
  (первое исполнение действия; вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`).
- **Идемпотентность** — факт `Deal.status = ERROR`: повтор на уже
  помеченной сделке — no-op.
- Падение записи самой пометки → `RETRY_PENDING`/`FAILED`
  (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
