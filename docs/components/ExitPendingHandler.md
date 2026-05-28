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
`REFRESH_PENDING_ORDERS`, `REFRESH_ALGO_ORDERS`; live ordinary orders →
`CANCEL_ORDER`; live algo → `CANCEL_ALGO_ORDER`; `REFRESH_FILLS` (факты
для PnL); `REFRESH_BALANCE` после снятия live risk и сопоставления fills;
при необходимости `REFRESH_ORDER_HISTORY`/`REFRESH_ALGO_ORDER_HISTORY`;
определить/подтвердить `Deal.CloseReason`; `FINALIZE_DEAL_EXIT` когда
факты готовы; `MARK_DEAL_CLOSED` когда всё очищено. Cleanup/safety команды
— без `RiskValidator`.

## Выходные проверки

`REFRESH_POSITION` подтвердил отсутствие позиции (или локальной не было и
entry/exit facts доказывают отсутствие live risk); active position в
домене → `CLOSED`; нет live orders/algo и активного рыночного риска; fills
загружены (если нужны); balance обновлён; история загружена (если нужна);
причина закрытия определена. → `EXIT_PENDING → CLOSED`. Риск не снят /
противоречие → `EXIT_PENDING → ERROR`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `FAIL_SAFE` (торговые steps обычно не применяются). Команды:
`REFRESH_POSITION`, `REFRESH_PENDING_ORDERS`, `REFRESH_ALGO_ORDERS`,
`CANCEL_ORDER`, `CANCEL_ALGO_ORDER`, `REFRESH_FILLS`,
`REFRESH_ORDER_HISTORY`, `REFRESH_ALGO_ORDER_HISTORY`, `FINALIZE_DEAL_EXIT`,
`MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.
