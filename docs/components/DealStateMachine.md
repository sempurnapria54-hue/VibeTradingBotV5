# DealStateMachine

## На какой вопрос отвечает этот файл

Кто управляет статусами сделки (компонент-оркестратор FSM): что делает,
конструкция handler'а (3 типа проверок), границы.

## Назначение

`DealStateMachine` управляет статусами сделки: выбирает handler по
текущему `Deal.Status`, запускает его проверки и логику, проверяет
свежесть данных и условия стратегии, выбирает допустимые `StrategyAction`,
возвращает команды и новый статус, если переход разрешён. Запускается
`DealOrchestratorJob` (см. `docs/components/DealOrchestratorJob.md`).
Per-status handlers — отдельные компоненты (см.
`.claude/decisions/fsm-handler-as-component.md`); статусная механика
(значения, инварианты переходов, recovery) — у `docs/lifecycles/Deal.md`.

Terminal-статусы `CLOSED` / `EMERGENCY_CLOSED` handler'ов **не** имеют.

## Конструкция handler'а: три типа проверок

Каждый FSM handler состоит из трёх блоков:

1. **Входные проверки** — можно ли вообще обрабатывать сделку в этом
   статусе (текущий `Deal.status` не противоречит фактам `DealContext`:
   pinned `StrategyDetail`, нужный `DealActionState`, отсутствие чужого
   риска, ≤1 позиция/инструмент, локальные сущности не в невозможных
   статусах). Не проходят безопасно → refresh / recovery / `ERROR` /
   kill-switch. В happy-path на следующий этап не переводят (исключение —
   safe forward recovery после рестарта).
2. **Рабочая логика этапа** — что сделать, чтобы приблизить завершение
   этапа (refresh / `CREATE_*`/`SUBMIT_*`/`CANCEL_*` /
   risk-reducing/cleanup команды; проверка condition; выбор action; вызов
   калькулятора и эмиссия команд (action-команды —
   `StrategyActionOrchestrator`, финализационные —
   `DealFinalizationCommandFactory`); REPLACE-действия секвенсятся
   этими же командами по фактам — `docs/decisions/replace-not-amend.md`).
   Сама по себе завершение этапа не означает.
3. **Выходные проверки** — можно ли считать этап завершённым; именно они
   отвечают за обычный переход между этапами.

## REPLACE-секвенс и компаунды (владелец оркестрации)

`DealStateMachine` / петля — **владелец оркестрации порядка ног REPLACE**:
вычисляет следующую ногу по **подтверждённым фактам** (не ACK), по
риск-классу действия (`docs/decisions/replace-not-amend.md`). Эмиссия команд
остаётся «одна атомарная команда за проход»: action-команды даёт
`StrategyActionOrchestrator` (`docs/components/StrategyActionOrchestrator.md`),
финализационные — `DealFinalizationCommandFactory`
(`docs/components/DealFinalizationCommandFactory.md`); секвенс ног в себя ни
та, ни другая не берут. Без петли, реагирующей на факты, правило ног было бы
мёртвым кодом (CMD-Q5).

Принцип границы (CMD-Q6, `docs/decisions/action-orchestration-vs-command.md`):
*действие-оркестрация* (REPLACE) — многошаговая последовательность, ведомая
петлёй по фактам; *команда-с-внутренними-шагами* (`KILL_SWITCH`) — доводит
свой teardown сама, не завися от исправности петли, и потому остаётся
**командой** (`docs/components/KillSwitchExecutor.md`), а не действием петли.

## Влияние стратегии

FSM не двигает статусы по правилу стратегии напрямую. Стратегия влияет
через: `StrategyDetail.stepsByStatus[Deal.status]` (допустимые шаги),
freshness-check перед `StrategyCondition`
(`MarketDataExpirationChecker.checkForStep`), `StrategyCondition`
(применим ли step, через `docs/components/StrategyConditionEvaluator.md`),
`StrategyAction` (что сделать), `StrategyActionCalculator` (как рассчитать
параметры). Переходы статусов делают выходные проверки по фактам, не
стратегия.

## Границы

Не создаёт `Deal`, не считает индикаторы/цены, не ходит на биржу, не
исполняет команды, не строит аудит. Свежие рыночные данные в
`DealContext` не кладёт — расчёт идёт по свежему `CalculationContext` в
`StrategyActionCalculator`. Balance freshness — precondition перед
risk-sensitive flow (см. `docs/models/domain/core/BalanceContainer.md`).
