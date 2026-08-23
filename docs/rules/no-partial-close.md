# Запрет partial close позиции

## На какой вопрос отвечает этот файл

Какое правило системы запрещает частичное закрытие позиции через
close-position и где выполняется частичный выход.

## Правило

- `CLOSE_POSITION_COMMAND` используется только для **полного** закрытия
  позиции. Direct partial close через `Position` / `CLOSE_POSITION_COMMAND`
  запрещён.
- Полное закрытие выражается **двумя способами, и оба законны** (решение
  держателя `GAPS_CLOSE_16`; прежняя клауза «полное закрытие — **не**
  действие стратегии» снята):
  - **условие-переход** `MANAGING → EXIT_PENDING` — шаг `EXIT` несёт
    только условие, действий у него нет;
  - **явное действие шага `EXIT`** — стратегия называет выход действием, а
    не только условием.

  **Механизм закрытия при этом один и тот же:** market-close исполняет
  `ExitPendingHandler` командой `CLOSE_POSITION_COMMAND`. Второй способ
  добавляет **способ объявить** выход, а не второй механизм его
  исполнения — это и сохраняет простоту, ради которой правило вводилось
  (§Почему). *(Форма действия — открытая развилка, `.claude/work/progress/phase-1-step-7-gaps-close-16.md` §«Развилки, возвращаемые на валидацию», позиция «Форма действия полного закрытия».)*
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
(direct partial close через `CLOSE_POSITION_COMMAND`).
Коды — `docs/components/models/RiskCheckResult.md` (`RiskCheckCode`).
Выход выражается условием-перехода `MANAGING → EXIT_PENDING` **либо**
явным действием шага `EXIT` (§Правило); `CLOSE_POSITION_COMMAND` в обоих
случаях исполняет `ExitPendingHandler` (full close, reduce-only; см.
`docs/models/domain/aggregate/Strategy.md`).

## Почему

Сквозное правило по нескольким сущностям (`Position`, `Order`,
`AlgoOrder`, `Deal`) без единственного владельца — первоисточник в
сквозном слое (`.claude/decisions/rule-source-of-truth.md`). Держит
модель закрытия простой: один механизм полного закрытия + reduce-only
действия для частичного выхода, без промежуточного
`PARTIALLY_CLOSED`-статуса. **Второй способ объявить выход простоты не
трогает:** механизм закрытия остаётся один, множится только форма записи
намерения в стратегии.

## Связанное

- `docs/models/domain/core/Position.md`, `docs/lifecycles/Position.md`.
- `docs/rules/ack-not-runtime-truth.md` (факт закрытия подтверждается
  refresh-ом, не ACK).
