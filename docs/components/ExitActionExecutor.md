# ExitActionExecutor

## На какой вопрос отвечает этот файл

Кто исполняет действие выхода из сделки за проход (компонент-executor):
состав команд, порядок, границы.

## Назначение

`ExitActionExecutor` — per-pass `StrategyActionExecutor` (см.
`docs/components/StrategyActionExecutor.md`) действия выхода
(`StrategyPositionAction` + `actionType = EXIT_ACTION`,
`docs/models/domain/aggregate/Strategy.md` §Действия). Заводится решением
держателя (валидация `GAPS_CLOSE_17`, позиция П16 — действие выхода входит в
объём `CODE` шага 7).

**Действие выхода — не одна команда.** `CLOSE_POSITION_COMMAND` закрывает
позицию, и всё; в стратегии закрытие одной позиции самостоятельного смысла
не имеет. Осмысленное действие — **выход из сделки**, и отмена живых входных
ног входит **в его состав**, а не является внешней дочисткой (решение
держателя, позиция С1 `GAPS_CLOSE_16`).

## Состав и порядок

Порядок задан инвариантом `docs/rules/exit-teardown-order.md` (единственное
место записи; собственной копии последовательности этот док не держит):

```text
живые входные (не reduce-only) ноги есть -> CANCEL_ORDER_COMMAND /
                                            CANCEL_ALGO_ORDER_COMMAND
живых входных ног нет                    -> CLOSE_POSITION_COMMAND
команда закрытия отправлена              -> дальше ведёт EXIT_PENDING
```

Стадия выводится из **подтверждённых фактов**, не из счётчика проходов
(`docs/decisions/command-action-boundary.md` §5); секвенс ведёт петля
(`docs/decisions/fsm-execution-layering.md`). Reduce-only ноги (защита,
частичный выход) под отмену этим действием **не идут**: они риск снимают, а
не создают, — их дочищает `ExitPendingHandler` уже после подтверждённого
закрытия.

## Границы

- **Закрытие позиции означает закрытие сделки** (решение держателя):
  закрывать одну позицию и открывать другую внутри сделки смысла нет.
  Поэтому исполнитель не запрашивает переход и не ставит терминал —
  подтверждение закрытия, финализацию числа и терминал ведёт тропа
  `EXIT_PENDING` (`docs/components/ExitPendingHandler.md`,
  `docs/decisions/pnl-finalization-mechanics.md`).
- **`RiskValidator` не вызывается** — действие risk-reducing
  (`docs/rules/risk-validator-scope.md`).
- **`DealActionState` заводится обычным порядком** — у действия есть
  runtime-сущность (`Position`), потому что исполнитель эмитит команды, а не
  запрашивает переход
  (`docs/models/domain/other/DealActionState.md`).
- **Сам команды не исполняет** и статус сделки не двигает: отдаёт `ActionPlan`,
  исполняет петля.

## Связи

- Тип действия и его форма — `docs/models/domain/aggregate/Strategy.md`
  §Действия.
- Инвариант порядка — `docs/rules/exit-teardown-order.md`.
- Команда закрытия и её исполнитель —
  `docs/components/ClosePositionExecutor.md`,
  `docs/rules/no-partial-close.md`.
- Вторая тропа выхода (условие-переход) —
  `docs/components/ExitPendingHandler.md`.
