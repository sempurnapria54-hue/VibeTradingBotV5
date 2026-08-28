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
│   │   └── 2026-08-23-разрывы-спека-кода-на-тропе-живой-сделки.md — Что разведано о фактическом состоянии контура входа и выхода в коде?
│   ├── projects/<путь-проекта>/memory/ — (артефакт харнесса: файловая память Claude Code; MEMORY.md + по файлу на факт. Не файлы знания проекта — правило «На какой вопрос отвечает» к ним не применяется.)
│   ├── processes/ — Как устроен этот методологический процесс?
│   │   ├── api-docs-completion.md — Как интеграционные доки источника доводятся до полного покрытия периметра?
│   │   ├── knowledge-classification.md — Как устроен процесс классификации знания?
│   │   ├── pipeline-shakedown.md — Как устроена обкатка агентского пайплайна?
│   │   ├── question-delegation.md — Как устроен режим автономии (кто решает развилки, что доходит до пользователя)?
│   │   ├── roadmap-step-execution.md — Как устроено исполнение одного шага роадмапа (docs-first)?
│   │   ├── source-api-testing.md — Как устроено тестирование API внешнего источника?
│   │   └── trading-library-distillation.md — Как знание из сырых книг выносится в дистиллят?
│   ├── rules/ — Какое у нас правило?
│   │   ├── classification-report.md — Какое правило отчётности при классификации знания?
│   │   ├── closed-work-transfer.md — Какое правило переноса закрытого из рабочих файлов в history/?
│   │   ├── codestyle.md — Как мы пишем код?
│   │   ├── curation.md — Какое правило регулярной курации базы знания?
│   │   ├── design-simplicity.md — Какое правило-дефолт проектирования (простота и переиспользование)?
│   │   ├── external-source-sync.md — Какое правило синхронизации файлов с внешним источником правды?
│   │   ├── naming.md — Какое правило именования файлов?
│   │   ├── policy-home.md — Какое правило о единственном носителе-доме каждой политики?
│   │   ├── pre-launch-schema-changes.md — Какое правило схемных изменений, пока проект не запущен?
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
│   │   ├── stagnation-detection.md — Как проверить итерации прогонов шага на топтание на месте?
│   │   ├── test-code.md — Как написать код-тесты контура API источника по плану?
│   │   ├── test-collection.md — Как построить исполняемую тест-коллекцию (Postman)?
│   │   ├── test-design.md — Как построить тест-план и кейсы по сырью API источника?
│   │   ├── test-review.md — Как сделать адверсариальное ревью тест-артефактов?
│   │   ├── test-run.md — Как прогнать утверждённый тест-план?
│   │   ├── trading-review.md — Как сделать адверсариальный проход по торговой корректности?
│   │   └── update-roadmap-progress.md — Как обновить статус шага и пересчитать статус фазы?
│   ├── snapshots/ — Где мы сейчас?
│   │   └── snapshot-v90.md — Где мы сейчас? (актуальный; старые — в work/history/snapshots/)
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
│       ├── decision-digest.md — Какие проектные решения CC принял автономно в текущей итерации?
│       ├── 2026-08-26-step-7-question-flow-analysis.md — Какие источники дали поток ~400 вопросов на шаге 7?
│       ├── progress/ — На каком шаге мы в этой активной операции?
│       │   ├── phase-1-step-7-chronicle.md — Какова хроника под-шагов шага 7 Фазы 1?
│       │   ├── corpus-rewrite-mapping.md — Что из каждого дока корпуса остаётся, что переезжает и что удаляется?
│       │   ├── risk-contour-design.md — Как устроен узел покрытия и риск-контур, если проектировать их целиком из концепции?
│       │   ├── phase-1-step-7-command-action-boundary.md — Что предлагается по развилке границы «команда ↔ действие»?
│       │   ├── phase-1-step-7-docs-check-1.md — Каков исход DOCS_CHECK_1 шага 7?
│       │   ├── phase-1-step-7-docs-check-2.md — Каков исход DOCS_CHECK_2 шага 7?
│       │   ├── phase-1-step-7-docs-check-3.md — Каков исход DOCS_CHECK_3 шага 7?
│       │   ├── phase-1-step-7-docs-check-4.md — Каков исход DOCS_CHECK_4 шага 7?
│       │   ├── phase-1-step-7-docs-check-5.md — Каков исход DOCS_CHECK_5 шага 7?
│       │   ├── phase-1-step-7-docs-check-6.md — Каков исход DOCS_CHECK_6 шага 7?
│       │   ├── phase-1-step-7-docs-check-7.md — Каков исход DOCS_CHECK_7 шага 7?
│       │   ├── phase-1-step-7-docs-check-8.md — Каков исход DOCS_CHECK_8 шага 7?
│       │   ├── phase-1-step-7-docs-check-9.md — Каков исход DOCS_CHECK_9 шага 7?
│       │   ├── phase-1-step-7-docs-check-10.md — Каков исход DOCS_CHECK_10 шага 7?
│       │   ├── phase-1-step-7-docs-check-11.md — Каков исход DOCS_CHECK_11 шага 7?
│       │   ├── phase-1-step-7-docs-check-12.md — Каков исход DOCS_CHECK_12 шага 7?
│       │   ├── phase-1-step-7-docs-check-13.md — Каков исход DOCS_CHECK_13 шага 7?
│       │   ├── phase-1-step-7-docs-check-14.md — Каков исход DOCS_CHECK_14 шага 7?
│       │   ├── phase-1-step-7-docs-check-15.md — Каков исход DOCS_CHECK_15 шага 7?
│       │   ├── phase-1-step-7-docs-check-16.md — Каков исход DOCS_CHECK_16 шага 7?
│       │   ├── phase-1-step-7-docs-check-17.md — Каков исход DOCS_CHECK_17 шага 7?
│       │   ├── phase-1-step-7-docs-check-18.md — Каков исход DOCS_CHECK_18 шага 7?
│       │   ├── phase-1-step-7-docs-check-19.md — Каков исход DOCS_CHECK_19 шага 7?
│       │   ├── phase-1-step-7-docs-check-20.md — Каков исход DOCS_CHECK_20 шага 7?
│       │   ├── phase-1-step-7-docs-check-21.md — Каков исход DOCS_CHECK_21 шага 7?
│       │   ├── phase-1-step-7-docs-check-22.md — Каков исход DOCS_CHECK_22 шага 7?
│       │   ├── phase-1-step-7-docs-check-23.md — Каков исход DOCS_CHECK_23 шага 7?
│       │   ├── phase-1-step-7-docs-check-24.md — Каков исход DOCS_CHECK_24 шага 7?
│       │   ├── phase-1-step-7-risk-q4-anom-q5.md — Что предлагается по RISK-Q4 и ANOM-Q5?
│       │   ├── phase-1-step-7-gaps-close-1.md — Как закрыты пробелы DOCS_CHECK_1 шага 7?
│       │   ├── phase-1-step-7-gaps-close-2.md — Как закрыты пробелы DOCS_CHECK_2 шага 7?
│       │   ├── phase-1-step-7-gaps-close-3.md — Как закрыты пробелы DOCS_CHECK_3 шага 7?
│       │   ├── phase-1-step-7-gaps-close-4.md — Как закрыты пробелы DOCS_CHECK_4 шага 7?
│       │   ├── phase-1-step-7-gaps-close-5.md — Как закрыты пробелы DOCS_CHECK_5 шага 7 (и что withhold)?
│       │   ├── phase-1-step-7-gaps-close-6.md — Как закрыты пробелы DOCS_CHECK_6 шага 7?
│       │   ├── phase-1-step-7-gaps-close-7.md — Как закрыты пробелы DOCS_CHECK_7 шага 7?
│       │   ├── phase-1-step-7-gaps-close-8.md — Как закрыты пробелы DOCS_CHECK_8 шага 7 (в объёме решённого)?
│       │   ├── phase-1-step-7-gaps-close-9.md — Каков исход закрытия находок DOCS_CHECK_9 по итогам сверки пользователя?
│       │   ├── phase-1-step-7-gaps-close-10.md — Каков исход закрытия находок DOCS_CHECK_10?
│       │   ├── phase-1-step-7-gaps-close-11.md — Как закрыты находки DOCS_CHECK_11?
│       │   ├── phase-1-step-7-gaps-close-12.md — Как закрыты находки DOCS_CHECK_12?
│       │   ├── phase-1-step-7-gaps-close-13.md — Как закрыты находки DOCS_CHECK_13?
│       │   ├── phase-1-step-7-gaps-close-14.md — Как закрыты находки DOCS_CHECK_14 (и RISK-Q4/ANOM-Q5)?
│       │   ├── phase-1-step-7-gaps-close-15.md — Как закрыты находки DOCS_CHECK_15?
│       │   ├── phase-1-step-7-gaps-close-16.md — Как закрыты находки DOCS_CHECK_16?
│       │   ├── phase-1-step-7-gaps-close-17.md — Как закрыты находки DOCS_CHECK_17?
│       │   ├── phase-1-step-7-gaps-close-18.md — Каков исход закрытия находок DOCS_CHECK_18?
│       │   ├── phase-1-step-7-gaps-close-19.md — Каков исход закрытия находок DOCS_CHECK_19?
│       │   ├── phase-1-step-7-gaps-close-20.md — Каков исход закрытия находок DOCS_CHECK_20?
│       │   ├── phase-1-step-7-gaps-close-21.md — Каков исход закрытия находок DOCS_CHECK_21?
│       │   ├── phase-1-step-7-gaps-close-22.md — Каков исход закрытия находок DOCS_CHECK_22?
│       │   ├── phase-1-step-7-gaps-close-23.md — Каков исход закрытия находок DOCS_CHECK_23?
│       │   └── phase-1-step-7-gaps-close-24.md — Каков исход закрытия находок DOCS_CHECK_24?
│       ├── questions/ — Что мы ещё не решили?
│       │   ├── open-questions.md — Что мы ещё не решили (общие вопросы)?
│       │   └── tasks/ — Что неясно по конкретной активной задаче? (сейчас пусто)
│       ├── roadmap/ — Куда движется продукт?
│       │   ├── roadmap.md — Какими фазами продукт движется к бизнес-ценности?
│       │   └── phase-1.md — В каком статусе каждый шаг Фазы 1?
│       └── history/ — Что мы уже сделали? (архив; содержимое не индексируется)
│           └── snapshots/ — Где мы были раньше? (снапшоты v1–v82)
├── docs/ — Как устроен продукт (продуктовая документация)?
│   ├── components/ — Кто выполняет?
│   │   ├── models/ — Что это за runtime-объект?
│   │   │   ├── CalculatedPrice.md — Что это за runtime value object `CalculatedPrice`: структура, енумы `PriceMode` / `StrategyPricePurpose`, под-объекты resolved-цен?
│   │   │   ├── CalculatedSize.md — Что это за runtime value object `CalculatedSize`: структура, енум `SizeMode`?
│   │   │   ├── CalculatedStrategyAction.md — Что это за runtime value object `CalculatedStrategyAction`: структура, что в него входит и что сознательно не входит?
│   │   │   ├── CalculationContext.md — Что это за runtime value object `CalculationContext`: структура, scope сборки, отношение к `DealContext`?
│   │   │   ├── CalculationError.md — Что это за runtime value object `CalculationError`: структура, енум `CalculationErrorType`, политика реакции?
│   │   │   ├── DealContext.md — Что это за runtime value object `DealContext`: структура, scope одного прохода FSM, отношение к `CalculationContext`?
│   │   │   ├── HoldSignal.md — Что это за runtime value object `HoldSignal` — параметр вызова `HoldService`: структура, фабрики, енумы `ReactionClass` / `HoldScope`?
│   │   │   ├── MarketDataExpirationResult.md — Что это за runtime value object `MarketDataExpirationResult`: структура, енум `Status`?
│   │   │   ├── MarketPriceData.md — Что это за runtime value object `MarketPriceData`: структура, boundary-snapshot, правила использования?
│   │   │   ├── PositionStatusResolveResult.md — Что это за runtime value object `PositionStatusResolveResult` и общий паттерн resolve-result?
│   │   │   ├── RiskBlockAction.md — Что это за runtime value object `RiskBlockAction`: структура, енум `Type`?
│   │   │   ├── RiskCheckResult.md — Что это за runtime value object `RiskCheckResult`: структура, енумы `RiskCheckStatus` и `RiskCheckCode`?
│   │   │   ├── RiskValidationResult.md — Что это за runtime value object `RiskValidationResult`: структура, енум `RiskDecision`?
│   │   │   ├── ServiceCommand.md — Что это за runtime value object `ServiceCommand`: структура, енум `ServiceCommandType`, ключевой инвариант «не persisted queue»?
│   │   │   ├── ServiceCommandPayload.md — Что такое `ServiceCommandPayload` (параметры команды) и где живут конкретные payload-подтипы?
│   │   │   └── StrategyActionCalculationResult.md — Что это за runtime value object `StrategyActionCalculationResult`: структура, енум `Status`?
│   │   ├── AlgoOrderExternalStatusResolver.md — Кто переводит внешний статус standalone algo-order в доменный (компонент- resolver): ответственность, границы, реализация под биржу?
│   │   ├── AnomalyJob.md — Кто ищет нарушения базовых инвариантов системы (компонент-job): что ищет, чем не является?
│   │   ├── AttachedAlgoOrderStateResolver.md — Кто определяет доменный статус attached protection «по фактам» (компонент-resolver): контракт, границы, реализация под биржу?
│   │   ├── CalculationContextFactory.md — Кто собирает `CalculationContext` (компонент-фабрика): что собирает, из каких сервисов, какие границы соблюдает?
│   │   ├── CancelAlgoOrderExecutor.md — Кто исполняет `CANCEL_ALGO_ORDER_COMMAND` (компонент-executor): что делает?
│   │   ├── CancelOrderExecutor.md — Кто исполняет `CANCEL_ORDER_COMMAND` (компонент-executor): что делает?
│   │   ├── CandleJob.md — Кто готовит базовые свечные данные (компонент-job): что делает, что не делает?
│   │   ├── ClosePositionExecutor.md — Кто исполняет `CLOSE_POSITION_COMMAND` (компонент-executor): что делает, инвариант full close?
│   │   ├── CreateAlgoOrderActionExecutor.md — Кто планирует CREATE-действие над standalone algo-order за проход (компонент-executor): стадии, сборка дерева `Condition`, отношение к risk?
│   │   ├── CreateAlgoOrderExecutor.md — Кто исполняет `CREATE_ALGO_ORDER_COMMAND` (компонент-executor): что делает?
│   │   ├── CreateOrderActionExecutor.md — Кто планирует CREATE-действие над ordinary order за проход (компонент- executor): стадии, связь с risk-layer?
│   │   ├── CreateOrderExecutor.md — Кто исполняет `CREATE_ORDER_COMMAND` (компонент-executor): что делает?
│   │   ├── DealContextService.md — Кто собирает `DealContext` для прохода FSM (компонент-сервис): что собирает, границы?
│   │   ├── DealOpeningService.md — Кто атомарно создаёт `Deal` (компонент-сервис): что делает, чего не делает?
│   │   ├── DealOrchestratorJob.md — Кто сопровождает уже созданные сделки (компонент-job): цикл работы, операционная оболочка (CRON / выключатель / фасад / выборка), concurrency-guard, границы?
│   │   ├── DealStateMachine.md — Кто управляет статусами сделки (компонент-оркестратор FSM): что делает, конструкция handler'а (3 типа проверок), границы?
│   │   ├── EntryFinalizedHandler.md — Что делает FSM handler статуса `ENTRY_FINALIZED` (компонент): проверки, логика, шаги, команды?
│   │   ├── EntryScannerJob.md — Кто ищет возможность создать новую сделку (компонент-job): шаги, что передаёт в `DealOpeningService`, чего не делает?
│   │   ├── EntrySubmittedHandler.md — Что делает FSM handler статуса `ENTRY_SUBMITTED` (компонент): проверки, логика, шаги, команды?
│   │   ├── ErrorHandler.md — Что делает FSM handler статуса `ERROR` (компонент): проверки, логика, команды, переход в `EMERGENCY_CLOSED`?
│   │   ├── ExitActionExecutor.md — Кто исполняет действие выхода из сделки за проход (компонент-executor): состав команд, порядок, границы?
│   │   ├── ExitPendingHandler.md — Что делает FSM handler статуса `EXIT_PENDING` (компонент): проверки, логика, шаги, команды?
│   │   ├── FinalizeDealEntryExecutor.md — Кто исполняет `FINALIZE_DEAL_ENTRY_COMMAND` (компонент-executor): что читает/пишет, статусное ребро `ENTRY_FINALIZED`, идемпотентность, retry-anchor?
│   │   ├── FinalizeDealExitExecutor.md — Кто исполняет команду финализации штатного выхода?
│   │   ├── HoldService.md — Кто исполняет блокировку по требованию детекторов?
│   │   ├── IndicatorJob.md — Кто считает технические индикаторы (компонент-job): что делает, что не делает, как обращается с warmup и идемпотентностью?
│   │   ├── IndicatorService.md — Кто отдаёт готовые значения индикаторов (компонент-сервис): контракт, поведение при отсутствии/устаревании?
│   │   ├── InstrumentExternalRulesDataService.md — Кто отдаёт внешние правила инструмента (компонент — граница domain ↔ persistence): что возвращает, как хранит?
│   │   ├── InstrumentExternalRulesSyncJob.md — Кто обновляет внешние справочные данные инструмента (компонент-job): что делает, источники, частота?
│   │   ├── IntegrationService.md — Кто является границей биржевого клиента / adapter-layer (компонент): nullable contract, что не выходит наружу?
│   │   ├── KillSwitchExecutor.md — Кто исполняет kill-switch teardown (компонент вне реестра команд): с чем работает, границы?
│   │   ├── KillSwitchService.md — Кто триггерит аварийный kill-switch для реактивной реакции холда (компонент-триггер): scope-исполнители, каскад биржи, агрегация подтверждения, границы?
│   │   ├── ManagingHandler.md — Что делает FSM handler статуса `MANAGING` (компонент): проверки, логика, шаги, команды?
│   │   ├── MarkDealClosedExecutor.md — Кто исполняет `MARK_DEAL_CLOSED_COMMAND` (компонент-executor): терминальное ребро, что читает/пишет, идемпотентность, retry-anchor, контракт обязательного `resultProfit`?
│   │   ├── MarkDealEmergencyClosedExecutor.md — Кто исполняет `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` (компонент-executor): аварийное терминальное ребро, что читает/пишет, best-effort число и его провенанс, идемпотентность, retry-anchor?
│   │   ├── MarkDealErrorExecutor.md — Кто исполняет `MARK_DEAL_ERROR_COMMAND` (компонент-executor): что читает/пишет, ребро в `ERROR`, идемпотентность, retry-anchor?
│   │   ├── MarketDataExpirationChecker.md — Кто проверяет свежесть рыночных данных (компонент-сервис): контракт, что проверяет, чем не управляет?
│   │   ├── MarketPhaseResolver.md — Кто резолвит авторские правила фазы в `MarketPhase.Type` (компонент): что делает, на каких данных, границы?
│   │   ├── MarketPhaseService.md — Кто отдаёт актуальную фазу рынка (компонент-сервис): контракт, как вычисляет, поведение при отсутствии/устаревании входов?
│   │   ├── MarketPriceDataService.md — Кто отдаёт runtime-цены инструмента (компонент-сервис): что возвращает, откуда?
│   │   ├── MarketStructureJob.md — Кто считает структуру рынка (компонент-job): что делает, что не делает?
│   │   ├── MarketStructureResolver.md — Кто вычисляет структуру рынка из свечей (доменный компонент): контракт (вход/выход), потребление готового ER, fallback, границы?
│   │   ├── MarketStructureService.md — Кто отдаёт готовую структуру рынка (компонент-сервис): контракт, поведение при отсутствии/устаревании?
│   │   ├── OrderExternalStatusResolver.md — Кто переводит внешний статус ordinary order в доменный (компонент- resolver): ответственность, границы, реализация под биржу?
│   │   ├── PositionStatusResolver.md — Кто определяет доменный статус позиции по факту её наличия (компонент- resolver): контракт, политика null/externalSize, реализация под биржу?
│   │   ├── PrecheckHandler.md — Что делает FSM handler статуса `PRECHECK` (компонент): проверки, логика, шаги, команды?
│   │   ├── PriceCalculator.md — Кто рассчитывает цены действия (компонент-калькулятор цены): контракт, формулы SL/TP/trailing/limit/structure, округление, вокабуляр источников цены?
│   │   ├── ProtectionSwitchedHandler.md — Что делает FSM handler статуса `PROTECTION_SWITCHED` (компонент): проверки, логика, шаги, команды?
│   │   ├── RefreshAlgoOrderExecutor.md — Кто исполняет `REFRESH_ALGO_ORDER_COMMAND` (компонент-executor): что делает, границы?
│   │   ├── RefreshBalanceExecutor.md — Кто исполняет `REFRESH_BALANCE_COMMAND` (компонент-executor): что делает, особый контракт (не normal null, не RiskValidator)?
│   │   ├── RefreshBillsExecutor.md — Кто исполняет команду добычи движений средств?
│   │   ├── RefreshOrderExecutor.md — Кто исполняет `REFRESH_ORDER_COMMAND` (компонент-executor): что делает, границы?
│   │   ├── RefreshPositionExecutor.md — Кто исполняет `REFRESH_POSITION_COMMAND` (компонент-executor): что делает, политика null/externalSize, evidence-cycle live → positions-history?
│   │   ├── RetryPolicyService.md — Кто управляет retry-политикой исполнения команд (компонент): контракт, модель политики, retry-состояние, правило для опасных команд?
│   │   ├── RiskBlockResolver.md — Кто превращает результат risk-проверки в действие handler'а (компонент): контракт, зачем каждый параметр?
│   │   ├── RiskValidator.md — Кто проверяет рассчитанное действие по risk-policy (компонент): что проверяет, что считает сам, чего не делает?
│   │   ├── SafetyHoldCoordinator.md — Кто держит последовательность полной реакции холда (`FULL`): шаги, исполнители, гейт терминала, эскалация, exception- и best-effort-политика, границы?
│   │   ├── ServiceCommandExecutor.md — Кто исполняет атомарную команду и маршрутизирует её в конкретный executor (компонент): контракт, общая семантика групп, обработка controlled exceptions?
│   │   ├── SizeCalculator.md — Кто рассчитывает размер действия (компонент-калькулятор размера): контракт, формула расчёта контрактов, инвариант partial exit?
│   │   ├── StrategyActionCalculator.md — Кто рассчитывает runtime-параметры действия стратегии (компонент- оркестратор расчёта): контракт, что объединяет, границы?
│   │   ├── StrategyActionExecutor.md — Кто выдаёт следующую команду одного типа действия стратегии за проход (компонент-интерфейс): контракт, per-pass семантика, реализации?
│   │   ├── StrategyActionOrchestrator.md — Кто диспетчеризует планирование одного действия стратегии за проход (компонент): контракт, гейт повтора, маршрутизация по типу действия?
│   │   ├── StrategyConditionEvaluator.md — Кто проверяет применимость `StrategyCondition` (компонент): что делает, на каких данных, границы?
│   │   ├── SubmitAlgoOrderExecutor.md — Кто исполняет `SUBMIT_ALGO_ORDER_COMMAND` (компонент-executor): что делает?
│   │   ├── SubmitOrderExecutor.md — Кто исполняет `SUBMIT_ORDER_COMMAND` (компонент-executor): что делает, recoverability?
│   │   └── SystemActionExecutor.md — Кто выдаёт следующую команду системного действия за проход?
│   ├── dictionary/ — —
│   ├── integrations/ — Что известно про источник?
│   │   └── okx/ — Что известно про источник OKX?
│   │       ├── contracts/ — Каков контракт и какие лимиты у этой операции источника?
│   │       │   ├── account-bills.md — Каков контракт OKX-операций по bill-записям аккаунта (7d, 3m, deep-архив с 2021): endpoint'ы, query, лимиты, пагинация?
│   │       │   ├── account-config.md — Каков контракт OKX-операций конфигурации счёта: чтение конфигурации (`account/config`), режим позиций (`set-position-mode`), плечо (`set-leverage`, `leverage-info`)?
│   │       │   ├── account-position-risk.md — Каков контракт OKX-операции `account-position-risk` — одновременный снапшот балансов и позиций аккаунта?
│   │       │   ├── account-rate-limit.md — Каков контракт OKX-операции чтения аккаунт-уровневого rate limit (fill-ratio-based лимит суб-аккаунта): endpoint, поля?
│   │       │   ├── algo-order.md — Каков контракт OKX-операций по algo-ордеру: endpoint'ы, лимиты, ACK-семантика, ordType-specific body, evidence-cycle, ветвление cancel-пути по семье algo?
│   │       │   ├── balance.md — Каков контракт OKX-операции получения баланса: endpoint, лимиты, validation?
│   │       │   ├── batch-operations.md — Каков контракт batch-операций OKX по ordinary order (place / cancel / amend пакетом): endpoint'ы, лимиты, поэлементный ACK, атомарность?
│   │       │   ├── cancel-all-after.md — Каков контракт OKX-операции Cancel All After (серверная отмена всех pending-ордеров по таймауту): endpoint, поля, лимиты, семантика?
│   │       │   ├── candle.md — Каков контракт OKX-операций по свечам: endpoint'ы, query, лимиты?
│   │       │   ├── fills-archive.md — Каков контракт OKX-операций для выгрузки fills > 3 месяцев: endpoint'ы, async-флоу (генерация → polling → скачивание), лимиты?
│   │       │   ├── fills.md — Каков контракт OKX-операций по fills (3d, 3m): endpoint'ы, query, лимиты, пагинация?
│   │       │   ├── funding-rate.md — Каков контракт OKX-операций чтения funding rate SWAP: текущий/ прогнозный (`funding-rate`) и история ставок (`funding-rate-history`)?
│   │       │   ├── index-data.md — Каков контракт OKX-операций чтения данных индекса: `index-tickers`, `index-candles`, `history-index-candles`?
│   │       │   ├── instrument.md — Каков контракт OKX-операции получения спецификации инструмента?
│   │       │   ├── insurance-fund.md — Каков контракт OKX-операции чтения баланса страхового фонда (`insurance-fund`; в офдоке — «security fund»)?
│   │       │   ├── mark-price.md — Каков контракт OKX-операций чтения mark price: текущее значение (`public/mark-price`) и свечи (`mark-price-candles`, `history-mark-price-candles`)?
│   │       │   ├── market-price-data.md — Каков контракт OKX-операции получения тикера?
│   │       │   ├── max-size.md — Каков контракт OKX-операций оценки максимального размера ордера (`max-size`) и доступного баланса/эквити под сделку (`max-avail-size`)?
│   │       │   ├── open-interest.md — Каков контракт OKX-операции чтения открытого интереса контрактов (`open-interest`)?
│   │       │   ├── order-book.md — Каков контракт OKX-операций чтения стакана: `books` (до 400 уровней) и `books-full` (до 5000 уровней)?
│   │       │   ├── order-precheck.md — Каков контракт OKX-операции order precheck (серверная пре-оценка влияния ордера на счёт до постановки): endpoint, поля, применимость?
│   │       │   ├── order.md — Каков контракт OKX-операций по ordinary order: endpoint'ы, лимиты, ACK-семантика, пагинация?
│   │       │   ├── position-tiers.md — Каков контракт OKX-операции чтения позиционных тиров (лимиты размера позиции, ставки маржи и максимальное плечо по тирам)?
│   │       │   ├── position.md — Каков контракт OKX-операций по позиции: endpoint'ы, лимиты, close-position ACK, подтверждение факта закрытия, история закрытых позиций?
│   │       │   ├── price-limit.md — Каков контракт OKX-операции чтения ценовых лимитов (`price-limit`): верхняя граница buy и нижняя граница sell?
│   │       │   ├── public-trades.md — Каков контракт OKX-операций чтения публичных сделок инструмента: последние (`trades`) и история 3 месяца (`history-trades`)?
│   │       │   ├── server-time.md — Каков контракт OKX-операции чтения серверного времени API (`public/time`)?
│   │       │   ├── service-urls.md — Какие URL у OKX по окружениям (production, demo) и регионам?
│   │       │   └── trade-fee.md — Каков контракт OKX-операции чтения ставок комиссий аккаунта (`trade-fee`): endpoint, поля, знаковая конвенция?
│   │       ├── rules/ — Какое правило источника OKX?
│   │       │   ├── adapter-constants.md — Какие константы OKX adapter выставляет сам, не из доменных моделей?
│   │       │   ├── reduce-only-invariant.md — Какой invariant OKX adapter проверяет по `reduceOnly` факту?
│   │       │   ├── timeframe-constants.md — Какое у нас правило обращения со строками таймфреймов OKX?
│   │       │   └── ws-limits.md — Какие лимиты у WebSocket соединений OKX и какие требования по keep-alive / количеству подписок?
│   │       └── coverage-manifest.md — Какова полнота покрытия поверхности OKX REST API нашими интеграционными доками — что задокументировано, что пробел, что вне продуктового периметра?
│   ├── lifecycles/ — Через какие состояния проходит этот объект?
│   │   ├── AlgoOrder.md — Через какие статусы проходит `AlgoOrder`, кто и при каких фактах их меняет?
│   │   ├── AnomalyReport.md — Через какие статусы проходит `AnomalyReport`, кто и при каких событиях их меняет?
│   │   ├── CandleGroup.md — Через какие статусы проходит загрузка свечей группы (`CandleGroup`) и кто ими управляет?
│   │   ├── Deal.md — Через какие FSM-статусы проходит `Deal`, какие из них terminal, какие инварианты переходов и как считается live risk сделки?
│   │   ├── DealActionState.md — Через какие статусы проходит исполнение действия (`DealActionState`, оба вида — STRATEGY и SYSTEM), кто и при каких фактах их меняет?
│   │   ├── Instrument.md — Через какие статусы проходит онбординг инструмента (`Instrument`) в шаге 1 и кто ими управляет?
│   │   ├── Order.md — Через какие статусы проходят `Order` и embedded `AttachedAlgoOrder`, кто и при каких фактах их меняет?
│   │   ├── Position.md — Через какие статусы проходит `Position`, кто и при каких событиях их меняет?
│   │   └── Strategy.md — Через какие административные статусы проходит `Strategy`, что каждый из них разрешает/блокирует и кто управляет переходами?
│   ├── models/ — Какие у нас модели и как они переходят между слоями?
│   │   ├── api/ — Что это за модель API нашего сервиса?
│   │   │   ├── OkxRawApiRequest.md — Какие поля у `OkxRawApiRequest` — конверта запроса generic-эндпоинта `POST /api/proxy/okx/raw`, и как эндпоинт их читает?
│   │   │   └── README.md — Что это за слой `docs/models/api/` и когда здесь появляются файлы?
│   │   ├── domain/ — Какие у нас доменные модели?
│   │   │   ├── aggregate/ — Что это за сущность без биржевой привязки, нужная для торговли?
│   │   │   │   ├── Deal.md — Что это за сущность `Deal`?
│   │   │   │   └── Strategy.md — Что это за сущность `Strategy` и из чего состоит её дерево?
│   │   │   ├── core/ — Что это за торговая модель с биржевым воплощением?
│   │   │   │   ├── AlgoOrder.md — Что это за сущность `AlgoOrder` — отдельная условная заявка сделки?
│   │   │   │   ├── BalanceContainer.md — Что это за сущность `BalanceContainer` и вложенный в неё `Balance`?
│   │   │   │   ├── Exchange.md — Что это за сущность `Exchange`?
│   │   │   │   ├── Instrument.md — Что это за сущность `Instrument`?
│   │   │   │   ├── Order.md — Что это за сущность `Order` и вложенная в неё встроенная защита?
│   │   │   │   └── Position.md — Что это за сущность `Position`?
│   │   │   └── other/ — Что это за прочая хранимая модель?
│   │   │       ├── AnomalyReport.md — Что это за модель `AnomalyReport`?
│   │   │       ├── Auditable.md — Какие общие поля аудита несут доменные сущности?
│   │   │       ├── Candle.md — Что это за доменная модель `Candle`: структура, персистентность, правило закрытых свечей?
│   │   │       ├── CandleGroup.md — Что это за доменная модель `CandleGroup`: структура, енум `TimeFrame`, целостность по count, персистентность; где описан её lifecycle?
│   │   │       ├── DealActionState.md — Что это за модель `DealActionState` — строка исполнения действия?
│   │   │       ├── DealCashFlow.md — Что это за модель `DealCashFlow` — журнал денежного движения, отнесённого к сделке?
│   │   │       ├── IndicatorValue.md — Что это за модель `IndicatorValue`: структура abstract-базы, наследники по типам индикаторов, енум `Type`, правила хранения?
│   │   │       ├── InstrumentExternalRules.md — Что это за модель `InstrumentExternalRules` — справочные правила инструмента?
│   │   │       ├── MarketPhase.md — Что это за `MarketPhase`: структура, енум `Type`, и почему она **вычисляется на лету** из текущих индикаторов/структур, а не хранится?
│   │   │       ├── MarketStructure.md — Что это за модель `MarketStructure`: структура, енум `Type`, вложенные ценовые уровни `MarketPriceLevel`, правила хранения и актуальности?
│   │   │       └── TradeFeeRate.md — Что это за модель `TradeFeeRate` — ставка торговой комиссии?
│   │   ├── externalSnapshot/ — Какая структура нормализованного граничного объекта?
│   │   │   └── README.md — Что это за слой `docs/models/externalSnapshot/` и когда здесь появляются файлы?
│   │   ├── integrations/ — Какие поля у нативной модели источника?
│   │   │   └── okx/ — Какие поля у нативной модели источника OKX?
│   │   │       ├── CandleOkxResponse.md — Какие поля у OKX candle response — что приходит от биржи и что из этого используется?
│   │   │       ├── InstrumentOkxResponse.md — Какие поля у OKX instrument response — что приходит от биржи и что из этого используется?
│   │   │       ├── OkxAccountBillResponse.md — Какие поля у OKX bill response — одной записи денежного движения по торговому аккаунту?
│   │   │       ├── OkxAlgoOrderResponse.md — Какие поля у нативной модели OKX algo-order response и какие из них использует bot?
│   │   │       ├── OkxBalanceResponse.md — Какие поля у OKX account balance response — что приходит от биржи и что из этого используется?
│   │   │       ├── OkxFillResponse.md — Какие поля у OKX fill response (одна сделка / одно исполнение)?
│   │   │       ├── OkxFillsArchiveResponse.md — Какие поля у OKX fills-archive responses (генерация и получение ссылки) — двух операций async-флоу выгрузки fills > 3 месяцев?
│   │   │       ├── OkxOrderResponse.md — Какие поля у нативной модели OKX ordinary order response (включая вложенный массив `attachAlgoOrds`) и какие из них использует bot?
│   │   │       ├── OkxPositionResponse.md — Какие поля у нативной модели OKX positions response и какие из них использует bot?
│   │   │       ├── OkxPositionsHistoryResponse.md — Какие поля у нативной модели OKX positions-history response и какие из них использует bot?
│   │   │       ├── OkxTickerResponse.md — Какие поля у OKX ticker response — что приходит от биржи и что из этого используется?
│   │   │       └── OkxTradeFeeResponse.md — Какие поля у OKX trade-fee response — ответа со ставками комиссий комиссионных групп аккаунта?
│   │   ├── mapping/ — Как сущность переходит между слоями?
│   │   │   ├── AlgoOrder.md — Как доменный `AlgoOrder` ложится на нативные модели источников, нормализуется через `AlgoOrderExternalSnapshot` и как резолвится его статус?
│   │   │   ├── Balance.md — Как доменные `BalanceContainer` / `Balance` ложатся на нативные модели источников, нормализуются через `BalanceContainerExternalSnapshot` / `BalanceExternalSnapshot`, какие поля валидируются?
│   │   │   ├── Candle.md — Как нативные представления свечей источников ложатся на доменные свечные данные и какие особенности их формата?
│   │   │   ├── DealCashFlow.md — Как OKX bill-записи ложатся на доменную `DealCashFlow`, как из `type`/`subType` резолвится `CashFlowCategory`, какие поля валидируются?
│   │   │   ├── Instrument.md — Как `Instrument` переходит между слоями (источник ↔ `InstrumentExternalSnapshot` ↔ domain) и что из снапшота персистится в шаге 1?
│   │   │   ├── InstrumentExternalRules.md — Как доменный `InstrumentExternalRules` ложится на нативные модели источников, нормализуется через `InstrumentExternalRulesExternalSnapshot` и как резолвятся типы и статус инструмента?
│   │   │   ├── MarketPriceData.md — Как доменный `MarketPriceData` ложится на нативные модели источников и нормализуется через `MarketPriceDataExternalSnapshot`?
│   │   │   ├── Order.md — Как доменный `Order` (+ `AttachedAlgoOrder`) ложится на нативные модели источников, нормализуется через `OrderExternalSnapshot` и как резолвится его статус?
│   │   │   ├── Position.md — Как доменная `Position` ложится на нативные модели источников, нормализуется через `PositionExternalSnapshot` и какие invariants проверяются?
│   │   │   ├── PositionCloseResult.md — Как положение закрытой позиции источника ложится на `Position`?
│   │   │   ├── Strategy.md — Как `Strategy` переходит между слоями (api ↔ domain ↔ persistence): полиморфные ветви, JSONB-навес, плоские строки шагов и резолв self-ссылок действий?
│   │   │   ├── TimeFrame.md — Как доменный enum `TimeFrame` маппится в строки таймфреймов источников?
│   │   │   ├── TradeFeeRate.md — Как ставка комиссии источника ложится на доменную `TradeFeeRate`?
│   │   │   └── TradeFill.md — Как нативные fills источников легли бы на доменный `TradeFill`, если бы он вводился (в фазе 1 — не вводится)?
│   │   └── persistence/ — Что это за модель хранимого слоя?
│   │       └── README.md — Что это за слой `docs/models/persistence/` и когда здесь появляются файлы?
│   ├── processes/ — Как устроен этот процесс?
│   │   ├── candle-loading.md — Как устроен процесс добычи и поддержания целостности свечной истории: оркестрация `CandleJob`, цикл статусов `CandleGroup`, политика глубины/целостности, координация онбординга инструмента?
│   │   ├── deal-management.md — Как устроен процесс сопровождения сделки во времени: поток от поиска входа до закрытия, какие компоненты и подпроцессы участвуют?
│   │   ├── fsm-execution-layering.md — Как разложены слои исполнения сделки от петли до биржевого вызова?
│   │   ├── market-data-calculation.md — Как устроен процесс вычисления производных рыночных данных (индикаторы / структура / фаза) поверх загруженных свечей: какие jobs, в какой последовательности, по какой цепочке зависимостей, и где результаты используются?
│   │   ├── risk-evaluation.md — Как устроен процесс оценки риска: когда вызывается, как обрабатывается результат, какова реакция на BLOCKED?
│   │   └── strategy-action-calculation.md — Как устроен процесс расчёта параметров одного `StrategyAction`: поток build `CalculationContext` → price → size, где его границы с FSM, risk и command-слоем?
│   ├── rules/ — Какое правило действует в системе?
│   │   ├── absent-value-semantics.md — Как выражается отсутствие значения у признака?
│   │   ├── ack-not-runtime-truth.md — Какое правило системы запрещает считать ACK биржи фактом о состоянии сущности?
│   │   ├── audit-not-runtime-source.md — Какое правило запрещает управляющей логике читать историю, чтобы решить, что делать дальше?
│   │   ├── command-lifecycle.md — Каков жизненный цикл `ServiceCommand`?
│   │   ├── condition-ruletype-granularity.md — Когда заводится именованный тип правила условия, а когда используется генерик-сравнение?
│   │   ├── controlled-exchange-exceptions.md — Какие категории контролируемых исключений существуют на границе с биржей и какова реакция на них?
│   │   ├── deal-without-operations.md — Как определяется, что по сделке не было операций на бирже?
│   │   ├── error-handling-policy.md — Как ошибки выходят наружу и как градируются внутри торгового контура?
│   │   ├── exchange-hold.md — Какое правило определяет биржевые safety-состояния и переходы между ними?
│   │   ├── execution-hierarchy.md — Какова иерархия уровней исполнения торговли?
│   │   ├── exit-teardown-order.md — В каком порядке снимаются живые ноги и закрывается позиция при выходе?
│   │   ├── external-status-resolution.md — Какое правило определяет работу с сырым статусом источника и реакцию на нераспознанный статус?
│   │   ├── idempotency-via-unique.md — Чем обеспечиваются уникальность и идемпотентность хранимых сущностей?
│   │   ├── instrument-hold.md — Какое правило определяет холд по одному инструменту?
│   │   ├── live-risk-protection.md — Какой инвариант системы запрещает живой риск без защиты?
│   │   ├── market-data-freshness.md — Как определяется свежесть рыночных данных и что она ограничивает?
│   │   ├── market-data-retention.md — Какое у нас правило хранения результатов расчёта рыночных данных?
│   │   ├── no-partial-close.md — Чем выражается полное закрытие позиции и чем — частичное уменьшение?
│   │   ├── persistence-representation.md — Какое у нас правило представления доменных структур в базе?
│   │   ├── pnl-reconciliation.md — Как сверяется заголовочный результат сделки с разбивкой движений и что делается при расхождении?
│   │   ├── raw-exchange-dto-boundary.md — Какое правило ограничивает распространение сырых DTO источника по слоям?
│   │   ├── replace-not-amend.md — Как система ремоделирует уже стоящие на бирже сущности?
│   │   ├── risk-policy.md — Какой риск система допускает на сделку и чем удерживает его в этих границах?
│   │   ├── risk-validator-scope.md — Для каких действий вызывается `RiskValidator`?
│   │   ├── runtime-error-classification.md — Как классифицируются неожиданные runtime-ошибки?
│   │   ├── strategy-condition-contract.md — Как автор стратегии записывает условие?
│   │   ├── strategy-validation.md — Что проверяется при создании стратегии и что откладывается до активации?
│   │   ├── time-utc.md — Какое у нас правило работы со временем?
│   │   ├── trading-configuration-ownership.md — Что в торговле настраивает стратегия, а чем владеет система?
│   │   ├── trading-constraints.md — В каком торговом контуре и с какими ограничениями работает бот?
│   │   └── writer-named-for-every-value.md — Какое правило требует называть писателя для каждого значения и перехода?
│   ├── spec/ — Чему равна эта величина или предикат и на каких примерах это проверено?
│   │   ├── deal-risk-numbers.json — Чему равны четыре числа риска сделки и по какому предикату отбираются их слагаемые?
│   │   ├── pnl-reconciliation.json — Чему равен допуск сверки, какие строки в него входят и сошлась ли сверка?
│   │   ├── protection-coverage.json — Какое покрытие несут защиты живого эпизода, достаточно ли оно и законно ли снятие защиты?
│   │   └── risk-limits.json — Чему равны операнды четырёх потолков риска и выполняются ли неравенства?
│   └── concept.md — Из каких принципов выведена система и почему они такие?
├── src/ — (Код, не документация.)
└── .claude-archive/ — (Архив старой инфраструктуры; не место для новых файлов.)
```
