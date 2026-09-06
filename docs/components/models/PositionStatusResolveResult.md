# PositionStatusResolveResult

## На какой вопрос отвечает этот файл

Что это за `PositionStatusResolveResult`.

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

**Сторона, на которой result-object возникает, у резолверов разная.**
У заявки и условной заявки он собирается у коннектора и границу сервисов
не пересекает — наружу уезжает один доменный статус; у позиции резолвер
и его результат целиком живут в ядре. Критерий —
`docs/rules/external-status-resolution.md` §«Где резолвится — сторона
выбирается по словарю источника».
