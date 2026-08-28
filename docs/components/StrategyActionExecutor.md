# StrategyActionExecutor

## На какой вопрос отвечает этот файл

Кто выдаёт следующую команду одного типа действия стратегии за проход
(компонент-интерфейс): контракт, per-pass семантика, реализации.

## Назначение

`StrategyActionExecutor` — per-pass исполнитель **одного типа действия**
стратегии (`CREATE` ordinary / `CREATE` algo / …). За проход смотрит стадию
`DealActionState` и выдаёт **следующую** команду действия (`place →
refresh-подтверждение по фактам → следующая`) либо пустой `ActionPlan`
(«готово / нечего делать»). Секвенс ведёт петля по подтверждённым фактам,
не по ACK (см. `docs/decisions/fsm-execution-layering.md`). Обобщает
прежние `DealActionPlanner` (стадии/повтор) + `ServiceCommandFactory`
(эмиссия команды по типу действия), разложенные по типам действий.

## Контракт

```java
Boolean supports(StrategyAction action);
ActionPlan next(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext);
```

- `supports` — исполняет ли этот executor данное действие (по подтипу +
  `actionType`); по нему диспетчер `StrategyActionOrchestrator`
  маршрутизирует действие (см. `docs/components/StrategyActionOrchestrator.md`).
- `next` — следующая команда действия за проход по стадии
  `DealActionState` (обёрнута в `ActionPlan`), либо пустой `ActionPlan`,
  если действие ещё не готово продвигаться / завершено.

## Реализации

- `CreateOrderActionExecutor` (CREATE ordinary order,
  `docs/components/CreateOrderActionExecutor.md`);
- `CreateAlgoOrderActionExecutor` (CREATE standalone algo-order,
  `docs/components/CreateAlgoOrderActionExecutor.md`);
- `ExitActionExecutor` (`EXIT_ACTION` над `StrategyPositionAction`,
  `docs/components/ExitActionExecutor.md`) — компонент-док заведён
  решением держателя (валидация `GAPS_CLOSE_17`, позиция П16) и входит в
  объём `CODE` шага 7; в перечне его **не было** (P7 `DOCS_CHECK_24`).

**Перечень пересобирается по составу компонент-доков, а не пополняется по
памяти правки.** Исполнители действий `REPLACE` и `CANCEL` в нём
отсутствуют потому, что их **нет**, а не потому, что перечень отстал:
диспетчер на такое действие возвращает пустой `ActionPlan`
(`docs/components/StrategyActionOrchestrator.md`), и это
зарегистрированная дельта `CODE` (`.claude/work/backlog.md` §«Шаг 7 —
исполнительный хвост»), а не доковый пробел.
