# ErrorHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `ERROR` (компонент): проверки, логика,
команды, переход в `EMERGENCY_CLOSED`.

## Назначение

`ERROR` — обнаружена авария, обычная strategy/FSM-логика заблокирована,
risk может быть ещё живым. Разрешены только safety / recovery / проверка
фактов. `ERROR` — non-terminal; `ERROR → CLOSED` запрещён, допустим только
`ERROR → EMERGENCY_CLOSED`. Конструкция handler'а —
`docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = ERROR`; есть ли активный риск; позиция без защиты; live
orders/algo без связи со сделкой; расхождение БД/биржи; нужна ли
аварийная очистка риска.

## Рабочая логика

Refresh при неактуальном состоянии; активный риск → `EXECUTE_KILL_SWITCH`
или конкретные safety-команды; live ordinary orders → `CANCEL_ORDER`; live
algo → `CANCEL_ALGO_ORDER`; открытая позиция → `CLOSE_POSITION`; после
safety-flow заново загрузить exchange facts; если live risk отсутствует и
подтверждён — подготовить переход в `EMERGENCY_CLOSED`. Обычные strategy
steps не выполняются. Safety-команды — без `RiskValidator` (см.
`docs/rules/risk-validator-scope.md`).

## Выходные проверки

`ERROR → EMERGENCY_CLOSED` только если подтверждено: позиция закрыта/
отсутствует; нет live ordinary orders и algo-orders; attached protection
отсутствует/не влияет; нет pending сущностей, способных создать риск;
финальные exchange facts подтверждены; сделка не требует FSM-сопровождения.
Иначе остаётся в `ERROR`. `EMERGENCY_CLOSED` — terminal, handler'а не
имеет; обязательны `resultProfit`/`resultProfitCurrency`.

## Возможные ServiceCommand

`EXECUTE_KILL_SWITCH`, `MARK_DEAL_ERROR`, `REFRESH_POSITION`,
`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_FILLS`, `CANCEL_ORDER`,
`CANCEL_ALGO_ORDER`, `CLOSE_POSITION`. Перечисление **неизвестных** live
orders/algo по инструменту (хвосты orphan) — CMD-Q4. Зона
`AnomalyJob`/`ReconciliationJob` — live risk после terminal (см.
`docs/components/AnomalyJob.md`).
