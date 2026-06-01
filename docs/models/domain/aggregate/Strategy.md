# Strategy

## На какой вопрос отвечает этот файл

Что это за торговая модель `Strategy` (immutable strategy-layer):
структура всего strategy-tree, енумы, условия, действия, правила
валидации.

Административный статус и его эффекты — в
`docs/lifecycles/Strategy.md`.

## Назначение и главная идея

`Strategy` — главный immutable-контейнер торговой стратегии. Хранит
торговые правила, условия, настройки расчёта рыночных данных и
**ожидаемые действия**. Говорит **что** должно быть создано /
изменено / отменено и **при каких условиях**, и какие индикаторы /
структуры / фазы рынка должны быть заранее подготовлены. FSM сделки
решает, **когда именно** интерпретировать эти правила.

## Архитектурные инварианты strategy-layer

- **Immutable.** Стратегия создаётся как immutable-конфигурация; для
  изменения правил создаётся новая стратегия, а не редактируется
  существующая. Относится ко всем вложенным типам ниже.
- **Все хранимые модели наследуются от `Auditable`** (единый аудит
  технических дат).
- **Жизненный цикл задаёт `Strategy.Status`** — у вложенных
  immutable-настроек отдельных статусов нет. Статус административный,
  не runtime-свежесть данных (см. `docs/lifecycles/Strategy.md`).
- **Стратегия не хранит `ServiceCommand`** и не является command
  queue; не управляет runtime-сущностями напрямую.
- **`Order`/`AlgoOrder`/`Position` не хранят `strategyActionId`,
  `strategyActionKey`, `role`, `level`** — runtime-связь
  `StrategyAction → runtime entity` хранится в `DealActionState`
  через `strategyActionId` и `RuntimeTarget` (см. §Связь с
  DealActionState).
- **`key` нужен только у `StrategyAction`** (через него работает
  `targetActionKey`); у settings `key` не используется — связи между
  settings объектные.
- **Direct partial close запрещён** как постоянный инвариант (см.
  `docs/rules/no-partial-close.md`): `StrategyPositionAction` только
  `CLOSE_FULL`; частичное уменьшение — только через
  `StrategyOrderAction`/`StrategyAlgoOrderAction` с
  position-reducing-only semantics.
- **`positionReducingOnly`** — доменное намерение strategy-layer;
  OKX `reduceOnly` — только client/adapter field, в strategy-модели
  не используется как имя.
- **Risk-check** — после расчёта `CalculatedStrategyAction` и до
  `ServiceCommandFactory`; не выполняется перед `REFRESH_*`/search/
  history (они не создают новый риск).
- **`CalculationContext`** собирается отдельно для каждого
  `StrategyAction` (один action = один свежий context; не
  переиспользуется на весь `StrategyStep`).
- Устаревшие рыночные данные **не** меняют `Strategy.Status` —
  свежесть проверяет `MarketDataExpirationChecker`; срок —
  `expirationDuration` в settings; поведение —
  `StrategyStep.marketDataExpiredSetting`.

## Strategy (root)

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID. |
| `internalId` | `String` | Безопасный внешний/межсервисный id. |
| `instrumentId` | `Long` | Инструмент стратегии. |
| `name` | `String` | Человекочитаемое имя. |
| `status` | `Status` | Административный статус (см. lifecycle). |
| `marketPhaseSetting` | `StrategyMarketPhaseSetting` | Настройка расчёта фазы рынка (живёт на уровне Strategy, т.к. фаза нужна **до** выбора `StrategyDetail`). |
| `details` | `List<StrategyDetail>` | Ровно одна detail на один `MarketPhase.Type` (инвариант). |

`Status`: `CREATED`, `ACTIVE`, `INACTIVE`, `DELETED` (значения и
эффекты — в `docs/lifecycles/Strategy.md`). Одна
`StrategyMarketPhaseSetting` описывает алгоритм классификации рынка во
все `MarketPhase.Type` (`BULL_TREND`/`BEAR_TREND`/`RANGE`/`UNKNOWN`);
`MarketPhaseJob` сохраняет один актуальный результат, `EntryScannerJob`
выбирает detail по `MarketPhase.Type → StrategyDetail.marketPhaseType`
(jobs — форвард-заметки в `.claude/work/questions/tasks/strategy.md`).

## Настройки рыночных данных (разделы)

### StrategyMarketPhaseSetting
`timeframe: TimeFrame`, `params: MarketPhaseParams`,
`indicatorSettings: List<StrategyIndicatorSetting>`,
`marketStructureSettings: List<StrategyMarketStructureSetting>`,
`expirationDuration: Duration` (срок свежести последней `MarketPhase`).

### MarketPhaseParams
`algorithmType: AlgorithmType` (`STRUCTURE_ONLY` / `INDICATORS_ONLY` /
`STRUCTURE_AND_INDICATORS`), `minTrendScore`, `minRangeScore`,
`confirmationBars`.

### StrategyIndicatorSetting
`timeframe`, `indicatorType: IndicatorValue.Type`, `params:
IndicatorParams`, `destiny: Destiny`, `expirationDuration`. `Destiny`:
`MARKET_PHASE`, `ENTRY_CONDITION`, `ACTION_PRICE`, `PROTECTION`,
`EXIT_CONDITION`. Используется внутри `StrategyMarketPhaseSetting`
(для фазы) и внутри `StrategyDetail` (после выбора детали).

### IndicatorParams (abstract) + наследники
База: `id`, `indicatorType: IndicatorValue.Type`. Наследники:
`AtrParams(period)`, `EmaParams(period)`, `RsiParams(period)`,
`MacdParams(fastPeriod, slowPeriod, signalPeriod)`,
`BollingerBandsParams(period, deviationMultiplier)`,
`StochasticParams(kPeriod, dPeriod, smoothPeriod)`,
`ObvParams(enabled)`. Волатильность отдельной сущностью не
моделируется — через индикаторы ATR / Bollinger bandwidth.

### StrategyMarketStructureSetting
`timeframe`, `structureType: MarketStructure.Type`, `params:
MarketStructureParams`, `destiny: Destiny`, `expirationDuration`.
`Destiny`: те же 5 значений, что у indicator (MARKET_PHASE /
ENTRY_CONDITION / ACTION_PRICE / PROTECTION / EXIT_CONDITION).

### MarketStructureParams
`lookbackBars`, `minTouches`, `minRangeWidthPercents`,
`maxRangeWidthPercents`, `breakoutBufferPercents`,
`breakoutConfirmationBars`, `swingLookbackBars`.

## StrategyDetail (раздел)

Набор торговых правил для конкретной фазы рынка.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID. |
| `marketPhaseType` | `MarketPhase.Type` | Для какой фазы работает detail. |
| `phaseEntryPolicy` | `PhaseEntryPolicy` | Как торгуем в этой фазе. |
| `riskPerTradePercent` | `BigDecimal` | Риск на сделку, % от капитала. |
| `maxLeverage` | `Integer` | Максимальное плечо (не выше глобального risk-лимита). |
| `targetRiskRewardRatio` | `BigDecimal` | High-level ориентир R/R. |
| `indicatorSettings` | `List<StrategyIndicatorSetting>` | После выбора detail (ATR для SL, RSI для ENTRY и т.д.). |
| `marketStructureSettings` | `List<StrategyMarketStructureSetting>` | После выбора detail. |
| `stepsByStatus` | `Map<Deal.Status, List<StrategyStep>>` | Шаги, сгруппированные по статусу сделки. |

`PhaseEntryPolicy`: `FOLLOW_PHASE`, `CONTRARIAN`, `GRID`, `NO_TRADE`.
Матрица допустимости: `BULL_TREND`/`BEAR_TREND` →
`FOLLOW_PHASE`/`CONTRARIAN`/`NO_TRADE`; `RANGE` → `GRID`/`NO_TRADE`;
`UNKNOWN` → `NO_TRADE`.

## StrategyStep (раздел)

`id`, `stepType: StrategyStepType`, `condition: StrategyCondition`
(общее условие применимости), `actions: List<StrategyAction>` (пакет,
выполняется целиком, если condition истинно), `marketDataExpiredSetting:
StrategyMarketDataExpiredSetting` (обязателен для каждого step;
default на уровне detail не используется).

`StrategyStepType`: `ENTRY`, `MAIN_PROTECTION`, `PROTECTION_ADJUSTMENT`,
`PARTIAL_EXIT` (только через reduce-only Order/AlgoOrder, не direct
position), `GRID_ENTRY`, `GRID_MANAGEMENT`, `EXIT`, `FAIL_SAFE`. Связь
с `Deal.entryStepType`: `ENTRY`/`GRID_ENTRY` → `Deal.entryReason =
STRATEGY` + соответствующий `entryStepType`; остальные типы не могут
быть `Deal.entryStepType`. `PRECHECK` может повторно проверить
ENTRY/GRID_ENTRY condition; если стал false до live risk — `Deal`
закрывается с `closeReason = ENTRY_CONDITION_EXPIRED`.
`PROTECTION_SWITCHED` не обязателен — `StrategyStep` определяет, нужен
ли protection switch фактически.

### StrategyMarketDataExpiredSetting
Что делать, если нужные именно этому step данные устарели/отсутствуют
(не определяет, *когда* устарели — это `expirationDuration` +
`MarketDataExpirationChecker`). Поля: `protectedPositionAction`,
`unprotectedPositionAction` — оба `MarketDataExpiredAction`: `WAIT`,
`BLOCK_STEP` (refresh/cancel/close/safety остаются разрешены),
`GRACEFUL_CLOSE`, `KILL_SWITCH`.

## Условия (разделы)

### Контракт авторинга условия (направление)
Доки задают **контракт авторинга** условия: для каждого `ruleType` —
какие поля `StrategyConditionRule` / `StrategyConditionOperand` нужно
заполнить, чтобы правило было валидным. Вычисление истинности правила
— деталь evaluator'а (downstream, `StrategyConditionEvaluator`); в
модели не фиксируется.

Сам контракт по-полям пока **не зафиксирован**: открыта нестыковка
представления между моделью и `Strategy API` / examples (индикатор как
строка-метка `leftOperand` + инлайновый `params` vs объектная ссылка
`indicatorSetting`; дублирование `sourceType` на правиле и операнде;
`valueType` vs `sourceType`). Источник истины — открытый вопрос
**STRAT-Q1** (`.claude/work/questions/open-questions.md`); до его
решения поля условия остаются как перечислены ниже.

### StrategyCondition
`rules: List<StrategyConditionRule>` — все rules должны быть истинны;
проверяются по `level` ASC (локальный порядок внутри condition, не
глобальный порядок шагов).

### StrategyConditionRule
`level`, `ruleType: StrategyConditionRuleType`, `timeframe` (nullable),
`sourceType: StrategyConditionSourceType`, `leftOperand: String`,
`operator: StrategyConditionOperator`, `rightOperand:
StrategyConditionOperand`, `indicatorSetting` (объектная ссылка),
`marketStructureSetting` (объектная ссылка), `percents`.

`StrategyConditionRuleType`: `NO_OPEN_POSITION`, `NO_ACTIVE_DEAL`,
`ENTRY_ORDER_FINALIZED`, `POSITION_OPENED`, `ATTACHED_STOP_LOSS_EXISTS`,
`MAIN_PROTECTION_EXISTS`, `PROFIT_PERCENTS_REACHED`,
`LOSS_PERCENTS_REACHED`, `RANGE_BREAKOUT_CONFIRMED`, `TREND_CHANGED`,
`EFFICIENCY_BELOW_THRESHOLD`, `MARKET_PHASE_IS`, `INDICATOR_COMPARE`,
`PRICE_COMPARE`, `CROSSOVER`, `SIGNAL_SCORE_REACHED`,
`VOLUME_FILTER_PASSED`, `CANDLE_CLOSED`.

`StrategyConditionSourceType`: `PRICE`, `INDICATOR`, `SIGNAL`,
`MARKET_PHASE`, `MARKET_STRUCTURE`, `POSITION`, `ORDER`, `ALGO_ORDER`,
`BALANCE`, `TIME`, `CONSTANT`.

`StrategyConditionOperator`: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`,
`BETWEEN`, `NOT_BETWEEN`, `CROSSED_ABOVE`, `CROSSED_BELOW`, `IS_TRUE`,
`IS_FALSE`, `EXISTS`, `NOT_EXISTS`.

### StrategyConditionOperand
`sourceType: StrategyConditionSourceType`, `valueType: String` (NUMBER/
STRING/ENUM/PRICE_FIELD/INDICATOR_VALUE/MARKET_STRUCTURE_LEVEL/
BOOLEAN), `name: String`, `numberValue: BigDecimal`, `stringValue:
String`.

## Действия (разделы)

`StrategyAction` — интерфейс с `getKey()`. Это **не** `ServiceCommand`:
описывает ожидаемое действие; runtime-сущность связывается через
`DealActionState`. JSON-дискриминатор `actionKind` (`ORDER`/
`ALGO_ORDER`/`POSITION`) — только для сериализации, не поле домена.
Общий `StrategyActionType`: `CREATE`, `AMEND`, `CANCEL`, `CLOSE_FULL`.

### StrategyOrderAction
`key`, `targetActionKey` (для AMEND/CANCEL; для CREATE null),
`actionType` (CREATE/AMEND/CANCEL), `orderType: Order.Type`
(ENTRY / ENTRY_ATTACHED_STOP_LOSS), `direction: StrategyTradeDirection`,
`allocationPercents` (доля расчётного объёма), `positionReducingOnly:
Boolean` (strategy-intent → `Order.positionReducingOnly` → OKX
`reduceOnly` только в adapter), `level: Integer` (живёт в стратегии, не
переносится в Order как runtime-role), `placement: StrategyPricePlacement`
(для market-like входа null), `attachedProtection:
StrategyAttachedProtectionSettings` (для ENTRY null; для
ENTRY_ATTACHED_STOP_LOSS обязательна).

### StrategyTradeDirection
`LONG`, `SHORT` — нормализованное торговое направление (runtime mapper
маппит в buy/sell, long/short). Тип `Deal.direction`.

### StrategyPricePlacement
`baseType: StrategyPriceBaseType`, `priceSource: StrategyPriceSource`
(только для `MARKET_PRICE`), `marketStructureSetting` (для
RANGE_LOW/RANGE_HIGH/SWING_LOW/SWING_HIGH/SUPPORT/RESISTANCE),
`offsetSide: StrategyPriceOffsetSide`, `percents`.
- `StrategyPriceBaseType`: `RANGE_LOW`, `RANGE_HIGH`, `SWING_LOW`,
  `SWING_HIGH`, `SUPPORT`, `RESISTANCE`, `ENTRY_PRICE`, `MARKET_PRICE`.
- `StrategyPriceSource`: `LAST_PRICE`, `MARK_PRICE`, `INDEX_PRICE`,
  `BEST_BID_PRICE`, `BEST_ASK_PRICE`, `MID_PRICE`.
- `StrategyPriceOffsetSide`: `ABOVE`, `BELOW`.
- Разделение смыслов: `StrategyTradeDirection` (торговое направление)
  ≠ `StrategyPriceOffsetSide` (геометрия смещения) ≠
  `StrategyPriceSource` (источник цены) ≠ `TriggerPriceType` (тип
  trigger-цены биржи).

### StrategyAttachedProtectionSettings
`attachedType: AttachedAlgoOrder.Type` (фактически ATTACHED_STOP_LOSS),
`stopLossSettings: StopLossSettings`.

### StrategyAlgoOrderAction
`key`, `targetActionKey`, `actionType` (CREATE/AMEND/CANCEL),
`conditionType: ConditionType`, `level`, `stopLossSettings`,
`trailingSettings`, `closeFractionPercents` (доля закрытия; в runtime →
fraction 0..1), `triggerProfitPercents`, `triggerPriceType:
TriggerPriceType`. Убраны из модели: `BreakevenSettings`,
`PartialTakeProfitSettings`, `ExitEfficiencySettings` (выражаются как
отдельные steps/actions). На первом этапе `positionReducingOnly = true`
выводится из назначения (SL/TP/OCO/trailing/partial — protective/
closing, не открывают позицию). Для `OCO_FULL`: SL из
`stopLossSettings`, TP из `triggerProfitPercents`+`triggerPriceType`,
`closeFractionPercents` = доля (обычно 100 для полного).

### StrategyPositionAction
`key`, `actionType` (только `CLOSE_FULL`), `level`. `CLOSE_PARTIAL`
запрещён всегда (инвариант). Частичное уменьшение — через
Order/AlgoOrder reduce-only.

### StopLossSettings
`calculationType: StopLossCalculationType` (`ENTRY_PRICE_PERCENT` /
`ATR_PERCENT` (150 = 1.5 ATR) / `MARKET_STRUCTURE_BUFFER_PERCENT`),
`distancePercents`, `triggerPriceType: TriggerPriceType` (обязателен),
`indicatorSetting` (для ATR), `marketStructureSetting` (для structure
buffer: swing/range/support/resistance).

### TrailingSettings
`activationProfitPercents` (null — сразу), `callbackPercents`
(callback ratio/percent на биржу), `activationBufferPercents`.

## key / targetActionKey и валидация

`key` — стабильный ключ action внутри одной `StrategyDetail` (задаётся
в JSON). `targetActionKey` — ключ action, создавшего runtime-сущность
для AMEND/CANCEL; при сохранении стратегии валидируется и резолвится во
внутреннюю ссылку. Валидация при создании стратегии (12 правил):

1. `key` обязателен у каждого `StrategyAction`.
2. `key` уникален в рамках одной `StrategyDetail`.
3. `targetActionKey` ссылается на существующий `action.key` в той же
   `StrategyDetail`.
4. `targetActionKey` обязателен для AMEND/CANCEL у ORDER/ALGO_ORDER.
5. CREATE не имеет `targetActionKey`.
6. ORDER AMEND/CANCEL ссылаются на ORDER CREATE.
7. ALGO_ORDER AMEND/CANCEL ссылаются на ALGO_ORDER CREATE.
8. Нельзя ссылаться на action из другой `StrategyDetail`.
9. `StrategyPositionAction.actionType` только `CLOSE_FULL`.
10. Direct partial close через `StrategyPositionAction` запрещён.
11. Partial exit — через `StrategyOrderAction`/`StrategyAlgoOrderAction`
    с position-reducing-only.
12. Partial exit action не открывает/не увеличивает позицию.

Допустимые `actionType` по подтипам: ORDER/ALGO_ORDER —
CREATE/AMEND/CANCEL; POSITION — только CLOSE_FULL. Валидатор
(компонент/процесс) — форвард-заметка в task-вопросах
(`.claude/decisions/rule-source-of-truth.md`: 12-пунктная валидация →
процесс/компонент-валидатор).

## Связь с DealActionState

Стратегия не хранит runtime-состояние выполнения. `StrategyAction.key`
— для валидации/резолва `targetActionKey` при сохранении;
`StrategyAction.id` — в runtime: `DealActionState.strategyActionId →
RuntimeTarget(entityType, entityId)`. Инварианты:
`UNIQUE(strategy_detail_id, key)`, `UNIQUE(deal_id,
strategy_action_id)`. Runtime работает через `strategyActionId`, не
`strategyActionKey`. AMEND/CANCEL: target StrategyAction →
`DealActionState` → `RuntimeTarget` → `ServiceCommand` с конкретным
orderId/algoOrderId. `placement` не основной способ идентификации
runtime-сущности. (`DealActionState`/`RuntimeTarget` — кластер Deal
management, форвард-заметка.)

## Персистентность

Дерево персистится **реляционным каркасом**: каждый узел (root,
detail, step, action, настройки) — строка/таблица с `id`, объектные
связи между узлами — через FK, загрузка дерева целиком — через
`@EntityGraph` / `JOIN FETCH` (без N+1). Часть листовых настроек
хранится JSONB-полями. Обоснование развилок и сознательный отход от
архива (индикаторные `params`) —
`docs/decisions/strategy-tree-persistence.md`.

### Индикаторные `params`
JSONB-поле `params` на строке `StrategyIndicatorSetting` (только
непустые значения). В коде — абстрактный `IndicatorParams` + 7
наследников; в БД — JSON, без отдельных таблиц params и без
inheritance-маппинга. Валидацию полей params делает приложение.

### Действия (`StrategyAction`)
Наследование `JOINED`: базовая таблица `strategy_action`
(`id`, `action_kind`, `key`, `action_type`, `level`,
`target_action_key`) + таблицы по видам: `strategy_order_action`,
`strategy_algo_order_action`, `strategy_position_action` (у позиции
собственных полей нет). Вложенные настройки действий (`placement`,
`attachedProtection`, `stopLossSettings`, `trailingSettings`) —
JSONB-поля на строках соответствующих видов.

### `stepsByStatus`
`Map<Deal.Status, List<StrategyStep>>` хранится плоскими строками
`strategy_step` с колонками `strategy_detail_id` (FK), `deal_status`
(ключ map), `step_index` (порядок в списке). В домене Map
пересобирается группировкой по `deal_status` и сортировкой по
`step_index`. `marketDataExpiredSetting` шага — JSONB-поле.

### Внутридеревные ссылки
- Правило условия → настройка (`StrategyConditionRule.indicatorSetting`
  / `marketStructureSetting`) — FK на строку настройки.
- Ссылки изнутри JSON-листьев (напр. `stopLossSettings` с
  `ATR_PERCENT` → индикаторная настройка) — «мягкие»: id/key внутри
  JSON, резолвит приложение.
- `targetActionKey` при сохранении стратегии резолвится в self-FK
  `target_action_id → strategy_action.id` (см. §key / targetActionKey
  и валидация).

### Не зафиксировано
Типы и nullability числовых полей дерева (Integer vs BigDecimal для
`*Bars`/`*Period` vs `*Percents`/`*Score`/`*Ratio`/`*Multiplier`) на
разборе `GAPS_CLOSE_2` не решались — проставляются при написании
entity/Flyway-миграции.

## TimeFrame

Доменный enum таймфреймов; используется многими настройками
strategy-tree (`timeframe`-поля). Каноническое определение (значения,
отношение к OKX-строкам) — `docs/models/domain/other/CandleGroup.md`
(§Енум `TimeFrame`); маппинг доменного значения ↔ строка биржи —
`docs/models/mapping/TimeFrame.md`. Здесь не дублируется.

## Связи (расчёт / jobs / риск)

Стратегия хранит правила расчёта, не готовые значения. Runtime-расчёт,
jobs (`IndicatorJob`/`MarketStructureJob`/`MarketPhaseJob`/
`EntryScannerJob`), evaluator (`StrategyConditionEvaluator`),
калькуляторы (`StrategyActionCalculator` → `PriceCalculator`/
`SizeCalculator`), risk-layer (`RiskValidator` → `RiskCheckResult` →
`RiskBlockResolver`), `ServiceCommandFactory`, freshness
(`MarketDataExpirationChecker`), модели рыночных данных
(`IndicatorValue`/`MarketStructure`/`MarketPhase`/`MarketPriceLevel`),
RVO (`CalculationContext`/`MarketPriceData`/`CalculatedStrategyAction`/
`InstrumentExternalRules`) — отдельные кластеры
(`docs/processes/` / `docs/components/` / `docs/components/models/` /
`docs/models/domain/other/`), мигрируются отдельно (форвард-заметки в
`.claude/work/questions/tasks/strategy.md`). JSON-примеры — отдельный
файл `Strategy API examples.md` (форвард-заметка).
