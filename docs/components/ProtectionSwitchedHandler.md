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
существует локально и связана через `DealActionState`; ≤1 позиция; нет
критичного расхождения БД/биржи; статус действительно применим. Если после
рестарта switch не нужен, а позиция безопасна — safe forward recovery в
`MANAGING`.

## Рабочая логика

`REFRESH_POSITION` (если давно не обновлялась); `REFRESH_ALGO_ORDERS` для
подтверждения active main protection; проверить, осталась ли attached
protection; если attached ещё активна, а main подтверждена — `CANCEL_*`;
проверить конфликтующие pending orders (cancel или `ERROR` по риску).

## Выходные проверки

Позиция активна; main protection активна; attached снята/не влияет; нет
дублирующей защиты, orphan algo-orders, конфликтующих pending orders;
сделка готова к сопровождению. → `PROTECTION_SWITCHED → MANAGING`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (этап технический). Команды: `REFRESH_POSITION`,
`REFRESH_ALGO_ORDERS`, `REFRESH_PENDING_ORDERS`, `CANCEL_ALGO_ORDER`,
`CANCEL_ORDER`, `MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.
