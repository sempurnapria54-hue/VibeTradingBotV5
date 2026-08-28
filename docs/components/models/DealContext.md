# DealContext

## На какой вопрос отвечает этот файл

Что это за runtime value object `DealContext`: структура, scope одного
прохода FSM, отношение к `CalculationContext`.

## Назначение

`DealContext` — процессный runtime-context одного прохода FSM: какая
runtime-картина нужна FSM прямо сейчас для обработки сделки. RVO, не
persisted (см. `.claude/decisions/runtime-value-object.md`); собирается
`DealContextService` (см. `docs/components/DealContextService.md`), **не**
часть доменной модели `Deal`.

Не является универсальным контейнером всех свежих рыночных данных: для
расчёта цены/размера/риска `StrategyActionCalculator` собирает отдельный
свежий `CalculationContext` (см.
`docs/components/models/CalculationContext.md`).

## Структура

## Runtime graph и сборка по фактам

`Order`/`AlgoOrder`/`Position` не дублируются отдельными полями — входят
в `Deal` runtime graph (`deal.orders`/`deal.algoOrders`/`deal.positions` —
**строки эпизодов**, живая ≤1;
`docs/models/domain/aggregate/Deal.md`). Exchange facts сначала применяются
refresh-командами к БД, затем `DealContext` собирает уже обновлённый
graph:

```text
exchange facts -> REFRESH_* -> обновлённые сущности в БД
  -> Deal runtime graph -> DealContext -> FSM decision
```

Live risk позиции вычисляется по **живому эпизоду**:
`deal.livePosition != null && status == ACTIVE && externalSize > 0`
(см. `docs/models/domain/core/Position.md`). Закрытые эпизоды в предикат
не входят по построению — они не `ACTIVE`.

## Свежесть баланса и отдельные данные

Наличие `balanceContainer` не означает свежесть — её проверяет
FSM/handler перед risk-sensitive flow; при absent/stale свежесть
добывается звеном `REFRESH_BALANCE_COMMAND` через
`REFRESH_DEAL_CONTEXT_ACTION` (handler добывающие `REFRESH_*` напрямую
не эмитит, `docs/components/SystemActionExecutor.md`), и следующий
проход пересобирает `DealContext` (см.
`docs/models/domain/core/BalanceContainer.md`). Свежие `InstrumentExternalRules`,
`MarketPriceData`, `IndicatorValue`, `MarketStructure`, `MarketPhase`,
`CalculationContext` в `DealContext` не входят — собираются в
`CalculationContext` в рантайме перед расчётом. Отдельный `PositionContext`
не используется: эпизоды живут строками `deal.positions`, живой резолвится
предикатом модели `livePosition()`
(`docs/models/domain/aggregate/Deal.md`).
