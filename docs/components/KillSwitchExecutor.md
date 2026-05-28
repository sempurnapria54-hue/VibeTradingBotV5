# KillSwitchExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `EXECUTE_KILL_SWITCH` (компонент-executor): с чем работает,
границы.

## Назначение

`KillSwitchExecutor` исполняет `EXECUTE_KILL_SWITCH` — аварийное снятие
риска. Работает только с live-сущностями: active positions, live orders,
live algo-orders. Всю историю по инструменту не чистит; `relatedInactive`
может добавляться только в report/snapshot, action-state работает только
с live risk.

## Проверки и границы

`RiskValidator` не вызывается (safety-flow, см.
`docs/rules/risk-validator-scope.md`); выполняются только minimal safety/
invariant checks: какой instrument / exchange account обезопасить, какие
live positions/orders/algo-orders известны, какие read/safety команды
выполнить. Risk-layer не должен блокировать kill-switch.

## Статус миграции

Полный kill-switch flow (`KillSwitchService`, kill-switch report,
after-snapshot, `Position.CloseReason = KILL_SWITCH`) разбирается в
backlog п.7 (anomaly/safety/kill-switch) — здесь зафиксирована только
исполнительная семантика executor'а из процессных доков.
