# StrategyActionOrchestrator

## На какой вопрос отвечает этот файл

Кто диспетчеризует планирование одного действия стратегии за проход
(компонент): контракт, гейт повтора, маршрутизация по типу действия.

## Назначение

`StrategyActionOrchestrator` — диспетчер действия. Для выбранного
handler'ом `StrategyAction` гейтит повтор и делегирует прогресс
подходящему `StrategyActionExecutor` (по типу действия), возвращая
`ActionPlan`. Сам команды не исполняет и статус сделки не двигает — это
делает handler / оркестратор петли. Обобщает прежний `DealActionPlanner`
(стадии/повтор) поверх per-type executor'ов (обобщение
`ServiceCommandFactory`). См. `docs/decisions/fsm-execution-layering.md`.

## Контракт

```java
ActionPlan plan(StrategyStep step, StrategyAction action, DealActionState state, DealContext dealContext);
```

- **Гейт повтора.** Если `DealActionState` в `RETRY_PENDING`: не наступил
  `nextRetryAt` → пустой `ActionPlan` (ждём backoff); наступил →
  пере-эмит со стадии повтора (target отсутствует → `PLANNED`; target
  создан → `CREATED`; `docs/lifecycles/DealActionState.md`).
- **Маршрутизация.** Ищет первый `StrategyActionExecutor`, чей
  `supports(action)` истинен, и делегирует ему `next(...)`; нет
  подходящего → пустой `ActionPlan`.

`ActionPlan` (команда / risk-block / ошибка расчёта / пусто) потребляет
FSM handler через `DealFsmSupport.reactToPlan(...)`.
