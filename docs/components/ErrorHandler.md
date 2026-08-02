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

Refresh при неактуальном состоянии и **добыча P&L-фактов best-effort** —
через `REFRESH_DEAL_CONTEXT_ACTION` (handler добывающие `REFRESH_*`
напрямую не эмитит; `docs/components/SystemActionExecutor.md`): положение
закрытия приезжает **второй ногой того же `REFRESH_POSITION_COMMAND`**, которым
подтверждено отсутствие позиции, и ложится на `Position` (+
`Deal.billsWindowEnd`); опц. `REFRESH_BILLS_COMMAND` — разбивка. Активный риск
снимается риск-минимизирующим порядком **cleanup-командами напрямую, без
анкера** (открытая позиция → `CLOSE_POSITION_COMMAND`; live ordinary orders →
`CANCEL_ORDER_COMMAND`; live algo → `CANCEL_ALGO_ORDER_COMMAND`; их серия неудач
считается на инструмент-scope), затем факт снятия подтверждается через
`REFRESH_*` (ACK не truth); после safety-flow заново загрузить exchange
facts; если live risk отсутствует и подтверждён — терминализировать через
**`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`** (второе исполнение
`FINALIZE_DEAL_ERROR_ACTION`; best-effort число,
`docs/decisions/pnl-finalization-mechanics.md` реш.3).
Отдельной команды `REFRESH_POSITIONS_HISTORY` handler не эмитит — её нет
в реестре.
Обычные strategy steps не выполняются. Kill-switch
ErrorHandler командой не эмитит: kill-switch — реактивный путь (`HoldSignal` →
`SafetyHoldCoordinator` в проходе оркестратора), не команда. Safety-команды —
без `RiskValidator` (см. `docs/rules/risk-validator-scope.md`).

## Выходные проверки

`ERROR → EMERGENCY_CLOSED` только если подтверждено: позиция закрыта/
отсутствует; нет live ordinary orders и algo-orders; attached protection
отсутствует/не влияет; нет pending сущностей, способных создать риск;
финальные exchange facts подтверждены; сделка не требует FSM-сопровождения.
Иначе остаётся в `ERROR`. `EMERGENCY_CLOSED` — terminal (ошибочный),
handler'а не имеет; терминал ставит `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`
(`docs/components/MarkDealEmergencyClosedExecutor.md`) с **best-effort числом**:
фактический realized net если доступен из positions-history, иначе `resultProfit
= null` с семантикой «неисчислимо» (**не ноль**) — сделка терминализуется всё
равно, факт помечается (`docs/lifecycles/Deal.md` §«Терминальный контракт
финализации», DEAL-Q2 / G5).

## Возможные ServiceCommand

`MARK_DEAL_ERROR_COMMAND`, `REFRESH_POSITION_COMMAND`,
`REFRESH_ORDER_COMMAND`, `REFRESH_ALGO_ORDER_COMMAND`, `CANCEL_ORDER_COMMAND`,
`CANCEL_ALGO_ORDER_COMMAND`, `CLOSE_POSITION_COMMAND`,
`REFRESH_BILLS_COMMAND`, `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`. Kill-switch не эмитится ErrorHandler'ом
как команда — реактивный side-executor вне реестра (`HoldSignal` →
`SafetyHoldCoordinator`). Перечисление **неизвестных** live
orders/algo по инструменту (хвосты orphan) — CMD-Q4. Зона
`AnomalyJob`/`ReconciliationJob` — live risk после terminal (см.
`docs/components/AnomalyJob.md`).
