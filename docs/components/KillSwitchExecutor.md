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

## Порядок исполнения (риск-минимизирующий)

Порядок вызовов значим — риск снижается максимально быстро:

1. **Close позиции первым** — доминирующий live market risk; market
   reduce-only close (с `autoCxl` — снимает resting-ордера на бирже
   атомарно) флэтит экспозицию за один вызов.
2. **Cancel оставшихся ordinary orders** — предотвратить re-entry
   (entry-ордер мог не попасть под `autoCxl`).
3. **Cancel algo-защит последними** — они reduce-only: пока позиция
   жива, это SL/TP-защита (снимать до close — окно без защиты), после
   flat — безвредный cleanup.
4. **Безусловный финальный close (best-effort)** — entry-ордер мог
   исполниться во время отмен и открыть позицию (в т.ч. с нуля), а
   runtime-граф снят на старте команды и этого не видит. Финальный close
   шлётся **безусловно**; на уже flat-позиции биржа вернёт «нет позиции»
   — ошибка гасится, чтобы не валить kill-switch.

Подтверждение факта снятия риска — отдельными `REFRESH_*` (ACK не
runtime truth).

## Статус миграции

Полный kill-switch flow (`KillSwitchService`, kill-switch report,
after-snapshot, `Position.CloseReason = KILL_SWITCH`) разбирается в
backlog п.7 (anomaly/safety/kill-switch) — здесь зафиксирована только
исполнительная семантика executor'а из процессных доков.
