# CalculatedStrategyAction

## На какой вопрос отвечает этот файл

Что это за `CalculatedStrategyAction`.

## Назначение

`CalculatedStrategyAction` — результат успешного расчёта параметров
действия стратегии, который возвращает `StrategyActionCalculator` (см.
`docs/components/StrategyActionCalculator.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Описывает именно **успешный** расчёт параметров команды. Risk-policy
результат в него не входит: после успешного расчёта price/size handler
отдельно решает, нужен ли `RiskValidator` (см.
`docs/processes/risk-evaluation.md`). На вход per-type
`StrategyActionExecutor` (под `StrategyActionOrchestrator`) идёт
уже рассчитанное действие.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `sourceAction` | `StrategyAction` | Исходное действие стратегии (см. `docs/models/domain/aggregate/Strategy.md`). |
| `calculatedPrice` | `CalculatedPrice` | Рассчитанная цена / набор цен (см. `docs/components/models/CalculatedPrice.md`). |
| `calculatedSize` | `CalculatedSize` | Рассчитанный размер order/algo action (см. `docs/components/models/CalculatedSize.md`). |
| `description` | `String` | Комментарий/пояснение расчёта для логов и аудита. |

`description` — целевое имя поля; в legacy-коде/тексте встречается
`explanation`. Имя унифицировано как `description` / `comment` по всем
calculated-RVO (`CalculatedStrategyAction`, `CalculatedPrice`,
`CalculatedSize`).

`CalculatedStrategyAction` **не** содержит `RiskValidationResult` и
`CalculatedRiskMetrics`: метрики для решения `ALLOWED / WARNING / BLOCKED`
считаются внутри risk-layer (см. `docs/components/RiskValidator.md`).

## Статус кода

Шаг 5 (риск-преконтроль/расчёт) **выполнен**:
`CalculatedStrategyAction` / `CalculatedPrice` / `CalculatedSize`
материализованы полной структурой (`StrategyPricePurpose`, набор цен с
resolved SL/TP/trailing-под-объектами, разложение sizing), и
производящий `StrategyActionCalculator` существует в коде
(`domain.command.calc`). Заглушек шага 4 больше нет.
