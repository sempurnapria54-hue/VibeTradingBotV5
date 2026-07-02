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

Refresh при неактуальном состоянии; активный риск снимается риск-минимизирующим
порядком cleanup-командами (открытая позиция → `CLOSE_POSITION`; live ordinary
orders → `CANCEL_ORDER`; live algo → `CANCEL_ALGO_ORDER`), затем факт снятия
подтверждается через `REFRESH_*` (ACK не truth); после safety-flow заново
загрузить exchange facts; если live risk отсутствует и подтверждён — подготовить
переход в `EMERGENCY_CLOSED`. Обычные strategy steps не выполняются. Kill-switch
ErrorHandler командой не эмитит: kill-switch — реактивный путь (`HoldSignal` →
`SafetyHoldCoordinator` в проходе оркестратора), не команда. Safety-команды —
без `RiskValidator` (см. `docs/rules/risk-validator-scope.md`).

## Выходные проверки

`ERROR → EMERGENCY_CLOSED` только если подтверждено: позиция закрыта/
отсутствует; нет live ordinary orders и algo-orders; attached protection
отсутствует/не влияет; нет pending сущностей, способных создать риск;
финальные exchange facts подтверждены; сделка не требует FSM-сопровождения.
Иначе остаётся в `ERROR`. `EMERGENCY_CLOSED` — terminal (ошибочный),
handler'а не имеет; число `resultProfit` — по терминальному контракту
финализации (`docs/lifecycles/Deal.md` §«Терминальный контракт финализации»,
DEAL-Q2), не блокируется инвариантом чистого закрытия.

## Возможные ServiceCommand

`MARK_DEAL_ERROR`, `REFRESH_POSITION`,
`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_FILLS`, `CANCEL_ORDER`,
`CANCEL_ALGO_ORDER`, `CLOSE_POSITION`. Kill-switch не эмитится ErrorHandler'ом
как команда — реактивный side-executor вне реестра (`HoldSignal` →
`SafetyHoldCoordinator`). Перечисление **неизвестных** live
orders/algo по инструменту (хвосты orphan) — CMD-Q4. Зона
`AnomalyJob`/`ReconciliationJob` — live risk после terminal (см.
`docs/components/AnomalyJob.md`).
