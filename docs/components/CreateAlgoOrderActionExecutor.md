# CreateAlgoOrderActionExecutor

## На какой вопрос отвечает этот файл

Кто планирует CREATE-действие над standalone algo-order за проход
(компонент-executor): стадии, сборка дерева `Condition`, отношение к risk.

## Назначение

`CreateAlgoOrderActionExecutor` — per-pass `StrategyActionExecutor` (см.
`docs/components/StrategyActionExecutor.md`) CREATE-действия над standalone
algo-order (SL/TP/OCO/trailing; `StrategyAlgoOrderAction` + `actionType =
CREATE`). По стадии `DealActionState` выдаёт следующую команду:

```text
PLANNED   -> расчёт -> CREATE_ALGO_ORDER_COMMAND
CREATED   -> SUBMIT_ALGO_ORDER_COMMAND
SUBMITTED -> REFRESH_ALGO_ORDER_COMMAND
```

## Сборка дерева `Condition`

На стадии `CREATE_ALGO_ORDER_COMMAND` собирает готовое дерево `Condition` с
рассчитанными trigger/trailing ценами (из `CalculatedPrice`): тип условия
задаёт, какие ноги заполняются — `STOP_LOSS`/`PARTIAL_STOP_LOSS` →
trigger stop-loss; `TAKE_PROFIT`/`PARTIAL_TAKE_PROFIT` → trigger
take-profit; `OCO_FULL` → обе; `TRAILING_*` → trailing. Иначе защитный
ордер ушёл бы на биржу без триггерной цены.

## Отношение к risk и направление

Risk-валидацию не проходит: algo-защита reduce-only, позицию не открывает
(`docs/rules/risk-validator-scope.md`). Направление — закрывающее к
направлению сделки (`positionReducingOnly = true`). Ошибка расчёта
возвращается как `calcError`-`ActionPlan`. Сам команды не исполняет и
статус сделки не двигает. Секвенс ведёт петля по подтверждённым фактам
(см. `docs/decisions/fsm-execution-layering.md`).
