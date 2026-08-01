# Дерево базы знания

## На какой вопрос отвечает этот файл

На какой вопрос отвечает каждый каталог и файл базы знания.

## Как читать и вести

- У каждой папки — её главный вопрос (тип знания, канон —
  `.claude/rules/structure.md`), у каждого файла — его вопрос
  (сокращённая форма раздела «На какой вопрос отвечает этот файл»).
- Обновляется `knowledge-curator` при каждом
  добавлении/перемещении/удалении файла знания
  (`.claude/rules/curation.md`).
- Не индексируются: содержимое `work/history/` и
  `.claude-archive/` (архив), PDF в `library/trading/raw/`
  (гитигнорятся), технические файлы (`.gitkeep`, конфиги, логи),
  `src/` (код).
- Иллюстрация-обзор (то же дерево крупными мазками, счёт файлов
  по каталогам) — соседний `knowledge-tree.svg`; канон — этот
  файл, картинка обновляется вместе с ним при изменении состава
  каталогов.

![Дерево базы знания](knowledge-tree.svg)

## Дерево

```text
.
├── CLAUDE.md — Каковы базовые правила проекта?
├── .claude/ — Как устроена инфраструктура работы над проектом (пайплайн)?
│   ├── knowledge-tree.md — На какой вопрос отвечает каждый каталог и файл базы знания? (этот файл)
│   ├── agents/ — Что делает Claude в роли X?
│   │   ├── code-writer.md — Что делает Claude в роли code-writer?
│   │   ├── integrator.md — Что делает Claude в роли интегратора?
│   │   ├── knowledge-curator.md — Что делает Claude в роли knowledge-curator?
│   │   ├── reviewer.md — Что делает Claude в роли reviewer?
│   │   ├── solution-designer.md — Что делает Claude в роли solution-designer?
│   │   ├── tester.md — Что делает Claude в роли tester?
│   │   └── trading-specialist.md — Что делает Claude в роли торгового специалиста?
│   ├── chat/ — Что вшивается в чат claude.ai?
│   │   ├── chat-project-instructions.md — Каковы правила поведения Claude в чате claude.ai на этом проекте?
│   │   └── structure-digest.md — Какие типы знания есть в проекте (компактный перечень для чата)?
│   ├── decisions/ — Почему мы решили так, а не иначе? (пайплайн)
│   │   ├── chat-vs-cc-knowledge-split.md — Как разделено знание по адресатам — чат vs Claude Code?
│   │   ├── client-layer-docs.md — Где живут exchange-specific факты?
│   │   ├── code-templates-vs-examples.md — Почему код-шаблоны и find-code-examples — два разных инструмента?
│   │   ├── component-vs-process.md — Как различать «компонент» и «процесс» при классификации?
│   │   ├── context-cost-diet.md — Почему контекстная стоимость знаниевых файлов сокращена именно так?
│   │   ├── cross-cutting-parking.md — Как мигрируем сущность, чьё знание частично относится к другим кластерам?
│   │   ├── executor-payload-file-granularity.md — Почему документация command-layer гранулируется file-per-executor?
│   │   ├── forward-notes-after-task-closure.md — Где живут форвард-заметки после закрытия задачи-источника?
│   │   ├── fsm-handler-as-component.md — Где живёт handler-per-status FSM-сущности?
│   │   ├── integrator-agent.md — Почему интеграционное знание внешних источников введено именно так?
│   │   ├── knowledge-classification.md — Почему фиксация знания устроена через пятишаговую классификацию?
│   │   ├── master-index-not-fixated.md — Почему не сохраняем master-index/навигационные доки как знание?
│   │   ├── migration-triad.md — По какому принципу фиксируем фрагменты при миграции из исчезающего источника?
│   │   ├── model-granularity.md — Что считать самостоятельной доменной моделью, а что разделом родителя?
│   │   ├── model-layer-ontology.md — Как организованы доменные и интеграционные модели в docs/models/?
│   │   ├── models-core-vs-other.md — Как разделены persisted-модели на core и other?
│   │   ├── negative-statements-not-fixated.md — Почему не фиксируем утверждения «X не хранит Y»?
│   │   ├── process-materialization-criterion.md — По какому критерию кандидат в процесс материализуется файлом?
│   │   ├── product-roadmap-type.md — Почему тип «роадмап» устроен так, а не иначе?
│   │   ├── rule-source-of-truth.md — Где первоисточник правила, когда оно ложится в несколько мест?
│   │   ├── runtime-value-object.md — Где живут runtime-объекты компонентного слоя?
│   │   ├── source-api-target-rebase.md — Почему контур тестов API источника бьёт в сырьё, а не в нашу границу?
│   │   ├── test-knowledge-type.md — Почему per-source тест-планы живут в .claude/tests/?
│   │   └── trading-council.md — Почему торговое знание введено в пайплайн именно так?
│   ├── library/ — Что говорят внешние первоисточники?
│   │   └── trading/
│   │       ├── raw/ — Что говорит торговый первоисточник? (книги; наполняет пользователь; PDF не в git)
│   │       └── distilled/ — Что говорит торговый первоисточник? (компактно, для точечной загрузки)
│   │           ├── corpus-map.md — Как по указателю «книга + глава + страница» попасть в нужное место PDF?
│   │           ├── microstructure.md — Что говорит корпус о микроструктуре рынка?
│   │           ├── risk-and-sizing.md — Что говорит корпус о риске на сделку, сайзинге и ожидаемости?
│   │           ├── strategy-patterns.md — Что говорит корпус о типах торговых систем?
│   │           └── system-design.md — Что говорит корпус о проектировании торговых систем?
│   ├── notes/ — Что я наблюдал / разведал?
│   │   ├── 2026-05-26-обкатка-классификации-торговые-модели.md — Что наблюдал на второй обкатке классификации?
│   │   ├── 2026-05-29-ростер-тулинга-роадмап.md — Какой состав тулинга нужен для исполнения шага роадмапа?
│   │   ├── 2026-06-05-аудит-делегируемости-вопросов.md — Кто из агентов может владеть каждым открытым вопросом?
│   │   ├── 2026-06-12-okx-api-key-14d-expiry.md — Что разведано про протухание OKX API-ключей?
│   │   ├── 2026-07-02-code-review-full-codebase.md — Что показало агентское ревью всей кодовой базы?
│   │   ├── 2026-07-06-аудит-контекстной-стоимости.md — Что показал аудит контекстной стоимости знаниевых файлов?
│   │   └── 2026-07-18-ревью-ролей-ревьюера-и-solution-designer.md — Что показало ревью пары «критик ↔ конструктив» (роли и скиллы)?
│   ├── projects/<путь-проекта>/memory/ — (артефакт харнесса: файловая память Claude Code; MEMORY.md + по файлу на факт. Не файлы знания проекта — правило «На какой вопрос отвечает» к ним не применяется.)
│   ├── processes/ — Как устроен этот методологический процесс?
│   │   ├── api-docs-completion.md — Как интеграционные доки источника доводятся до полного покрытия периметра?
│   │   ├── knowledge-classification.md — Как устроен процесс классификации знания?
│   │   ├── pipeline-shakedown.md — Как устроена обкатка агентского пайплайна?
│   │   ├── question-delegation.md — Как устроена поэтапная передача вопросов от пользователя агентам?
│   │   ├── roadmap-step-execution.md — Как устроено исполнение одного шага роадмапа (docs-first)?
│   │   ├── source-api-testing.md — Как устроено тестирование API внешнего источника?
│   │   └── trading-library-distillation.md — Как знание из сырых книг выносится в дистиллят?
│   ├── rules/ — Какое у нас правило?
│   │   ├── classification-report.md — Какое правило отчётности при классификации знания?
│   │   ├── closed-work-transfer.md — Какое правило переноса закрытого из рабочих файлов в history/?
│   │   ├── codestyle.md — Как мы пишем код?
│   │   ├── curation.md — Какое правило регулярной курации базы знания?
│   │   ├── external-source-sync.md — Какое правило синхронизации файлов с внешним источником правды?
│   │   ├── naming.md — Какое правило именования файлов?
│   │   ├── snapshot-format.md — Какое правило формата снапшота?
│   │   ├── structure.md — Какое правило размещения знания?
│   │   └── tech-radar.md — Что мы используем при написании кода (стэк со статусами)?
│   ├── skills/ — Как именно делать X?
│   │   ├── classify-area.md — Как определить область фрагмента знания?
│   │   ├── classify-theme.md — Как определить тему фрагмента внутри типа?
│   │   ├── classify-type.md — Как определить тип знания фрагмента?
│   │   ├── concept-review.md — Как сделать сквозную проверку концепции доков под шаг роадмапа?
│   │   ├── conventions-review.md — Как сделать ревью соответствия кода конвенциям?
│   │   ├── design-fork.md — Как конструктивный владелец прорабатывает развилку до вариантов с креном?
│   │   ├── disaster-review.md — Как сделать ревью поведения кода при сбоях и рестартах?
│   │   ├── divergence-review.md — Как найти расхождения утверждённого кода с доками?
│   │   ├── find-code-examples.md — Как подобрать примеры из реального кода для доков?
│   │   ├── integration-okx.md — Какова специфика источника OKX при доукомплектации доков?
│   │   ├── performance-review.md — Как сделать ревью производительности кода?
│   │   ├── place-knowledge.md — Как разместить фрагмент знания в репозитории?
│   │   ├── recognize-knowledge.md — Как опознать фрагмент, который стоит зафиксировать?
│   │   ├── reconcile-knowledge.md — Как привести доку к изменившемуся/удалённому коду?
│   │   ├── security-review.md — Как сделать ревью безопасности кода?
│   │   ├── test-code.md — Как написать код-тесты контура API источника по плану?
│   │   ├── test-collection.md — Как построить исполняемую тест-коллекцию (Postman)?
│   │   ├── test-design.md — Как построить тест-план и кейсы по сырью API источника?
│   │   ├── test-review.md — Как сделать адверсариальное ревью тест-артефактов?
│   │   ├── test-run.md — Как прогнать утверждённый тест-план?
│   │   ├── trading-review.md — Как сделать адверсариальный проход по торговой корректности?
│   │   └── update-roadmap-progress.md — Как обновить статус шага и пересчитать статус фазы?
│   ├── snapshots/ — Где мы сейчас?
│   │   └── snapshot-v72.md — Где мы сейчас? (актуальный; старые — в work/history/snapshots/)
│   ├── templates/
│   │   ├── code/ — Каков абстрактный паттерн/шаблон кода для X?
│   │   │   └── Java/Controller.md — Каков паттерн контроллера нашего API?
│   │   └── docs/ — Каков формат докового файла типа X?
│   │       ├── gap-report.md — Каков формат gap-отчёта DOCS_CHECK (поля находки, узлы, эскалации)?
│   │       └── test-plan.md — Каков формат тест-плана контура API источника?
│   ├── tests/ — Как проверяем API источника?
│   │   └── source-api/okx/
│   │       ├── plan.md — Как проверяем API OKX на уровне сырья (план + факт прогонов)?
│   │       ├── collection.postman_collection.json — (артефакт: Postman-коллекция)
│   │       └── environment.postman_environment.json — (артефакт: Postman-окружение)
│   └── work/ — В каком состоянии исполнительная работа?
│       ├── backlog.md — Что мы планируем сделать?
│       ├── delegation-ledger.md — Какой счёт прогонов гейта делегирования у категорий владельцев?
│       ├── progress/ — На каком шаге мы в этой активной операции?
│       │   ├── phase-1-step-7-chronicle.md — Какова хроника под-шагов шага 7 Фазы 1?
│       │   ├── phase-1-step-7-docs-check-1.md — Каков исход DOCS_CHECK_1 шага 7?
│       │   ├── phase-1-step-7-docs-check-2.md — Каков исход DOCS_CHECK_2 шага 7?
│       │   ├── phase-1-step-7-docs-check-3.md — Каков исход DOCS_CHECK_3 шага 7?
│       │   ├── phase-1-step-7-docs-check-4.md — Каков исход DOCS_CHECK_4 шага 7?
│       │   ├── phase-1-step-7-docs-check-5.md — Каков исход DOCS_CHECK_5 шага 7?
│       │   ├── phase-1-step-7-docs-check-6.md — Каков исход DOCS_CHECK_6 шага 7?
│       │   ├── phase-1-step-7-docs-check-7.md — Каков исход DOCS_CHECK_7 шага 7?
│       │   ├── phase-1-step-7-docs-check-8.md — Каков исход DOCS_CHECK_8 шага 7?
│       │   ├── phase-1-step-7-gaps-close-1.md — Как закрыты пробелы DOCS_CHECK_1 шага 7?
│       │   ├── phase-1-step-7-gaps-close-2.md — Как закрыты пробелы DOCS_CHECK_2 шага 7?
│       │   ├── phase-1-step-7-gaps-close-3.md — Как закрыты пробелы DOCS_CHECK_3 шага 7?
│       │   ├── phase-1-step-7-gaps-close-4.md — Как закрыты пробелы DOCS_CHECK_4 шага 7?
│       │   ├── phase-1-step-7-gaps-close-5.md — Как закрыты пробелы DOCS_CHECK_5 шага 7 (и что withhold)?
│       │   ├── phase-1-step-7-gaps-close-6.md — Как закрыты пробелы DOCS_CHECK_6 шага 7?
│       │   └── phase-1-step-7-gaps-close-7.md — Как закрыты пробелы DOCS_CHECK_7 шага 7?
│       ├── questions/ — Что мы ещё не решили?
│       │   ├── open-questions.md — Что мы ещё не решили (общие вопросы)?
│       │   └── tasks/ — Что неясно по конкретной активной задаче? (сейчас пусто)
│       ├── roadmap/ — Куда движется продукт?
│       │   ├── roadmap.md — Какими фазами продукт движется к бизнес-ценности?
│       │   └── phase-1.md — В каком статусе каждый шаг Фазы 1?
│       └── history/ — Что мы уже сделали? (архив; содержимое не индексируется)
│           └── snapshots/ — Где мы были раньше? (снапшоты v1–v71)
├── docs/ — Как устроен продукт (продуктовая документация)?
│   ├── components/ — Кто выполняет?
│   │   ├── models/ — Что это за runtime-объект?
│   │   │   ├── CalculatedPrice.md — Что это за RVO CalculatedPrice?
│   │   │   ├── CalculatedSize.md — Что это за RVO CalculatedSize?
│   │   │   ├── CalculatedStrategyAction.md — Что это за RVO CalculatedStrategyAction?
│   │   │   ├── CalculationContext.md — Что это за RVO CalculationContext?
│   │   │   ├── CalculationError.md — Что это за RVO CalculationError?
│   │   │   ├── DealContext.md — Что это за RVO DealContext?
│   │   │   ├── HoldSignal.md — Что это за RVO HoldSignal?
│   │   │   ├── MarketDataExpirationResult.md — Что это за RVO MarketDataExpirationResult?
│   │   │   ├── MarketPriceData.md — Что это за RVO MarketPriceData?
│   │   │   ├── PositionStatusResolveResult.md — Что это за RVO PositionStatusResolveResult?
│   │   │   ├── RiskBlockAction.md — Что это за RVO RiskBlockAction?
│   │   │   ├── RiskCheckResult.md — Что это за RVO RiskCheckResult?
│   │   │   ├── RiskValidationResult.md — Что это за RVO RiskValidationResult?
│   │   │   ├── ServiceCommand.md — Что это за RVO ServiceCommand?
│   │   │   ├── ServiceCommandPayload.md — Что такое ServiceCommandPayload и где живут payload-подтипы?
│   │   │   └── StrategyActionCalculationResult.md — Что это за RVO StrategyActionCalculationResult?
│   │   ├── AlgoOrderExternalStatusResolver.md — Кто переводит внешний статус algo-order в доменный?
│   │   ├── AnomalyJob.md — Кто ищет нарушения базовых инвариантов системы?
│   │   ├── AttachedAlgoOrderStateResolver.md — Кто определяет доменный статус attached protection?
│   │   ├── CalculationContextFactory.md — Кто собирает CalculationContext?
│   │   ├── CancelAlgoOrderExecutor.md — Кто исполняет CANCEL_ALGO_ORDER?
│   │   ├── CancelOrderExecutor.md — Кто исполняет CANCEL_ORDER?
│   │   ├── CandleJob.md — Кто готовит базовые свечные данные?
│   │   ├── ClosePositionExecutor.md — Кто исполняет CLOSE_POSITION?
│   │   ├── CreateAlgoOrderActionExecutor.md — Кто планирует CREATE-действие над standalone algo-order?
│   │   ├── CreateAlgoOrderExecutor.md — Кто исполняет CREATE_ALGO_ORDER?
│   │   ├── CreateOrderActionExecutor.md — Кто планирует CREATE-действие над ordinary order?
│   │   ├── CreateOrderExecutor.md — Кто исполняет CREATE_ORDER?
│   │   ├── DealContextService.md — Кто собирает DealContext для прохода FSM?
│   │   ├── DealFinalizationCommandFactory.md — Кто эмитит финализационную команду сделки за проход?
│   │   ├── DealOpeningService.md — Кто атомарно создаёт Deal?
│   │   ├── DealOrchestratorJob.md — Кто сопровождает уже созданные сделки?
│   │   ├── DealStateMachine.md — Кто управляет статусами сделки (FSM)?
│   │   ├── EntryFinalizedHandler.md — Что делает FSM handler статуса ENTRY_FINALIZED?
│   │   ├── EntryScannerJob.md — Кто ищет возможность создать новую сделку?
│   │   ├── EntrySubmittedHandler.md — Что делает FSM handler статуса ENTRY_SUBMITTED?
│   │   ├── ErrorHandler.md — Что делает FSM handler статуса ERROR?
│   │   ├── ExitPendingHandler.md — Что делает FSM handler статуса EXIT_PENDING?
│   │   ├── FinalizeDealEntryExecutor.md — Кто исполняет FINALIZE_DEAL_ENTRY?
│   │   ├── FinalizeDealExitExecutor.md — Кто исполняет FINALIZE_DEAL_EXIT?
│   │   ├── IndicatorJob.md — Кто считает технические индикаторы?
│   │   ├── IndicatorService.md — Кто отдаёт готовые значения индикаторов?
│   │   ├── InstrumentExternalRulesDataService.md — Кто отдаёт внешние правила инструмента (граница persistence)?
│   │   ├── InstrumentExternalRulesSyncJob.md — Кто обновляет внешние правила инструмента?
│   │   ├── IntegrationService.md — Кто является границей биржевого клиента / adapter-layer?
│   │   ├── KillSwitchExecutor.md — Кто исполняет kill-switch teardown?
│   │   ├── KillSwitchService.md — Кто триггерит аварийный kill-switch для реакции холда?
│   │   ├── ManagingHandler.md — Что делает FSM handler статуса MANAGING?
│   │   ├── MarkDealClosedExecutor.md — Кто исполняет MARK_DEAL_CLOSED?
│   │   ├── MarkDealEmergencyClosedExecutor.md — Кто исполняет MARK_DEAL_EMERGENCY_CLOSED?
│   │   ├── MarkDealErrorExecutor.md — Кто исполняет MARK_DEAL_ERROR?
│   │   ├── MarketDataExpirationChecker.md — Кто проверяет свежесть рыночных данных?
│   │   ├── MarketPhaseResolver.md — Кто резолвит авторские правила фазы в MarketPhase.Type?
│   │   ├── MarketPhaseService.md — Кто отдаёт актуальную фазу рынка?
│   │   ├── MarketPriceDataService.md — Кто отдаёт runtime-цены инструмента?
│   │   ├── MarketStructureJob.md — Кто считает структуру рынка (job)?
│   │   ├── MarketStructureResolver.md — Кто вычисляет структуру рынка из свечей?
│   │   ├── MarketStructureService.md — Кто отдаёт готовую структуру рынка?
│   │   ├── OrderExternalStatusResolver.md — Кто переводит внешний статус ordinary order в доменный?
│   │   ├── PositionStatusResolver.md — Кто определяет доменный статус позиции по факту наличия?
│   │   ├── PrecheckHandler.md — Что делает FSM handler статуса PRECHECK?
│   │   ├── PriceCalculator.md — Кто рассчитывает цены действия?
│   │   ├── ProtectionSwitchedHandler.md — Что делает FSM handler статуса PROTECTION_SWITCHED?
│   │   ├── RefreshAlgoOrderExecutor.md — Кто исполняет REFRESH_ALGO_ORDER?
│   │   ├── RefreshBalanceExecutor.md — Кто исполняет REFRESH_BALANCE?
│   │   ├── RefreshBillsExecutor.md — Кто исполняет REFRESH_BILLS?
│   │   ├── RefreshOrderExecutor.md — Кто исполняет REFRESH_ORDER?
│   │   ├── RefreshPositionExecutor.md — Кто исполняет REFRESH_POSITION?
│   │   ├── RetryPolicyService.md — Кто управляет retry-политикой исполнения команд?
│   │   ├── RiskBlockResolver.md — Кто превращает результат risk-проверки в действие handler'а?
│   │   ├── RiskValidator.md — Кто проверяет рассчитанное действие по risk-policy?
│   │   ├── SafetyHoldCoordinator.md — Кто координирует реактивную реакцию CRITICAL-холда над сделкой?
│   │   ├── ServiceCommandExecutor.md — Кто исполняет атомарную команду и маршрутизирует её?
│   │   ├── SizeCalculator.md — Кто рассчитывает размер действия?
│   │   ├── StrategyActionCalculator.md — Кто рассчитывает runtime-параметры действия стратегии?
│   │   ├── StrategyActionExecutor.md — Кто выдаёт следующую команду одного типа действия за проход?
│   │   ├── StrategyActionOrchestrator.md — Кто диспетчеризует планирование действия стратегии за проход?
│   │   ├── StrategyConditionEvaluator.md — Кто проверяет применимость StrategyCondition?
│   │   ├── SubmitAlgoOrderExecutor.md — Кто исполняет SUBMIT_ALGO_ORDER?
│   │   └── SubmitOrderExecutor.md — Кто исполняет SUBMIT_ORDER?
│   ├── decisions/ — Почему мы решили так, а не иначе? (продукт)
│   │   ├── action-orchestration-vs-command.md — Чем действие-оркестрация отличается от аварийного teardown?
│   │   ├── controlled-violation-exchange-wide-hold.md — Почему контролируемая биржевая ошибка поднимает L4-холд биржи?
│   │   ├── deal-action-state-materialization.md — Почему DealActionState материализован именно так?
│   │   ├── deal-finalization-state-materialization.md — Почему retry-state финализации — отдельная сущность?
│   │   ├── derived-market-data-code-increments.md — Почему код-инкременты производных рыночных данных такие?
│   │   ├── efficiency-ratio-as-catalog-indicator.md — Почему ER — каталожный операнд без выделенного ruleType?
│   │   ├── fsm-execution-layering.md — Как разложены слои исполнения сделки и почему?
│   │   ├── instrument-external-rules-materialization.md — Как материализуется InstrumentExternalRules и почему?
│   │   ├── market-data-result-identity-keying.md — Почему результаты расчёта ключуются настройкой-владельцем?
│   │   ├── market-phase-conditional-classification.md — Почему MarketPhase.Type определяется авторскими условиями?
│   │   ├── market-phase-stateless.md — Почему MarketPhase вычисляется на лету, а не хранится?
│   │   ├── per-trade-risk-policy.md — Какова риск-политика на сделку в фазе 1 и почему?
│   │   ├── pnl-finalization-mechanics.md — Как механически добываются P&L-факты и пишется resultProfit?
│   │   ├── refresh-evidence-cycle-ownership.md — Кто проходит evidence-cycle refresh-команд?
│   │   ├── replace-not-amend.md — Почему домен ремоделирует через REPLACE, а не амендит?
│   │   ├── result-profit-source.md — Откуда берётся Deal.resultProfit и почему?
│   │   ├── service-command-payload-base-type.md — Почему у payload'ов общий маркер-базовый тип?
│   │   ├── source-model-change-absorption.md — Как мы обходимся с известным заранее изменением модели источника?
│   │   ├── strategy-condition-authoring-contract.md — Почему контракт авторинга условия — объектная settings-модель?
│   │   ├── strategy-materialization-and-validation.md — Как материализуется «одна реализация» и scope валидатора?
│   │   ├── strategy-signal-is-entry-condition.md — Почему «сигнал» — это условие входного шага стратегии?
│   │   ├── strategy-tree-persistence.md — Почему дерево Strategy персистится реляционным каркасом?
│   │   └── volume-condition-semantics.md — Почему объёмное условие — подтверждающий фильтр?
│   ├── dictionary/ — Что означает этот термин? (пока пусто)
│   ├── integrations/ — Каков контракт и правила источника {name}?
│   │   └── okx/
│   │       ├── coverage-manifest.md — Какова полнота покрытия поверхности OKX REST API доками?
│   │       ├── contracts/ — Каков контракт и лимиты операции OKX?
│   │       │   ├── account-bills.md — Каков контракт операций по bill-записям аккаунта?
│   │       │   ├── account-config.md — Каков контракт операций конфигурации счёта?
│   │       │   ├── account-position-risk.md — Каков контракт операции account-position-risk?
│   │       │   ├── account-rate-limit.md — Каков контракт чтения аккаунт-уровневого rate limit?
│   │       │   ├── algo-order.md — Каков контракт операций по algo-ордеру?
│   │       │   ├── balance.md — Каков контракт получения баланса?
│   │       │   ├── batch-operations.md — Каков контракт batch-операций по ordinary order?
│   │       │   ├── cancel-all-after.md — Каков контракт Cancel All After?
│   │       │   ├── candle.md — Каков контракт операций по свечам?
│   │       │   ├── fills-archive.md — Каков контракт выгрузки fills старше 3 месяцев?
│   │       │   ├── fills.md — Каков контракт операций по fills?
│   │       │   ├── funding-rate.md — Каков контракт чтения funding rate SWAP?
│   │       │   ├── index-data.md — Каков контракт чтения данных индекса?
│   │       │   ├── instrument.md — Каков контракт получения спецификации инструмента?
│   │       │   ├── insurance-fund.md — Каков контракт чтения баланса страхового фонда?
│   │       │   ├── mark-price.md — Каков контракт чтения mark price?
│   │       │   ├── market-price-data.md — Каков контракт получения тикера?
│   │       │   ├── max-size.md — Каков контракт оценки max-size / max-avail-size?
│   │       │   ├── open-interest.md — Каков контракт чтения открытого интереса?
│   │       │   ├── order-book.md — Каков контракт чтения стакана?
│   │       │   ├── order-precheck.md — Каков контракт order precheck?
│   │       │   ├── order.md — Каков контракт операций по ordinary order?
│   │       │   ├── position-tiers.md — Каков контракт чтения позиционных тиров?
│   │       │   ├── position.md — Каков контракт операций по позиции?
│   │       │   ├── price-limit.md — Каков контракт чтения ценовых лимитов?
│   │       │   ├── public-trades.md — Каков контракт чтения публичных сделок?
│   │       │   ├── server-time.md — Каков контракт чтения серверного времени?
│   │       │   ├── service-urls.md — Какие URL у OKX по окружениям и регионам?
│   │       │   └── trade-fee.md — Каков контракт чтения ставок комиссий аккаунта?
│   │       └── rules/ — Какое правило источника OKX?
│   │           ├── adapter-constants.md — Какие константы OKX adapter выставляет сам?
│   │           ├── reduce-only-invariant.md — Какой invariant adapter проверяет по reduceOnly?
│   │           ├── timeframe-constants.md — Какое правило обращения со строками таймфреймов OKX?
│   │           └── ws-limits.md — Какие лимиты у WebSocket-соединений OKX?
│   ├── lifecycles/ — Через какие состояния проходит этот объект?
│   │   ├── AlgoOrder.md — Через какие статусы проходит AlgoOrder?
│   │   ├── AnomalyReport.md — Через какие статусы проходит AnomalyReport?
│   │   ├── CandleGroup.md — Через какие статусы проходит загрузка свечей группы?
│   │   ├── Deal.md — Через какие FSM-статусы проходит Deal?
│   │   ├── DealActionState.md — Через какие статусы проходит DealActionState?
│   │   ├── DealFinalizationState.md — Через какие статусы проходит DealFinalizationState?
│   │   ├── Instrument.md — Через какие статусы проходит онбординг инструмента?
│   │   ├── Order.md — Через какие статусы проходят Order и AttachedAlgoOrder?
│   │   ├── Position.md — Через какие статусы проходит Position?
│   │   └── Strategy.md — Через какие административные статусы проходит Strategy?
│   ├── models/
│   │   ├── api/ — Что это за модель API нашего сервиса?
│   │   │   ├── README.md — Что это за слой и когда здесь появляются файлы?
│   │   │   └── OkxRawApiRequest.md — Какие поля у конверта запроса POST /api/proxy/okx/raw?
│   │   ├── domain/
│   │   │   ├── aggregate/ — Что это за сущность без биржевой привязки, нужная для торговли?
│   │   │   │   ├── Deal.md — Что это за торговая модель Deal?
│   │   │   │   └── Strategy.md — Что это за торговая модель Strategy?
│   │   │   ├── core/ — Что это за торговая модель с биржевым воплощением?
│   │   │   │   ├── AlgoOrder.md — Что это за торговая модель AlgoOrder?
│   │   │   │   ├── BalanceContainer.md — Что это за торговая модель BalanceContainer (и Balance)?
│   │   │   │   ├── Exchange.md — Что это за доменная модель Exchange?
│   │   │   │   ├── Instrument.md — Что это за доменная модель Instrument?
│   │   │   │   ├── Order.md — Что это за торговая модель Order (и AttachedAlgoOrder)?
│   │   │   │   └── Position.md — Что это за торговая модель Position?
│   │   │   └── other/ — Что это за прочая хранимая модель?
│   │   │       ├── AnomalyReport.md — Что это за модель AnomalyReport?
│   │   │       ├── Auditable.md — Какие общие audit-поля несут доменные сущности?
│   │   │       ├── Candle.md — Что это за доменная модель Candle?
│   │   │       ├── CandleGroup.md — Что это за доменная модель CandleGroup?
│   │   │       ├── DealActionState.md — Что это за модель DealActionState?
│   │   │       ├── DealCashFlow.md — Что это за модель DealCashFlow?
│   │   │       ├── DealFinalizationState.md — Что это за модель DealFinalizationState?
│   │   │       ├── IndicatorValue.md — Что это за модель IndicatorValue?
│   │   │       ├── InstrumentExternalRules.md — Что это за модель InstrumentExternalRules?
│   │   │       ├── MarketPhase.md — Что это за MarketPhase и почему вычисляется на лету?
│   │   │       ├── MarketStructure.md — Что это за модель MarketStructure?
│   │   │       └── TradeFeeRate.md — Что это за модель TradeFeeRate?
│   │   ├── externalSnapshot/ — Какая структура нормализованного граничного объекта *ExternalSnapshot?
│   │   │   └── README.md — Что это за слой и когда здесь появляются файлы?
│   │   ├── integrations/ — Какие поля у нативной модели источника {name}?
│   │   │   └── okx/
│   │   │       ├── CandleOkxResponse.md — Какие поля у OKX candle response?
│   │   │       ├── InstrumentOkxResponse.md — Какие поля у OKX instrument response?
│   │   │       ├── OkxAccountBillResponse.md — Какие поля у OKX bill response?
│   │   │       ├── OkxAlgoOrderResponse.md — Какие поля у OKX algo-order response?
│   │   │       ├── OkxBalanceResponse.md — Какие поля у OKX account balance response?
│   │   │       ├── OkxFillResponse.md — Какие поля у OKX fill response?
│   │   │       ├── OkxFillsArchiveResponse.md — Какие поля у OKX fills-archive responses?
│   │   │       ├── OkxOrderResponse.md — Какие поля у OKX ordinary order response?
│   │   │       ├── OkxPositionResponse.md — Какие поля у OKX positions response?
│   │   │       ├── OkxPositionsHistoryResponse.md — Какие поля у OKX positions-history response?
│   │   │       ├── OkxTickerResponse.md — Какие поля у OKX ticker response?
│   │   │       └── OkxTradeFeeResponse.md — Какие поля у OKX trade-fee response?
│   │   ├── mapping/ — Как сущность переходит между слоями?
│   │   │   ├── AlgoOrder.md — Как AlgoOrder ложится на нативные модели и нормализуется?
│   │   │   ├── Balance.md — Как BalanceContainer/Balance ложатся на нативные модели?
│   │   │   ├── Candle.md — Как нативные свечи ложатся на доменные свечные данные?
│   │   │   ├── DealCashFlow.md — Как OKX bill-записи ложатся на DealCashFlow?
│   │   │   ├── Instrument.md — Как Instrument переходит между слоями?
│   │   │   ├── InstrumentExternalRules.md — Как InstrumentExternalRules ложится на нативные модели?
│   │   │   ├── MarketPriceData.md — Как MarketPriceData ложится на нативные модели?
│   │   │   ├── Order.md — Как Order (+AttachedAlgoOrder) ложится на нативные модели?
│   │   │   ├── Position.md — Как Position ложится на нативные модели?
│   │   │   ├── PositionCloseResult.md — Как positions-history нормализуется в PositionCloseResult-снапшот?
│   │   │   ├── Strategy.md — Как Strategy переходит между слоями (api ↔ domain ↔ persistence)?
│   │   │   ├── TimeFrame.md — Как enum TimeFrame маппится в строки таймфреймов источников?
│   │   │   ├── TradeFeeRate.md — Как ставка комиссии источника ложится на TradeFeeRate?
│   │   │   └── TradeFill.md — Как fills легли бы на TradeFill, если бы он вводился?
│   │   └── persistence/ — Что это за модель хранимого слоя?
│   │       └── README.md — Что это за слой и когда здесь появляются файлы?
│   ├── processes/ — Как устроен этот процесс?
│   │   ├── candle-loading.md — Как устроена добыча и поддержание целостности свечной истории?
│   │   ├── deal-management.md — Как устроено сопровождение сделки во времени?
│   │   ├── market-data-calculation.md — Как устроено вычисление производных рыночных данных?
│   │   ├── risk-evaluation.md — Как устроена оценка риска?
│   │   └── strategy-action-calculation.md — Как устроен расчёт параметров одного StrategyAction?
│   └── rules/ — Какое правило действует в системе?
│       ├── ack-not-runtime-truth.md — Почему ACK не подтверждает фактическое состояние сущности?
│       ├── audit-not-runtime-source.md — Почему аудит не источник runtime-логики FSM?
│       ├── business-logic-on-domain-model.md — Где выполняется бизнес-логика относительно слоёв?
│       ├── command-lifecycle.md — Каков жизненный цикл ServiceCommand?
│       ├── condition-ruletype-granularity.md — Когда заводить выделенный StrategyConditionRuleType?
│       ├── controlled-exchange-exceptions.md — Какие категории controlled exchange exceptions и реакции?
│       ├── error-handling-policy.md — Как ошибки выходят наружу и градируются внутри?
│       ├── exchange-hold.md — Какое правило определяет exchange-scope холд?
│       ├── external-status-resolution.md — Как работать с сырым внешним статусом сущности?
│       ├── idempotency-via-unique.md — Как обеспечивается уникальность и идемпотентность сущностей?
│       ├── instrument-hold.md — Какое правило определяет инструмент-scope холд?
│       ├── market-data-freshness.md — Какое правило свежести рыночных данных?
│       ├── market-data-retention.md — Какое правило хранения/чистки результатов расчёта?
│       ├── no-partial-close.md — Почему запрещено частичное закрытие через close-position?
│       ├── persistence-representation.md — Как сущность представляется в БД?
│       ├── raw-exchange-dto-boundary.md — Как ограничено распространение raw exchange DTO по слоям?
│       ├── risk-creating-entry-protection.md — Почему вход без определимого стопа не доходит до биржи?
│       ├── risk-validator-scope.md — Когда RiskValidator вызывается, а когда нет?
│       ├── runtime-error-classification.md — Как классифицируются unexpected runtime-ошибки?
│       ├── time-utc.md — Какое правило по работе со временем?
│       ├── trading-configuration-ownership.md — Что настраивает стратегия, а чем владеет система?
│       └── trading-constraints.md — В каком торговом контуре и с какими ограничениями работает бот?
├── src/ — (Код, не документация.)
└── .claude-archive/ — (Архив старой инфраструктуры; не место для новых файлов.)
```
