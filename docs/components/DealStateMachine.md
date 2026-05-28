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
   этапа (refresh / `CREATE_*`/`SUBMIT_*`/`AMEND_*`/`CANCEL_*` /
   risk-reducing/cleanup команды; проверка condition; выбор action; вызов
   калькулятора и `ServiceCommandFactory`). Сама по себе завершение этапа
   не означает.
3. **Выходные проверки** — можно ли считать этап завершённым; именно они
   отвечают за обычный переход между этапами.

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
risk-sensitive flow (см. `docs/models/core/BalanceContainer.md`).
