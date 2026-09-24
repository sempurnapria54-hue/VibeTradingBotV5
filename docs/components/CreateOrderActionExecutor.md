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
фактам из `DealActionState.target`. Секвенс ведёт петля по подтверждённым
фактам (см. `docs/processes/fsm-execution-layering.md`).

**Исход округления reduce-only выхода читается до преконтроля.** Исход
`SKIPPED` — команды нет, строка исполнения уходит в `SKIPPED`; `FULL` —
команда уходит экспозицией транша целиком. Оба пишут журнальный отчёт —
таблица исходов и коды — `docs/components/SizeCalculator.md` §«Reduce-only
выход: пола минимального размера нет».

## Связь с risk-layer

Расчёт делает `StrategyActionCalculator`; для risk-creating действия
(не `positionReducingOnly`) прогоняет `RiskValidator`, и при блокировке
маппит решение через `RiskBlockResolver` в `RiskBlockAction`, отдавая его
`ActionPlan`'ом (реакцию исполняет resolver в handler'е —
`docs/rules/risk-validator-scope.md`, `docs/components/RiskBlockResolver.md`).
Ошибка расчёта возвращается как `calcError`-`ActionPlan`. Сам команды не
исполняет и статус сделки не двигает.
