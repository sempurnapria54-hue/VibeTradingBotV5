# CalculationContext

## На какой вопрос отвечает этот файл

Что это за runtime value object `CalculationContext`: структура, scope
сборки, отношение к `DealContext`.

## Назначение

`CalculationContext` — рабочий runtime-контекст калькуляторов, собираемый
`CalculationContextFactory` внутри `StrategyActionCalculator` (см.
`docs/components/CalculationContextFactory.md`,
`docs/components/StrategyActionCalculator.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Не заменяет `DealContext` (см.
`docs/components/models/DealContext.md`); строится из `DealContext +
StrategyAction + свежие runtime-data` и должен быть собран максимально
близко ко времени создания команды.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `deal` | `Deal` | Сделка, для которой выполняется расчёт. |
| `instrument` | `Instrument` | Инструмент сделки. |
| `strategyDetail` | `StrategyDetail` | Pinned `StrategyDetail` сделки. |
| `action` | `StrategyAction` | Действие, для которого считаются параметры. |
| `instrumentExternalRules` | `InstrumentExternalRules` | Внешние правила инструмента (tick/lot/min size, contract value, max leverage, state). |
| `marketPriceData` | `MarketPriceData` | Runtime-цены (не persisted; получаются через REST ticker перед расчётом). |
| `indicatorValues` | `List<IndicatorValue>` | Готовые значения индикаторов (через `IndicatorService`). |
| `marketStructures` | `List<MarketStructure>` | Готовые структуры рынка (через `MarketStructureService`). |
| `marketPhase` | `MarketPhase` | Актуальная фаза рынка, если нужна. |
| `balanceContainer` | `BalanceContainer` | Persisted snapshot баланса для sizing и подготовки risk-policy; context его не обновляет. |
| `activePosition` | `Position` | Активная позиция, если открыта. |
| `entryOrder` | `Order` | Entry order, если уже создан. |
| `strategyDirection` | `StrategyTradeDirection` | Направление стратегии (`LONG`/`SHORT`). |

Риск-настройки сделки (`riskPerTradePercent`) читаются из присутствующего
`strategyDetail` (pinned деталь). Отдельного поля/RVO `RiskSettings` нет
(закрыт RISK-Q1, см. `docs/decisions/per-trade-risk-policy.md`).

## Scope сборки

Один рассчитываемый `StrategyAction` = один свежий `CalculationContext`.
Общий context на весь `StrategyStep` / handler / проход не собирается
(`StrategyStep` не atomic transaction; после каждого action могут
измениться Order/AlgoOrder/Position/Balance/market facts). Подробнее —
`docs/processes/strategy-action-calculation.md`.

`MarketPriceData` в рамках одного context получается один раз и
переиспользуется. `CalculationContextFactory` не вызывает `IntegrationService`
и не создаёт `REFRESH_BALANCE`; freshness баланса обеспечивает FSM/handler
до запуска калькулятора.
