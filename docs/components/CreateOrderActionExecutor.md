# CreateOrderActionExecutor

## На какой вопрос отвечает этот файл

Кто планирует CREATE-действие над ordinary order за проход.

## Назначение

`CreateOrderActionExecutor` — per-pass `StrategyActionExecutor` (см.
`docs/components/StrategyActionExecutor.md`) CREATE-действия над ordinary
order (`StrategyOrderAction` + `actionType = CREATE`). По стадии
`DealActionState` выдаёт следующую команду:

```text
PLANNED   -> расчёт -> исход округления выхода -> risk (для risk-creating, т.е. не reduce-only) -> CREATE_ORDER_COMMAND
CREATED   -> SUBMIT_ORDER_COMMAND
SUBMITTED -> REFRESH_ORDER_COMMAND
```

На продвинутых стадиях расчёт и риск не повторяются — нога ведётся по
фактам из `DealActionState.target`; повтор заведённой ноги возвращается на
`CREATED`, а не в планирование (повтор возвращает исполнение на стадию
факта — `docs/lifecycles/DealActionState.md`). Секвенс ведёт петля по подтверждённым
фактам (см. `docs/processes/fsm-execution-layering.md`).

**Исход округления reduce-only выхода читается до преконтроля.** Исход
`SKIPPED` — команды нет, строка исполнения уходит в `SKIPPED`; `FULL` —
команда уходит экспозицией транша целиком. Оба пишут журнальный отчёт —
таблица исходов и коды — reduce-only выход без пола минимального размера,
`docs/components/SizeCalculator.md`.

## Связь с risk-layer

Расчёт делает `StrategyActionCalculator`; для risk-creating действия
(не `positionReducingOnly`) прогоняет `RiskValidator`, и при блокировке
маппит решение через `RiskBlockResolver` в `RiskBlockAction`, отдавая его
`ActionPlan`'ом (реакцию исполняет resolver в handler'е —
`docs/rules/risk-validator-scope.md`, `docs/components/RiskBlockResolver.md`).
Разрешённый вход он отмечает в контексте прохода, а риск-создающее действие,
пришедшее в проход, где вход сделки уже решён, откладывает до расчёта —
пустым планом при запланированной строке (правило —
живое меряется от живой экспозиции, `docs/rules/risk-policy.md`).
До расчёта он откладывает и риск-создающее действие при несвежем снимке
средств счёта — на всякой стадии, не только на входе: строка остаётся
запланированной и бюджета не тратит, но план не пуст — он несёт исход
отсрочки до свежего снимка, по которому проход заказывает добычу снимка
звеном системного действия (`docs/components/models/ActionPlan.md`).
Предикат свежести, область отсрочки (защитные действия её не ждут) и
исход исчерпания добычи — дом `docs/components/RiskValidator.md`, раздел
проверок средств счёта.
Ошибка расчёта возвращается как `calcError`-`ActionPlan`. Сам команды не
исполняет и статус сделки не двигает.
