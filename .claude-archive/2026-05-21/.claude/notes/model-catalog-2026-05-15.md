# Каталог доменных и runtime-объектов проекта

> Дата отчёта: 2026-05-15.
> Источники: `docs/domain/**/*.md` (HEAD = `e790e8a`), `src/main/java/com/example/tradingbot/**`, `src/main/resources/db/migration/V1..V7`.
> Назначение: рабочий артефакт для подготовки решения о декомпозиции моделей в `docs/spec/`.
> **Не Project Knowledge. Не основание для правок. Не предлагает структуру `docs/spec/models/` и не определяет порядок миграции.**

---

## 1. Источники и метод

* **Документация:** все 20 содержательных markdown-файлов в `docs/domain/` (см. инвентарь `.claude/notes/migration-inventory-2026-05-15.md`). Файл `Strategy API examples.md` использован как источник имён моделей (через JSONC), но не как авторитетный источник профилей.
* **Код:** `src/main/java/com/example/tradingbot/`, 357 файлов. Доменные модели — `domain/model/`; persistence — `persistence/model/`; рантайм-сервисы — `domain/service/`.
* **БД-таблицы:** определены по Flyway `V1..V7`. Имена таблиц: `exchanges`, `instruments`, `candle_groups`, `candles`, `deals`, `orders`, `attached_algo_orders`, `algo_orders`, `positions`, `balance_containers`, `balances`, `anomaly_reports`, `strategies`, `strategy_details`, `strategy_steps`.
* **`@Entity`-классы:** 15 (одно к одному с таблицами).
* **Включаемые модели:** только то, что **имеет имя класса/enum/интерфейса** (например, `Deal`, `Condition`, `ServiceCommandType`). Общие концепции вроде "стратегия как идея" или "защита" — не включаются.
* **Persistent-классификация:**
  * `yes` — есть `@Entity` + таблица в Flyway.
  * `embedded` — нет своего `@Entity`, но сериализуется внутрь JSONB чужой таблицы (например, `Condition` → `algo_orders.condition`, `StrategyAction` → `strategy_steps.actions`).
  * `no` — runtime-only, в БД не хранится.
  * `documented only` — модель упоминается в docs/domain, но в коде её НЕТ.

---

## 2. Каталог по категориям

### 2.1. Aggregate root

> Корни-агрегаты: имеют собственную таблицу, стабильный `internalId`, явный lifecycle/FSM, переживают рестарт.

#### `AlgoOrder`

* Категория: aggregate root.
* Persistent: yes (`algo_orders`, FK `deal_id` → `deals`).
* Identity: `Long id` + `String internalId` + `String externalId`.
* Lifecycle: создаётся как составной артефакт сделки (CREATED → PENDING → ACTIVE → CLOSED/FAILED), но имеет собственный finite-state machine; живёт между рестартами.
* Где описана: `docs/domain/models/AlgoOrder.md` (model), `docs/domain/models/mapping/okx/OKX_AlgoOrder_mapping.md`, `Статусы торговых сущностей.md`, `Сервисные команды.md`, `FSM этапы сделки.md`.
* Где в коде: `src/main/java/com/example/tradingbot/domain/model/core/algo_order/AlgoOrder.java`; persistence: `persistence/model/deal/algo_order/AlgoOrderEntity.java`.
* Используется кем: `DealContext`, `Condition`, all `*AlgoOrderExecutor`, `RefreshAlgoOrderExecutor`, `AlgoOrderSyncService`, `KillSwitchService`, `StateSnapshot`, `StrategyAlgoOrderAction` (через id), payloads (`CreateAlgoOrderCommandPayload`, `SubmitAlgoOrderCommandPayload`, …).
* Контейнер: формально живёт внутри сделки (`Deal.algoOrders`), но имеет независимый внешний lifecycle на бирже.

#### `AnomalyReport`

* Категория: aggregate root.
* Persistent: yes (`anomaly_reports`, FK `exchange_id`, FK `instrument_id`).
* Identity: `Long id` + `String internalId`.
* Lifecycle: CREATED → IN_PROGRESS → KILL_SWITCH_EXECUTED → COMPLETED/ERROR; самостоятельный, не привязан к сделке.
* Где описана: `docs/domain/models/Справочник по доменным моделям.md`, `docs/domain/processes/Audit/Аудит и история исполнения.md`; в `docs/spec/models/AnomalyReport.md` уже мигрирован (за рамками этого отчёта).
* Где в коде: `domain/model/anomaly/AnomalyReport.java`; persistence: `persistence/model/anomaly/AnomalyReportEntity.java`.
* Используется кем: `AnomalyService`, `KillSwitchService` (как потребитель события), `Аудит и история исполнения.md` (process-документ).
* Контейнер: нет; глобальная сущность системы.

#### `BalanceContainer`

* Категория: aggregate root.
* Persistent: yes (`balance_containers`, FK `exchange_id`, UNIQUE по `exchange_id`).
* Identity: `Long id` + `exchangeId` (UK).
* Lifecycle: фактически singleton на биржу; обновляется через `REFRESH_BALANCE`; не имеет публичного FSM-статуса.
* Где описана: `docs/domain/models/Balance.md`, `docs/domain/models/mapping/okx/OKX_Balance_mapping.md`, `Статусы торговых сущностей.md`.
* Где в коде: `domain/model/core/balance/BalanceContainer.java`; persistence: `persistence/model/balance/BalanceContainerEntity.java`.
* Используется кем: `BalanceService`, `RefreshBalanceExecutor`, `DealContextService`, риск-слой (по докам); содержит `List<Balance>`.
* Контейнер: нет.

#### `Deal`

* Категория: aggregate root.
* Persistent: yes (`deals`, FK `instrument_id`).
* Identity: `Long id` + `String internalId` (UNIQUE).
* Lifecycle: явный FSM `Deal.Status` (PRECHECK → ENTRY_SUBMITTED → … → CLOSED/ERROR); главный агрегат, которым управляет `DealStateMachine`.
* Где описана: `docs/domain/models/Deal.md`, `Жизненный цикл сделки.md`, `FSM этапы сделки.md`, `Статусы торговых сущностей.md`, `Сервисные команды.md`.
* Где в коде: `domain/model/core/deal/Deal.java`; persistence: `persistence/model/deal/DealEntity.java`.
* Используется кем: `DealContext`, `DealOrchestrator`, `DealStateMachine`, all state handlers, `DealAggregateService`, `DealService`, `KillSwitchService`, `ExitService`.
* Контейнер: нет.

#### `Exchange`

* Категория: aggregate root.
* Persistent: yes (`exchanges`).
* Identity: `Long id` + `String internalId` + `String name` (оба UK).
* Lifecycle: CREATED → PENDING → ACTIVE → CLOSED/ERROR; самостоятельный.
* Где описана: `Статусы торговых сущностей.md`, `Справочник по доменным моделям.md`.
* Где в коде: `domain/model/core/exchange/Exchange.java`; persistence: `persistence/model/exchange/ExchangeEntity.java`.
* Используется кем: `ExchangeService`, `Instrument`, `BalanceContainer`, `AnomalyReport`, `DealContext`.
* Контейнер: нет.

#### `Instrument`

* Категория: aggregate root.
* Persistent: yes (`instruments`, FK `exchange_id`).
* Identity: `Long id` + `internalId` (UK) + UK (`exchange_id`, `external_id`).
* Lifecycle: CREATED → HOLD → SYNC → CANDLES_LOADING → ACTIVE → CLOSED/ERROR.
* Где описана: `Справочник по доменным моделям.md`, `Статусы торговых сущностей.md`, `Расчёт индикаторов и рыночных данных.md`.
* Где в коде: `domain/model/core/instrument/Instrument.java`; persistence: `persistence/model/instrument/InstrumentEntity.java`.
* Используется кем: `InstrumentService`, `DealContext`, `KillSwitchService`, `MarketPhaseService`, `Strategy` (через `instrumentId`), `CandleGroup`.
* Контейнер: нет; содержит `List<CandleGroup>`.

#### `Order`

* Категория: aggregate root.
* Persistent: yes (`orders`, FK `deal_id`).
* Identity: `Long id` + `internalId` (UK) + `externalId`.
* Lifecycle: CREATED → PENDING → ACTIVE → COMPLETED/PARTIALLY_COMPLETED/CLOSED/FAILED; собственный FSM-под-статус.
* Где описана: `docs/domain/models/Order.md`, `OKX_Order_mapping.md`, `Статусы торговых сущностей.md`, `Сервисные команды.md`.
* Где в коде: `domain/model/core/order/Order.java`; persistence: `persistence/model/deal/order/OrderEntity.java`.
* Используется кем: `DealContext` (`entryOrder`, `orders`), `*OrderExecutor`, `RefreshOrderExecutor`, `OrderStatusResolver`, `KillSwitchService`, payloads (`CreateOrderCommandPayload`, …); содержит `List<AttachedAlgoOrder>`.
* Контейнер: формально живёт внутри сделки (`Deal.orders`).

#### `Position`

* Категория: aggregate root.
* Persistent: yes (`positions`, FK `deal_id`).
* Identity: `Long id` + `internalId` + `externalId` (UK).
* Lifecycle: ACTIVE → CLOSED/ERROR; источник правды — внешняя биржа, локально синхронизируется через `REFRESH_POSITION`.
* Где описана: `docs/domain/models/Position.md`, `OKX_Position_mapping.md`, `Статусы торговых сущностей.md`.
* Где в коде: `domain/model/core/position/Position.java`; persistence: `persistence/model/deal/position/PositionEntity.java`.
* Используется кем: `DealContext.activePosition`, `RefreshPositionExecutor`, `ClosePositionExecutor`, `PositionStatusResolver`, `PositionSyncService`, `KillSwitchService`, `StateSnapshot`.
* Контейнер: технически живёт внутри `Deal` (FK `deal_id`), но доменно — самостоятельная сущность с собственным lifecycle.

#### `Strategy`

* Категория: aggregate root.
* Persistent: yes (`strategies`, FK `instrument_id`).
* Identity: `Long id` + `internalId` (UK) + `version`; уникальный активный — partial unique index по `instrument_id WHERE status = 'ACTIVE'`.
* Lifecycle: CREATED → ACTIVE → INACTIVE → DELETED (append-only по версиям); ровно одна ACTIVE на инструмент.
* Где описана: `docs/domain/models/Strategy.md`, `Strategy API examples.md`, `Справочник по доменным моделям.md`, `Статусы торговых сущностей.md`.
* Где в коде: `domain/model/trade/strategy/Strategy.java`; persistence: `persistence/model/strategy/StrategyEntity.java`.
* Используется кем: `StrategyService`, `StrategyStatusResolver`, `StrategyValidator`, `DealContext`, `ServiceCommand.strategyId`; содержит `List<StrategyDetails>`.
* Контейнер: нет.

---

### 2.2. Domain entity (вложенная, со своим ID и таблицей, но живёт только в составе родителя)

> Имеют собственное `@Entity` и таблицу, но в домене существуют только как часть родительского агрегата (FK к нему обязателен).

#### `AttachedAlgoOrder`

* Категория: domain entity.
* Persistent: yes (`attached_algo_orders`, FK `order_id`).
* Identity: `Long id` + `internalId` (UK) + `externalAttachedId` + `externalId`.
* Lifecycle: CREATED → ATTACHED → ACTIVE → CLOSED/FAILED; имеет встроенную `canTransitionTo` и `transitionTo` (явная FSM в коде).
* Где описана: `docs/domain/models/Order.md` (§AttachedAlgoOrder), `OKX_Order_mapping.md`, `Статусы торговых сущностей.md`.
* Где в коде: `domain/model/core/order/AttachedAlgoOrder.java`; persistence: `persistence/model/deal/order/AttachedAlgoOrderEntity.java`.
* Используется кем: `Order.attachedAlgoOrders`, `DealContext.getActiveAttachedStopLoss`, `RefreshAttachedAlgoOrderExecutor`, `CreateOrderCommandPayload`, `StrategyAttachedProtectionSettings`.
* Контейнер: `Order` (FK `order_id`).

#### `Balance`

* Категория: domain entity.
* Persistent: yes (`balances`, FK `balance_container_id`, UK (`balance_container_id`, `currency`)).
* Identity: `Long id` + UK (`balance_container_id`, `currency`).
* Lifecycle: только в составе `BalanceContainer`; реплейс через `replaceBalances`.
* Где описана: `docs/domain/models/Balance.md`, `OKX_Balance_mapping.md`.
* Где в коде: `domain/model/core/balance/Balance.java`; persistence: `persistence/model/balance/BalanceEntity.java`.
* Используется кем: `BalanceContainer.balances`, `BalanceService`, `RefreshBalanceExecutor`.
* Контейнер: `BalanceContainer`.

#### `Candle`

* Категория: domain entity.
* Persistent: yes (`candles`, FK `candle_group_id`, UK (`candle_group_id`, `open_timestamp`)).
* Identity: `Long id` + UK (`candle_group_id`, `open_timestamp`).
* Lifecycle: только в составе `CandleGroup`.
* Где описана: `Справочник по доменным моделям.md`, `Расчёт индикаторов и рыночных данных.md` (косвенно).
* Где в коде: `domain/model/trade/candle/Candle.java`; persistence: `persistence/model/candle/CandleEntity.java`.
* Используется кем: `CandleGroupService` (косвенно), market data jobs (в коде самих jobs нет — см. §3).
* Контейнер: `CandleGroup`.

#### `CandleGroup`

* Категория: domain entity (имеет собственный мини-FSM статус, формально близко к aggregate root).
* Persistent: yes (`candle_groups`, FK `instrument_id`, UK (`instrument_id`, `timeframe`)).
* Identity: `Long id` + UK (`instrument_id`, `timeframe`).
* Lifecycle: CREATED → BACKFILL → SYNC → CHECK → REPAIR → ACTIVE → ERROR/DELETED.
* Где описана: `Справочник по доменным моделям.md`, `Расчёт индикаторов и рыночных данных.md` (косвенно).
* Где в коде: `domain/model/trade/candle/CandleGroup.java`; persistence: `persistence/model/candle/CandleGroupEntity.java`.
* Используется кем: `Instrument.candleGroups`, `CandleGroupService`.
* Контейнер: `Instrument` (FK `instrument_id`).

#### `StrategyDetails`

* Категория: domain entity.
* Persistent: yes (`strategy_details`, FK `strategy_id`, UK (`strategy_id`, `market_phase_type`)).
* Identity: `Long id` + UK (`strategy_id`, `market_phase_type`).
* Lifecycle: только в составе `Strategy`; одна `StrategyDetails` на одну `MarketPhase.Type`.
* Где описана: `docs/domain/models/Strategy.md`, `Справочник по доменным моделям.md` (под именем `StrategyDetail` без `s` — расхождение, см. §4).
* Где в коде: `domain/model/trade/strategy/StrategyDetails.java`; persistence: `persistence/model/strategy/StrategyDetailsEntity.java`.
* Используется кем: `Strategy.details`, `DealContext.strategyDetails`, `Strategy.getActiveDetails`, `StrategyService`, `StrategyValidator`, `StrategyActionInterpreter`, `ServiceCommand.strategyDetailsId`.
* Контейнер: `Strategy`.

#### `StrategyStep`

* Категория: domain entity.
* Persistent: yes (`strategy_steps`, FK `strategy_details_id`, UK (`strategy_details_id`, `deal_status`, `step_index`)). Полиморфные actions хранятся в `actions JSONB`; `condition JSONB`.
* Identity: `Long id` + UK по `(details_id, deal_status, step_index)`.
* Lifecycle: только в составе `StrategyDetails`; группируется по `Deal.Status`.
* Где описана: `docs/domain/models/Strategy.md` (значительный блок), `Strategy API examples.md`.
* Где в коде: `domain/model/trade/strategy/StrategyStep.java`; persistence: `persistence/model/strategy/StrategyStepEntity.java`.
* Используется кем: `StrategyDetails.stepsByStatus`, `StrategyActionInterpreter`, `ServiceCommand.sourceStepType`.
* Контейнер: `StrategyDetails`.

---

### 2.3. Value object

> Без собственного ID или с локальным ID, сериализуются вместе с родителем (обычно в JSONB), нет независимого lifecycle.

#### `Condition` (+ subtypes: `OcoFullCondition`, `PartialStopLossCondition`, `PartialTakeProfitCondition`, `StopLossCondition`, `TakeProfitCondition`, `TrailingPercentsCondition`, `TrailingValueCondition`)

* Категория: value object (полиморфная иерархия по `ConditionType`).
* Persistent: embedded (внутри `algo_orders.condition JSONB`).
* Identity: нет.
* Lifecycle: внутри `AlgoOrder`; immutable после создания.
* Где описана: `docs/domain/models/AlgoOrder.md`, `OKX_AlgoOrder_mapping.md`.
* Где в коде: `domain/model/core/algo_order/Condition.java` (+ 7 подклассов); persistence-копия: `persistence/model/deal/algo_order/Condition.java`.
* Используется кем: `AlgoOrder.condition`, `StrategyPriceResolver`, `ResolvedAlgoOrderPrice`, `CreateAlgoOrderCommandPayload`, `ConditionMapper`.
* Контейнер: `AlgoOrder`.

#### `StopLossSettings`

* Категория: value object (strategy config part).
* Persistent: embedded (`strategy_steps.actions JSONB` — внутри `StrategyAlgoOrderAction` / `StrategyAttachedProtectionSettings`).
* Identity: нет.
* Lifecycle: immutable, часть конфигурации стратегии.
* Где описана: `docs/domain/models/Strategy.md`, `Strategy API examples.md`, `Калькуляторы действий стратегии.md`.
* Где в коде: `domain/model/trade/strategy/StopLossSettings.java`; persistence: `persistence/model/strategy/StopLossSettingsEntity.java`.
* Используется кем: `StrategyAlgoOrderAction`, `StrategyAttachedProtectionSettings`, `StrategyPriceResolver`, `StrategyValidator`.
* Контейнер: `StrategyAlgoOrderAction` / `StrategyAttachedProtectionSettings`.

#### `StrategyAttachedProtectionSettings`

* Категория: value object (strategy config part).
* Persistent: embedded (внутри `StrategyOrderAction`, в `strategy_steps.actions`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`.
* Где в коде: `domain/model/trade/strategy/StrategyAttachedProtectionSettings.java`; persistence: `persistence/model/strategy/StrategyAttachedProtectionSettingsEntity.java`; REST DTO: `rest/model/strategy/StrategyAttachedProtectionSettingsModel.java`.
* Используется кем: `StrategyOrderAction.attachedProtection`, `StrategyPriceResolver.resolveAttachedProtectionPrice`.
* Контейнер: `StrategyOrderAction`.

#### `StrategyCondition`

* Категория: value object.
* Persistent: embedded (`strategy_steps.condition JSONB`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`.
* Где в коде: `domain/model/trade/strategy/StrategyCondition.java`; persistence: `persistence/model/strategy/StrategyConditionEntity.java`.
* Используется кем: `StrategyStep.condition`, `StrategyConditionEvaluator`.
* Контейнер: `StrategyStep`.

#### `StrategyConditionRule`

* Категория: value object.
* Persistent: embedded (внутри `StrategyCondition.rules`).
* Identity: только локальный `level: Integer` для порядка проверки.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`.
* Где в коде: `domain/model/trade/strategy/StrategyConditionRule.java`; persistence: `persistence/model/strategy/StrategyConditionRuleEntity.java`.
* Используется кем: `StrategyCondition.rules`, `StrategyConditionEvaluator`.
* Контейнер: `StrategyCondition`.

#### `StrategyPricePlacement`

* Категория: value object (strategy config part).
* Persistent: embedded (внутри `StrategyOrderAction` / иных action в `strategy_steps.actions`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`, `Strategy API examples.md`.
* Где в коде: `domain/model/trade/strategy/StrategyPricePlacement.java`; persistence: `persistence/model/strategy/StrategyPricePlacementEntity.java`.
* Используется кем: `StrategyOrderAction.placement`, `StrategyPriceResolver.resolveOrderPrice`.
* Контейнер: `StrategyOrderAction`.

#### `Trailing`

* Категория: value object.
* Persistent: embedded (внутри `Condition` → `algo_orders.condition`).
* Identity: нет.
* Lifecycle: immutable; XOR с `Trigger` (одно из двух).
* Где описана: `docs/domain/models/AlgoOrder.md`.
* Где в коде: `domain/model/core/algo_order/Trailing.java`; persistence: `persistence/model/deal/algo_order/Trailing.java`.
* Используется кем: `Condition.trailing`.
* Контейнер: `Condition`.

#### `TrailingSettings`

* Категория: value object (strategy config part).
* Persistent: embedded (внутри `StrategyAlgoOrderAction`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`, `Калькуляторы действий стратегии.md`.
* Где в коде: `domain/model/trade/strategy/TrailingSettings.java`; persistence: `persistence/model/strategy/TrailingSettingsEntity.java`.
* Используется кем: `StrategyAlgoOrderAction.trailingSettings`.
* Контейнер: `StrategyAlgoOrderAction`.

#### `Trigger`

* Категория: value object.
* Persistent: embedded (внутри `Condition`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/AlgoOrder.md`.
* Где в коде: `domain/model/core/algo_order/Trigger.java`; persistence: `persistence/model/deal/algo_order/Trigger.java`.
* Используется кем: `Condition.trigger`.
* Контейнер: `Condition`.

#### `TriggerPrice`

* Категория: value object.
* Persistent: embedded (внутри `Trigger` или `Trailing.activationPrice`).
* Identity: нет.
* Lifecycle: immutable.
* Где описана: `docs/domain/models/AlgoOrder.md`.
* Где в коде: `domain/model/core/algo_order/TriggerPrice.java`; persistence: `persistence/model/deal/algo_order/TriggerPrice.java`.
* Используется кем: `Trigger.stopLoss`, `Trigger.takeProfit`, `Trailing.activationPrice`.
* Контейнер: `Trigger`, `Trailing`.

#### `Auditable`

* Категория: value object (shared mixin: `createdAt/createdBy/modifiedAt/modifiedBy/externalCreatedAt/externalModifiedAt`).
* Persistent: embedded (как абстрактный родитель почти всех domain-моделей).
* Identity: нет.
* Lifecycle: нет собственного, заполняется родителем.
* Где описана: `Справочник по доменным моделям.md`.
* Где в коде: `domain/model/Auditable.java`; persistence-аналог: `persistence/model/AuditableEntity.java`.
* Используется кем: `Deal`, `Order`, `AlgoOrder`, `Position`, `Balance`, `BalanceContainer`, `Exchange`, `Instrument`, `CandleGroup`, `Candle`, `Strategy`, `StrategyDetails`, `AnomalyReport`, `PriceTicker`, `TradeFill`, `TradeFillsArchive`, все `*ExternalSnapshot`.
* Контейнер: любой родитель.

---

### 2.4. Runtime context (контекст исполнения, держит ссылки на агрегаты для текущего цикла FSM)

#### `DealContext`

* Категория: runtime context.
* Persistent: no.
* Identity: нет (контекст одного цикла FSM).
* Lifecycle: транзитный — собирается `DealContextService.load(dealId)` в начале цикла `DealOrchestrator.runOneCycle`, передаётся handler'ам, выбрасывается.
* Где описана: `docs/domain/models/Deal.md`, `Жизненный цикл сделки.md`, `FSM этапы сделки.md`, `Сервисные команды.md`, `Калькуляторы действий стратегии.md`, `Оценка рисков.md` (везде широко).
* Где в коде: `domain/service/deal/state_machine/DealContext.java`.
* Используется кем: `DealStateMachine`, `DealOrchestrator`, все 8 `StateHandler` implementations, `StrategyActionInterpreter`, `StrategyConditionEvaluator`, `StrategyPriceResolver`, `ServiceCommandFactory`.
* Контейнер: нет; держит ссылки на `Exchange`, `Instrument`, `Deal`, `entryOrder`, `orders`, `activePosition`, `activeAlgoOrders`, `algoOrders`, `marketPhase`, `Strategy`, `StrategyDetails`.

---

### 2.5. Runtime state (живёт в памяти на время операции, не контекст)

#### `KillSwitchResult`

* Категория: runtime state (рабочий результат kill-switch перед persistence в `AnomalyReport`).
* Persistent: no; поля `internalAfter` / `externalAfter` записываются в `anomaly_reports.internal_after` / `external_after` через `AnomalyReport`.
* Identity: нет.
* Lifecycle: транзитный.
* Где описана: только в `Открытые вопросы по движку.md` (косвенно как часть kill-switch flow).
* Где в коде: `domain/model/kill_switch/KillSwitchResult.java`.
* Используется кем: `KillSwitchService` (возвращает наружу), `AnomalyService` (по флоу обновления).
* Контейнер: нет.

#### `StateSnapshot`

* Категория: runtime state (комбинированный снимок internal + external для kill-switch).
* Persistent: no напрямую; используется как источник `anomaly_reports.internal_before` / `external_before`.
* Identity: нет.
* Lifecycle: транзитный, создаётся `StateSnapshotReader`.
* Где описана: `Открытые вопросы по движку.md`.
* Где в коде: `domain/model/kill_switch/StateSnapshot.java`; reader: `domain/service/kill_switch/reader/StateSnapshotReader.java`.
* Используется кем: `KillSwitchService`, `AnomalyService`, kill-switch readers (`ExternalAlgoOrderReader`, `ExternalAlgoOrderSnapshotReader`, `ExternalOrderSnapshotReader`, `ExternalPositionSnapshotReader`).
* Контейнер: нет.

#### `TransitionResult`

* Категория: runtime state (результат шага FSM).
* Persistent: no.
* Identity: нет.
* Lifecycle: транзитный — возвращается из `StateHandler.handle`.
* Где описана: косвенно в `FSM этапы сделки.md`.
* Где в коде: `domain/service/deal/state_machine/TransitionResult.java`.
* Используется кем: `DealStateMachine`, все `StateHandler` implementations, `DealOrchestrator`.
* Контейнер: нет.

---

### 2.6. Strategy config part (части иерархии immutable конфигурации стратегии)

> Все объекты ниже сериализуются полиморфно в `strategy_steps.actions JSONB` (через `@JsonValue`/Jackson type info). У каждого `StrategyAction` есть стабильный `Long id`, чтобы FSM мог восстанавливать "какие actions уже материализованы" после рестарта (см. `orders.strategy_action_id`, `algo_orders.strategy_action_id` в `V7`).

#### `StrategyAction` (interface) + три реализации

* Категория: strategy config part (полиморфная иерархия).
* Persistent: embedded (`strategy_steps.actions JSONB`).
* Identity: `Long id` (стабильный внутри стратегии; уникальность гарантируется конструированием, не БД).
* Lifecycle: immutable.
* Где описана: `docs/domain/models/Strategy.md`, `Strategy API examples.md`, `Калькуляторы действий стратегии.md`.
* Где в коде: `domain/model/trade/strategy/StrategyAction.java` (interface) + `StrategyOrderAction`, `StrategyAlgoOrderAction`, `StrategyPositionAction`; persistence: `StrategyActionEntity` (+ `StrategyOrderActionEntity`, `StrategyAlgoOrderActionEntity`, `StrategyPositionActionEntity`).
* Используется кем: `StrategyStep.actions`, `StrategyActionInterpreter`, `StrategyPriceResolver`, `ServiceCommandFactory.strategy`, `ServiceCommand.strategyActionId`.
* Контейнер: `StrategyStep`.

#### `StrategyOrderAction`, `StrategyAlgoOrderAction`, `StrategyPositionAction`

* Категории/контейнеры/идентичность — те же, что у `StrategyAction`.
* Различие: `StrategyOrderAction` управляет обычным ордером + attached protection; `StrategyAlgoOrderAction` — standalone algo; `StrategyPositionAction` — операции над позицией (CLOSE_FULL/CLOSE_PARTIAL).

---

### 2.7. Calculation result (типизированные результаты вычислений на цикл FSM)

> Простые Java `record`-обёртки над результатами `StrategyPriceResolver`. По докам должны были называться `CalculatedPrice` / `CalculatedSize` / `CalculatedStrategyAction`; в коде реализованы как разные `Resolved*Price`.

#### `ResolvedAlgoOrderPrice`

* Категория: calculation result.
* Persistent: no.
* Identity: нет.
* Lifecycle: транзитный.
* Где описана: косвенно в `Калькуляторы действий стратегии.md` (под именами `CalculatedPrice` / `CalculatedStrategyAction`).
* Где в коде: `domain/service/strategy/price/ResolvedAlgoOrderPrice.java`.
* Используется кем: `StrategyPriceResolver`, `StrategyActionInterpreter` (для построения `CreateAlgoOrderCommandPayload.condition`).
* Контейнер: нет.

#### `ResolvedAttachedProtectionPrice`

* Те же координаты; содержит только `stopLossTriggerPrice`. Используется при формировании `AttachedAlgoOrder` для `CreateOrderCommandPayload`.

#### `ResolvedOrderPrice`

* Те же координаты; содержит только `price`. Используется для `CreateOrderCommandPayload.price`.

#### `ResolvedPositionClosePrice`

* Те же координаты; содержит только `price`. Используется для `ClosePositionCommandPayload`.

---

### 2.8. Command payload (типизированные параметры команд FSM)

> Все реализуют `ServiceCommandPayload`. Хранятся в памяти; `ServiceCommand`-обёртка не сохраняется как `@Entity` (см. §3 — `ServiceCommandExecutionHistory` упомянут в доках, в коде отсутствует).

* `ServiceCommandPayload` (interface) — маркер-интерфейс, `domain/model/commands/ServiceCommandPayload.java`.
* `ServiceCommand` — runtime-объект-обёртка (`type`, `dealId`, `instrumentId`, `strategyId`, `strategyDetailsId`, `sourceDealStatus`, `sourceStepType`, `sourceActionType`, `strategyActionId`, `payload`).
* `CreateOrderCommandPayload` — поля `strategyActionId`, `orderType (Order.Type)`, `side`, `price`, `size`, `attachedAlgoOrders`.
* `SubmitOrderCommandPayload`, `AmendOrderCommandPayload`, `CancelOrderCommandPayload` — соответствуют `SUBMIT_*/AMEND_*/CANCEL_*` командам.
* `CreateAlgoOrderCommandPayload` — `strategyActionId`, `conditionType (ConditionType)`, `size`, `direction (AlgoOrder.Direction)`, `externalType`, `externalDirection`, `condition (Condition)`.
* `SubmitAlgoOrderCommandPayload`, `AmendAlgoOrderCommandPayload`, `CancelAlgoOrderCommandPayload` — symmetric algo-payloads.
* `ClosePositionCommandPayload` — closeFraction/etc.

Все эти классы:
* Persistent: no.
* Identity: нет.
* Lifecycle: транзитный (создаются `ServiceCommandFactory.strategy(...)` / `.system(...)`).
* Где описаны: `docs/domain/processes/Deal management/Сервисные команды.md` (большой catalog в формате reference).
* Используются кем: `ServiceCommandFactory`, соответствующие `*Executor` (`CreateOrderExecutor`, `SubmitOrderExecutor`, …).
* Контейнер: `ServiceCommand.payload`.

---

### 2.9. External snapshot (внешние снимки биржевых ответов)

> Шаблон: всегда DTO без ID, без lifecycle; создаётся mapper-ом из OKX-ответа; служит источником для `*StatusResolver` и refresh-executors.

* `AlgoOrderExternalSnapshot` + вложенные `ConditionExternalSnapshot`, `TriggerExternalSnapshot`, `TriggerPriceExternalSnapshot`, `TrailingExternalSnapshot` — `domain/model/core/algo_order/external_snapshot/AlgoOrderExternalSnapshot.java`. Используется: `RefreshAlgoOrderExecutor`, `AlgoOrderSyncService`, kill-switch readers.
* `AttachedAlgoOrderExternalSnapshot` — `domain/model/core/order/external_snapshot/AttachedAlgoOrderExternalSnapshot.java`. Используется: `OrderExternalSnapshot.attachedAlgoOrders`, refresh attached executor.
* `BalanceContainerExternalSnapshot` + `BalanceExternalSnapshot` — `domain/model/core/balance/external_snapshot/`. Используется: `RefreshBalanceExecutor`, OKX balance mapper.
* `InstrumentExternalSnapshot` — `domain/model/core/instrument/external_snapshot/InstrumentExternalSnapshot.java`. Используется: instrument sync flow (по docs — `InstrumentExternalRulesSyncJob`, в коде явного job нет).
* `OrderExternalSnapshot` — `domain/model/core/order/external_snapshot/OrderExternalSnapshot.java`. Используется: `OrderStatusResolver`, `RefreshOrderExecutor`, kill-switch readers, `KillSwitchService`.
* `PositionExternalSnapshot` — `domain/model/core/position/external_snapshot/PositionExternalSnapshot.java`. Используется: `PositionStatusResolver`, `RefreshPositionExecutor`, `PositionSyncService`, `KillSwitchService`.

Все: Persistent = no; Identity = нет; Lifecycle = транзитный; Контейнер = нет.
Где описаны: соответствующие model-документы (`AlgoOrder.md`, `Balance.md`, `Order.md`, `Position.md`) + все четыре `mapping/okx/OKX_*_mapping.md`.

---

### 2.10. Status resolver

> Чистые сервисы (`@Component`/`@Service`), маппят внешний статус → внутренний.

#### `OrderStatusResolver`

* Категория: status resolver.
* Persistent: no.
* Identity: нет (singleton-bean).
* Lifecycle: stateless.
* Где описана: `OKX_Order_mapping.md`, `Сервисные команды.md`, `Статусы торговых сущностей.md` (под именем `OrderExternalStatusResolver` — расхождение, см. §4).
* Где в коде: `domain/service/deal/command/refresh/OrderStatusResolver.java`.
* Используется кем: `RefreshOrderExecutor`, `AlgoOrderSyncService` (через резолверы), kill-switch flow.
* Контейнер: нет.

#### `PositionStatusResolver`

* Категория: status resolver.
* Persistent/Identity/Lifecycle: аналогично.
* Где описана: `OKX_Position_mapping.md`, `Position.md`, `Статусы торговых сущностей.md`.
* Где в коде: `domain/service/deal/command/refresh/PositionStatusResolver.java`.
* Используется кем: `RefreshPositionExecutor`, `PositionSyncService`, `KillSwitchService`.
* Контейнер: нет.

#### `StrategyStatusResolver`

* Категория: status resolver (валидатор переходов FSM статусов `Strategy`).
* Persistent/Identity/Lifecycle: аналогично.
* Где описана: `Статусы торговых сущностей.md` (косвенно).
* Где в коде: `domain/service/strategy/StrategyStatusResolver.java`.
* Используется кем: `StrategyService` (lifecycle стратегии).
* Контейнер: нет.

---

### 2.11. FSM state handlers (8 handlers + interface)

> Не "модели" в строгом смысле, но в `docs/domain` они упоминаются по имени (`ExitPendingHandler`, `ErrorHandler` и т.д.).

* `StateHandler` (interface) — `domain/service/deal/state_machine/handler/StateHandler.java`. Контракт: `supportedStatus()`, `checkEntryInvariants`, `handle`, `checkExitInvariants`.
* `PrecheckHandler`, `EntrySubmittedHandler`, `EntryFinalizedHandler`, `ProtectionSwitchedHandler`, `ManagingHandler`, `ExitPendingHandler`, `ClosedHandler`, `ErrorHandler` — по одному handler'у на каждый `Deal.Status`.

Все: Persistent = no; Identity = stateless singleton; Lifecycle = bean; Контейнер = `DealStateMachine`.
Где описаны: `FSM этапы сделки.md`, `Открытые вопросы по движку.md` (`ExitPendingHandler`, `ErrorHandler`), `Жизненный цикл сделки.md`.

---

### 2.12. Service / executor / orchestrator (рантайм-сервисы, упомянутые в доках по имени)

> Перечислены только те, что встречаются в `docs/domain/` по имени класса. Не "модели", но входят в каталог именованных сущностей.

* `DealOrchestrator` — `domain/service/deal/orchestrator/DealOrchestrator.java`. В докиах под именем `DealOrchestratorJob` (расхождение, см. §4). Главный driver одного цикла сделки.
* `DealContextService` — `domain/service/deal/orchestrator/DealContextService.java`. Загружает `DealContext.load(dealId)`.
* `DealAggregateService` — `domain/service/deal/orchestrator/DealAggregateService.java`. Не упомянут в `docs/domain` напрямую (см. §5.3).
* `DealStateMachine` — `domain/service/deal/state_machine/DealStateMachine.java`. Применяет `StateHandler.handle`.
* `ServiceCommandFactory` — `domain/service/deal/command/core/ServiceCommandFactory.java`. Строит `ServiceCommand` через `system(...)` / `strategy(...)`.
* `ServiceCommandExecutor` — `domain/service/deal/command/core/ServiceCommandExecutor.java`. Диспетчер по `ServiceCommandType` к соответствующему `*Executor`.
* `CreateOrderExecutor`, `SubmitOrderExecutor`, `AmendOrderExecutor`, `CancelOrderExecutor` — `domain/service/deal/command/order/`.
* `CreateAlgoOrderExecutor`, `SubmitAlgoOrderExecutor`, `AmendAlgoOrderExecutor`, `CancelAlgoOrderExecutor` — `domain/service/deal/command/algo/`.
* `RefreshOrderExecutor`, `RefreshAlgoOrderExecutor`, `RefreshAttachedAlgoOrderExecutor`, `RefreshBalanceExecutor`, `RefreshPositionExecutor`, `SyncAlgoOrderExecutor`, `AlgoOrderSyncService`, `PositionSyncService` — `domain/service/deal/command/refresh/`.
* `CloseAlgoOrderExecutor`, `CloseDealExecutor`, `CloseOrderExecutor`, `ClosePositionExecutor` — `domain/service/deal/command/close/`.
* `KillSwitchService`, `KillSwitchLiveStatuses` — `domain/service/kill_switch/`. В докиах под именами `KillSwitch` и `KillSwitchService`.
* `StateSnapshotReader`, `ExternalAlgoOrderReader`, `ExternalAlgoOrderSnapshotReader`, `ExternalOrderSnapshotReader`, `ExternalPositionSnapshotReader` — `domain/service/kill_switch/reader/`.
* `AnomalyService` — `domain/service/anomaly/AnomalyService.java`.
* `ExitService` — `domain/service/deal/ExitService.java`.
* `StrategyService`, `StrategyValidator` — `domain/service/strategy/`.
* `StrategyActionInterpreter` — `domain/service/strategy/interpreter/`.
* `StrategyPriceResolver` — `domain/service/strategy/price/`. В докиах формально присутствует через `Resolved*Price`.
* `StrategyConditionEvaluator` — `domain/service/strategy/condition/`.
* `MarketPhaseService` — `domain/service/market/MarketPhaseService.java`. В докиах упоминается, в коде реализован.
* `TradeRuleValidator` — `domain/service/validator/TradeRuleValidator.java`. В докиах не упомянут (см. §5.3).
* `DealService`, `OrderService`, `AlgoOrderService`, `PositionService`, `BalanceService`, `ExchangeService`, `InstrumentService`, `CandleGroupService` — `domain/service/core/`.

Все: Persistent = no; Identity = singleton bean; Lifecycle = bean.

---

### 2.13. Search params (рантайм query objects)

> Упомянуты в OKX-mapping документах под именами `Get*SearchParams`.

* `AlgoOrderSearchParams`, `BalanceSearchParams`, `CandleSearchParams`, `InstrumentSearchParams`, `OrderSearchParams`, `PriceTickerSearchParams`, `TradeFillsSearchParams` — `domain/model/search_params/*.java`.
* В докиах под именами: `GetOrderDetailsSearchParams`, `GetOrdersPendingSearchParams`, `GetOrdersHistorySearchParams`, `GetOrdersHistoryArchiveSearchParams` (OKX_Order_mapping.md). В коде один общий `OrderSearchParams` (расхождение, см. §4).
* Persistent: no. Identity: нет. Lifecycle: транзитный. Контейнер: нет.

---

### 2.14. External market DTO (биржевые данные без классификации в `Auditable`-смысле)

#### `PriceTicker`

* Категория: external DTO (snapshot цены инструмента).
* Persistent: no; модель есть в коде, своей таблицы НЕТ.
* Identity: только `externalInstrumentId` + `timestamp` строкой.
* Lifecycle: транзитный.
* Где описана: `Справочник по доменным моделям.md`.
* Где в коде: `domain/model/trade/market/PriceTicker.java`; search: `PriceTickerSearchParams`.
* Используется кем: market data flow (по докам), но в кодовом graph практически не используется (см. §5.3).
* Контейнер: нет.

#### `TradeFill`, `TradeFillsArchive`

* Категория: external DTO (биржевые исполнения).
* Persistent: no; своих таблиц нет.
* Identity: нет.
* Lifecycle: транзитный.
* Где описана: `Deal.md`, `Справочник по доменным моделям.md`, `Сервисные команды.md` (через `REFRESH_FILLS`).
* Где в коде: `domain/model/core/trade_fill/TradeFill.java`, `TradeFillsArchive.java`; search: `TradeFillsSearchParams`.
* Используется кем: search params; реального refresh-flow по fills в коде НЕТ (`REFRESH_FILLS` команда в `ServiceCommandType` объявлена, но executor для неё в `ServiceCommandExecutor` НЕТ — см. §5.3).
* Контейнер: нет.

---

### 2.15. Market phase

#### `MarketPhase`

* Категория: external snapshot / runtime state (классификация рыночной фазы по инструменту).
* Persistent: no.
* Identity: только `instrumentId` (на инструмент — одна актуальная фаза).
* Lifecycle: транзитный (вычисляется `MarketPhaseService`).
* Где описана: `docs/domain/models/Strategy.md`, `Расчёт индикаторов и рыночных данных.md`, `Справочник по доменным моделям.md`.
* Где в коде: `domain/model/trade/market/MarketPhase.java`; используется `MarketPhaseService`.
* Используется кем: `DealContext.marketPhase`, `Strategy.getActiveDetails(marketPhaseType)`, `StrategyDetails.marketPhaseType`.
* Контейнер: нет.

---

### 2.16. Enum

> Энумы, явно поименованные в `docs/domain/`.

* `AlgoOrder.Status` — CREATED/PENDING/ACTIVE/CLOSED/FAILED. `Сервисные команды.md`, `Статусы торговых сущностей.md`, `AlgoOrder.md`.
* `AlgoOrder.Direction` — BUY/SELL. `AlgoOrder.md`, `OKX_AlgoOrder_mapping.md`.
* `AlgoOrder.CloseReason` — KILL_SWITCH. `AlgoOrder.md`.
* `AnomalyReport.Status` — CREATED/IN_PROGRESS/KILL_SWITCH_EXECUTED/COMPLETED/ERROR. `Справочник по доменным моделям.md`, `docs/spec/models/AnomalyReport.md` (вне scope этого отчёта).
* `AnomalyReport.Severity` — CRITICAL/NON_CRITICAL.
* `AttachedAlgoOrder.Status` — CREATED/ATTACHED/ACTIVE/CLOSED/FAILED.
* `AttachedAlgoOrder.Type` — ATTACHED_STOP_LOSS.
* `CandleGroup.Status` — CREATED/BACKFILL/SYNC/CHECK/REPAIR/ACTIVE/ERROR/DELETED.
* `ConditionType` — STOP_LOSS/TAKE_PROFIT/OCO_FULL/TRAILING_PERCENTS/TRAILING_VALUE/PARTIAL_TAKE_PROFIT/PARTIAL_STOP_LOSS. `AlgoOrder.md`.
* `Deal.Status` — PRECHECK/ENTRY_SUBMITTED/ENTRY_FINALIZED/PROTECTION_SWITCHED/MANAGING/EXIT_PENDING/CLOSED/ERROR. `Deal.md`, `FSM этапы сделки.md`, `Жизненный цикл сделки.md`.
* `Deal.CloseReason` — STOP_LOSS/TAKE_PROFIT/STRATEGY_EXIT/TIME_STOP/RISK_CONTROL/EMERGENCY_STOP/MANUAL/RECONCILIATION/PROTECTION_FAILED/LIQUIDATION.
* `DealEvent` — CHECK_ENTRY_INVARIANTS/PROCESS/CHECK_EXIT_INVARIANTS/RETRY/FAIL. В докиах называется по-разному (`event`/`сигнал`).
* `Exchange.Status` — CREATED/PENDING/ACTIVE/CLOSED/ERROR.
* `Instrument.Status` — CREATED/HOLD/SYNC/CANDLES_LOADING/ACTIVE/CLOSED/ERROR.
* `Instrument.MarginMode` — ISOLATED/CROSS.
* `MarketPhase.Type` — BULL_TREND/BEAR_TREND/RANGE/UNKNOWN.
* `Order.Status` — CREATED/PENDING/ACTIVE/COMPLETED/PARTIALLY_COMPLETED/CLOSED/FAILED.
* `Order.Type` — ENTRY/ENTRY_ATTACHED_STOP_LOSS.
* `Order.CloseReason` — KILL_SWITCH.
* `PhaseEntryPolicy` — FOLLOW_PHASE/CONTRARIAN/GRID/NO_TRADE.
* `Position.Status` — ACTIVE/CLOSED/ERROR.
* `Position.Side` — LONG/SHORT/NET.
* `Position.CloseReason` — STOP_LOSS/TAKE_PROFIT/TRAILING_STOP/STRATEGY_EXIT/MANUAL_CLOSE/EMERGENCY_CLOSE/LIQUIDATION/AUTO_DELEVERAGING/EXCHANGE_FORCED/UNKNOWN.
* `ServiceCommandType` — REFRESH_BALANCE, REFRESH_POSITION, CLOSE_POSITION, CREATE_ORDER, SUBMIT_ORDER, AMEND_ORDER, CANCEL_ORDER, REFRESH_ORDER, REFRESH_PENDING_ORDERS, REFRESH_ORDER_HISTORY, CREATE_ALGO_ORDER, SUBMIT_ALGO_ORDER, AMEND_ALGO_ORDER, CANCEL_ALGO_ORDER, REFRESH_ALGO_ORDER, REFRESH_ALGO_ORDERS, REFRESH_ALGO_ORDER_HISTORY, REFRESH_FILLS, FINALIZE_DEAL_ENTRY, FINALIZE_DEAL_EXIT, MARK_DEAL_CLOSED, MARK_DEAL_ERROR, EXECUTE_KILL_SWITCH. Большой каталог в `Сервисные команды.md`.
* `StrategyActionType` — CREATE/AMEND/CANCEL/CLOSE_FULL/CLOSE_PARTIAL. `Strategy.md`.
* `StrategyConditionRuleType` — NO_OPEN_POSITION/ENTRY_ORDER_FINALIZED/POSITION_OPENED/ATTACHED_STOP_LOSS_EXISTS/MAIN_PROTECTION_EXISTS/PROFIT_PERCENTS_REACHED/LOSS_PERCENTS_REACHED/RANGE_BREAKOUT_CONFIRMED/TREND_CHANGED/EFFICIENCY_BELOW_THRESHOLD.
* `StrategyPriceBaseType` — упомянут в `StrategyPricePlacement.baseType`.
* `StrategyPriceOffsetSide` — упомянут в `StrategyPricePlacement.offsetSide`.
* `StrategyStatus` — CREATED/ACTIVE/INACTIVE/DELETED.
* `StrategyStepType` — ENTRY/MAIN_PROTECTION/PROTECTION_ADJUSTMENT/PARTIAL_EXIT/GRID_ENTRY/GRID_MANAGEMENT/EXIT/FAIL_SAFE.
* `StrategyTradeDirection` — LONG/SHORT.
* `TriggerPriceType` — LAST/INDEX/MARK. `AlgoOrder.md`, `Strategy.md` (через `StrategyPricePlacement.marketPriceType`).

---

### 2.17. Documented only (упомянуто в docs/domain, в коде НЕТ)

> Эти "модели" описаны в `docs/domain/`, но соответствующих Java-классов в `src/main/java/` сейчас нет. При миграции в `docs/spec/` нужно решать: либо это будущая модель (плановая), либо устаревшая, либо описана под другим именем.

#### Runtime state / lifecycle (планируемое, отсутствует в коде)

* `DealActionState` — упоминается в `Сервисные команды.md`, `FSM этапы сделки.md`, `Жизненный цикл сделки.md`, `AlgoOrder.md`, `Audit.md`. Описывается как "учётная карточка действия стратегии в рамках цикла FSM". Нет ни класса, ни таблицы.
* `DealActionStateStatus` — спутник `DealActionState`. Нет в коде.
* `RuntimeTarget`, `TargetEntityType` — упомянуты в `Сервисные команды.md` как часть payload-каталога. Нет.

#### Calculator layer (вынесен в доки, ни одного класса в коде)

* `StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator`, `RiskCalculator` — `Калькуляторы действий стратегии.md`, `Strategy.md`. В коде реализована только цепочка `StrategyActionInterpreter → StrategyPriceResolver → ServiceCommandFactory`; калькуляторов как отдельных классов НЕТ.
* `CalculationContext`, `CalculatedStrategyAction`, `CalculatedPrice`, `CalculatedSize`, `CalculationError`, `StrategyPricePurpose` — все упомянуты в `Калькуляторы действий стратегии.md`. Их частичный аналог в коде — `Resolved*Price` records, но они не покрывают `CalculatedSize`/`CalculationContext`/`CalculationError`.

#### Risk layer (полностью отсутствует в коде)

* `RiskValidator`, `RiskValidationResult`, `RiskCheckResult`, `RiskCheckCode`, `RiskBlockResolver`, `RiskBlockAction`, `RiskDecision` — описаны в `Оценка рисков.md` и упоминаются повсеместно (`FSM этапы сделки.md`, `Сервисные команды.md`, `Калькуляторы действий стратегии.md`, `Жизненный цикл сделки.md`). В коде НЕТ ни одного класса с этими именами.

#### Market data jobs + результаты (полностью отсутствует)

* `CandleJob`, `InstrumentExternalRulesSyncJob`, `IndicatorJob`, `MarketStructureJob`, `MarketPhaseJob`, `EntryScannerJob` — job-классы в `Расчёт индикаторов и рыночных данных.md`. В коде НЕТ. `MarketPhase` (model) есть, `MarketPhaseService` (как сервис) есть, но это не job.
* `IndicatorValue`, `MarketStructure`, `MarketPriceLevel`, `MarketPriceData`, `InstrumentExternalRules`, `TimeFrame`, `IndicatorParams`, `MarketStructureParams`, `MarketPhaseParams`, `InstrumentType`, `ContractType` — модели результатов и параметров market-data. Нет в коде.
* `IndicatorService`, `MarketStructureService` — сервисы. Нет в коде.
* `MarketDataExpirationChecker` — `FSM этапы сделки.md`, `Сервисные команды.md`, `Расчёт индикаторов и рыночных данных.md`. Нет.
* `InstrumentExternalRulesExternalSnapshot`, `MarketPriceDataExternalSnapshot` — external snapshots под несуществующие модели. Нет.

#### Strategy settings (документированные строительные блоки внутри Strategy.md, без отдельных классов)

* `StrategyMarketPhaseSetting`, `StrategyIndicatorSetting`, `StrategyMarketStructureSetting`, `StrategyMarketDataExpiredSetting` — описаны в `Strategy.md` как "settings"-вкладки. В коде НЕТ — все strategy-настройки сейчас сидят в `StrategyDetails` и `StrategyStep`.
* `StrategyConditionSourceType`, `StrategyConditionOperator`, `StrategyConditionOperand` — `Strategy.md` описывает более богатую модель условий. В коде только плоский `StrategyConditionRule` + `StrategyConditionRuleType` (10 значений). Расхождение, см. §4.

#### Status resolvers под другими именами

* `OrderExternalStatusResolver` — в коде `OrderStatusResolver` (без `External`).
* `AlgoOrderExternalStatusResolver` — в коде нет; функцию выполняет `AlgoOrderSyncService` (`domain/service/deal/command/refresh/AlgoOrderSyncService.java`).

#### Exceptions

* `ExternalStatusException`, `ControlledExchangeException` — упомянуты в `Order.md`, `AlgoOrder.md`, `Статусы торговых сущностей.md`, `Сервисные команды.md`. В коде НЕТ (см. §5.3).
* `RetryError`, `RetryPolicyService` — `Сервисные команды.md`. Нет.

#### Audit / history

* `ServiceCommandExecutionHistory` — `Сервисные команды.md`, `Аудит и история исполнения.md`, `Справочник по доменным моделям.md`. Нет ни класса, ни таблицы. В `V1..V7` миграциях нет таблицы `service_command_execution_history`.

#### Misc

* `ExchangeAccount` — упомянут только в `Статусы торговых сущностей.md` (введение). Нет в коде.
* `DealOpeningService` — `Жизненный цикл сделки.md`. В коде есть `DealOrchestrator` (другое имя, другие границы).
* `DealOrchestratorJob` — `Жизненный цикл сделки.md`. В коде `DealOrchestrator` (Spring `@Service`, не Job).
* `RefreshExecutor` (как абстрактный) — `Открытые вопросы по движку.md`. В коде есть конкретные `Refresh*Executor`, но абстрактного `RefreshExecutor` НЕТ.
* `EntryReason`, `entryStepType`, `ShutdownReason` — упомянуты в `Deal.md`. В Java-`Deal` этих enum'ов нет (есть только `Status`, `CloseReason`).

---

## 3. Сложно классифицируемые модели

Модели, у которых жанровая категория не очевидна, или у которых описание в docs/domain расходится с кодом, или которые отсутствуют в одной из сторон.

| Имя | Возможные категории | Почему неоднозначно |
|------|---------------------|----------------------|
| `Auditable` | value object / mixin | Не самостоятельная модель: 6 audit-полей. Скорее технический super-type. В docs упоминается в `Справочник по доменным моделям.md` как самостоятельная сущность. |
| `Candle` | domain entity / external DTO | Имеет своё `@Entity` и таблицу, но в доменной модели по сути representing внешние рыночные данные; lifecycle определяется `CandleGroup`. |
| `CandleGroup` | aggregate root / domain entity | Имеет полноценный FSM-статус (8 значений), но FK к `Instrument` обязателен и доменно живёт только в его составе. |
| `Condition` (+7 subtypes) | value object / strategy config part | Polymorphic value-object, но через `JSONB` хранится внутри `algo_orders`. Subtypes в коде — фабричные конструкторы, без своего persistent-хранения. |
| `DealEvent` | enum / domain command | Это enum, но семантически он играет роль "команды FSM", т.е. ближе к command payload. |
| `KillSwitchResult`, `StateSnapshot` | runtime state / external snapshot | В коде это рантайм-объекты для kill-switch flow; для AnomalyReport их сериализуют в JSON-string-поля (`internalBefore`/`externalBefore`/`internalAfter`/`externalAfter`). Жанр зависит от точки взгляда. |
| `MarketPhase` | external snapshot / runtime state / value object | Это снимок состояния, но не приходит с биржи напрямую — вычисляется `MarketPhaseService`. Identity = `instrumentId`; lifecycle транзитный. |
| `PriceTicker`, `TradeFill`, `TradeFillsArchive` | external DTO / документ | В `Справочник по доменным моделям.md` они называются моделями; в коде это plain DTO без entity/таблицы, не используются в активном flow (см. §5.3 ниже). |
| `Resolved*Price` (4 шт.) | calculation result | В коде это четыре `record`-обёртки, не покрывающие документированные `CalculatedPrice`/`CalculatedSize`/`CalculatedStrategyAction`. Документация и код расходятся по структуре результатов. |
| `ServiceCommand` | command payload / runtime context | Это не payload, а обёртка для payload. По коду — обычный POJO с `payload: ServiceCommandPayload`. В докиах назван "command", но фактически тоже DTO. |
| `StrategyAction` (interface + 3 реализации) | strategy config part / domain entity | Имеет стабильный `Long id` (используется как FK-like в `orders.strategy_action_id`/`algo_orders.strategy_action_id`), но сериализуется полиморфно внутрь `strategy_steps.actions JSONB`. Не имеет своего `@Entity`/таблицы — embedded полиморфия. |
| `StrategyDetails` vs `StrategyDetail` | domain entity / naming clash | В докиах называется `StrategyDetail` (без `s`), в коде — `StrategyDetails`. Это одна и та же сущность, но текстовое расхождение. |
| `StateHandler` + 8 implementations | runtime service / lifecycle policy | Не "модель", но в `docs/domain` явно поименованы (`ExitPendingHandler`, `ErrorHandler` и др.). Жанр — lifecycle handler. |

---

## 4. Замеченные паттерны

### 4.1. Семейства моделей

* **Семейство `*ExternalSnapshot`** (7 классов): `AlgoOrderExternalSnapshot`, `OrderExternalSnapshot`, `AttachedAlgoOrderExternalSnapshot`, `PositionExternalSnapshot`, `BalanceContainerExternalSnapshot`, `BalanceExternalSnapshot`, `InstrumentExternalSnapshot`. Все — Persistent=no, Identity=нет, Lifecycle=транзитный. Один и тот же шаблон: maps OKX response → доменный snapshot → потом передаётся в `*StatusResolver` и `Refresh*Executor`. Хорошо повторяющийся жанр.
* **Семейство `Resolved*Price`** (4 record'а): `ResolvedOrderPrice`, `ResolvedAlgoOrderPrice`, `ResolvedAttachedProtectionPrice`, `ResolvedPositionClosePrice`. Все — Persistent=no, immutable record, выходы `StrategyPriceResolver`. Очень компактная семья.
* **Семейство `*CommandPayload`** (9 классов): `CreateOrderCommandPayload`, `SubmitOrderCommandPayload`, `AmendOrderCommandPayload`, `CancelOrderCommandPayload`, `CreateAlgoOrderCommandPayload`, `SubmitAlgoOrderCommandPayload`, `AmendAlgoOrderCommandPayload`, `CancelAlgoOrderCommandPayload`, `ClosePositionCommandPayload`. Все реализуют `ServiceCommandPayload`. Все — Persistent=no, Identity=нет, всё через `ServiceCommandFactory`/`ServiceCommandExecutor`. По симметрии в коде НЕТ `Refresh*CommandPayload`, хотя `REFRESH_*` команды есть.
* **Семейство `*Executor`** (≈15 классов в `domain/service/deal/command/*/`): `Create/Submit/Amend/Cancel/Close/Refresh*` для Order/AlgoOrder/Position. Жанр — runtime-сервис; не модель, но повторяющийся pattern.
* **Семейство `*StatusResolver`** (3 класса в коде): `OrderStatusResolver`, `PositionStatusResolver`, `StrategyStatusResolver`. В докиах называются `*ExternalStatusResolver` (с приставкой `External`), плюс упомянут несуществующий `AlgoOrderExternalStatusResolver`. Расхождение по имени и по покрытию.
* **Семейство `StateHandler` implementations** (8 классов): по одному handler'у на каждый `Deal.Status`. Полное покрытие.
* **Семейство `Condition`-subtypes** (7 классов): `OcoFullCondition`, `StopLossCondition`, `TakeProfitCondition`, `TrailingPercentsCondition`, `TrailingValueCondition`, `PartialStopLossCondition`, `PartialTakeProfitCondition`. Полное покрытие `ConditionType`.
* **Семейство `Strategy*Action` implementations** (3 класса): `StrategyOrderAction`, `StrategyAlgoOrderAction`, `StrategyPositionAction`. Полная иерархия для `StrategyAction`.
* **Семейство `*Service` (core)** (8 классов в `domain/service/core/`): `DealService`, `OrderService`, `AlgoOrderService`, `PositionService`, `BalanceService`, `ExchangeService`, `InstrumentService`, `CandleGroupService`. Чистые domain-сервисы, не модели.

### 4.2. Сквозные паттерны

* **`Auditable` mixin** — почти все доменные модели расширяют его (включая `*ExternalSnapshot`). Через `AuditableEntity` зеркалится в persistence.
* **`internalId` + `externalId` пара** на всех aggregate roots, что общаются с биржей: `Deal`, `Order`, `AlgoOrder`, `Position`, `Strategy`, `AnomalyReport`, `Instrument`, `Exchange`, `AttachedAlgoOrder`.
* **`Status` + `CloseReason` пара** на runtime-сущностях: `Deal`, `Order`, `AlgoOrder`, `Position` (`AttachedAlgoOrder` имеет только `Status`).
* **Полиморфное хранение в `JSONB`** — `algo_orders.condition`, `strategy_steps.actions`, `strategy_steps.condition`. Это вычисляемое богатство модели не отражено в схеме BD напрямую.
* **Стабильный `strategy_action_id` как cross-cutting key** — миграция `V7` добавила `strategy_action_id` в `orders` и `algo_orders` с UK по `(deal_id, strategy_action_id)`. Это явная ссылка на immutable конфигурацию стратегии без FK.
* **"Statuses by entity" документ** — `Статусы торговых сущностей.md` собирает status-таксономию из всех agg-roots вместе. По коду это можно полностью пересобрать из enum'ов и `*StatusResolver`.

### 4.3. Странности и расхождения между docs/domain и кодом

* **Расхождение в именах:**
  * `StrategyDetail` (docs) vs `StrategyDetails` (code, и таблица `strategy_details`).
  * `OrderExternalStatusResolver` / `AlgoOrderExternalStatusResolver` (docs) vs `OrderStatusResolver` (code, без `External`) / `AlgoOrderSyncService` (code, не resolver).
  * `KillSwitch` (docs) vs `KillSwitchService` (code).
  * `DealOpeningService` / `DealOrchestratorJob` (docs) vs `DealOrchestrator` (code Service).
  * `Get*SearchParams` (docs, 4 параметрических класса в `OKX_Order_mapping.md`) vs `OrderSearchParams` (code, один общий).

* **Доменные модели, описанные в `docs/domain`, но отсутствующие в коде** (объёмный список — см. §2.17):
  * Полностью отсутствуют: `RiskValidator`, `RiskValidationResult`, `RiskCheckResult`, `RiskCheckCode`, `RiskBlockResolver`, `RiskBlockAction`, `RiskDecision`, `StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator`, `RiskCalculator`, `CalculationContext`, `CalculatedStrategyAction`, `CalculatedPrice`, `CalculatedSize`, `CalculationError`, `DealActionState`, `DealActionStateStatus`, `ServiceCommandExecutionHistory`, `MarketDataExpirationChecker`, `RetryPolicyService`, `RetryError`, `ExternalStatusException`, `ControlledExchangeException`.
  * Market data layer полностью описан, но в коде ни одного класса: `CandleJob`, `IndicatorJob`, `MarketStructureJob`, `MarketPhaseJob`, `EntryScannerJob`, `InstrumentExternalRulesSyncJob`, `IndicatorValue`, `MarketStructure`, `MarketPriceLevel`, `MarketPriceData`, `InstrumentExternalRules`, `TimeFrame`, `IndicatorParams`, `MarketStructureParams`, `MarketPhaseParams`, `IndicatorService`, `MarketStructureService`, `InstrumentExternalRulesExternalSnapshot`, `MarketPriceDataExternalSnapshot`.
  * Дополнительные strategy-блоки: `StrategyMarketPhaseSetting`, `StrategyIndicatorSetting`, `StrategyMarketStructureSetting`, `StrategyMarketDataExpiredSetting`, `StrategyConditionSourceType`, `StrategyConditionOperator`, `StrategyConditionOperand`.

* **Модели в коде, не описанные (или едва описанные) в `docs/domain/`:**
  * `DealAggregateService` — есть в коде, не упомянут в `docs/domain/`.
  * `DealEvent` enum (5 значений) — в `docs/domain/` упоминается как "event", но без полного списка значений.
  * `TransitionResult` — в `docs/domain/` не описан как модель.
  * `TradeRuleValidator` — `domain/service/validator/TradeRuleValidator.java`, в `docs/domain` не упомянут.
  * Все 7 `search_params`-классы — в `docs/domain/` только частично описаны через mapping-документы (и под другими именами).
  * `OcoFullCondition`, `StopLossCondition`, `TakeProfitCondition`, `PartialStopLossCondition`, `PartialTakeProfitCondition`, `TrailingPercentsCondition`, `TrailingValueCondition` — конкретные подклассы `Condition`. В `docs/domain/models/AlgoOrder.md` упоминается полиморфная иерархия, но без полного списка subtypes; шаблон именования отличается (`OCO_FULL` enum value vs `OcoFullCondition` class).
  * `PriceTicker`, `TradeFill`, `TradeFillsArchive` — в коде есть, в `docs/domain/` упомянуты только в `Справочник по доменным моделям.md` (минимально).
  * `ResolvedOrderPrice`, `ResolvedAlgoOrderPrice`, `ResolvedAttachedProtectionPrice`, `ResolvedPositionClosePrice` — в `docs/domain/` обозначены под другими именами (`CalculatedPrice` и т.п.) и не как records.
  * `InstrumentExternalSnapshot` — есть в коде, в `docs/domain/` упомянут через mapping-документ.

* **Несовпадение между `ServiceCommandType` и наличием executor'а:**
  * `REFRESH_FILLS` — есть значение в `ServiceCommandType`, но в `ServiceCommandExecutor.execute(...)` нет ветки/executor'а (по чтению `ServiceCommandExecutor.java` и наличию executor-классов в `command/refresh/`).
  * `REFRESH_PENDING_ORDERS`, `REFRESH_ORDER_HISTORY`, `REFRESH_ALGO_ORDERS`, `REFRESH_ALGO_ORDER_HISTORY` — есть в enum, в коде явных executor-классов под этими именами НЕТ (есть только `RefreshOrderExecutor`, `RefreshAlgoOrderExecutor`).
  * `FINALIZE_DEAL_ENTRY`, `FINALIZE_DEAL_EXIT`, `MARK_DEAL_CLOSED`, `MARK_DEAL_ERROR` — есть в enum, в коде нет явных executor-классов.

---

## 5. Сводная таблица

| Имя | Категория | Persistent | Identity | Lifecycle | Контейнер |
|------|-----------|------------|----------|-----------|-----------|
| AlgoOrder | aggregate root | yes (`algo_orders`) | id + internalId + externalId | own FSM | (Deal — formal) |
| AnomalyReport | aggregate root | yes (`anomaly_reports`) | id + internalId | own FSM | — |
| BalanceContainer | aggregate root | yes (`balance_containers`) | id + exchangeId (UK) | — | — |
| Deal | aggregate root | yes (`deals`) | id + internalId | own FSM | — |
| Exchange | aggregate root | yes (`exchanges`) | id + internalId + name | own FSM | — |
| Instrument | aggregate root | yes (`instruments`) | id + internalId + (exchange,external) | own FSM | — |
| Order | aggregate root | yes (`orders`) | id + internalId + externalId | own FSM | (Deal — formal) |
| Position | aggregate root | yes (`positions`) | id + internalId + externalId | own FSM | (Deal — formal) |
| Strategy | aggregate root | yes (`strategies`) | id + internalId + version | own FSM (append-only) | — |
| AttachedAlgoOrder | domain entity | yes (`attached_algo_orders`) | id + internalId | only inside Order | Order |
| Balance | domain entity | yes (`balances`) | id + UK (container, currency) | only inside container | BalanceContainer |
| Candle | domain entity | yes (`candles`) | id + UK (group, ts) | only inside group | CandleGroup |
| CandleGroup | domain entity | yes (`candle_groups`) | id + UK (instr, tf) | own status (~8) | Instrument |
| StrategyDetails | domain entity | yes (`strategy_details`) | id + UK (strategy, phase) | only inside Strategy | Strategy |
| StrategyStep | domain entity | yes (`strategy_steps`) | id + UK (details, status, idx) | only inside Details | StrategyDetails |
| Auditable | value object (mixin) | embedded | — | with parent | many |
| Condition (+ 7 subtypes) | value object | embedded (JSONB) | — | with AlgoOrder | AlgoOrder |
| StopLossSettings | value object | embedded | — | with action | StrategyAlgoOrderAction / StrategyAttachedProtectionSettings |
| StrategyAttachedProtectionSettings | value object | embedded | — | with action | StrategyOrderAction |
| StrategyCondition | value object | embedded (JSONB) | — | with step | StrategyStep |
| StrategyConditionRule | value object | embedded | level only | with condition | StrategyCondition |
| StrategyPricePlacement | value object | embedded | — | with action | StrategyOrderAction (and others) |
| Trailing | value object | embedded | — | with Condition | Condition |
| TrailingSettings | value object | embedded | — | with action | StrategyAlgoOrderAction |
| Trigger | value object | embedded | — | with Condition | Condition |
| TriggerPrice | value object | embedded | — | with Trigger/Trailing | Trigger / Trailing |
| DealContext | runtime context | no | — | transient | — |
| KillSwitchResult | runtime state | no | — | transient | — |
| StateSnapshot | runtime state | no | — | transient | — |
| TransitionResult | runtime state | no | — | transient | — |
| StrategyAction (interface) | strategy config part | embedded (JSONB) | id | with step | StrategyStep |
| StrategyOrderAction | strategy config part | embedded | id | with step | StrategyStep |
| StrategyAlgoOrderAction | strategy config part | embedded | id | with step | StrategyStep |
| StrategyPositionAction | strategy config part | embedded | id | with step | StrategyStep |
| ResolvedAlgoOrderPrice | calculation result | no | — | transient | — |
| ResolvedAttachedProtectionPrice | calculation result | no | — | transient | — |
| ResolvedOrderPrice | calculation result | no | — | transient | — |
| ResolvedPositionClosePrice | calculation result | no | — | transient | — |
| ServiceCommand | command payload | no | — | transient | — |
| ServiceCommandPayload (interface) | command payload | no | — | transient | — |
| CreateOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| SubmitOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| AmendOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| CancelOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| CreateAlgoOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| SubmitAlgoOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| AmendAlgoOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| CancelAlgoOrderCommandPayload | command payload | no | — | transient | ServiceCommand |
| ClosePositionCommandPayload | command payload | no | — | transient | ServiceCommand |
| AlgoOrderExternalSnapshot | external snapshot | no | — | transient | — |
| AttachedAlgoOrderExternalSnapshot | external snapshot | no | — | transient | OrderExternalSnapshot |
| BalanceContainerExternalSnapshot | external snapshot | no | — | transient | — |
| BalanceExternalSnapshot | external snapshot | no | — | transient | BalanceContainerExternalSnapshot |
| InstrumentExternalSnapshot | external snapshot | no | — | transient | — |
| OrderExternalSnapshot | external snapshot | no | — | transient | — |
| PositionExternalSnapshot | external snapshot | no | — | transient | — |
| OrderStatusResolver | status resolver | no (bean) | singleton | bean | — |
| PositionStatusResolver | status resolver | no (bean) | singleton | bean | — |
| StrategyStatusResolver | status resolver | no (bean) | singleton | bean | — |
| StateHandler (interface) + 8 impls | lifecycle handler | no (bean) | singleton | bean | DealStateMachine |
| DealOrchestrator | runtime service | no (bean) | singleton | bean | — |
| DealContextService | runtime service | no (bean) | singleton | bean | — |
| DealStateMachine | runtime service | no (bean) | singleton | bean | — |
| ServiceCommandFactory | runtime service | no (bean) | singleton | bean | — |
| ServiceCommandExecutor | runtime service | no (bean) | singleton | bean | — |
| KillSwitchService | runtime service | no (bean) | singleton | bean | — |
| AnomalyService | runtime service | no (bean) | singleton | bean | — |
| ExitService | runtime service | no (bean) | singleton | bean | — |
| MarketPhaseService | runtime service | no (bean) | singleton | bean | — |
| StrategyService | runtime service | no (bean) | singleton | bean | — |
| StrategyActionInterpreter | runtime service | no (bean) | singleton | bean | — |
| StrategyConditionEvaluator | runtime service | no (bean) | singleton | bean | — |
| StrategyPriceResolver | runtime service | no (bean) | singleton | bean | — |
| MarketPhase | external snapshot / runtime state | no | instrumentId | transient | — |
| PriceTicker | external DTO | no | — | transient | — |
| TradeFill | external DTO | no | — | transient | — |
| TradeFillsArchive | external DTO | no | — | transient | — |
| AlgoOrderSearchParams | runtime query object | no | — | transient | — |
| BalanceSearchParams | runtime query object | no | — | transient | — |
| CandleSearchParams | runtime query object | no | — | transient | — |
| InstrumentSearchParams | runtime query object | no | — | transient | — |
| OrderSearchParams | runtime query object | no | — | transient | — |
| PriceTickerSearchParams | runtime query object | no | — | transient | — |
| TradeFillsSearchParams | runtime query object | no | — | transient | — |
| AlgoOrder.Status / .Direction / .CloseReason | enum | embedded | — | — | AlgoOrder |
| AnomalyReport.Status / .Severity | enum | embedded | — | — | AnomalyReport |
| AttachedAlgoOrder.Status / .Type | enum | embedded | — | — | AttachedAlgoOrder |
| CandleGroup.Status | enum | embedded | — | — | CandleGroup |
| ConditionType | enum | embedded | — | — | AlgoOrder/Condition |
| Deal.Status / .CloseReason | enum | embedded | — | — | Deal |
| DealEvent | enum | no | — | — | FSM contract |
| Exchange.Status | enum | embedded | — | — | Exchange |
| Instrument.Status / .MarginMode | enum | embedded | — | — | Instrument |
| MarketPhase.Type | enum | no | — | — | MarketPhase |
| Order.Status / .Type / .CloseReason | enum | embedded | — | — | Order |
| PhaseEntryPolicy | enum | embedded | — | — | StrategyDetails |
| Position.Status / .Side / .CloseReason | enum | embedded | — | — | Position |
| ServiceCommandType | enum | no | — | — | ServiceCommand |
| StrategyActionType | enum | embedded (JSONB) | — | — | StrategyAction |
| StrategyConditionRuleType | enum | embedded | — | — | StrategyConditionRule |
| StrategyPriceBaseType | enum | embedded | — | — | StrategyPricePlacement |
| StrategyPriceOffsetSide | enum | embedded | — | — | StrategyPricePlacement |
| StrategyStatus | enum | embedded | — | — | Strategy |
| StrategyStepType | enum | embedded | — | — | StrategyStep |
| StrategyTradeDirection | enum | embedded (JSONB) | — | — | StrategyOrderAction |
| TriggerPriceType | enum | embedded | — | — | TriggerPrice / settings |
| DealActionState (+ DealActionStateStatus) | documented only (планируемое) | — | — | — | — |
| RuntimeTarget, TargetEntityType | documented only | — | — | — | — |
| StrategyActionCalculator / PriceCalculator / SizeCalculator / RiskCalculator | documented only | — | — | — | — |
| CalculationContext / CalculationError / CalculatedStrategyAction / CalculatedPrice / CalculatedSize / StrategyPricePurpose | documented only | — | — | — | — |
| RiskValidator / RiskValidationResult / RiskCheckResult / RiskCheckCode / RiskBlockResolver / RiskBlockAction / RiskDecision | documented only | — | — | — | — |
| CandleJob / IndicatorJob / MarketStructureJob / MarketPhaseJob / EntryScannerJob / InstrumentExternalRulesSyncJob / IndicatorService / MarketStructureService | documented only | — | — | — | — |
| IndicatorValue / MarketStructure / MarketPriceLevel / MarketPriceData / InstrumentExternalRules / TimeFrame / IndicatorParams / MarketStructureParams / MarketPhaseParams / InstrumentType / ContractType | documented only | — | — | — | — |
| InstrumentExternalRulesExternalSnapshot / MarketPriceDataExternalSnapshot | documented only | — | — | — | — |
| MarketDataExpirationChecker | documented only | — | — | — | — |
| StrategyMarketPhaseSetting / StrategyIndicatorSetting / StrategyMarketStructureSetting / StrategyMarketDataExpiredSetting | documented only | — | — | — | — |
| StrategyConditionSourceType / StrategyConditionOperator / StrategyConditionOperand | documented only | — | — | — | — |
| OrderExternalStatusResolver / AlgoOrderExternalStatusResolver | documented only (другое имя в коде) | — | — | — | — |
| ExternalStatusException / ControlledExchangeException | documented only | — | — | — | — |
| RetryPolicyService / RetryError | documented only | — | — | — | — |
| ServiceCommandExecutionHistory | documented only | — | — | — | — |
| ExchangeAccount | documented only | — | — | — | — |
| DealOpeningService / DealOrchestratorJob | documented only (другое имя в коде) | — | — | — | — |
| RefreshExecutor (абстрактный) | documented only | — | — | — | — |
| EntryReason / entryStepType / ShutdownReason | documented only (Deal enums) | — | — | — | — |
| AnomalyJob | documented only | — | — | — | — |

---

## 6. Что НЕ входило в задачу (для прозрачности)

* Не предложена структура `docs/spec/models/`.
* Не предложен порядок миграции.
* Не описаны `docs/spec/` (там пока только `AnomalyReport.md`) и `docs/api/`.
* Не аудированы `*Entity`-классы построчно (только проверено наличие `@Entity`/`@Table` и связь с таблицами Flyway).
* Не описаны `client/` (OKX REST DTO), `mapping/`, `rest/` (controllers), `exception/`, `config/`, `persistence/repository/`/`/service/`/`/specification/`, `util/`.

---

## 7. Summary

* **Всего именованных моделей в каталоге: 154** (с учётом subtypes, enums, all `*Payload`, all `Resolved*Price`, всех `*ExternalSnapshot`, всех handlers/resolvers/executors, `documented only`-моделей).
* **Aggregate roots: 9** (`AlgoOrder`, `AnomalyReport`, `BalanceContainer`, `Deal`, `Exchange`, `Instrument`, `Order`, `Position`, `Strategy`).
* **Domain entities (вложенные с таблицей): 6** (`AttachedAlgoOrder`, `Balance`, `Candle`, `CandleGroup`, `StrategyDetails`, `StrategyStep`).
* **Value objects: ~12** (`Condition` + 7 subtypes, `StopLossSettings`, `StrategyAttachedProtectionSettings`, `StrategyCondition`, `StrategyConditionRule`, `StrategyPricePlacement`, `Trailing`, `TrailingSettings`, `Trigger`, `TriggerPrice`, `Auditable`).
* **Runtime context: 1** (`DealContext`).
* **Runtime state: 3** (`KillSwitchResult`, `StateSnapshot`, `TransitionResult`).
* **Strategy config parts: 4** (`StrategyAction` + 3 implementations).
* **Calculation result: 4** (`Resolved*Price`).
* **Command payload: 11** (`ServiceCommand`, `ServiceCommandPayload`, 9 concrete payloads).
* **External snapshots: 7** (+ 4 вложенных в `AlgoOrderExternalSnapshot`).
* **Status resolvers: 3** (`OrderStatusResolver`, `PositionStatusResolver`, `StrategyStatusResolver`).
* **Lifecycle handlers: 9** (`StateHandler` interface + 8 implementations).
* **Runtime services / executors / readers: ~35** (включая 4 close-executor'а, 4 order-executor'а, 4 algo-executor'а, 8 refresh/sync-executor'ов, и общие сервисы).
* **External DTO: 4** (`MarketPhase`, `PriceTicker`, `TradeFill`, `TradeFillsArchive`).
* **Search params: 7**.
* **Enums: 30** (топ-level + nested).
* **Documented only (нет в коде): ~55**, в том числе целые слои: calculator-layer (~6), risk-layer (~7), market-data jobs (~7), market-data модели (~11), strategy settings (~4), strategy condition mini-grammar (~3), audit/history (~1), exception-семейство (~2), naming-clashes (~5), Deal-enums (~3).
* **Сложно классифицируемых моделей (§3): 14**.
* **Замеченных паттернов / странностей: 9 семейств + 8 сквозных + 3 группы расхождений** (см. §4).

Главные наблюдения:
1. **Объём `documented only`-моделей (~55) сопоставим с объёмом моделей в коде (~99 классов в `domain/model/`).** Это значит: либо doc-страницы описывают будущее (плановое), либо историческое (устаревшее), либо то и другое одновременно. Без отдельного решения по каждой группе миграция в `docs/spec/` затащит туда и существующее, и несуществующее.
2. **Целые слои документации не реализованы:** risk-layer (~7 классов), calculator-layer (~6), market-data jobs + результаты (~18), `DealActionState` + `ServiceCommandExecutionHistory` (audit-layer). Это огромная разница между специфицированной системой и кодом.
3. **Расхождения по именам (≥5):** `StrategyDetail`/`StrategyDetails`, `*ExternalStatusResolver`/`*StatusResolver`, `KillSwitch`/`KillSwitchService`, `DealOrchestratorJob`/`DealOrchestrator`, `Get*SearchParams`/`*SearchParams`.
4. **Strategy.md фактически описывает несколько десятков моделей.** Сейчас в одном файле живут: `Strategy`, `StrategyDetails`, `StrategyStep`, все три `Strategy*Action`, `StrategyCondition`, `StrategyConditionRule`, `StrategyAttachedProtectionSettings`, `StopLossSettings`, `TrailingSettings`, `StrategyPricePlacement` + большое количество "settings"-блоков (`StrategyMarketPhaseSetting` и т.п.), которых в коде нет.
5. **`ServiceCommandType` enum (23 значения)** широко расходится с реальным набором executor-классов. Часть значений — для будущего flow (`REFRESH_FILLS`, `FINALIZE_DEAL_*`, `MARK_DEAL_*`).
6. **Подсемья `Resolved*Price` (4 record'а)** — это компактная реальная реализация документированной разветвлённой иерархии "калькуляторов". Если этот стиль закрепить, calculator-layer из docs можно частично заменить на `*Resolver` + `Resolved*`-records.
