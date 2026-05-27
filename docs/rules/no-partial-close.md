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
