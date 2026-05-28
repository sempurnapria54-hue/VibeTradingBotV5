# RiskValidationResult

## На какой вопрос отвечает этот файл

Что это за runtime value object `RiskValidationResult`: структура, енум
`RiskDecision`.

## Назначение

`RiskValidationResult` — итог проверки риска, который возвращает
`RiskValidator` (см. `docs/components/RiskValidator.md`). RVO, не persisted
(см. `.claude/decisions/runtime-value-object.md`). Контролируемый результат
risk-policy проверки; unexpected exceptions в него не превращаются (для них
`RuntimeErrorCode`, см. `docs/rules/runtime-error-classification.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `decision` | `RiskDecision` | Итоговое решение risk-layer. |
| `checks` | `List<RiskCheckResult>` | Детальные результаты отдельных проверок (см. `docs/components/models/RiskCheckResult.md`). |
| `comment` | `String` | Короткое пояснение для логов / будущей истории исполнения. |

## Енум `RiskDecision`

- `ALLOWED` — действие разрешено;
- `WARNING` — есть риск-предупреждения, но действие не блокируется;
- `BLOCKED` — действие заблокировано risk-policy.

## Обработка результата

Реакция на `decision` принимается не здесь: `RiskBlockResolver` маппит
`BLOCKED` в `RiskBlockAction`, FSM handler исполняет (см.
`docs/processes/risk-evaluation.md`). `WARNING` не блокирует и сам в
`ERROR` не переводит; `ALLOWED` разрешает переход к
`ServiceCommandFactory`.
