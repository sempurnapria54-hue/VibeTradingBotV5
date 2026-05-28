# PositionStatusResolveResult

## На какой вопрос отвечает этот файл

Что это за runtime value object `PositionStatusResolveResult` и общий
паттерн resolve-result.

## Назначение

`PositionStatusResolveResult` — результат `PositionStatusResolver` (см.
`docs/components/PositionStatusResolver.md`): доменный статус позиции +
candidate причины. RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `status` | `Position.Status` | Доменный статус позиции. |
| `closeReason` | `Position.CloseReason` | Candidate причины; executor применяет только если текущий `closeReason == null`. |

## Общий паттерн resolve-result

Resolver'ы возвращают однотипный result-object `status + optional
closeReason candidate` (обобщённо `EntityStatusResolveResult` /
`StatusResolveResult<S, C>`): доменный статус сущности + candidate
причины финализации/problem-state. Этим же паттерном пользуются
`OrderExternalStatusResolver` и `AlgoOrderExternalStatusResolver` (для
них статус берётся из внешнего статуса биржи, для позиции — из факта
наличия). Применение result-object к сущности и сохранение — у refresh/
executor layer (см. `docs/rules/external-status-resolution.md`).
