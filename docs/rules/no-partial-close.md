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

  **Команда закрытия одна, эмитентов у неё два** (решение держателя
  `GAPS_CLOSE_16`, вариант B): `CLOSE_POSITION_COMMAND` эмитит
  `ExitPendingHandler` на тропе условия-перехода и `ExitActionExecutor` —
  на тропе явного действия. Правило «полное закрытие идёт только
  `CLOSE_POSITION_COMMAND`» держится. Форма действия:
  `actionKind = POSITION`, `StrategyPositionAction`,
  `StrategyActionType.EXIT_ACTION`
  (`docs/models/domain/aggregate/Strategy.md` §Действия).

  **Дочистка на тропе явного действия — не внешняя, а состав действия**
  (решение держателя, позиция С1): `CLOSE_POSITION_COMMAND` закрывает
  позицию и только её, а осмысленное действие стратегии — **выход из
  сделки**, включающий отмену живых входных ног. Порядок команд — инвариант
  `docs/rules/exit-teardown-order.md` (единственное место записи, общее для
  штатной и аварийной троп). Общий компонент teardown **не заводится**:
  носителей последовательности три, и они разной природы (реестр — там
  же, §«Носителей последовательности три»).
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
случаях исполняет `ClosePositionExecutor` (full close, reduce-only), а
эмитит её `ExitPendingHandler` либо `ExitActionExecutor` — по тропе (см.
`docs/models/domain/aggregate/Strategy.md`).

## Почему

Сквозное правило по нескольким сущностям (`Position`, `Order`,
`AlgoOrder`, `Deal`) без единственного владельца — первоисточник в
сквозном слое (`.claude/decisions/rule-source-of-truth.md`). Держит
модель закрытия простой: один механизм полного закрытия + reduce-only
действия для частичного выхода, без промежуточного
`PARTIALLY_CLOSED`-статуса. **Второй эмитент команды простоту трогает, и
это названо:** сама команда остаётся одна, но последовательность выхода
несут две тропы — довод простоты здесь перевешен ценностью явного
объявления выхода в стратегии (решение держателя `GAPS_CLOSE_16`).
Дублем это не является: тропы **разной природы** (системный kill-switch
против стратегии), а порядок у них общий и записан один раз —
`docs/rules/exit-teardown-order.md`.

## Связанное

- `docs/models/domain/core/Position.md`, `docs/lifecycles/Position.md`.
- `docs/rules/ack-not-runtime-truth.md` (факт закрытия подтверждается
  refresh-ом, не ACK).
