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

`sourceType` + ссылка/значение по источнику:

- `valueType` — только там, где тип **не выводится** из `sourceType`.
  У `CONSTANT` обязателен (`NUMBER` / `ENUM` / `PERCENT` / …); у
  вычисляемых (`INDICATOR` / `PRICE` / `MARKET_STRUCTURE` /
  `MARKET_PHASE`) не пишется — тип подразумевается источником.
- `value` (литерал) — только у `CONSTANT`; у вычисляемых источников
  значение приходит в рантайме (evaluator).
- индикаторный операнд ссылается на настройку по ключу `indicatorKey`;
  операнд market-structure — по ключу настройки структуры.

**Отвергнутая альтернатива.** `valueType` на всех операндах —
избыточен у вычисляемых (`INDICATOR` + `INDICATOR_VALUE` — дубль).

### Иммутабельность

Правок стратегии на месте нет; изменение любого поля (`period`,
`warmup`, правила, …) = **новая версия** стратегии (общий инвариант
strategy-layer).

## Что осталось открытым (контракт не блокирует)

- **percent-anchor** — «−N% относительно чего» (вход / предыдущая
  свеча / хай): чистая бизнес-семантика, вынесена в открытый вопрос
  **STRAT-Q4** (`.claude/work/questions/open-questions.md`).
- **Per-`ruleType` контракт полей** (какие поля под каждый `ruleType`,
  включая конкретные правила валидности комбинаций операндов) —
  инкрементальный: дозаполняется при реализации каждого `ruleType`,
  превентивно не перечисляется.
- **Нейминг поля-ссылки операнда** (per-source `priceKey` /
  `indicatorKey` / `structureKey` против одного generic `key`) и
  консолидация литерала `CONSTANT` (единое `value` по `valueType`
  вместо `name`/`stringValue`/`numberValue`) — инкрементальная деталь.

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
- Открытый вопрос percent-anchor — STRAT-Q4
  (`.claude/work/questions/open-questions.md`).
