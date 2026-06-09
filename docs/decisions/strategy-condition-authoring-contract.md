# Контракт авторинга условия стратегии

## На какой вопрос отвечает этот файл

Почему источник истины контракта авторинга условия
`StrategyCondition` — объектная settings-модель (а не инлайновая
строка-метка), как устроены правило и операнд, и откуда берётся
`warmup` индикатора.

## Контекст

Грамматика `StrategyConditionRule` была представлена в доках двумя
несведёнными способами: в модели
(`docs/models/domain/aggregate/Strategy.md`) индикатор в правиле
задавался объектной ссылкой `indicatorSetting`, а в `Strategy API` /
`Strategy API examples` — строкой-меткой `leftOperand` + инлайновый
`params`. Плюс избыточность представления: `sourceType` дублировался
на правиле и на операнде; `leftOperand: String` соседствовал со
структурированным `rightOperand: StrategyConditionOperand`;
`StrategyConditionOperand.valueType` пересекался с `sourceType`.
Пробел зафиксирован эскалацией Э2 на `DOCS_CHECK_2`, открыт как
STRAT-Q1; решён на разборе и применяется здесь.

## Принятое решение

**Источник истины — объектная settings-модель.** Согласуется с
решением о персистентности дерева (правило/операнд → настройка;
`docs/decisions/strategy-tree-persistence.md`). Строка-метка
`leftOperand` + инлайновый `params` из `Strategy API examples` — это
**форма ввода**, не второй источник истины: API при сохранении
резолвит метки в ссылки/настройки, домен работает только с
settings-моделью.

Доки задают **контракт авторинга** (для валидного правила — какие
поля заполнить); вычисление истинности правила — деталь evaluator'а
(downstream, `StrategyConditionEvaluator`), в модели не фиксируется.

### Настройка индикатора — `{ key, type, params }`

Индикаторы объявляются в стратегии явным набором настроек формы
`{ key, type, params }`:

- `key` — стабильный ключ настройки; по нему ссылается индикаторный
  операнд условия (`indicatorKey`).
- `type` — `IndicatorValue.Type`.
- `params` — JSONB (хранение — `strategy-tree-persistence.md`),
  мапится в типизированный `IndicatorParams` и валидируется в коде.
  Содержит: доменный `timeframe`; математические параметры по типу
  (`period`; MACD — `fast/slow/signal`; Bollinger — `period/stddev`
  и т. п.); опционально `warmup`.

`timeframe` пока живёт в `params` (валидируется в коде, через JSONB
запрашивается). Поднять в отдельную колонку — позже, по потребности
candle-loading (схемная валидация / индексированные запросы «какие
серии грузить»).

**Отвергнутая альтернатива.** Инлайновые `params` на правиле как
источник истины — дублируют конфиг индикатора в каждом правиле и
ломаются на indicator-vs-indicator (непонятно, чей `period`).

**Отвергнутая альтернатива.** `type`-строкой `"EMA(15, 200)"` —
stringly-typed: позиционно, требует парсера на тип, не валидируется и
не индексируется по полю; бьётся с уже принятым (операнды — структурой,
настройки — JSONB-объекты `{ key, type, params }` внутри контейнера;
ревизия `GAPS_CLOSE_3` — `strategy-tree-persistence.md`).

### `warmup` — выводимый по умолчанию, с override

`warmup` по умолчанию **выводится** реализацией индикатора из `type` +
`period`:

- оконные индикаторы — `warmup = period`;
- рекурсивные (EMA / RSI / ATR) — кратно `period`;
- MACD — от старшего периода.

Автор может задать **явный override** в `params`. Эффективный
`warmup = override ?? derived`. Потребитель — candle-loading: задаёт
глубину истории, нужную для прогрева
(`docs/processes/candle-loading.md`). Runtime-пропуск warmup-зоны при
расчёте — ответственность `IndicatorJob` (`docs/components/IndicatorJob.md`
§Warmup); declared `warmup` в `params` — объявленная глубина для
загрузки.

### Правило (`StrategyConditionRule`) — единая структура, операнды опциональны

Единая структура; операнды **опциональны** (левый / правый / оба / нет
— отсутствующий не пишется):

- **доменные правила** — плоские: `ruleType` [+ простые поля, напр.
  `percents`];
- **сравнивающие правила** — симметричные структурированные операнды
  (`leftOperand` / `rightOperand`) + `operator`.

Любой источник — на любой стороне (число слева или справа,
indicator-vs-indicator допускается — базовый кейс кроссовера). Убраны
с правила (несёт операнд / источник): rule-level `sourceType` (операнд
самоописателен), rule-level `timeframe` (живёт на источнике/операнде),
объектные ссылки `indicatorSetting` / `marketStructureSetting` (ссылку
несёт операнд).

### Операнд (`StrategyConditionOperand`) — самоописательный

`sourceType` + ссылка/значение по источнику (форма зафиксирована на
`CODE` шага 2):

- `valueType: ConstantValueType` (`NUMBER` / `PERCENT` / `ENUM` /
  `BOOLEAN`) — только там, где тип **не выводится** из `sourceType`:
  у `CONSTANT` обязателен; у вычисляемых (`INDICATOR` / `PRICE` /
  `MARKET_STRUCTURE` / `MARKET_PHASE`) не пишется — тип
  подразумевается источником.
- `value: String` — единый литерал `CONSTANT` (строковое
  представление, интерпретируется по `valueType`); у вычисляемых
  источников значение приходит в рантайме (evaluator).
- Ссылки per-source: индикаторный операнд — `indicatorKey`;
  операнд market-structure — `structureKey`; ценовой операнд несёт
  `priceSource: StrategyPriceSource`. Те же имена ключей-ссылок — у
  «мягких» ссылок JSON-листьев (`StopLossSettings`,
  `StrategyPricePlacement`).

**Отвергнутая альтернатива.** `valueType` на всех операндах —
избыточен у вычисляемых (`INDICATOR` + `INDICATOR_VALUE` — дубль).

**Отвергнутая альтернатива (закрыта на `CODE`).** Generic `key` вместо
per-source имён — теряет самоописательность операнда; раздельные
`name`/`stringValue`/`numberValue` для литерала — три nullable-поля
там, где заполняется ровно одно.

### Иммутабельность

Правок стратегии на месте нет; изменение любого поля (`period`,
`warmup`, правила, …) = **новая версия** стратегии (общий инвариант
strategy-layer).

## Per-`ruleType` контракт — зафиксированный минимум (`CODE` шага 2)

Create-валидация (400) проверяет авторинг-минимум использованных
типов; контракт дозаполняется при реализации каждого следующего
`ruleType`:

- `PROFIT_PERCENTS_REACHED` / `LOSS_PERCENTS_REACHED` — требуется
  `percents`;
- `CANDLE_CLOSED` — требуется простое поле `timeframe` (какой
  таймфрейм закрыт);
- `RANGE_BREAKOUT_CONFIRMED` — структурно-событийное: ссылается на
  `MarketStructure` по `structureKey`, **читает готовым** предвычисленное
  событие пробоя (`breakoutEvent`); `percents` у условия **нет** —
  детекция (буфер + подтверждение) на стороне резолвера
  (`MarketStructureParams.breakoutBufferPercents`/`breakoutConfirmationBars`,
  `docs/components/MarketStructureResolver.md`). Точная форма `breakoutEvent`
  и per-`ruleType` поля — `CODE`;
- `MARKET_PHASE_IS` — operator + оба операнда, один из них `CONSTANT`
  с фазой (`ENUM`-значение валидно по `MarketPhase.Type`);
- `MARKET_STRUCTURE_IS` — operator + оба операнда, операнд
  `MARKET_STRUCTURE` по `structureKey` + `CONSTANT` со структурой
  (`ENUM`-значение валидно по `MarketStructure.Type`); зеркало
  `MARKET_PHASE_IS`, введён редизайном условной фазы. **Точечный вердикт
  sugar-vs-алиас: остаётся именованным, не сворачивается** (enum-равенство
  ≠ числовой `INDICATOR_COMPARE`, генерик-`ENUM_COMPARE` в грамматике нет —
  `docs/rules/condition-ruletype-granularity.md`);
- `INDICATOR_COMPARE` / `PRICE_COMPARE` — operator + оба операнда,
  хотя бы один с требуемым источником (INDICATOR / PRICE);
- `CROSSOVER` — operator `CROSSED_ABOVE` / `CROSSED_BELOW` + оба
  операнда;
- операнды: `INDICATOR` → `indicatorKey` существует в настройках того же
  контейнера; `MARKET_STRUCTURE` → `structureKey` существует;
  `PRICE` → валидный `priceSource`; `CONSTANT` → `valueType` + `value`.
  Контейнер ссылки — `StrategyDetail` для entry-условий, либо
  `StrategyMarketPhaseSetting` для правил классификации фазы (ниже).
- **OBV-операнд** (`INDICATOR` типа `OBV`) — только относительные формы
  (`CROSSED_ABOVE`/`CROSSED_BELOW` против серии/своей скользящей,
  сравнение с другой вычисляемой серией); абсолютный compare OBV с
  `CONSTANT` отклоняется (OBV кумулятивен, уровень нестабилен). Объёмное
  условие (OBV / `VOLUME_FILTER_PASSED`) — подтверждающий фильтр, не
  единственное основание `ENTRY` (авторинг-правило, чек-лист СТ-1).
  Грунт и альтернативы — `docs/decisions/volume-condition-semantics.md`;
  крипто-надёжность объёма открыта (IND-Q1).

### Переиспользование грамматики в классификации фазы

`MarketPhase.Type` определяется авторскими условиями той же грамматики
(`docs/decisions/market-phase-conditional-classification.md`): клаузы
`StrategyMarketPhaseSetting.phaseRules` несут `condition:
StrategyCondition`. Контекст — **классификация фазы** (до выбора детали,
без сделки): операнды только `INDICATOR`/`MARKET_STRUCTURE`/`PRICE`/
`CONSTANT`/`TIME` (по `key` из настроек той же `StrategyMarketPhaseSetting`),
без `MARKET_PHASE` (само-референция) и runtime-источников сделки;
`ruleType` — сравнивающие и структурно-событийные (вкл.
`MARKET_STRUCTURE_IS`; тест эффективности рынка (ER) — через
`INDICATOR_COMPARE` над ER-операндом каталога `EFFICIENCY_RATIO`,
выделенного `EFFICIENCY_BELOW_THRESHOLD` нет —
`docs/decisions/efficiency-ratio-as-catalog-indicator.md`), без
lifecycle-сделки и `MARKET_PHASE_IS`. Этот
контекстный whitelist — create-валидация (400). `MarketStructure` тем
самым **операнд** правил фазы (вычисляемый шаримый результат, на который
ссылаются), не вход скоринга.

## Что осталось открытым (контракт не блокирует)

- **percent-anchor** — «−N% относительно чего» (вход / предыдущая
  свеча / хай): чистая бизнес-семантика, вынесена в открытый вопрос
  **STRAT-Q4** (`.claude/work/questions/open-questions.md`).
- **Per-`ruleType` контракт остальных типов** (NO_OPEN_POSITION и
  прочие плоские без полей — тривиальны; VOLUME_FILTER_PASSED и др.) —
  дозаполняется при реализации, превентивно не перечисляется. Заводя
  новый именованный `ruleType`, проверяем критерий
  `docs/rules/condition-ruletype-granularity.md`: чистый алиас одного
  сравнения не заводим — выражаем генериком (`INDICATOR_COMPARE` и др.).
  `EFFICIENCY_BELOW_THRESHOLD` по этому критерию свёрнут в
  `INDICATOR_COMPARE` (`docs/decisions/efficiency-ratio-as-catalog-indicator.md`);
  `VOLUME_FILTER_PASSED` и `MARKET_PHASE_IS`/`MARKET_STRUCTURE_IS` —
  кандидаты для точечной проверки при фиксации их контрактов, пакетно
  не сворачиваются.

## Следствия

- `docs/models/domain/aggregate/Strategy.md` — §Условия переписан под
  контракт (правило без rule-level `sourceType`/`timeframe`/объектных
  ссылок; операнд самоописателен; `valueType`/`value` только у
  `CONSTANT`); §StrategyIndicatorSetting получает `key`, `timeframe` и
  `warmup` уходят в `params`; обновлён архитектурный инвариант о ключах
  настроек.
- `docs/decisions/strategy-tree-persistence.md` — бул «правило условия
  → настройка = FK» уточнён: ссылку несёт операнд по ключу (мягкая
  ссылка), rule-level FK снят.
- Закрывает эскалацию **Э2** (`GAPS_CLOSE_2`) и вопрос **STRAT-Q1**.

## Связи

- Модель — `docs/models/domain/aggregate/Strategy.md` (§Условия,
  §StrategyIndicatorSetting, §IndicatorParams).
- Персистентность дерева — `docs/decisions/strategy-tree-persistence.md`.
- Терминология «сигнала» (удаление `SIGNAL`) —
  `docs/decisions/strategy-signal-is-entry-condition.md`.
- Материализация и валидация —
  `docs/decisions/strategy-materialization-and-validation.md`.
- Потребитель `warmup` — `docs/processes/candle-loading.md`,
  `docs/components/IndicatorJob.md`.
- Критерий «именованный `ruleType` vs генерик-сравнение» —
  `docs/rules/condition-ruletype-granularity.md`.
- ER как операнд каталога (снят `EFFICIENCY_BELOW_THRESHOLD`) —
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md`.
- Семантика объёмных условий (OBV-операнд — относительные формы; объём —
  не единственное основание `ENTRY`) —
  `docs/decisions/volume-condition-semantics.md`.
- Открытый вопрос percent-anchor — STRAT-Q4
  (`.claude/work/questions/open-questions.md`).
