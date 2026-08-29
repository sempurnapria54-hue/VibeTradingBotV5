# Сопровождение сделки (deal-management)

## На какой вопрос отвечает этот файл

Как устроен процесс сопровождения сделки во времени.

## Главная идея

Сделка управляется не напрямую стратегией и не executor'ами. Стратегия
задаёт ожидаемые действия и привязывает их к этапам; FSM проверяет
фактическое состояние и решает, можно ли применить действие сейчас;
калькулятор считает runtime-параметры; executor исполняет атомарные
команды.

Это композиционный корень процессов (см.
`.claude/decisions/process-materialization-criterion.md`): использует
`docs/processes/market-data-calculation.md` как поставщика данных,
вызывает подпроцессы `docs/processes/strategy-action-calculation.md` и
`docs/processes/risk-evaluation.md`. Имя `deal-management` (не
`deal-lifecycle`) — для различения с `docs/lifecycles/Deal.md`, который
владеет статусной механикой.

## Поток во времени

```text
IndicatorJob / MarketStructureJob   (market-data-calculation)
  -> готовят свежие данные рынка (фаза — на лету через MarketPhaseService)

EntryScannerJob
  -> активная Strategy -> freshness (MarketDataExpirationChecker)
  -> MarketPhase -> StrategyDetail -> ENTRY/GRID_ENTRY condition
  -> DealOpeningService

DealOpeningService
  -> создаёт Deal (ACTIVE), pinned StrategyDetail, entryReason
  -> материализует DealTranche по объявлениям детали (шаблон -> levelCount штук),
     каждый в PRECHECK, с pinned StrategyTranche и уровнем

DealOrchestratorJob
  -> загружает DealContext (DealContextService) -> DealStateMachine

DealStateMachine / handler сделки (DealActiveHandler, DealExitPendingHandler, ErrorHandler)
  -> координация: прогон FSM каждого нетерминального транша,
     шаги уровня сделки (EXIT, FAIL_SAFE), сверка Σ экспозиций с нетто-размером

DealTrancheStateMachine / handler транша
  -> ветвь стратегии:
       этап транша, freshness step, StrategyCondition, выбор StrategyAction
       -> strategy-action-calculation: CalculationContext -> Price -> Size
       -> risk-evaluation: RiskValidator -> RiskBlockResolver (для risk-creating)
       -> StrategyActionOrchestrator (per-type StrategyActionExecutor) -> ServiceCommand
  -> системная ветвь:
       SystemActionExecutor (REFRESH_DEAL_CONTEXT_ACTION / FINALIZE_DEAL_EXIT_ACTION /
       FINALIZE_DEAL_ERROR_ACTION) -> звенья-команды; состав звена выводится из
       Deal.status (docs/components/SystemActionExecutor.md). Статусные рёбра,
       являющиеся исходом системного действия, пишет звено, а не handler
       (docs/processes/fsm-execution-layering.md)

ServiceCommandExecutor -> конкретный Executor
  -> исполняет атомарную операцию, обновляет DealActionState
  -> перезагрузка DealContext, сохранение статуса
```

Компоненты: `EntryScannerJob`, `DealOpeningService`,
`DealOrchestratorJob`, `DealStateMachine`, `DealTrancheStateMachine`,
FSM handlers сделки и транша,
`StrategyActionCalculator`, `StrategyActionOrchestrator`,
`SystemActionExecutor`, `ServiceCommandExecutor`, executors
(см. `docs/components/`). Контекст
прохода — `DealContext` (`docs/components/models/DealContext.md`).

## Статусная механика и recovery

Статусы `Deal` (ACTIVE, EXIT_PENDING, CLOSED, ERROR, EMERGENCY_CLOSED),
статусы `DealTranche` (PRECHECK … CLOSED), инварианты переходов, graceful
shutdown, live risk и recovery — у `docs/lifecycles/Deal.md` и
`docs/lifecycles/DealTranche.md` (здесь не дублируются). Ключевое: `ERROR` —
non-terminal; `ERROR → CLOSED` запрещён; `ERROR → EMERGENCY_CLOSED` —
после подтверждения отсутствия live risk; для **чистого** terminal
`CLOSED` обязательны `resultProfit`/`resultProfitCurrency` — **со
смягчением по валюте на тропах закрытия без входа** (там ассерт ребра
проверяет только `resultProfit`, а валюта пишется, только если
резолвится), для ошибочного `EMERGENCY_CLOSED` — по терминальному
контракту. Безусловное
прочтение «обязательны оба поля» даёт недостижимый терминал на самом
частом сценарии сканера.

После рестарта pending `ServiceCommand` как очередь не восстанавливаются
(см. `docs/rules/command-lifecycle.md`): FSM пересобирает состояние по
runtime-графу сделки и её траншей, `DealContext`, `DealActionState` и
exchange facts.
Аудит/история не источник runtime-логики (см.
`docs/rules/audit-not-runtime-source.md`).

## Границы и ограничения

`DealOrchestratorJob` (ведёт известные `Deal`) и `AnomalyJob` (ищет
нарушения инвариантов) не смешиваются. Торговый контур и ограничения —
`docs/rules/trading-constraints.md`. Свежесть данных —
`docs/rules/market-data-freshness.md`; scope risk-проверки —
`docs/rules/risk-validator-scope.md`.
