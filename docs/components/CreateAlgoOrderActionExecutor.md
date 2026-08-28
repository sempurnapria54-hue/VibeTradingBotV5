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

**Прогоняет `RiskValidator` по ветке risk-weakening** (A1 `DOCS_CHECK_24`).
Действие reduce-only и позицию не открывает, поэтому входной набор
неравенств к нему не применяется, — но `CREATE_ALGO_ORDER_COMMAND` **входит**
в множество валидируемых как «создание защитного algo-order, не
обеспечивающего требуемый контроль риска»
(`docs/rules/risk-validator-scope.md` §Вызывается). Здесь считается
**предикат покрытия** (`docs/rules/risk-creating-entry-protection.md`
§«Предикат покрытия и точки его проверки», строка «преконтроль»);
`BLOCKED` `PROTECTION_COVERAGE_REDUCED` в `ERROR` не уводит
(`docs/processes/risk-evaluation.md` §«Карв-аут исчерпанного бюджета
сделки»).

**Прежняя редакция («Risk-валидацию не проходит: algo-защита reduce-only,
позицию не открывает») утверждала о названном правиле факт, которого в нём
нет**, и оставляла преконтрольную точку предиката покрытия **без
вызывающего**: этот executor — единственный планировщик действия над
standalone algo-order, а соседний `docs/components/CreateAlgoOrderExecutor.md`
в то же время писал, что валидатор на алго-тропе вызывается по ветке
weakening. Смешаны были две разные вещи: «не валидируется **как вход**»
(истинно) и «не валидируется вовсе» (ложно).

Направление — закрывающее к направлению сделки
(`positionReducingOnly = true`). Ошибка расчёта
возвращается как `calcError`-`ActionPlan`. Сам команды не исполняет и
статус сделки не двигает. Секвенс ведёт петля по подтверждённым фактам
(см. `docs/decisions/fsm-execution-layering.md`).
