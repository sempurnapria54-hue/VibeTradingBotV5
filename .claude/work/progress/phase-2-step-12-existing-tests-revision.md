# Ревизия существующего набора тестов — шаг 12 фазы 2

## На какой вопрос отвечает этот файл

Какой исход у каждого существующего теста дерева `services/**` по критерию
чистой логики.

## Предмет и граница

**Последний предмет под-шага 1 `CODE`** — тот, у которого форма не кейсовая
(`.claude/decisions/test-contour-design-pass.md` §«Перечень предметов и
порядок работы»): концепция требует прогнать существующий набор критерием
чистой логики и назвать тесты, закрепляющие структуру
(`.claude/work/progress/phase-2-step-12-chronicle.md` §«2. Юнит-тесты —
только чистая логика»). Форма ревизии — **инвентарь с исходом на класс**, а
не документ кейсов.

**Этот заход не удаляет ни одного теста и не правит ни одного.** Снятие
принадлежит последнему под-шагу `CODE` и наступает не раньше, чем поведение
теста покрыл ящик — ответ держателя 2026-09-16
(§«Пересмотр границы» хроники шага). Инвентарь называет **класс** и
**адресата**; что конкретное поведение адресатом покрыто, проверяет тот
заход, который тест снимает.

## Чем мерено

Обе команды воспроизводимы и печатают то, на чём стои́т таблица ниже:

```bash
find services -path '*/src/test/java/*' -name '*.java' | sort   # население ревизии
py tools/pure-logic-candidates.py                               # кандидаты уровня 2 по модулям
```

## Признак — закрытый словарь, и мерится он грепом по файлу теста

| Буква | Что найдено в тексте теста |
|---|---|
| `M` | подменённый коллаборатор — `@Mock`, `mock(`, `@MockitoBean` |
| `V` | ассерт по вызову коллаборатора — `verify*` при импорте `org.mockito` |
| `O` | ассерт по **порядку** вызовов — `InOrder` |
| `R` | ассерт по аннотации или сигнатуре — рефлексия, `Transactional.class`, `getDeclaredMethods` |
| `F` | чтение артефакта репозитория — `Files.readString`, `ClassPathResource` |
| `K` | поднятый контекст — `@SpringBootTest`, `ApplicationContextRunner`, `MockMvc`, `@Autowired` |
| `T` | подменённый транспорт spring-test — `MockRestServiceServer` |
| `—` | ничего из перечисленного |

## Что признак НЕ мерит, и это названо, а не умолчано

- **`verify` бывает доменным методом.** Первый замер считал всякое
  `verify(`, и `SubscriptionTicketService.verify(билет)` с
  `MockRestServiceServer.verify()` попали в ассерты по вызову коллаборатора.
  Буква `V` поэтому требует импорта `org.mockito` в том же файле; у
  транспорта spring-test заведена своя буква `T`.
- **Признак наследника читается у его контракта.** У четырёх проб
  вместимости планировщика в самом классе нет ни одного `@Test`: форма живёт
  в `services/common/test-support/src/main/.../SchedulerCapacityContract.java`,
  и `F`+`K` стоят там. Исход им проставлен по контракту, а не по файлу.
- **Верность исхода в клетке не мерит ничто.** Признак механический, исход —
  суждение пишущего; читает его адверсариальное ревью под-шага 2
  (`.claude/skills/test-review.md`).

## Исход — закрытый словарь из шести значений

| Исход | Что значит | Что с тестом делает под-шаг 3 |
|---|---|---|
| `логика` | предмет — чистая логика с комбинаторным входом; ввода-вывода у теста нет | остаётся; дерево предмета может смениться |
| `в-уровень-2` | предмет назван документом уровня 2, но тест держится подменёнными коллабораторами | снимается либо переписывается без подмен — после покрытия документом |
| `в-ящик` | предмет наблюдаем входами-выходами сервиса | снимается после покрытия документом ящика |
| `структура` | ассерт адресован внутреннему устройству — аннотации, сигнатуре, составу вызовов | снимается с названной потерей (§«Что снос класса `структура` уносит») |
| `корпус` | сверка кода с артефактом **вне прогона** — манифест, миграция, `env`, спека, объявленная величина конфигурации | остаётся: ящик манифестов не поднимает |
| `оснастка` | не тест — вспомогательный класс тестового дерева | остаётся |

**Правило вывода, применённое к каждой строке по порядку** (первое
сработавшее выигрывает): нет ни одного `@Test` → `оснастка`; есть `F` →
`корпус`; есть `R` → `структура`; признак `—` → `логика`; иначе → `в-ящик`.
Отклонения от правила — именованные, и их два рода: класс, чья единица
названа таблицей документа уровня 2 (`в-уровень-2` вместо `в-ящик`), и
класс, у которого артефакт репозитория служит **фикстурой**, а не предметом
сверки (`логика` вместо `корпус` — `DeclaredActionRejectsTest`, читающий
эталонное определение затем, чтобы его испортить).

## Инвентарь

| Класс | Признак | Исход | Адресат |
|---|---|---|---|
| `audit · AccessDenialActorTest` | `RK` | структура | `audit.md` |
| `audit · AccessDenialRowTest` | `MVR` | структура | `audit.md` |
| `audit · AccessDenialTrailTest` | `M` | в-ящик | `audit.md` |
| `audit · AlertRuleContractTest` | `F` | корпус | — |
| `audit · AuditSurfaceAccessTest` | `MVK` | в-ящик | `audit.md` |
| `audit · ConsumerLagTest` | `M` | в-ящик | `audit.md` |
| `audit · EnvironmentAxisArrivalTest` | `F` | корпус | — |
| `audit · JobExecutionGuardTest` | `—` | логика | `audit.md` |
| `audit · JournalCleanupBoundariesTest` | `MVR` | структура | `audit.md` |
| `audit · JournalCleanupTest` | `MVO` | в-ящик | `audit.md` |
| `audit · JournalCompletenessBoundTest` | `M` | в-ящик | `audit.md` |
| `audit · JournalContinuityTest` | `MV` | в-ящик | `audit.md` |
| `audit · JournalReadBoundariesTest` | `MVR` | структура | `audit.md` |
| `audit · JournalReadPageTest` | `MV` | в-ящик | `audit.md` |
| `audit · JournalSurfaceReadTest` | `MK` | в-ящик | `audit.md` |
| `audit · PersistenceWiringTest` | `K` | корпус | — |
| `audit · ReceptionEnvelopeTest` | `MV` | в-ящик | `audit.md` |
| `audit · ReceptionGapDetectionTest` | `MV` | в-ящик | `audit.md` |
| `audit · ReceptionHaltTest` | `MV` | в-ящик | `audit.md` |
| `audit · ReceptionMetricsExportTest` | `—` | логика | `audit.md` |
| `audit · ReceptionStateTickTest` | `MV` | в-ящик | `audit.md` |
| `audit · ReceptionStateWriterBoundariesTest` | `MVOR` | структура | `audit.md` |
| `audit · ReceptionSubscriptionTest` | `F` | корпус | — |
| `audit · ReceptionTransactionBoundariesTest` | `R` | структура | `audit.md` |
| `audit · SchedulerCapacityTest` | `—` | корпус | — |
| `audit · SchemaMigrationChainTest` | `MF` | корпус | — |
| `audit · TopicRetentionTest` | `M` | в-ящик | `audit.md` |
| `audit · WireFormContractTest` | `F` | корпус | — |
| `auth · ExchangeAccountKeyPathTest` | `—` | логика | `domain-model-predicates.md` |
| `auth · ExchangeAccountRegistrationServiceTest` | `—` | логика | `auth.md` |
| `auth · InternalIdFormTest` | `—` | логика | `domain-model-predicates.md` |
| `auth · MembershipResolutionTest` | `MV` | в-ящик | `auth.md` |
| `bff · OwnerProxyRetryTest` | `T` | в-ящик | `bff.md` |
| `bff · PerimeterSurfaceAccessTest` | `K` | в-ящик | `bff.md` |
| `bff · PerimeterSurfaceContext` | `MK` | оснастка | — |
| `bff · SchedulerCapacityTest` | `—` | корпус | — |
| `bff · StreamEventFormTest` | `—` | логика | `bff.md` |
| `bff · StreamPulseTest` | `MK` | в-ящик | `bff.md` |
| `bff · StreamSubscriptionTest` | `K` | в-ящик | `bff.md` |
| `bff · SubscriptionTicketServiceTest` | `—` | логика | `bff.md` |
| `bff · SurfaceErrorContractTest` | `K` | в-ящик | `bff.md` |
| `bff · TenantContextResolverTest` | `MV` | в-ящик | `bff.md` |
| `common/strategy-engine · OrderSizingSpecTest` | `—` | логика | `strategy-engine-calculation.md` |
| `common/strategy-engine · PriceLevelTest` | `—` | логика | `strategy-engine-calculation.md` |
| `common/strategy-engine · StrategyActionCalculationTest` | `—` | логика | `strategy-engine-calculation.md` |
| `common/strategy-engine · DealConditionEvaluationTest` | `—` | логика | `strategy-engine-condition.md` |
| `common/test-support · Spec` | `F` | оснастка | — |
| `common/test-support · SpecException` | `—` | оснастка | — |
| `common/test-support · SpecExpression` | `—` | оснастка | — |
| `common/test-support · SpecMutation` | `F` | оснастка | — |
| `common/test-support · SpecRunnerTest` | `—` | корпус | — |
| `common/test-support · SpecScope` | `—` | оснастка | — |
| `connector-okx · CachingExchangeCredentialsResolverTest` | `MV` | в-ящик | `connector-okx.md` |
| `connector-okx · ClosedCandleBoundaryTest` | `M` | в-ящик | `connector-okx.md` |
| `connector-okx · ConnectorSurfaceTest` | `MVK` | в-ящик | `connector-okx.md` |
| `connector-okx · OkxExchangeGatewayTest` | `MV` | в-ящик | `connector-okx.md` |
| `connector-okx · OkxSigningInterceptorTest` | `—` | логика | `okx-mapping.md` |
| `connector-okx · VaultExchangeCredentialsResolverTest` | `M` | в-ящик | `connector-okx.md` |
| `market-data · CandleBackfillTest` | `M` | в-ящик | `market-data.md` |
| `market-data · CandleDemandTest` | `MV` | в-ящик | `market-data.md` |
| `market-data · ComputationIdentityTest` | `—` | логика | `jsonb-overlay-roundtrip.md` |
| `market-data · ExchangeRefusalAbortTest` | `MV` | в-ящик | `market-data.md` |
| `market-data · InstrumentReadinessTest` | `M` | в-ящик | `market-data.md` |
| `market-data · MarketDataFreshnessTest` | `M` | в-ящик | `market-data.md` |
| `market-data · MarketFeatureReadTest` | `MV` | в-ящик | `market-data.md` |
| `market-data · SnapshotCollectionTest` | `MV` | в-ящик | `market-data.md` |
| `statistics · AccessDenialActorTest` | `RK` | структура | `statistics.md` |
| `statistics · AccessDenialRowTest` | `MVR` | структура | `statistics.md` |
| `statistics · AccessDenialTrailTest` | `M` | в-ящик | `statistics.md` |
| `statistics · AggregateReadBoundariesTest` | `MVR` | структура | `statistics.md` |
| `statistics · AggregateReadPageTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · AggregateRecomputeGuardTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · AggregateRecomputeTest` | `MVO` | в-ящик | `statistics.md` |
| `statistics · AggregateSurfaceReadTest` | `MK` | в-ящик | `statistics.md` |
| `statistics · AggregateUpsertArgumentsTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · AggregateWriteBoundariesTest` | `MVR` | структура | `statistics.md` |
| `statistics · AlertRuleContractTest` | `F` | корпус | — |
| `statistics · ConsumerLagTest` | `M` | в-ящик | `statistics.md` |
| `statistics · JobExecutionGuardTest` | `—` | логика | `statistics.md` |
| `statistics · PersistenceWiringTest` | `K` | корпус | — |
| `statistics · ReceptionCompletenessBoundTest` | `M` | в-ящик | `statistics.md` |
| `statistics · ReceptionContinuityTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · ReceptionEnvelopeTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · ReceptionGapDetectionTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · ReceptionHaltTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · ReceptionMetricsExportTest` | `—` | логика | `statistics.md` |
| `statistics · ReceptionStateTickTest` | `MV` | в-ящик | `statistics.md` |
| `statistics · ReceptionStateWriterBoundariesTest` | `MVOR` | структура | `statistics.md` |
| `statistics · ReceptionSubscriptionTest` | `F` | корпус | — |
| `statistics · ReceptionTransactionBoundariesTest` | `R` | структура | `statistics.md` |
| `statistics · SchedulerCapacityTest` | `—` | корпус | — |
| `statistics · SchemaMigrationChainTest` | `MF` | корпус | — |
| `statistics · StatisticsSurfaceAccessTest` | `MVK` | в-ящик | `statistics.md` |
| `statistics · TopicRetentionTest` | `M` | в-ящик | `statistics.md` |
| `statistics · WireFormContractTest` | `F` | корпус | — |
| `strategies · ActorProviderTest` | `K` | в-ящик | `strategies.md` |
| `strategies · DeclaredActionRejectsTest` | `F` | логика | `strategies.md` |
| `strategies · ReferenceDefinitionBodyTest` | `F` | корпус | — |
| `strategies · StrategyEventFormTest` | `MVR` | структура | `strategies.md` |
| `strategies · StrategyLifecycleTest` | `MV` | в-ящик | `strategies.md` |
| `strategies · StrategySurfaceAccessTest` | `MK` | в-ящик | `strategies.md` |
| `strategies · SurfaceErrorContractTest` | `MK` | в-ящик | `strategies.md` |
| `trading-core · AccountInstrumentStateTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · ActionCommandPlanTest` | `M` | в-ящик | `trading-core.md` |
| `trading-core · ActorProviderTest` | `K` | в-ящик | `trading-core.md` |
| `trading-core · AnomalyDetectorTest` | `MV` | в-уровень-2 | `trading-core-safety.md` |
| `trading-core · AnomalyPassTest` | `MVO` | в-ящик | `trading-core.md`, `trading-core-safety.md` |
| `trading-core · BalanceRefreshTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · CalculationContextAssemblyTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · CalculationFailureCarveOutTest` | `M` | в-ящик | `trading-core.md` |
| `trading-core · CashFlowHarvestTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · CoreEventFormTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · DealAggregateBoundaryTest` | `MVO` | в-ящик | `trading-core.md` |
| `trading-core · DealContextAssemblyTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · DealFsmPassTest` | `MV` | в-уровень-2 | `trading-core-fsm.md` |
| `trading-core · DealOpeningTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · DealOrchestratorPassTest` | `MVO` | в-ящик | `trading-core.md` |
| `trading-core · DealResultSpecTest` | `—` | логика | `trading-core.md` |
| `trading-core · DealRiskNumbersTest` | `MV` | в-уровень-2 | `trading-core-risk.md` |
| `trading-core · DealShutdownEdgeTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · DealTerminalEdgeTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · EntryScanPassTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · ExchangeAccountProjectionWriterTest` | `MVRK` | структура | `trading-core.md` |
| `trading-core · ExchangeCallWireTest` | `MT` | в-ящик | `trading-core.md` |
| `trading-core · ExchangeFailureClassTest` | `MT` | в-ящик | `trading-core.md` |
| `trading-core · ExecutionRowSelectionTest` | `—` | логика | `trading-core-fsm.md` |
| `trading-core · FinalizationLinkTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · InstrumentProjectionPassTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · KillSwitchTeardownTest` | `MVO` | в-уровень-2 | `trading-core-safety.md` |
| `trading-core · ManualHaltSurfaceTest` | `MV` | в-уровень-2 | `trading-core-safety.md` |
| `trading-core · MarketFeatureAssemblyTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · OrderGraphLoadTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · OrderHarvestTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · OutboxRelayTest` | `MVO` | в-ящик | `trading-core.md` |
| `trading-core · PlacementExecutorTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · PositionHarvestTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · RetryPolicyTest` | `—` | логика | `trading-core.md` |
| `trading-core · RiskLimitsSpecTest` | `M` | в-уровень-2 | `trading-core-risk.md` |
| `trading-core · RiskReactionMapTest` | `—` | логика | `trading-core-risk.md` |
| `trading-core · SafetyHoldReactionTest` | `MVO` | в-уровень-2 | `trading-core-safety.md` |
| `trading-core · SchedulerCapacityTest` | `—` | корпус | — |
| `trading-core · ServiceCommandDispatchTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · StrategyActionPlanningTest` | `MV` | в-уровень-2 | `trading-core-fsm.md` |
| `trading-core · StrategyDefinitionApplierTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · StrategyDemandDeclarationTest` | `MVO` | в-ящик | `trading-core.md` |
| `trading-core · StrategyStepSelectionTest` | `M` | в-уровень-2 | `trading-core-fsm.md` |
| `trading-core · StrategyTreeCopyTest` | `M` | в-ящик | `trading-core.md` |
| `trading-core · SystemActionEmissionTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · TradeFeeRateRegistryTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · TradingSurfaceReadTest` | `MV` | в-ящик | `trading-core.md` |
| `trading-core · TrancheFsmPassTest` | `M` | в-уровень-2 | `trading-core-fsm.md` |
| `trading-core · TrancheTransitionGateTest` | `—` | логика | `trading-core-fsm.md` |

## Равенство перечней предъявляет прогон, а не проза

Что таблица накрывает дерево целиком, утверждает команда, а не счёт рядом с
ней: обе стороны — мультимножества имён классов, и сходятся они пусто́й
выдачей.

```bash
diff <(find services -path '*/src/test/java/*' -name '*.java' \
        | sed 's|.*/||; s|\.java$||' | sort) \
     <(grep -o '^| `[^`]* · [^`]*`' .claude/work/progress/phase-2-step-12-existing-tests-revision.md \
        | sed 's/.*· //; s/`//' | sort)
```

## Что снос класса `структура` уносит

У этих проб предмет **не наблюдаем входами-выходами сервиса**, и ящик его не
воспроизводит ничем. Снос под-шагом 3 поэтому не бесплатен, и цена названа
здесь, чтобы решение принималось, а не случалось:

- **границы транзакций приёма** (`ReceptionTransactionBoundariesTest` у обоих
  потребителей) — что флаг остановки лежит **вне** транзакции обработки.
  Снаружи видно только следствие, и то лишь на отказе, который ящику нечем
  устроить;
- **раздел колонок между писателями** (`ReceptionStateWriterBoundariesTest`,
  `JournalCleanupBoundariesTest`, `AggregateWriteBoundariesTest`,
  `ExchangeAccountProjectionWriterTest`) — что такт **не трогает** чужих
  колонок. Ящик видит итоговую строку, а не то, чьей рукой она написана:
  затёртое значение неотличимо от незаписанного;
- **присутствие аннотации на тропе** (`AccessDenialActorTest`,
  `AccessDenialRowTest`, `JournalReadBoundariesTest`,
  `AggregateReadBoundariesTest`) — слушатель аудита на сущности, читающая
  транзакция у выборки, названный менеджер;
- **состав конверта, объявленный классом события** (`StrategyEventFormTest`)
  — что перечень компонентов доехал **целиком**, а не в том объёме, который
  сегодня кладёт производитель.

Развилка у под-шага 3 одна и на всех: снять с потерей либо оставить как
объявленную структурную охрану. Здесь она названа, а не решена — решать её
заходу, который тест снимает, и по каждому классу отдельно.

## Находка: у общего артефакта `common/platform` предмета уровня 2 нет

Перечень уровня 2 накрывает два общих артефакта из трёх, несущих логику:
`common/model/domain` (предмет `domain-model-predicates`) и
`common/strategy-engine` (два предмета). У `common/platform` предмета нет, а
команда кандидатов признак «нет ввода-вывода» подтверждает для **всех** его
классов. Тесты его классов при этом живут в тестовых деревьях четырёх
сервисов — `JobExecutionGuardTest`, `ActorProviderTest`,
`AccessDenialActorTest` и соседние по строке отказа, — то есть переезжают
вместе с чужим деревом и снимаются по чужому исходу.

Тот же класс дефекта у `common/model/domain`: **своего тестового дерева у
артефакта нет вовсе**, а две пробы его предмета (`ExchangeAccountKeyPathTest`,
`InternalIdFormTest`) стоя́т в дереве `auth`.

Парковка — `.claude/work/backlog.md` §«Классы общих артефактов проверяются из
тестовых деревьев сервисов».

## Чего этот заход НЕ делает

- **Не снимает ни одного теста** и не правит их: снятие — под-шаг 3, и не
  раньше покрытия ящиком.
- **Не мерит покрытия.** Адресат назван документом; что документ покрывает
  **это** поведение, читает заход снятия.
- **Не меняет перечня предметов** под-шага 1. Находка о `common/platform`
  предъявлена ревью под-шага 2, которое перечень и читает.

## Связи

- Перечень предметов и порядок — `.claude/decisions/test-contour-design-pass.md`.
- Концепция уровней — `.claude/work/progress/phase-2-step-12-chronicle.md`
  §«Концепция».
- Форма кейсовых документов — `.claude/skills/test-design.md`.
- Правило тестов доменных моделей — `.claude/rules/codestyle.md`
  §«Тесты доменных моделей».
