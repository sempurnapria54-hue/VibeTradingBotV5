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

Позиция активна; main protection активна; attached снята/не влияет; нет
дублирующей защиты, orphan algo-orders, конфликтующих pending orders;
сделка готова к сопровождению. → `PROTECTION_SWITCHED → MANAGING`.

## Допустимые StrategyStep

Steps: `FAIL_SAFE` (этап технический). Перечень команд handler-док не
держит: состав команд — собственность действий
(`docs/decisions/fsm-execution-layering.md` §«Handler исполняет действия»;
реестры звеньев — `docs/decisions/command-action-boundary.md` §2,
`docs/components/SystemActionExecutor.md`).
