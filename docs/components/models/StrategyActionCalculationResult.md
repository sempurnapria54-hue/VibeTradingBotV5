# StrategyActionCalculationResult

## На какой вопрос отвечает этот файл

Что это за runtime value object `StrategyActionCalculationResult`:
структура, енум `Status`.

## Назначение

`StrategyActionCalculationResult` — внешний контракт-результат
`StrategyActionCalculator` (см.
`docs/components/StrategyActionCalculator.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

Отделяет успешно рассчитанное действие от контролируемой ошибки расчёта.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `status` | `Status` | Итог расчёта. |
| `calculatedAction` | `CalculatedStrategyAction` | Рассчитанное действие; заполнено только при `SUCCESS` (см. `docs/components/models/CalculatedStrategyAction.md`). |
| `error` | `CalculationError` | Контролируемая ошибка расчёта; заполнена только при `ERROR` (см. `docs/components/models/CalculationError.md`). |

## Енум `Status`

`SUCCESS`, `ERROR`.
