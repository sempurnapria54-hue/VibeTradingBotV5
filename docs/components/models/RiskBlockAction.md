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
| `riskCode` | `RiskCheckCode` | Старший код **вердикта** — причина реакции. Пуст у разрешающих реакций и у отложенного вердикта: причины у них нет. |
| `comment` | `String` | Короткое пояснение для логов / будущей истории. |

**Код вердикта, а не `RuntimeErrorCode`.** Прежняя редакция типовала это
поле классификацией **неожиданных исключений**, чей собственный дом
объявляет прямо: результат риск-проверки в неё не превращается
(`docs/rules/runtime-error-classification.md`). Записанный туда вердикт
читался бы технической ошибкой исполнения, а её код назначает граница
исполнения по своему разбору, не риск-контроль.

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
