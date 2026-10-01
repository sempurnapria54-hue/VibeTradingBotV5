# RiskBlockAction

## На какой вопрос отвечает этот файл

Что это за `RiskBlockAction`.

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
| `closeReason` | `Deal.CloseReason` | Причина закрытия, если нужно закрыть candidate Deal (см. `docs/models/domain/aggregate/Deal.md`). |
| `riskCode` | `RiskCheckCode` | Старший код **вердикта** — причина реакции. Пуст у разрешающей реакции: причины у неё нет. |
| `comment` | `String` | Короткое пояснение для логов / будущей истории. |

**Код вердикта, а не `RuntimeErrorCode`.** Классификацией **неожиданных
исключений** это поле не типуется: её собственный дом объявляет прямо —
результат риск-проверки в неё не превращается
(`docs/rules/runtime-error-classification.md`). Записанный туда вердикт
читался бы технической ошибкой исполнения, а её код назначает граница
исполнения по своему разбору, не риск-контроль.

## Енум `Type`

- `CONTINUE` — продолжить выполнение action;
- `CLOSE_CANDIDATE_DEAL` — закрыть candidate Deal без ошибки (live risk
  ещё не создан);
- `MOVE_DEAL_TO_ERROR` — перевести сделку в `ERROR`, дальше
  `ErrorHandler` / safety-flow;
- `SKIP_ACTION` — пропустить action как более не актуальный.
