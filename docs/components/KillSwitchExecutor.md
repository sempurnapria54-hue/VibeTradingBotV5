# KillSwitchExecutor

## На какой вопрос отвечает этот файл

Кто исполняет kill-switch teardown (компонент вне реестра команд): с чем
работает, границы.

## Назначение

`KillSwitchExecutor` — аварийное снятие риска. Живёт в пакете
`domain.command.action`; это обычный `@Component` **вне реестра команд**
(не `CommandExecutor`, по типу команды не диспатчится). Тип команды
`EXECUTE_KILL_SWITCH` **убран** — kill-switch не команда. Зовётся
программно из `KillSwitchService`
(`killSwitchExecutor.execute(dealContext).getSuccess()`). Работает только с
live-сущностями: active positions, live orders, live algo-orders. Всю
историю по инструменту не чистит; `relatedInactive` может добавляться
только в report/snapshot, action-state работает только с live risk.

**Не действие стратегии и не команда:** аварийный тормоз доводит свой
teardown **сам**, прямыми best-effort вызовами `IntegrationService`
(close/cancel), и **не зависит** от того, жива ли петля — в отличие от
REPLACE (действие-оркестрация петлёй по фактам). Подтверждение снятия
риска — дёрганьем `REFRESH_*`-команд через диспетчер (резолвер статуса
живёт в REFRESH-executor'ах, здесь не дублируется), перечиткой графа из БД
и проверкой flat по доменным моделям. Не подтверждено flat — teardown
повторяется, **bounded** лимитом попыток
`KillSwitchProperties.maxTeardownAttempts` (дефолт 3); лимит исчерпан →
`failure` (эскалацию L3→биржа держит `SafetyHoldCoordinator`, HOLD-Q1).
Жёсткое правило порядка (ниже): защиту снимать **последней** и только
после подтверждённого закрытия позиции — никогда не оголять живую позицию.

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

Полный kill-switch flow построен: триггер — `KillSwitchService`
(`fireInstrument` L3 / `fireExchange` L4 каскадом по сделкам биржи),
оркестрация отчёта и слепков — `SafetyHoldCoordinator` (по сигналу холда
в проходе `DealOrchestratorJob`; журнал —
`docs/models/domain/other/AnomalyReport.md`). Здесь зафиксирована
исполнительная семантика самого executor'а.
