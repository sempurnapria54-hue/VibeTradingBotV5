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

`REFRESH_POSITION` (подтвердить отсутствие live-risk позиции; **та же
команда второй ногой цикла добывает положение закрытия** — число P&L
приземляется на `Position`, H1/H3 `GAPS_CLOSE_7`,
`docs/components/RefreshPositionExecutor.md`);
`REFRESH_ORDER` / `REFRESH_ALGO_ORDER` по известным сущностям сделки
(каждый внутри себя проходит pending/history); live ordinary orders →
`CANCEL_ORDER`; live algo → `CANCEL_ALGO_ORDER`; `REFRESH_BALANCE` после
снятия live risk; определить/подтвердить `Deal.CloseReason`; `REFRESH_BILLS`
(`DealCashFlow` — категорийная разбивка; **отдельная команда**, её факт
durable); `FINALIZE_DEAL_EXIT` когда факты готовы (считает `resultProfit`
из положения закрытия на `Position` + bills и **пишет его на `Deal`**,
`docs/decisions/pnl-finalization-mechanics.md`); `MARK_DEAL_CLOSED` когда всё
очищено (ассертит число, ставит терминал). `REFRESH_FILLS` **снимается** на
`CODE` шага 7 (в коде пока жив — H15, `GAPS_CLOSE_6`).
Cleanup/safety команды — без `RiskValidator`.

**Отдельной команды `REFRESH_POSITIONS_HISTORY` handler не эмитит** —
её нет в реестре (`docs/components/models/ServiceCommand.md`).

## Выходные проверки

`REFRESH_POSITION` подтвердил отсутствие позиции (или локальной не было и
entry/exit facts доказывают отсутствие live risk); active position в
домене → `CLOSED`; нет live orders/algo и активного рыночного риска;
balance обновлён; причина закрытия определена. → `EXIT_PENDING → CLOSED`.
Риск не снят / противоречие → `EXIT_PENDING → ERROR`.

**P&L-факты выходной проверкой не гейтятся** (H15, `GAPS_CLOSE_7`).
Положение закрытия — поля на `Position`, проверяемые (в отличие от
прежнего in-memory-снапшота); их пустота — легитимная тропа
«неисчислимо», а не незавершённая очистка. Разбивка bills —
**best-effort**: её отсутствие даёт `AnomalyReport`, а не удержание
сделки в `EXIT_PENDING`. Асимметрия штатной и аварийной троп отказа
добычи — `docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп
отказа добычи».

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (торговые steps обычно не применяются). Команды:
`REFRESH_POSITION`, `REFRESH_ORDER`, `REFRESH_ALGO_ORDER`,
`CANCEL_ORDER`, `CANCEL_ALGO_ORDER`, `REFRESH_BALANCE`,
`REFRESH_BILLS`, `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR`.
