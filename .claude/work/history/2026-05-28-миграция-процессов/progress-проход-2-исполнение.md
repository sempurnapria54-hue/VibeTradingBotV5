# Проход 2: исполнение миграции процессов

## На какой вопрос отвечает этот файл

На каком шаге исполнение прохода 2 (создание артефактов в `docs/` по
карте), что уже создано, что осталось. Рабочий журнал, переедет в
history при закрытии миграции.

## Порядок исполнения (doc-centric)

Обрабатываю по архивным докам (каждый — primary для вертикального
слайса), внутри — модели → RVO → правила → компоненты → процесс.
Кросс-доковые артефакты создаю в primary-доке по карте, в остальных —
только ссылки.

1. РИ (Расчёт индикаторов) — market-data модели, jobs/services,
   MarketDataExpirationChecker+RVO, market-data-freshness, OKX
   timeframe/instrument/market-price, market-data-calculation, TIME-Q1.
2. КЛ (Калькуляторы) — calc RVO, calculator-компоненты,
   CalculationContext, MarketPriceData, strategy-action-calculation,
   PROC-Q1, RISK-Q1.
3. ОР (Оценка рисков) — risk RVO, RiskValidator/RiskBlockResolver,
   risk-validator-scope, risk-evaluation, ENUM-Q1.
4. СК (Сервисные команды) — ServiceCommand+payloads, executors,
   ClientService, RetryPolicyService, retry-модели, DealActionState,
   command-lifecycle, runtime-error-classification, CMD-Q1, DEAL-Q3,
   расширения ack/no-partial-close.
5. СТ (Статусы) — resolver'ы+result RVO, AnomalyJob,
   controlled-exchange-exceptions, расширения exchange-hold/
   external-status-resolution, расширения Order/AlgoOrder/Position/
   BalanceContainer, OKX mapping расширения.
6. ЖЦ (Жизненный цикл) — DealContext, EntryScanner/DealOpening/
   Orchestrator, deal-management, trading-constraints, расширения
   Deal/lifecycle.
7. FSM — handlers, DealStateMachine, StrategyConditionEvaluator,
   расширения.
8. АУ (Аудит) — audit-not-runtime-source, AUDIT-вопросы.
9. Закрытие: history, backlog, snapshot v12.

## Статус по слоям

- [x] РИ — модели other (InstrumentExternalRules, IndicatorValue,
  MarketStructure+MarketPriceLevel, MarketPhase); RVO MarketPriceData,
  MarketDataExpirationResult; компоненты Candle/InstrumentSync/Indicator/
  MarketStructure/MarketPhase Job + Indicator/MarketStructure/MarketPhase
  Service + MarketDataExpirationChecker; rule market-data-freshness; OKX
  timeframe/instrument/market-price mapping; process market-data-calculation;
  TIME-Q1. Strategy settings — уже были в Strategy.md.
- [x] КЛ — RVO CalculatedStrategyAction, StrategyActionCalculationResult,
  CalculationError, CalculatedSize, CalculatedPrice, CalculationContext;
  компоненты StrategyActionCalculator, CalculationContextFactory,
  PriceCalculator, SizeCalculator, MarketPriceDataService,
  InstrumentExternalRulesService; process strategy-action-calculation;
  PROC-Q1 дополнен цитатами, RISK-Q1 заведён.
- [x] ОР — RVO RiskValidationResult, RiskCheckResult, RiskBlockAction;
  компоненты RiskValidator, RiskBlockResolver; rule risk-validator-scope;
  process risk-evaluation; ENUM-Q1 заведён.
- [x] СК — RVO ServiceCommand(+Type), ServiceCommandPayload(+подтипы
  разделами); компоненты ServiceCommandExecutor, ServiceCommandFactory,
  ClientService, RetryPolicyService(+retry-модели разделами), 14
  executor'ов (Order/Algo Create/Submit/Refresh/Amend/Cancel,
  RefreshPosition, ClosePosition, RefreshFills, RefreshBalance); rules
  command-lifecycle, runtime-error-classification (новые, владеет
  RuntimeErrorCode); расширены ack-not-runtime-truth, no-partial-close,
  raw-exchange-dto-boundary; CMD-Q1, DEAL-Q3 заведены; СК-Q1 закрыт,
  финализационные executor'ы отложены (DEAL-Q1) — пометка в tasks.
  DealActionState/Retryable/RuntimeTarget — НЕ материализованы (DEAL-Q3).
- [x] СТ — компоненты OrderExternalStatusResolver,
  AlgoOrderExternalStatusResolver, PositionStatusResolver, AnomalyJob,
  KillSwitchExecutor; RVO PositionStatusResolveResult; rule
  controlled-exchange-exceptions; дополнены external-status-resolution,
  exchange-hold (DISABLED). Статусные модели/lifecycles — уже полны
  (model-кластер). Mappers → backlog п.2; ReconciliationJob → п.7.
- [x] ЖЦ — RVO DealContext; компоненты EntryScannerJob,
  DealOpeningService, DealOrchestratorJob, DealContextService; rule
  trading-constraints; process deal-management; DEAL-Q3 дополнен ЖЦ §7.
- [x] FSM — DealStateMachine (+3 проверки), StrategyConditionEvaluator,
  7 handlers. CLOSED/EMERGENCY_CLOSED handler'ов нет.
- [x] АУ — rule audit-not-runtime-source; процесс НЕ материализован
  (backlog п.6), ~30 подвопросов остаются в tasks-аудит.
- [x] Закрытие — история архивирована, backlog обновлён, snapshot v12.

## Итог

86 новых файлов в `docs/` (53 компонента, 15 RVO, 4 процесса, 4
market-data модели в other, 7 новых сквозных правил, 3 новых OKX rule);
5 расширений существующих правил. OKX mapping-доки и статусные
модели/lifecycles были уже полны после прошлого model-кластера — не
дублировались. Open-questions: TIME-Q1, RISK-Q1, ENUM-Q1, CMD-Q1,
DEAL-Q3; PROC-Q1 дополнен. Не материализованы: DealActionState/Retryable/
RuntimeTarget (DEAL-Q3), PositionContext (PROC-Q1), RiskSettings (RISK-Q1),
финализационные executor'ы (DEAL-Q1), audit (backlog п.6), mappers (п.2),
ReconciliationJob (п.7), Exchange/Instrument (п.9), TradeFill (п.6).
