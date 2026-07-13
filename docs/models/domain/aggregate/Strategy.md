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
  технических дат). **Исключение — листовые настройки**
  `StrategyIndicatorSetting` / `StrategyMarketStructureSetting` (таблицы
  `strategy_indicator_settings` / `strategy_market_structure_settings`):
  **не `Auditable`**, без аудит-колонок. Сознательное исключение:
  настройки — неизменяемые части агрегата `Strategy`, их технический аудит
  покрывается Auditable-корнем агрегата (`Strategy`) — DDD-корректно (аудит
  на границе агрегата, не на каждом value-листе).
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
- **Инвариант каталога настроек.** `StrategyIndicatorSetting` /
  `StrategyMarketStructureSetting` объявляются **раз на уровне
  стратегии** (strategy-scope), уникальность ключа —
  **`UNIQUE(strategy_id, key)`**, адресация — **по `key`**: операнд
  условия (`indicatorKey` / `structureKey`), «мягкие» ссылки
  JSON-листьев (`stopLossSettings`/`placement` — те же поля), а также
  `phaseRules` фазы и шаги деталей ссылаются на нужную настройку по её
  `key` (`docs/decisions/strategy-tree-persistence.md`). У
  `StrategyAction` `key` работает через `targetActionKey`. Прочие
  settings (`StrategyMarketPhaseSetting`, `StrategyDetail`, шаги) — без
  `key`, связи объектные (контейнмент / `marketPhaseType`).
- **Direct partial close запрещён** как постоянный инвариант (см.
  `docs/rules/no-partial-close.md`): полного закрытия позиции как
  **действия** нет — выход выражается условием-перехода
  `MANAGING → EXIT_PENDING` (market-close ведёт `ExitPendingHandler`);
  частичное уменьшение — только через
  `StrategyOrderAction`/`StrategyAlgoOrderAction` с
  position-reducing-only semantics.
- **`positionReducingOnly`** — доменное намерение strategy-layer;
  OKX `reduceOnly` — только client/adapter field, в strategy-модели
  не используется как имя.
- **Risk-check** — после расчёта `CalculatedStrategyAction` и до эмиссии
  команды per-type `StrategyActionExecutor`'ом (под
  `StrategyActionOrchestrator`); не выполняется перед `REFRESH_*`/search/
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
| `marketPhaseSetting` | `StrategyMarketPhaseSetting` | Настройка классификации фазы рынка (живёт на уровне Strategy, т.к. фаза нужна **до** выбора `StrategyDetail`). |
| `details` | `List<StrategyDetail>` | Ровно одна detail на один `MarketPhase.Type` (инвариант): create требует детали всех четырёх типов, неторгуемая фаза объявляется явной `NO_TRADE`-деталью. |
| `indicatorSettings` | `List<StrategyIndicatorSetting>` | Настройки индикаторов стратегии (инвариант каталога — см. §Архитектурные инварианты). |
| `marketStructureSettings` | `List<StrategyMarketStructureSetting>` | Настройки структуры стратегии (инвариант каталога — см. §Архитектурные инварианты). |

`Status`: `CREATED`, `ACTIVE`, `INACTIVE`, `DELETED` (значения и
эффекты — в `docs/lifecycles/Strategy.md`). Одна
`StrategyMarketPhaseSetting` несёт авторские правила классификации
рынка (`phaseRules`) во все `MarketPhase.Type`
(`BULL_TREND`/`BEAR_TREND`/`RANGE`/`UNKNOWN`). `MarketPhase`
**вычисляется на лету и не персистится**
(`docs/decisions/market-phase-stateless.md`): `EntryScannerJob` через
`MarketPhaseService` получает текущую фазу (классификатор поверх
последних `IndicatorValue` / `MarketStructure`) и выбирает detail по
`MarketPhase.Type → StrategyDetail.marketPhaseType`.

## Каталог настроек стратегии (реестр настроек)

`Strategy.indicatorSettings` / `marketStructureSettings` — **реестр
(каталог) настроек конкретной стратегии** (инвариант каталога — см.
§Архитектурные инварианты). Это per-strategy объявление,
**принадлежащее стратегии**: не общая вычисляемая идентичность ради
шаринга между стратегиями (шаринга нет, owner-ключевание —
`docs/decisions/market-data-result-identity-keying.md`), а то, **какие
настройки объявила эта стратегия**. Остальные сущности стратегии
(`StrategyMarketPhaseSetting`, детали, действия, условия) настройки **не
хранят** — ссылаются на них **по `key`** в пределах стратегии (резолвит
приложение; необъявленный `key` отвергается create-валидацией —
strategy-scope ref-resolution, см. §StrategyConditionOperand).
Результат расчёта (`IndicatorValue` / `MarketStructure`) ссылается на
свою настройку **одной типизированной FK**
(`strategy_indicator_setting_id` /
`strategy_market_structure_setting_id`) — owner-ключевание
(`docs/decisions/market-data-result-identity-keying.md`).

## Настройки рыночных данных (разделы)

### StrategyMarketPhaseSetting
`phaseRules: List<StrategyMarketPhaseRule>`. Операнды клауз ссылаются по
`key` на индикаторные/структурные настройки **стратегии** (инвариант
каталога — см. §Архитектурные инварианты). Отдельного `params`-объекта
у настройки нет: `MarketPhase.Type` определяется авторскими условиями
(`phaseRules`), не скоринговым алгоритмом (см.
`docs/decisions/market-phase-conditional-classification.md`).

Своих `timeframe` и `expirationDuration` у настройки нет: `MarketPhase`
вычисляется на лету и не персистится, свежесть наследуется от входов
(устаревший вход → операнд недоступен → `UNKNOWN`) —
`docs/decisions/market-phase-stateless.md`.

### StrategyMarketPhaseRule
Клауза «условие → фаза»: `type: MarketPhase.Type`,
`condition: StrategyCondition`. Набор клауз настройки — **упорядоченный
first-match-список**: проверяются **по позиции в списке** (очерёдность =
позиция, поля `level` у клаузы нет —
`docs/decisions/market-phase-stateless.md`), первая клауза с истинным
`condition` задаёт `MarketPhase.Type`; не сработала ни одна → `UNKNOWN`
(неявный консервативный дефолт, не авторится). Несколько клауз на один
`type` допустимы (разные паттерны свидетельства → одна фаза).

`condition` — тот же `StrategyCondition` (ниже), но в **контексте
классификации фазы** (до выбора детали, без сделки): операнды только
`INDICATOR` / `MARKET_STRUCTURE` (по `key` из каталога стратегии) /
`PRICE` / `CONSTANT` /
`TIME`; **запрещены** `MARKET_PHASE` (само-референция — фаза вычисляется)
и runtime-источники сделки (`POSITION` / `ORDER` / `ALGO_ORDER` /
`BALANCE`). Допустимые `ruleType` — сравнивающие (`INDICATOR_COMPARE` /
`PRICE_COMPARE` / `CROSSOVER`) и структурно-событийные
(`RANGE_BREAKOUT_CONFIRMED` / `VOLUME_FILTER_PASSED` / `CANDLE_CLOSED` /
`MARKET_STRUCTURE_IS`); запрещены lifecycle-сделки, `MARKET_PHASE_IS`
(цикл) и `TREND_CHANGED` (темпоральное «текущее vs прошлое» —
несовместимо со stateless-контрактом `MarketPhaseResolver` без
источника истории; структурные переходы в фазе выражаются
`RANGE_BREAKOUT_CONFIRMED` / `MARKET_STRUCTURE_IS` над операндом
`MARKET_STRUCTURE`). Тест эффективности
рынка (ER) — через `INDICATOR_COMPARE` над ER-операндом каталога
(`EFFICIENCY_RATIO`), в обе стороны (`LT` — шум/range, `GT` — тренд;
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`).
Контекстный whitelist — create-валидация (400). Анти-whipsaw —
операнд-уровневый (сглаживающие периоды индикаторов, переиспользуемые
по `key`; структурный `breakoutConfirmationBars`); отдельного
фаза-дебаунса нет.

Темпоральное правило фазы — дверь на будущее (сейчас не реализуется):
понадобится позже — вводить читающим **готовую историю структуры** (не
фазы), с явной квалификацией «stateless = без истории фаз» и
per-`ruleType` контрактом источника. В entry-контексте (где `MarketPhase`
есть в данных) `TREND_CHANGED` не затронут — там остаётся как есть.

### StrategyIndicatorSetting
`id` (реляционный — цель FK результата, см. ниже), `key` (стабильный ключ
настройки — по нему ссылается индикаторный операнд условия, `indicatorKey`),
`indicatorType: IndicatorValue.Type` (= `type` в форме ввода; дискриминатор
подтипа `params` — см. §IndicatorParams), `params: IndicatorParams`,
`destiny: Destiny`, `expirationDuration`. Доменный `timeframe` и `warmup`
живут **внутри `params`** (см. §IndicatorParams; контракт —
`docs/decisions/strategy-condition-authoring-contract.md`). `Destiny`:
`MARKET_PHASE`, `ENTRY_CONDITION`, `ACTION_PRICE`, `PROTECTION`,
`EXIT_CONDITION` (`destiny` — пометка назначения настройки).

**Scope — стратегия**: собственная реляционная строка strategy-scope,
FK результата — см. §Каталог настроек стратегии; инвариант каталога —
см. §Архитектурные инварианты.

### IndicatorParams (abstract) + наследники
База: `timeframe: TimeFrame` (доменный таймфрейм серии), `warmup`
(опциональный override — см. ниже). Собственного поля-типа у базы
нет: дискриминатор подтипа — `indicatorType` настройки-владельца
(`StrategyIndicatorSetting`), резолвится **вручную в
`StrategyJsonConverter`** (сериализация — конкретным подтипом без тега,
десериализация — в конкретный класс по `indicator_type`), и в
JSON-payload `params` не дублируется (единственный источник
тега — `docs/rules/persistence-representation.md`). Наследники несут
только математические параметры по типу: `AtrParams(period)`,
`EmaParams(period)`, `RsiParams(period)`,
`MacdParams(fastPeriod, slowPeriod, signalPeriod)`,
`BollingerBandsParams(period, deviationMultiplier)`,
`StochasticParams(kPeriod, dPeriod, smoothPeriod)`,
`ObvParams(enabled)`, `EfficiencyRatioParams(period)` (ER — оконный,
`warmup = period`;
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`).
Волатильность отдельной сущностью не
моделируется — через индикаторы ATR / Bollinger bandwidth.

`warmup` по умолчанию **выводится** реализацией индикатора из
`indicatorType` + `period` (оконные — `= period`; рекурсивные
EMA/RSI/ATR — кратно `period`; MACD — от старшего периода). Автор может
задать явный override в `params`; эффективный `warmup = override ??
derived`. Create-валидация проверяет override против упрощённого
минимума по типу (окно/рекурсивные → `period`; MACD → `slow + signal`;
стохастик — сумма окон; OBV → 1); настоящий derive — у реализаций
индикаторов. Потребитель — candle-loading (глубина истории для прогрева,
`docs/processes/candle-loading.md`); runtime-пропуск разгонной зоны при
расчёте — `docs/components/IndicatorJob.md` §Warmup. Контракт —
`docs/decisions/strategy-condition-authoring-contract.md`.

### StrategyMarketStructureSetting
`id` (реляционный — цель FK результата, см. ниже), `key` (на него
ссылаются операнд market-structure и «мягкие» ссылки JSON-листьев — поле
`structureKey`, см. §StrategyConditionOperand), `timeframe`,
`efficiencyRatioKey`, `atrKey`, `params: MarketStructureParams`, `destiny:
Destiny`, `expirationDuration`. `Destiny`: те же 5 значений, что у indicator
(MARKET_PHASE / ENTRY_CONDITION / ACTION_PRICE / PROTECTION /
EXIT_CONDITION).

**Scope — стратегия**: собственная реляционная строка strategy-scope,
FK результата — см. §Каталог настроек стратегии; инвариант каталога —
см. §Архитектурные инварианты.

`efficiencyRatioKey` / `atrKey` — «мягкие» ссылки (по `key`) на настройки
каталожных индикаторов **стратегии**, которые резолвер потребляет готовыми
входами: ER — дискриминатор тренд/шум, ATR — пол толеранса уровней.
`null` → резолвер использует внутренний прокси (ER) / fallback на
долю цены (ATR-толеранс). Объявлен, но не готов / устарел → консервативный
`UNKNOWN` (job, не proxy). Каждая настройка структуры (со своими
ER/ATR-ключами) пишет в **свою** строку результата
(`docs/decisions/derived-market-data-code-increments.md`).

`MarketStructure.Type` — **выход** расчёта (`MarketStructureResolver`
его выводит), не вход настройки: поля `structureType` у настройки нет.
Правила хранения результата —
`docs/models/domain/other/MarketStructure.md` §Правила хранения.

### MarketStructureParams
`lookbackBars`, `minTouches`, `minRangeWidthPercents`,
`maxRangeWidthPercents`, `breakoutBufferPercents`,
`breakoutConfirmationBars`, `swingLookbackBars`,
`trendEfficiencyThreshold`, `levelToleranceAtrMultiplier`.

- `trendEfficiencyThreshold: BigDecimal` — порог ER тренда (ER ≥ порога →
  тренд-сила vs диапазон).
- `levelToleranceAtrMultiplier: BigDecimal` — множитель `k` в толерансе
  кластеризации уровней (`толеранс = k·ATR`; при необъявленном ATR —
  fallback резолвера на долю цены).

Оба — хвост пользователя; значения провизорны (value: бэктест-гейт фазы 2,
STRUCT-Q1), числом в канон не зашиваются; при `null` резолвер применяет
провизорные дефолты. Грунт и альтернативы —
`docs/decisions/derived-market-data-code-increments.md`.

## StrategyDetail (раздел)

Набор торговых правил для конкретной фазы рынка.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID. |
| `marketPhaseType` | `MarketPhase.Type` | Для какой фазы работает detail. |
| `phaseEntryPolicy` | `PhaseEntryPolicy` | Как торгуем в этой фазе. |
| `riskPerTradePercent` | `BigDecimal` | Риск на сделку, % от **свободного депозита** (`externalAvailableEquity`); см. `docs/decisions/per-trade-risk-policy.md`. |
| `targetRiskRewardRatio` | `BigDecimal` | High-level ориентир R/R. |
| `stepsByStatus` | `Map<Deal.Status, List<StrategyStep>>` | Шаги, сгруппированные по статусу сделки. |

Индикаторы/структуры, нужные детали (ATR для SL, RSI для ENTRY и т. д.),
адресуются **по `key`** на настройки каталога стратегии (инвариант
каталога — см. §Архитектурные инварианты); собственных inline-настроек
деталь не держит. Какие именно ключи задействует деталь — выводится из
операндов её условий и листьев действий (резолв по `key`); точная форма
списка ключей на детали — деталь реализации (`CODE`).

`PhaseEntryPolicy`: `FOLLOW_PHASE`, `CONTRARIAN`, `GRID`, `NO_TRADE`.
Матрица допустимости: `BULL_TREND`/`BEAR_TREND` →
`FOLLOW_PHASE`/`CONTRARIAN`/`NO_TRADE`; `RANGE` → `GRID`/`NO_TRADE`;
`UNKNOWN` → `NO_TRADE`. Матрица — инвариант доменной модели (метод
`PhaseEntryPolicy.isAllowedFor`); проверяется на create (400). У
`NO_TRADE`-детали риск-поля и настройки опциональны (nullable).

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

### Контракт авторинга условия
Доки задают **контракт авторинга** условия (для валидного правила —
какие поля заполнить); вычисление истинности правила — деталь
evaluator'а (downstream, `StrategyConditionEvaluator`), в модели не
фиксируется.

**Источник истины — объектная settings-модель** (ниже). Строка-метка
`leftOperand` + инлайновый `params` из `Strategy API examples` — это
**форма ввода**: API при сохранении резолвит метки в ссылки/настройки,
домен работает с settings-моделью. Обоснование, отвергнутые
альтернативы и открытые инкременты —
`docs/decisions/strategy-condition-authoring-contract.md`.

Per-`ruleType` контракт полей (какие именно поля под каждый `ruleType`,
включая правила валидности комбинаций операндов) дозаполняется
инкрементально при реализации каждого `ruleType`, превентивно не
перечисляется.

### StrategyCondition
`rules: List<StrategyConditionRule>` — все rules должны быть истинны;
проверяются по `level` ASC (локальный порядок внутри condition, не
глобальный порядок шагов).

### StrategyConditionRule
Единая структура; операнды **опциональны** (левый / правый / оба / нет
— отсутствующий не пишется):
- `level`, `ruleType: StrategyConditionRuleType`;
- **доменные правила** — плоские: `ruleType` [+ простые поля:
  `percents` (PROFIT_/LOSS_PERCENTS_REACHED), `timeframe`
  (CANDLE_CLOSED — какой таймфрейм закрыт)];
- **структурно-событийные правила** (`RANGE_BREAKOUT_CONFIRMED`,
  `MARKET_STRUCTURE_IS`) — ссылаются на `MarketStructure` по `structureKey`
  (см. примечание ниже);
- **сравнивающие правила** — `operator: StrategyConditionOperator` +
  симметричные структурированные `leftOperand` / `rightOperand:
  StrategyConditionOperand`.

Любой источник — на любой стороне (число слева или справа,
indicator-vs-indicator допускается — базовый кейс кроссовера).
`sourceType` и `timeframe` живут на операнде/источнике, не на правиле;
ссылки на настройки — по `key` операнда, объектных ссылок на настройки
правило не несёт.

`StrategyConditionRuleType`: `NO_OPEN_POSITION`, `NO_ACTIVE_DEAL`,
`ENTRY_ORDER_FINALIZED`, `POSITION_OPENED`, `ATTACHED_STOP_LOSS_EXISTS`,
`MAIN_PROTECTION_EXISTS`, `PROFIT_PERCENTS_REACHED`,
`LOSS_PERCENTS_REACHED`, `RANGE_BREAKOUT_CONFIRMED`, `TREND_CHANGED`,
`MARKET_PHASE_IS`, `MARKET_STRUCTURE_IS`,
`INDICATOR_COMPARE`, `PRICE_COMPARE`, `CROSSOVER`, `VOLUME_FILTER_PASSED`,
`CANDLE_CLOSED`. Критерий гранулярности `ruleType` (sugar vs алиас) —
`docs/rules/condition-ruletype-granularity.md`. `MARKET_STRUCTURE_IS`
(тест `MarketStructure.Type` равенством, зеркало `MARKET_PHASE_IS`) —
**отдельный именованный `ruleType`, не сворачивается** в
`INDICATOR_COMPARE` (enum-равенство ≠ числовое сравнение: разные
валидационные контракты, генерик-`ENUM_COMPARE` в грамматике нет). Операнд —
`MARKET_STRUCTURE` по `structureKey`, `CONSTANT` с `MarketStructure.Type`
(`ENUM`, валиден по енуму).

`RANGE_BREAKOUT_CONFIRMED` — структурно-событийное: ссылается на
`MarketStructure` по `structureKey` и **читает готовым** предвычисленное
событие пробоя (`breakoutEvent`); детекция (буфер + подтверждение) —
**на стороне резолвера** (`MarketStructureParams.breakoutBufferPercents`/
`breakoutConfirmationBars`), не поле условия. Точная форма `breakoutEvent`
и per-`ruleType` поля — `CODE` (см.
`docs/models/domain/other/MarketStructure.md` §Семантика классификации,
`docs/components/MarketStructureResolver.md`).

`StrategyConditionSourceType`: `PRICE`, `INDICATOR`, `MARKET_PHASE`,
`MARKET_STRUCTURE`, `POSITION`, `ORDER`, `ALGO_ORDER`, `BALANCE`,
`TIME`, `CONSTANT`.

`StrategyConditionOperator`: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`,
`BETWEEN`, `NOT_BETWEEN`, `CROSSED_ABOVE`, `CROSSED_BELOW`, `IS_TRUE`,
`IS_FALSE`, `EXISTS`, `NOT_EXISTS`.

### StrategyConditionOperand
Самоописательный: `sourceType: StrategyConditionSourceType` + ссылка /
значение по источнику:
- `valueType: ConstantValueType` (`NUMBER` / `PERCENT` / `ENUM` /
  `BOOLEAN`) — только у `CONSTANT` (там обязателен); у вычисляемых
  (`INDICATOR` / `PRICE` / `MARKET_STRUCTURE` / `MARKET_PHASE`) не
  пишется — тип подразумевается источником.
- `value: String` — единый литерал `CONSTANT` (строковое
  представление, интерпретируется по `valueType`); у вычисляемых
  источников значение приходит в рантайме (evaluator).
- Ссылки per-source: индикаторный операнд — `indicatorKey`; операнд
  market-structure — `structureKey`; ценовой операнд несёт
  `priceSource: StrategyPriceSource`. `indicatorKey` / `structureKey`
  ссылаются на настройку из **каталога стратегии** (§Каталог настроек
  стратегии) по `key`; использовать необъявленную настройку нельзя — ключ
  обязан резолвиться в каталог (create-валидация, strategy-scope
  ref-resolution). Операнд-`CONSTANT` (литерал) и `PRICE` (источник цены) —
  **не** ссылки на настройку, ограничение их не касается.
- `indicatorComponent: IndicatorComponent` (только у `INDICATOR`) —
  адресный компонент многокомпонентного индикатора: какую часть
  сравнивать. MACD — `MACD_LINE`/`SIGNAL_LINE`/`HISTOGRAM`; Stochastic —
  `STOCH_K`/`STOCH_D`; Bollinger — `UPPER_BAND`/`MIDDLE_BAND`/`LOWER_BAND`/
  `BANDWIDTH`/`PERCENT_B`. **Обязателен** для многокомпонентных типов
  (MACD/Stochastic/Bollinger), **запрещён** для одно-компонентных
  (EMA/RSI/ATR/OBV/`EFFICIENCY_RATIO`), проверяется на совместимость с
  типом индикатора (create-валидация, 400; справочник «тип → компоненты» —
  `util.IndicatorComponents`). Снимает масштаб-зависимость абсолютного
  compare многокомпонентного индикатора (зеркало OBV-принципа
  относительных форм — `docs/decisions/derived-market-data-code-increments.md`).

### Объёмные условия (OBV / `VOLUME_FILTER_PASSED`)

Торговый грунт — `docs/decisions/volume-condition-semantics.md`:

- **OBV-операнд — только относительные формы.** OBV кумулятивен,
  абсолютный уровень нестабилен; операнд типа `OBV` допускает
  `CROSSED_ABOVE`/`CROSSED_BELOW` против серии/своей скользящей и
  сравнение с другой вычисляемой серией (направление/динамика).
  **Абсолютный compare OBV с `CONSTANT` не допускается.** Стабильный
  абсолютный порог по объёму — отдельный нормированный операнд (volume
  oscillator / нормированный объём), не OBV; по потребности, сейчас не
  заведён.
- **Объёмное условие — не единственное основание `ENTRY`.** OBV-операнд
  и `VOLUME_FILTER_PASSED` — подтверждающий фильтр направления (объём
  манипулируем, надёжность ниже на тонком объёме); набор entry-условий
  сопрягает объём с ценовым/структурным свидетельством, не опирается
  только на объём. Авторинг-правило (чек-лист СТ-1 / контракт авторинга).
- **Крипто-надёжность объёма — открыта** (IND-Q1): достоверность
  спот-объёма крипто-CEX в корпусе ∅; докручивается после дообучения.

## Действия (разделы)

`StrategyAction` — интерфейс с `getKey()`. Это **не** `ServiceCommand`:
описывает ожидаемое действие; runtime-сущность связывается через
`DealActionState`. JSON-дискриминатор `actionKind` (`ORDER`/
`ALGO_ORDER`) — только для сериализации, не поле домена.
Общий `StrategyActionType`: `CREATE`, `REPLACE`, `CANCEL`.

Полного закрытия позиции как **действия** нет, позиционного подтипа
действия (`POSITION`) в модели нет: выход — условие-переход
`MANAGING → EXIT_PENDING` (`docs/decisions/fsm-execution-layering.md`),
частичное уменьшение — reduce-only (инвариант — см. §Архитектурные
инварианты, `docs/rules/no-partial-close.md`).

`REPLACE` — единственная операция ремоделирования: действие задаёт
**полное новое желаемое состояние** (палитра настроек как у CREATE)
и исполняется заменой runtime-сущности — новая сущность + отмена
старой, оркестрацией существующих атомарных команд; порядок ног — по
риск-классу действия (`docs/decisions/replace-not-amend.md`).

### StrategyOrderAction
`key`, `targetActionKey` (для REPLACE/CANCEL; для CREATE null),
`actionType` (CREATE/REPLACE/CANCEL), `orderType: Order.Type`
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
(только для `MARKET_PRICE`), `structureKey` (ключ настройки структуры,
для RANGE_LOW/RANGE_HIGH/SWING_LOW/SWING_HIGH/SUPPORT/RESISTANCE),
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
`key`, `targetActionKey`, `actionType` (CREATE/REPLACE/CANCEL),
`conditionType: ConditionType`, `level`, `stopLossSettings`,
`trailingSettings`, `closeFractionPercents` (доля закрытия; в runtime →
fraction 0..1), `triggerProfitPercents`, `triggerPriceType:
TriggerPriceType`. Breakeven / partial-take-profit / exit-efficiency
отдельными настройками не моделируются — выражаются отдельными
steps/actions. На первом этапе `positionReducingOnly = true`
выводится из назначения (SL/TP/OCO/trailing/partial — protective/
closing, не открывают позицию). Для `OCO_FULL`: SL из
`stopLossSettings`, TP из `triggerProfitPercents`+`triggerPriceType`,
`closeFractionPercents` = доля (обычно 100 для полного).

### StopLossSettings
`calculationType: StopLossCalculationType` (`ENTRY_PRICE_PERCENT` /
`ATR_PERCENT` (150 = 1.5 ATR) / `MARKET_STRUCTURE_BUFFER_PERCENT`),
`distancePercents`, `triggerPriceType: TriggerPriceType` (обязателен),
`indicatorKey` (ключ ATR-настройки, для `ATR_PERCENT`), `structureKey`
(ключ настройки структуры, для `MARKET_STRUCTURE_BUFFER_PERCENT`:
swing/range/support/resistance).

### TrailingSettings
`activationProfitPercents` (null — сразу), `callbackPercents`
(callback ratio/percent на биржу), `activationBufferPercents`.

Trailing-защита — заявленная возможность стратегии, не латентное
поле: защитный арсенал действий включает trailing наравне с TP/SL.
Следствие для биржевого слоя — cancel-путь algo ветвится по семье
(trailing — advance-семья OKX);
см. `docs/integrations/okx/contracts/algo-order.md` §Ветвление
cancel-пути (И-1).

## key / targetActionKey и валидация

`key` — стабильный ключ action внутри одной `StrategyDetail` (задаётся
в JSON). `targetActionKey` — ключ action, создавшего runtime-сущность
для REPLACE/CANCEL; при сохранении стратегии валидируется и резолвится
во внутреннюю ссылку. Валидация при создании стратегии (11 правил):

1. `key` обязателен у каждого `StrategyAction`.
2. `key` уникален в рамках одной `StrategyDetail`.
3. `targetActionKey` ссылается на существующий `action.key` в той же
   `StrategyDetail`.
4. `targetActionKey` обязателен для REPLACE/CANCEL у ORDER/ALGO_ORDER.
5. CREATE не имеет `targetActionKey`.
6. ORDER REPLACE/CANCEL ссылаются на ORDER CREATE.
7. ALGO_ORDER REPLACE/CANCEL ссылаются на ALGO_ORDER CREATE.
8. Нельзя ссылаться на action из другой `StrategyDetail`.
9. Полного закрытия позиции как действия нет: выход — переход
   `MANAGING → EXIT_PENDING` (`CLOSE_POSITION` через `ExitPendingHandler`);
   direct partial close позиции запрещён.
10. Partial exit — через `StrategyOrderAction`/`StrategyAlgoOrderAction`
    с position-reducing-only.
11. Partial exit action не открывает/не увеличивает позицию.

Допустимые `actionType` по подтипам: ORDER/ALGO_ORDER —
CREATE/REPLACE/CANCEL.

**Линия реза валидатора (create / activate).** Структурно-ссылочные
пункты (1-3, 8: наличие/уникальность `key`, разрешённость ссылок в
рамках detail) проверяются на **create (400)**. Пункты семантики
действий (4-7, 9-11: `targetActionKey` для REPLACE/CANCEL,
ORDER↔ORDER / ALGO↔ALGO, отсутствие полного закрытия как действия /
partial-exit) опираются на зреющую модель команд/сделок/FSM и отложены
— на **activate (422)** как «готова к запуску» и/или более поздние
инкременты реализации. Числовые торговые
поля (`riskPerTradePercent`, `allocationPercents`, …) на create
ограничиваются только структурно — невозможные значения (отрицательный
риск; доля вне [0; 100] там, где поле — доля) → 400; торгово-суждённые
диапазоны (консервативные ориентиры риска и т. п.) — семантика
activate, на create не проверяются. Материализация
«одной реализации» — через Strategy API (`POST`/`GET`/`PUT`).
Обоснование — `docs/decisions/strategy-materialization-and-validation.md`.

## Связь с DealActionState

Стратегия не хранит runtime-состояние выполнения. `StrategyAction.key`
— для валидации/резолва `targetActionKey` при сохранении;
`StrategyAction.id` — в runtime: `DealActionState.strategyActionId →
RuntimeTarget(entityType, entityId)`. Инварианты:
`UNIQUE(strategy_detail_id, key)`, `UNIQUE(deal_id,
strategy_action_id)`. Runtime работает через `strategyActionId`, не
`strategyActionKey`. REPLACE/CANCEL: target StrategyAction →
`DealActionState` → `RuntimeTarget` → `ServiceCommand` с конкретным
orderId/algoOrderId. **Резолюция цели по цепочке замещений:** после
REPLACE актуальная сущность — последнее звено цепочки от
target-action (target-сущность → вперёд по `replacesInternalId`);
REPLACE/CANCEL всегда целятся в актуальное звено, не в исходную
сущность CREATE. `placement` не основной способ идентификации
runtime-сущности.

## Персистентность

Верхнеуровневые тезисы; детальная схема (колонки, денормализованные FK,
CHECK-констрейнты, отвергнутые альтернативы) —
`docs/decisions/strategy-tree-persistence.md`; общее правило
представления сущностей в БД — `docs/rules/persistence-representation.md`.

- **Реляционный каркас** для узлов-контейнеров и каркасных узлов
  (`Strategy` (root), `StrategyMarketPhaseSetting`, `StrategyDetail`,
  `StrategyStep`, `StrategyAction`): каждый узел — строка/таблица с
  `id`, связи — FK, загрузка дерева целиком — `@EntityGraph` /
  `JOIN FETCH` (без N+1).
- Таблицы: `strategies`, `strategy_market_phase_settings`,
  `strategy_details`, `strategy_steps`, `strategy_actions` + видовые
  `strategy_order_actions` / `strategy_algo_order_actions`, плюс
  `strategy_indicator_settings` / `strategy_market_structure_settings`.
- На `strategies` — частичный UNIQUE-индекс «одна `ACTIVE` на
  инструмент» (БД-страховка инварианта lifecycle).
- Настройки рыночных данных — собственные реляционные строки
  strategy-scope; `UNIQUE(strategy_id, key)` — DB-страховка инварианта
  каталога (см. §Архитектурные инварианты); `id` настройки — цель
  типизированной FK результата расчёта (см. §Каталог настроек
  стратегии). Точная форма привязки настроек к контейнерам/действиям в
  объектном графе — деталь `CODE`.
- `params` настройки (`IndicatorParams` + наследники;
  `MarketStructureParams`) — JSONB-колонка `params` на строке настройки
  (только непустые значения), без отдельных таблиц params и без
  inheritance-маппинга; дискриминатор подтипа в payload не дублируется —
  тег несёт `indicator_type` строки-владельца (см. §IndicatorParams).
- `phaseRules` — JSONB-колонка `phase_rules` на строке
  `strategy_market_phase_settings` (условие клаузы — тот же JSONB, что
  `condition` шага).
- `StrategyAction` — наследование **`JOINED`**: базовая
  `strategy_actions` + таблицы по видам; вложенные настройки действий
  (`placement`, `attachedProtection`, `stopLossSettings`,
  `trailingSettings`) — JSONB-поля на строках видов.
- `stepsByStatus` — плоские строки `strategy_steps` (`deal_status`,
  `step_index`); Map пересобирается в домене;
  `marketDataExpiredSetting` шага — JSONB-поле.
- Условие шага (`StrategyCondition` с `rules` и операндами) —
  **целиком JSONB-поле `condition`** на строке `strategy_steps`;
  отдельных таблиц условия нет; evaluator десериализует условие в
  объектную модель независимо от формы хранения.
- Внутридеревные ссылки: операнд условия / JSON-лист → настройка —
  «мягкие» по `key`, резолвит приложение; `targetActionKey` при
  сохранении резолвится в self-FK `target_action_id →
  strategy_actions.id` (БД-защита ссылки действие→действие; детали —
  decision).
- Типы числовых полей: `*Percents` / `*Score` / `*Ratio` /
  `*Multiplier` — `BigDecimal` (numeric(36,18), Constants.Price);
  `*Bars` / `*Period` / `level` / `warmup` — `Integer`. Риск-поля детали
  (`riskPerTradePercent`, `targetRiskRewardRatio`) — nullable (у
  `NO_TRADE`-детали риска нет).
- Валидацию полей и контекстный whitelist операндов/`ruleType` делает
  приложение.

## TimeFrame

Доменный enum таймфреймов; используется многими настройками
strategy-tree (`timeframe`-поля). Каноническое определение (значения,
отношение к OKX-строкам) — `docs/models/domain/other/CandleGroup.md`
(§Енум `TimeFrame`); маппинг доменного значения ↔ строка биржи —
`docs/models/mapping/TimeFrame.md`. Здесь не дублируется.

## Связи (расчёт / jobs / риск)

Стратегия хранит правила расчёта, не готовые значения. Runtime-расчёт,
jobs (`IndicatorJob`/`MarketStructureJob`/`EntryScannerJob`; фаза —
не job, а деривация на чтение через `MarketPhaseService`/
`MarketPhaseResolver`, `docs/decisions/market-phase-stateless.md`),
evaluator (`StrategyConditionEvaluator`),
калькуляторы (`StrategyActionCalculator` → `PriceCalculator`/
`SizeCalculator`), risk-layer (`RiskValidator` → `RiskCheckResult` →
`RiskBlockResolver`), `StrategyActionOrchestrator` +
`DealFinalizationCommandFactory`, freshness
(`MarketDataExpirationChecker`), модели рыночных данных
(`IndicatorValue`/`MarketStructure`/`MarketPhase`/`MarketPriceLevel`),
RVO (`CalculationContext`/`MarketPriceData`/`CalculatedStrategyAction`/
`InstrumentExternalRules`) — отдельные кластеры
(`docs/processes/` / `docs/components/` / `docs/components/models/` /
`docs/models/domain/other/`). JSON-примеры — отдельный
файл `Strategy API examples.md`.
