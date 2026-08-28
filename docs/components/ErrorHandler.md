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

## Выходные проверки

`ERROR → EMERGENCY_CLOSED` только если подтверждено: позиция закрыта/
отсутствует; нет live ordinary orders и algo-orders; attached protection
отсутствует/не влияет; нет pending сущностей, способных создать риск;
**факты живого риска** подтверждены; сделка не требует FSM-сопровождения.
Иначе остаётся в `ERROR`.

## Границы

Перечень команд handler-док не держит: состав команд — собственность
действий (`docs/processes/fsm-execution-layering.md`; реестры звеньев — `docs/rules/command-lifecycle.md`, `docs/components/SystemActionExecutor.md`). Kill-switch не эмитится
ErrorHandler'ом как команда — реактивный side-executor вне реестра
(`HoldService` → `SafetyHoldCoordinator`). Перечисление **неизвестных**
live orders/algo по инструменту (хвосты orphan) — CMD-Q4. Зона
`AnomalyJob`/`ReconciliationJob` — live risk после terminal (см.
`docs/components/AnomalyJob.md`).
