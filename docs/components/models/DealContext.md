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

| Поле | Тип | Назначение |
|---|---|---|
| `deal` | `Deal` | Сделка с runtime graph (`orders` + `algoOrders` + `position`). |
| `exchange` | `Exchange` | Биржа / exchange account (для HOLD / safety / adapter context). |
| `instrument` | `Instrument` | Торговый инструмент сделки. |
| `strategyDetail` | `StrategyDetail` | Pinned-конфигурация сделки. |
| `balanceContainer` | `BalanceContainer` | Последний persisted snapshot баланса (свежесть **не** гарантирована). |
| `actionStates` | `List<DealActionState>` | Runtime-состояние выполнения actions (recovery/retry/idempotency/target-resolution; модель — `docs/models/domain/other/DealActionState.md`). |
| `finalizationStates` | `List<DealFinalizationState>` | Runtime-состояние финализационных команд (`FINALIZE_*`/`MARK_*`) сделки — recovery/retry/idempotency финализации; модель — `docs/models/domain/other/DealFinalizationState.md`. По нему `ServiceCommandFactory` выбирает финализационную команду за проход. |

## Runtime graph и сборка по фактам

`Order`/`AlgoOrder`/`Position` не дублируются отдельными полями — входят
в `Deal` runtime graph (`deal.orders`/`deal.algoOrders`/`deal.position`;
≤1 `Position` на `Deal`). Exchange facts сначала применяются
refresh-командами к БД, затем `DealContext` собирает уже обновлённый
graph:

```text
exchange facts -> REFRESH_* -> обновлённые сущности в БД
  -> Deal runtime graph -> DealContext -> FSM decision
```

Live risk позиции вычисляется: `deal.position != null && status ==
ACTIVE && externalSize > 0` (см. `docs/models/domain/core/Position.md`).

## Свежесть баланса и отдельные данные

Наличие `balanceContainer` не означает свежесть — её проверяет
FSM/handler перед risk-sensitive flow; при absent/stale handler создаёт
`REFRESH_BALANCE` и пересобирает `DealContext` (см.
`docs/models/domain/core/BalanceContainer.md`). Свежие `InstrumentExternalRules`,
`MarketPriceData`, `IndicatorValue`, `MarketStructure`, `MarketPhase`,
`CalculationContext` в `DealContext` не входят — собираются в
`CalculationContext` в рантайме перед расчётом. Отдельный `PositionContext`
не используется (≤1 `Position` на `Deal`).
