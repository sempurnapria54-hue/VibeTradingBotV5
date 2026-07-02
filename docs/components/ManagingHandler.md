# ManagingHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `MANAGING` (компонент): проверки, логика,
шаги, команды.

## Назначение

Сопровождает открытую позицию по стратегии — основной рабочий статус
после входа и защиты. Конструкция handler'а —
`docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = MANAGING`; pinned `StrategyDetail`; позиция активна с live
risk **или** есть факты, что позиция закрыта и нужен переход в
`EXIT_PENDING`; `ACTIVE && externalSize == 0` — не normal `CLOSED`, а
cleanup/retry/anomaly; main protection актуальна; ≤1 позиция; нет чужих
live orders/algo; нет критичного расхождения и borrow/debt.

## Рабочая логика

Обновить позицию/live-сущности при необходимости; взять
`stepsByStatus[MANAGING]` (`PROTECTION_ADJUSTMENT`, `PARTIAL_EXIT`,
`GRID_MANAGEMENT`, `EXIT`, `FAIL_SAFE`); для data-dependent step —
freshness (`checkForStep`) → при устаревании `marketDataExpiredSetting`;
для fresh — `StrategyCondition`; для применимых — actions →
`DealActionState` → `StrategyActionCalculator` → нужные `ServiceCommand`.
Risk-creating actions — через risk-layer; reduce-only partial exit — без
`RiskValidator`, через safety/invariant checks (см.
`docs/rules/risk-validator-scope.md`). Полный выход → `CLOSE_POSITION` /
cancel-команды. `REFRESH_POSITION` без позиции → `EXIT_PENDING`;
`ACTIVE && externalSize==0` → cleanup/retry/anomaly; fail-safe → emergency.

## Выходные проверки

`→ EXIT_PENDING`, если стратегия инициировала выход / позиция
закрывается или закрыта / есть команда закрытия или факт через
`REFRESH_POSITION` / нужно дочистить хвосты. `→ ERROR`, если защита
потеряна без безопасного восстановления, активный риск без контроля,
опасное расхождение, >1 позиция, borrow/debt, небезопасный recovery.
Иначе остаётся в `MANAGING`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `PROTECTION_ADJUSTMENT`, `PARTIAL_EXIT`, `GRID_MANAGEMENT`, `EXIT`,
`FAIL_SAFE`. Команды: `REFRESH_BALANCE`, `CREATE_ALGO_ORDER`,
`SUBMIT_ALGO_ORDER`, `CANCEL_ALGO_ORDER`, `CREATE_ORDER`,
`SUBMIT_ORDER`, `CANCEL_ORDER`, `CLOSE_POSITION`, `REFRESH_POSITION`,
`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_FILLS`,
`MARK_DEAL_ERROR`. Ремодел защиты
(`PROTECTION_ADJUSTMENT`) — REPLACE-оркестрацией из этого же набора
(place-new → факт → cancel-old; `docs/decisions/replace-not-amend.md`),
амендных команд нет.
