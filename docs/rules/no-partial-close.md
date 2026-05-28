# Запрет partial close позиции

## На какой вопрос отвечает этот файл

Какое правило системы запрещает частичное закрытие позиции через
close-position и где выполняется частичный выход.

## Правило

- `CLOSE_POSITION` используется только для **полного** закрытия
  позиции. Direct partial close через `Position` / `CLOSE_POSITION`
  запрещён.
- Частичное уменьшение (partial exit) выполняется только через
  reduce-only `Order` / `AlgoOrder` actions.
- Частичное уменьшение — это `Position.status == ACTIVE` с
  обновлённым `externalSize`, а не отдельный статус и не partial
  close.

### Механизм partial exit

Partial exit идёт через трассируемые runtime-сущности:
`StrategyOrderAction` / `StrategyAlgoOrderAction` → `CREATE_* → SUBMIT_*
→ REFRESH_*`/fills/history → `DealActionState.COMPLETED`. Обязательные
свойства action: reduce-only semantics, stable client id, связь через
`DealActionState`, восстановление через fills/history/refresh, запрет на
увеличение позиции. Для reduce-only partial exit `RiskValidator` не
вызывается — handler выполняет minimal safety/invariant checks (см.
`docs/rules/risk-validator-scope.md`).

### Коды нарушения инварианта

Нарушения partial-exit инварианта — safety/invariant violation (не
risk-policy check `RiskValidator`): `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`, `DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`
(direct partial close через `StrategyPositionAction` / `CLOSE_POSITION`).
Коды — `docs/components/models/RiskCheckResult.md` (`RiskCheckCode`).
`StrategyPositionAction.actionType` — только `CLOSE_FULL` (см.
`docs/models/core/Strategy.md`).

## Почему

Сквозное правило по нескольким сущностям (`Position`, `Order`,
`AlgoOrder`, `Deal`) без единственного владельца — первоисточник в
сквозном слое (`.claude/decisions/rule-source-of-truth.md`). Держит
модель закрытия простой: один механизм полного закрытия + reduce-only
действия для частичного выхода, без промежуточного
`PARTIALLY_CLOSED`-статуса.

## Связанное

- `docs/models/core/Position.md`, `docs/lifecycles/Position.md`.
- `docs/rules/ack-not-runtime-truth.md` (факт закрытия подтверждается
  refresh-ом, не ACK).
