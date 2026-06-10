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
- **`key` — у `StrategyAction` и у настроек индикаторов/структур.** У
  `StrategyAction` `key` работает через `targetActionKey`. У
  `StrategyIndicatorSetting` (и настройки market-structure) `key`
  нужен, чтобы на настройку ссылался операнд условия (`indicatorKey` /
  `structureKey`) и «мягкие» ссылки JSON-листьев
  (`stopLossSettings`/`placement` — те же поля `indicatorKey` /
  `structureKey`). Прочие settings — без `key`,
  связи объектные (контейнмент внутри `StrategyMarketPhaseSetting` /
  `StrategyDetail`).
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
| `details` | `List<StrategyDetail>` | Ровно одна detail на один `MarketPhase.Type` (инвариант): create требует детали всех четырёх типов, неторгуемая фаза объявляется явной `NO_TRADE`-деталью. |

`Status`: `CREATED`, `ACTIVE`, `INACTIVE`, `DELETED` (значения и
эффекты — в `docs/lifecycles/Strategy.md`). Одна
`StrategyMarketPhaseSetting` несёт авторские правила классификации
рынка (`phaseRules`) во все `MarketPhase.Type`
(`BULL_TREND`/`BEAR_TREND`/`RANGE`/`UNKNOWN`);
`MarketPhaseJob` сохраняет один актуальный результат, `EntryScannerJob`
выбирает detail по `MarketPhase.Type → StrategyDetail.marketPhaseType`
(jobs — форвард-заметки в
`.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-strategy.md`).

## Настройки рыночных данных (разделы)

### StrategyMarketPhaseSetting
`timeframe: TimeFrame`, `phaseRules: List<StrategyMarketPhaseRule>`,
`indicatorSettings: List<StrategyIndicatorSetting>`,
`marketStructureSettings: List<StrategyMarketStructureSetting>`,
`expirationDuration: Duration` (срок свежести последней `MarketPhase`).
Отдельного `params`-объекта у настройки нет: `MarketPhase.Type`
определяется авторскими условиями (`phaseRules`), не скоринговым
алгоритмом (см. `docs/decisions/market-phase-conditional-classification.md`).

### StrategyMarketPhaseRule
Клауза «условие → фаза»: `level: Integer`, `type: MarketPhase.Type`,
`condition: StrategyCondition`. Набор клауз настройки — **упорядоченный
first-match-список**: проверяются по `level` ASC, первая клауза с
истинным `condition` задаёт `MarketPhase.Type`; не сработала ни одна →
`UNKNOWN` (неявный консервативный дефолт, не авторится). Несколько клауз
на один `type` допустимы (разные паттерны свидетельства → одна фаза).

`condition` — тот же `StrategyCondition` (ниже), но в **контексте
классификации фазы** (до выбора детали, без сделки): операнды только
`INDICATOR` / `MARKET_STRUCTURE` (по `key` из `indicatorSettings` /
`marketStructureSettings` этой же настройки) / `PRICE` / `CONSTANT` /
`TIME`; **запрещены** `MARKET_PHASE` (само-референция — фаза вычисляется)
и runtime-источники сделки (`POSITION` / `ORDER` / `ALGO_ORDER` /
`BALANCE`). Допустимые `ruleType` — сравнивающие (`INDICATOR_COMPARE` /
`PRICE_COMPARE` / `CROSSOVER`) и структурно-событийные
(`RANGE_BREAKOUT_CONFIRMED` / `VOLUME_FILTER_PASSED` / `CANDLE_CLOSED` /
`MARKET_STRUCTURE_IS`); запрещены lifecycle-сделки, `MARKET_PHASE_IS`
(цикл) и `TREND_CHANGED` (темпоральное «текущее vs прошлое» —
несовместимо со stateless-контрактом `MarketPhaseClassifier` без
источника истории; структурные переходы в фазе выражаются
`RANGE_BREAKOUT_CONFIRMED` / `MARKET_STRUCTURE_IS` над операндом
`MARKET_STRUCTURE`). Тест эффективности
рынка (ER) — через `INDICATOR_COMPARE` над ER-операндом каталога
(`EFFICIENCY_RATIO`), в обе стороны (`LT` — шум/range, `GT` — тренд);
выделенного `EFFICIENCY_BELOW_THRESHOLD` нет (свёрнут как чистый алиас —
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`). Контекстный
whitelist — create-валидация (400). Анти-whipsaw — операнд-уровневый
(сглаживающие периоды индикаторов, переиспользуемые по `key`; структурный
`breakoutConfirmationBars`); отдельного фаза-дебаунса нет.

Темпоральное правило фазы — дверь на будущее (сейчас не реализуется):
понадобится позже — вводить читающим **готовую историю структуры** (не
фазы), с явной квалификацией «stateless = без истории фаз» и
per-`ruleType` контрактом источника. В entry-контексте (где `MarketPhase`
есть в данных) `TREND_CHANGED` не затронут — там остаётся как есть.

### StrategyIndicatorSetting
`key` (стабильный ключ настройки — по нему ссылается индикаторный
операнд условия, `indicatorKey`), `indicatorType: IndicatorValue.Type`
(= `type` в форме ввода; дискриминатор подтипа `params` — см.
§IndicatorParams), `params: IndicatorParams`, `destiny:
Destiny`, `expirationDuration`. Доменный `timeframe` и `warmup` живут
**внутри `params`** (см. §IndicatorParams; контракт —
`docs/decisions/strategy-condition-authoring-contract.md`). `Destiny`:
`MARKET_PHASE`, `ENTRY_CONDITION`, `ACTION_PRICE`, `PROTECTION`,
`EXIT_CONDITION`. Используется внутри `StrategyMarketPhaseSetting`
(для фазы) и внутри `StrategyDetail` (после выбора детали).

### IndicatorParams (abstract) + наследники
База: `timeframe: TimeFrame` (доменный таймфрейм серии), `warmup`
(опциональный override — см. ниже). Собственного поля-типа у базы
нет: дискриминатор подтипа — `indicatorType` настройки-владельца
(`StrategyIndicatorSetting`), маппится Jackson `EXTERNAL_PROPERTY`
и в JSON-payload `params` не дублируется (единственный источник
тега — `docs/rules/persistence-representation.md`). Наследники несут
только математические параметры по типу: `AtrParams(period)`,
`EmaParams(period)`, `RsiParams(period)`,
`MacdParams(fastPeriod, slowPeriod, signalPeriod)`,
`BollingerBandsParams(period, deviationMultiplier)`,
`StochasticParams(kPeriod, dPeriod, smoothPeriod)`,
`ObvParams(enabled)`, `EfficiencyRatioParams(period)` (ER — оконный,
`warmup = period`; `EFFICIENCY_RATIO` — fork A,
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`).
Волатильность отдельной сущностью не
моделируется — через индикаторы ATR / Bollinger bandwidth.

`warmup` по умолчанию **выводится** реализацией индикатора из
`indicatorType` + `period` (оконные — `= period`; рекурсивные
EMA/RSI/ATR — кратно `period`; MACD — от старшего периода). Автор может
задать явный override в `params`; эффективный `warmup = override ??
derived`. Create-валидация (шаг 2) проверяет override против
упрощённого минимума по типу (окно/рекурсивные → `period`; MACD →
`slow + signal`; стохастик — сумма окон; OBV → 1); настоящий derive —
у реализаций индикаторов (шаг 3). Потребитель — candle-loading
(глубина истории для прогрева,
`docs/processes/candle-loading.md`); runtime-пропуск разгонной зоны при
расчёте — `docs/components/IndicatorJob.md` §Warmup. Контракт —
`docs/decisions/strategy-condition-authoring-contract.md`.

### StrategyMarketStructureSetting
`key` (на него ссылаются операнд market-structure и «мягкие» ссылки
JSON-листьев — поле `structureKey`, см. §StrategyConditionOperand),
`timeframe`, `efficiencyRatioKey`, `atrKey`, `params:
MarketStructureParams`, `destiny: Destiny`, `expirationDuration`.
`Destiny`: те же 5 значений, что у indicator (MARKET_PHASE /
ENTRY_CONDITION / ACTION_PRICE / PROTECTION / EXIT_CONDITION).

`efficiencyRatioKey` / `atrKey` — «мягкие» ссылки (по `key`) на настройки
каталожных индикаторов **того же контейнера**, которые резолвер
потребляет готовыми входами (fork-A): ER — дискриминатор тренд/шум, ATR —
пол толеранса уровней (D3). `null` → резолвер использует внутренний прокси
(ER) / fallback на долю цены (ATR-толеранс). Объявлен, но не готов /
устарел → консервативный `UNKNOWN` (job, не proxy). Эти ключи **не входят**
в идентичность конфигурации структуры (`timeframe` + canonical-`params`) —
краевой случай STRUCT-Q2; грунт —
`docs/decisions/derived-market-data-code-increments.md`.

Поля `structureType` у настройки **нет**: `MarketStructure.Type` —
**выход** расчёта (`MarketStructureResolver` его выводит), не вход
настройки; идентичность конфигурации структуры = `timeframe` +
canonical-`params` (вид расчёта один — см.
`docs/models/domain/other/MarketStructure.md` §Правила хранения,
`docs/decisions/market-data-result-identity-keying.md`).

### MarketStructureParams
`lookbackBars`, `minTouches`, `minRangeWidthPercents`,
`maxRangeWidthPercents`, `breakoutBufferPercents`,
`breakoutConfirmationBars`, `swingLookbackBars`,
`trendEfficiencyThreshold`, `levelToleranceAtrMultiplier`.

- `trendEfficiencyThreshold: BigDecimal` — порог ER тренда (ER ≥ порога →
  тренд-сила vs диапазон, D2).
- `levelToleranceAtrMultiplier: BigDecimal` — множитель `k` в толерансе
  кластеризации уровней (`толеранс = k·ATR`, D3; при необъявленном ATR —
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
| `riskPerTradePercent` | `BigDecimal` | Риск на сделку, % от капитала. |
| `targetRiskRewardRatio` | `BigDecimal` | High-level ориентир R/R. |
| `indicatorSettings` | `List<StrategyIndicatorSetting>` | После выбора detail (ATR для SL, RSI для ENTRY и т.д.). |
| `marketStructureSettings` | `List<StrategyMarketStructureSetting>` | После выбора detail. |
| `stepsByStatus` | `Map<Deal.Status, List<StrategyStep>>` | Шаги, сгруппированные по статусу сделки. |

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
indicator-vs-indicator допускается — базовый кейс кроссовера). Убраны
с правила (несёт операнд / источник): rule-level `sourceType`,
rule-level `timeframe`, объектные ссылки `indicatorSetting` /
`marketStructureSetting`.

`StrategyConditionRuleType`: `NO_OPEN_POSITION`, `NO_ACTIVE_DEAL`,
`ENTRY_ORDER_FINALIZED`, `POSITION_OPENED`, `ATTACHED_STOP_LOSS_EXISTS`,
`MAIN_PROTECTION_EXISTS`, `PROFIT_PERCENTS_REACHED`,
`LOSS_PERCENTS_REACHED`, `RANGE_BREAKOUT_CONFIRMED`, `TREND_CHANGED`,
`MARKET_PHASE_IS`, `MARKET_STRUCTURE_IS`,
`INDICATOR_COMPARE`, `PRICE_COMPARE`, `CROSSOVER`, `VOLUME_FILTER_PASSED`,
`CANDLE_CLOSED`. (`SIGNAL_SCORE_REACHED` удалён —
`docs/decisions/strategy-signal-is-entry-condition.md`;
`EFFICIENCY_BELOW_THRESHOLD` удалён — чистый алиас, свёрнут в
`INDICATOR_COMPARE` над ER-операндом каталога,
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`; критерий
sugar-vs-алиас — `docs/rules/condition-ruletype-granularity.md`.) `MARKET_STRUCTURE_IS`
(тест `MarketStructure.Type` равенством, зеркало `MARKET_PHASE_IS`) введён
редизайном условной фазы; **остаётся именованным `ruleType`, не
сворачивается** (enum-равенство ≠ числовой `INDICATOR_COMPARE`: разные
валидационные контракты, генерик-`ENUM_COMPARE` в грамматике нет — точечный
вердикт, `docs/rules/condition-ruletype-granularity.md`). Операнд —
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
`TIME`, `CONSTANT`. (`SIGNAL` удалён —
`docs/decisions/strategy-signal-is-entry-condition.md`.)

`StrategyConditionOperator`: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`,
`BETWEEN`, `NOT_BETWEEN`, `CROSSED_ABOVE`, `CROSSED_BELOW`, `IS_TRUE`,
`IS_FALSE`, `EXISTS`, `NOT_EXISTS`.

### StrategyConditionOperand
Самоописательный: `sourceType: StrategyConditionSourceType` + ссылка /
значение по источнику (зафиксировано на `CODE` шага 2):
- `valueType: ConstantValueType` (`NUMBER` / `PERCENT` / `ENUM` /
  `BOOLEAN`) — только у `CONSTANT` (там обязателен); у вычисляемых
  (`INDICATOR` / `PRICE` / `MARKET_STRUCTURE` / `MARKET_PHASE`) не
  пишется — тип подразумевается источником.
- `value: String` — единый литерал `CONSTANT` (строковое
  представление, интерпретируется по `valueType`); у вычисляемых
  источников значение приходит в рантайме (evaluator).
- Ссылки per-source: индикаторный операнд — `indicatorKey`; операнд
  market-structure — `structureKey`; ценовой операнд несёт
  `priceSource: StrategyPriceSource`.
- `indicatorComponent: IndicatorComponent` (только у `INDICATOR`) —
  адресный компонент многокомпонентного индикатора: какую часть
  сравнивать. MACD — `MACD_LINE`/`SIGNAL_LINE`/`HISTOGRAM`; Stochastic —
  `STOCH_K`/`STOCH_D`; Bollinger — `UPPER_BAND`/`MIDDLE_BAND`/`LOWER_BAND`/
  `BANDWIDTH`/`PERCENT_B`. **Обязателен** для многокомпонентных типов
  (MACD/Stochastic/Bollinger), **запрещён** для одно-компонентных
  (EMA/RSI/ATR/OBV/`EFFICIENCY_RATIO`), проверяется на совместимость с
  типом индикатора (create-валидация, 400; справочник «тип → компоненты» —
  `util.IndicatorComponents`). Снимает масштаб-зависимость абсолютного
  compare многокомпонентного индикатора (D1, зеркало OBV-принципа
  относительных форм — `docs/decisions/derived-market-data-code-increments.md`).

### Объёмные условия (OBV / `VOLUME_FILTER_PASSED`)

Зафиксировано торговым грунтом (`docs/decisions/volume-condition-semantics.md`):

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
`indicatorKey` (ключ ATR-настройки, для `ATR_PERCENT`), `structureKey`
(ключ настройки структуры, для `MARKET_STRUCTURE_BUFFER_PERCENT`:
swing/range/support/resistance).

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
CREATE/AMEND/CANCEL; POSITION — только CLOSE_FULL.

**Линия реза валидатора (create / activate).** Структурно-ссылочные
пункты (1-3, 8: наличие/уникальность `key`, разрешённость ссылок в
рамках detail) проверяются на **create (400)** в шаге 2. Пункты
семантики действий (4-7, 9-12: `targetActionKey` для AMEND/CANCEL,
ORDER↔ORDER / ALGO↔ALGO, `CLOSE_FULL`/partial-exit) опираются на
незрелую в шаге 2 модель команд/сделок/FSM и отложены — до шагов 4/7
и/или на **activate (422)** как «готова к запуску». Числовые торговые
поля (`riskPerTradePercent`, `allocationPercents`, …) на create
ограничиваются только структурно — невозможные значения (отрицательный
риск; доля вне [0; 100] там, где поле — доля) → 400; торгово-суждённые
диапазоны (консервативные ориентиры риска и т. п.) — семантика
activate, на create не проверяются. Материализация
«одной реализации» — через Strategy API (`POST`/`GET`/`PUT`).
Обоснование — `docs/decisions/strategy-materialization-and-validation.md`.
Сам компонент-валидатор и Strategy API — артефакты под-шага `CODE`.

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

Дерево персистится **реляционным каркасом** для узлов-контейнеров и
каркасных узлов (`Strategy` (root), `StrategyMarketPhaseSetting`,
`StrategyDetail`, `StrategyStep`, `StrategyAction`): каждый такой узел —
строка/таблица с `id`, объектные связи между ними — через FK, загрузка
дерева целиком — через `@EntityGraph` / `JOIN FETCH` (без N+1).
Таблицы: `strategies`, `strategy_market_phase_settings`,
`strategy_details`, `strategy_steps`, `strategy_actions` + таблицы
видов действий (имена таблиц — во множественном числе, общее правило
`.claude/rules/codestyle.md` §Схема БД). На `strategies` — частичный
UNIQUE-индекс «одна `ACTIVE` на инструмент» (БД-страховка инварианта
lifecycle).
**Листовые настройки рыночных данных (`StrategyIndicatorSetting`,
`StrategyMarketStructureSetting`) и их `params` — JSONB** на строке
своего контейнера, отдельных строк/таблиц у них нет (см. §Настройки
рыночных данных ниже). **Условие шага (`StrategyCondition` с `rules`
и операндами) — JSONB** на строке `strategy_steps` (см. §Условие ниже).
Обоснование развилок и сознательный отход от
архива — `docs/decisions/strategy-tree-persistence.md`; общее правило
представления сущностей в БД —
`docs/rules/persistence-representation.md`.

### Настройки рыночных данных (`StrategyIndicatorSetting` / `StrategyMarketStructureSetting`)
Хранятся **JSONB**, не реляционными строками: контейнеры
(`StrategyMarketPhaseSetting`, `StrategyDetail`) несут свои
`indicatorSettings` / `marketStructureSettings` JSON-массивами на
собственной строке. Своего `id`/таблицы у настройки нет; на неё
ссылаются **по `key`** (операнд условия `indicatorKey` / `structureKey`,
мягкие ссылки JSON-листьев `stopLossSettings`/`placement`) — резолвит
приложение. `id` настройки в рантайме не используется (там работает
`StrategyAction.id` через `DealActionState`). Уникальность `key`
настройки в пределах контейнера — проверка приложения по JSON-массиву,
не DB-UNIQUE.

`params` настройки (`IndicatorParams` + 7 наследников;
`MarketStructureParams`) едут внутри того же JSON (только непустые
значения), без отдельных таблиц params и без inheritance-маппинга — в
коде иерархия типов сохраняется. Дискриминатор подтипа
`IndicatorParams` в payload не дублируется — тег несёт `indicatorType`
настройки-владельца (Jackson `EXTERNAL_PROPERTY`, см. §IndicatorParams).
`phaseRules` (список клауз `StrategyMarketPhaseRule`, каждая с вложенным
`condition: StrategyCondition`) — **JSONB-колонка `phase_rules`** на
реляционной строке `strategy_market_phase_settings` (условие внутри
клаузы — тот же JSONB, что и `condition` шага, см. ниже §Условие).
Отдельного `params`-объекта/колонки у контейнера нет — `MarketPhaseParams`
распущен редизайном условной фазы
(`docs/decisions/market-phase-conditional-classification.md`). Валидацию
полей и контекстный whitelist операндов/`ruleType` делает приложение. Обоснование (почему настройки не
реляционные узлы) — `docs/decisions/strategy-tree-persistence.md`.

### Действия (`StrategyAction`)
Наследование `JOINED`: базовая таблица `strategy_actions`
(`id`, `strategy_step_id`, `strategy_detail_id`, `action_kind`, `key`,
`action_type`, `level`, `target_action_key`, `target_action_id`) +
таблицы по видам: `strategy_order_actions`,
`strategy_algo_order_actions`, `strategy_position_actions` (у позиции
собственных полей нет). Родитель действия — `strategy_step_id`
(FK → `strategy_steps`); `strategy_detail_id` — денормализованный FK →
`strategy_details` ради DB-`UNIQUE(strategy_detail_id, key)`
(уникальность `key` действия — в рамках `StrategyDetail`, через
несколько шагов, `UNIQUE` через join невозможен; см. §Связь с
DealActionState). Денормализация безопасна при immutable-дереве — как
у `target_action_id`. Self-ссылка действия хранится **двумя** колонками базовой таблицы:
`target_action_key` (логический ключ — форма ввода и чтения) и
`target_action_id` (self-FK `→ strategy_actions.id`, deferrable;
резолвится при сохранении; см. §Внутридеревные ссылки). Вложенные настройки действий
(`placement`, `attachedProtection`, `stopLossSettings`,
`trailingSettings`) — JSONB-поля на строках соответствующих видов.

### `stepsByStatus`
`Map<Deal.Status, List<StrategyStep>>` хранится плоскими строками
`strategy_steps` с колонками `strategy_detail_id` (FK), `deal_status`
(ключ map), `step_index` (порядок в списке). В домене Map
пересобирается группировкой по `deal_status` и сортировкой по
`step_index`. `marketDataExpiredSetting` шага — JSONB-поле.

### Условие (`StrategyCondition`)
Условие шага персистится **целиком JSONB-полем `condition`** на строке
`strategy_steps`: массив `rules` (у каждого правила `level` / `ruleType`
/ `operator` + простые поля), операнды — JSONB внутри того же объекта.
Отдельных таблиц `strategy_condition` / `strategy_condition_rule` нет;
перечень реляционных каркасных узлов не пополняется. Это дефолт правила
`docs/rules/persistence-representation.md` (условие навешано на
каркасный `strategy_step`, FK внутрь условия ниоткуда нет); решение и
отвергнутая реляционная альтернатива —
`docs/decisions/strategy-tree-persistence.md` §Условие. Ссылки
операнд → настройка остаются «мягкими» по `key` (см. §Внутридеревные
ссылки); evaluator десериализует условие в объектную модель независимо
от формы хранения.

### Внутридеревные ссылки
- Операнд условия → настройка (индикаторный операнд по `indicatorKey`,
  market-structure операнд по `structureKey`) — «мягкая»
  ссылка: ключ внутри структуры операнда (операнды — внутри
  condition-JSONB на строке `strategy_steps`, см. §Условие), резолвит
  приложение. STRAT-Q1 перенёс
  ссылку с правила на операнд (рантайм-резолв по `key` настройки, не
  rule-level FK; `docs/decisions/strategy-condition-authoring-contract.md`).
- Ссылки изнутри JSON-листьев (напр. `stopLossSettings` с
  `ATR_PERCENT` → индикаторная настройка по `indicatorKey`) —
  «мягкие»: ключ внутри JSON, резолвит приложение.
- `targetActionKey` при сохранении стратегии резолвится в self-FK
  `target_action_id → strategy_actions.id`; базовая таблица хранит **и**
  ключ, **и** id (денормализация, безопасная при immutable-записи).
  Защита БД — FK + CHECK `target_action_id <> id` (нет self-loop). FK
  здесь, в отличие от мягкой ссылки операнд→настройка (по `key`), —
  ради БД-защиты ссылки действие→действие (см. §key / targetActionKey
  и валидация).

### Типы числовых полей (зафиксировано на `CODE`)
`*Percents` / `*Score` / `*Ratio` / `*Multiplier` — `BigDecimal`
(numeric(36,18), Constants.Price); `*Bars` / `*Period` / `level` /
`warmup` — `Integer`. Риск-поля детали (`riskPerTradePercent`,
`targetRiskRewardRatio`) — nullable (у `NO_TRADE`-детали риска нет).

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
`.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-strategy.md`).
JSON-примеры — отдельный
файл `Strategy API examples.md` (форвард-заметка).
