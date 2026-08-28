# ProtectionSwitchedHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `PROTECTION_SWITCHED` (компонент):
проверки, логика, шаги, команды.

## Назначение

Подтверждает конкретный switch-сценарий: temporary attached protection →
standalone main protection подтверждена active → attached снята или
больше не влияет на риск. Статус **не обязателен**: если strategy steps не
требуют замены, FSM в него не переводит. Конструкция —
`docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = PROTECTION_SWITCHED`; позиция активна; main protection
существует локально и связана через `DealActionState`; ≤1 живая позиция; нет
критичного расхождения БД/биржи; статус действительно применим. Если после
рестарта switch не нужен, а позиция безопасна — safe forward recovery в
`MANAGING`.

## Рабочая логика

`REFRESH_POSITION_COMMAND` (если давно не обновлялась); `REFRESH_ALGO_ORDER_COMMAND` для
подтверждения active main protection; проверить, осталась ли attached
protection; если attached ещё активна, а main подтверждена — `CANCEL_*`;
проверить конфликтующие pending orders (cancel или `ERROR` по риску).

## Выходные проверки

Позиция активна; **main protection покрывает позицию целиком** —
`Σ (AlgoOrder.size − coalesce(AlgoOrder.externalSize, 0))` по живым
standalone-защитам ≥ `Position.externalSize` (C1 `DOCS_CHECK_23`: предикат —
покрытие, не активность; лестница частичных стопов легальна, недопокрытие —
нет. Операнд и множество защит уточнены A2/A3/A4 `DOCS_CHECK_24`: «живая» —
`AlgoOrder.isActiveLike()` = `{PENDING, ACTIVE, PARTIALLY_COMPLETED}`,
`conditionType` — из закрытого перечня защит, сработавшая доля вычитается.
Дом формулы — `docs/rules/risk-creating-entry-protection.md` §Правило, точки
проверки — §«Предикат покрытия и точки его проверки»); attached снята/не
влияет; нет
дублирующей защиты, orphan algo-orders, конфликтующих pending orders;
сделка готова к сопровождению. → `PROTECTION_SWITCHED → MANAGING`.

## Допустимые StrategyStep

Steps: `FAIL_SAFE` (этап технический). Перечень команд handler-док не
держит: состав команд — собственность действий
(`docs/decisions/fsm-execution-layering.md` §«Handler исполняет действия»;
реестры звеньев — `docs/decisions/command-action-boundary.md` §2,
`docs/components/SystemActionExecutor.md`).
