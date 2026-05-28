# RiskBlockAction

## На какой вопрос отвечает этот файл

Что это за runtime value object `RiskBlockAction`: структура, енум `Type`.

## Назначение

`RiskBlockAction` — действие, которое `RiskBlockResolver` (см.
`docs/components/RiskBlockResolver.md`) возвращает handler'у по результату
risk-проверки, чтобы handler не содержал большой `switch` по всем
risk-кодам. RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `type` | `Type` | Что должен сделать FSM handler. |
| `closeReason` | `Deal.CloseReason` | Причина закрытия, если нужно закрыть candidate Deal (см. `docs/models/core/Deal.md`). |
| `errorCode` | `RuntimeErrorCode` | Код ошибки, если сделку нужно перевести в `ERROR` (см. `docs/rules/runtime-error-classification.md`). |
| `comment` | `String` | Короткое пояснение для логов / будущей истории. |

## Енум `Type`

- `CONTINUE` — продолжить выполнение action;
- `CONTINUE_WITH_WARNING` — продолжить, но сохранить предупреждение в
  логах / будущей истории;
- `CLOSE_CANDIDATE_DEAL` — закрыть candidate Deal без ошибки (live risk
  ещё не создан);
- `MOVE_DEAL_TO_ERROR` — перевести сделку в `ERROR`, дальше
  `ErrorHandler` / safety-flow;
- `REQUEST_REFRESH` — не выполнять текущий action, запросить refresh
  фактов;
- `SKIP_ACTION` — пропустить action как более не актуальный.
