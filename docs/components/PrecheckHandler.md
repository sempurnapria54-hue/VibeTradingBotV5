# PrecheckHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `PRECHECK` (компонент): проверки, логика,
шаги, команды.

## Назначение

Готовит сделку к созданию entry order. `Deal` уже создана
`DealOpeningService`, но runtime-сущности входа ещё не подтверждены.
Конструкция handler'а (3 проверки) — `docs/components/DealStateMachine.md`;
статусная механика и переходы — `docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = PRECHECK`; есть pinned `StrategyDetail` и `Instrument`;
есть `BalanceContainer` или можно создать `REFRESH_BALANCE`; refresh/search
не показывают >1 позиции; нет активной позиции/сделки (при максимуме
одной); нет конфликтующих live orders/algo; нет borrow/debt; режим
isolated. Не прошли безопасно → refresh / остаться в `PRECHECK` / `ERROR`.

## Рабочая логика

Сначала обеспечить fresh `BalanceContainer` (absent/stale →
`REFRESH_BALANCE`, остаться, не вызывать `RiskValidator`/`CREATE_ORDER` на
этой итерации). Затем: найти `ENTRY`/`GRID_ENTRY` step → freshness
(`checkForStep`) → при устаревании `marketDataExpiredSetting` → проверить
`StrategyCondition`. Если condition false и live risk нет → закрыть
candidate Deal без ошибки (`CLOSED` + `ENTRY_CONDITION_EXPIRED`); если live
risk есть/неизвестно → recovery/safety. Если condition true → взять
action, проверить `DealActionState`, вызвать `StrategyActionCalculator`,
создать `CREATE_ORDER` → `SUBMIT_ORDER`. Risk-check entry action — через
risk-layer (`docs/processes/risk-evaluation.md`): BLOCKED в PRECHECK без
live risk → `CLOSED` + `RISK_CONTROL`.

## Выходные проверки

Entry action материализован в локальный `Order`; `DealActionState` →
`RuntimeTarget(ORDER, orderId)`; order создан/отправлен; нет критичных
конфликтов; нет риска под kill-switch. → `PRECHECK → ENTRY_SUBMITTED`.

## Допустимые StrategyStep / возможные ServiceCommand

Steps: `ENTRY`, `GRID_ENTRY`, `FAIL_SAFE`. Команды: `REFRESH_BALANCE`,
`REFRESH_POSITION`, `REFRESH_PENDING_ORDERS`, `CREATE_ORDER`,
`SUBMIT_ORDER`, `MARK_DEAL_ERROR`, `EXECUTE_KILL_SWITCH`.
