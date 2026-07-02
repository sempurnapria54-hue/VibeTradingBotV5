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
  `docs/components/CreateAlgoOrderActionExecutor.md`).
