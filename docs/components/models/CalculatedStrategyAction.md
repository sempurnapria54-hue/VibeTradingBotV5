# CalculatedStrategyAction

## На какой вопрос отвечает этот файл

Что это за runtime value object `CalculatedStrategyAction`: структура,
что в него входит и что сознательно не входит.

## Назначение

`CalculatedStrategyAction` — результат успешного расчёта параметров
действия стратегии, который возвращает `StrategyActionCalculator` (см.
`docs/components/StrategyActionCalculator.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Описывает именно **успешный** расчёт параметров команды. Risk-policy
результат в него не входит: после успешного расчёта price/size handler
отдельно решает, нужен ли `RiskValidator` (см.
`docs/processes/risk-evaluation.md`). На вход `ServiceCommandFactory` идёт
уже рассчитанное действие.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `sourceAction` | `StrategyAction` | Исходное действие стратегии (см. `docs/models/domain/aggregate/Strategy.md`). |
| `calculatedPrice` | `CalculatedPrice` | Рассчитанная цена / набор цен (см. `docs/components/models/CalculatedPrice.md`). |
| `calculatedSize` | `CalculatedSize` | Рассчитанный размер order/algo/position action (см. `docs/components/models/CalculatedSize.md`). |
| `description` | `String` | Комментарий/пояснение расчёта для логов и аудита. |

`description` — целевое имя поля; в legacy-коде/тексте встречается
`explanation`. Имя унифицировано как `description` / `comment` по всем
calculated-RVO (`CalculatedStrategyAction`, `CalculatedPrice`,
`CalculatedSize`).

`CalculatedStrategyAction` **не** содержит `RiskValidationResult` и
`CalculatedRiskMetrics`: метрики для решения `ALLOWED / WARNING / BLOCKED`
считаются внутри risk-layer (см. `docs/components/RiskValidator.md`
§«Метрики (считает сам)»).

## Статус кода (шаг 4)

На шаге 4 (командный слой) `CalculatedStrategyAction` / `CalculatedPrice`
/ `CalculatedSize` материализованы в коде как **минимальные
command-facing заглушки** (`domain.command.calc`) — ровно то, что
потребляет `ServiceCommandFactory` (sourceAction, цена + sendToExchange,
sizeContracts). Полная структура (`StrategyPricePurpose`, набор цен,
разложение sizing) и производящий `StrategyActionCalculator` — **шаг 5**
(риск-преконтроль/расчёт). До шага 5 фабрика собирает algo-`Condition`
только с `type`; рассчитанные SL/TP/trailing-цены придут с калькулятором.
