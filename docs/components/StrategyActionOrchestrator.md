# StrategyActionOrchestrator

## На какой вопрос отвечает этот файл

Кто диспетчеризует планирование одного действия стратегии за проход
(компонент): контракт, гейт повтора, маршрутизация по типу действия.

## Назначение

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
