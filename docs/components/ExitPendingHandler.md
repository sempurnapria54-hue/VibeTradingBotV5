# ExitPendingHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `EXIT_PENDING` (компонент): проверки,
логика, шаги, команды.

## Назначение

Дочищает сделку после инициированного выхода: подтверждает закрытие
позиции, отменяет live-сущности, финализирует факты. Конструкция handler'а
— `docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = EXIT_PENDING`; pinned `StrategyDetail`; есть факты
инициированного выхода/закрытия; ≤1 позиция; можно безопасно проверить
live risk; локальные orders/algo доступны для очистки.

## Рабочая логика

`REFRESH_POSITION` (подтвердить отсутствие live-risk позиции);
`REFRESH_ORDER` / `REFRESH_ALGO_ORDER` по известным сущностям сделки
(каждый внутри себя проходит pending/history); live ordinary orders →
`CANCEL_ORDER`; live algo → `CANCEL_ALGO_ORDER`; `REFRESH_BALANCE` после
снятия live risk; определить/подтвердить `Deal.CloseReason`; **добыть
P&L-факты** — `REFRESH_POSITIONS_HISTORY` (positions-history-снапшот — число)
и `REFRESH_BILLS` (`DealCashFlow` — разбивка); `FINALIZE_DEAL_EXIT` когда факты
готовы (считает `resultProfit` из снапшота+bills и **пишет его на `Deal`**,
`docs/decisions/pnl-finalization-mechanics.md`); `MARK_DEAL_CLOSED` когда всё
очищено (ассертит число, ставит терминал). `REFRESH_FILLS` **снят** (шаг 7).
Cleanup/safety команды — без `RiskValidator`.

## Выходные проверки

`REFRESH_POSITION` подтвердил отсутствие позиции (или локальной не было и
entry/exit facts доказывают отсутствие live risk); active position в
домене → `CLOSED`; нет live orders/algo и активного рыночного риска;
P&L-факты готовы (positions-history-снапшот + bills, шаг 7); balance
обновлён; причина закрытия определена. → `EXIT_PENDING → CLOSED`. Риск не
снят / противоречие → `EXIT_PENDING → ERROR`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (торговые steps обычно не применяются). Команды:
`REFRESH_POSITION`, `REFRESH_ORDER`, `REFRESH_ALGO_ORDER`,
`CANCEL_ORDER`, `CANCEL_ALGO_ORDER`, `REFRESH_POSITIONS_HISTORY`,
`REFRESH_BILLS`, `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR`.
