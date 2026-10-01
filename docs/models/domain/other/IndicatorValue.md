# IndicatorValue

## На какой вопрос отвечает этот файл

Что это за модель `IndicatorValue`.

## Назначение

`IndicatorValue` — готовое значение технического индикатора,
рассчитанное по закрытым свечам. Persisted-модель рыночных данных, не про
бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

## Ключевание — идентичностью вычисления

**Значение ключуется идентичностью вычисления:** тип индикатора,
таймфрейм и канонические параметры. Реестр идентичностей живёт в
`market-data`; потребитель спрашивает «ATR(14) на 1H по инструменту X» и
получает значение, посчитанное **один раз** для всех, кому оно нужно.

**Прежняя схема — привязка строки результата к настройке стратегии
внешним ключом — снята.** Она была верна в монолите и невыразима в
сервисной конструкции по двум независимым причинам:

- **настройка живёт в базе другого сервиса.** `StrategyIndicatorSetting`
  принадлежит `strategies`, значение — `market-data`
  (`.claude/rules/knowledge-ownership-by-service.md`), а внешнего ключа
  через границу сервиса не бывает
  (`docs/architecture/data-ownership.md`);
- **у фич по всему листингу владельца нет вовсе.** Детекторам советника
  нужны фичи «по всему листингу, а не только по торгуемым инструментам»
  (дом — требования к сбору, `docs/architecture/market-data-collection.md`:
  пригодность для детекторов); стратегии, которая их заказала, не существует, и привязка
  к настройке не даёт такой строке места в таблице.

**Что смена НЕ отменяет.** Срок свежести по-прежнему задаёт стратегия
параметром своей настройки — но применяет его **потребитель** к
`candleTimestamp` значения, а не строка результата к себе
(`docs/rules/market-data-freshness.md`). Толерантность принадлежит
читателю: одно и то же значение для одной стратегии свежее, для другой —
уже нет.

**Отвергнуто:** непрозрачный ключ владельца строкой (одинаковые настройки
двух стратегий считались бы дважды, а `market-data` считал бы под ключ, о
смысле которого ничего не знает) и отказ от хранения производных вовсе
(противоречит инвентарю сервисов и лишает бэктест истории).

Потребители читают готовые значения и **не** считают индикаторы сами:
`StrategyConditionEvaluator` (условия), `StrategyActionCalculator` /
`PriceCalculator` (цены, например SL = entry − 1.5·ATR),
`MarketPhaseResolver` (классификация фазы на чтение через
`MarketPhaseService`). Раздачей готовых значений занимается
`docs/components/IndicatorService.md`.

## Структура (abstract база)

Java abstract-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID значения. |
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `indicatorConfigId` | `Long` | FK на идентичность вычисления (`indicator_configs.id`): тип, таймфрейм, канонические параметры; ею значение и ключуется. |
| `candleTimestamp` | `OffsetDateTime` | Время свечи, на которой рассчитан индикатор. |

Конкретное значение лежит в наследнике (по типу индикатора).

## Енум `Type`

`ATR`, `EMA`, `RSI`, `MACD`, `STOCHASTIC`, `BOLLINGER_BANDS`, `OBV`,
`EFFICIENCY_RATIO`.

`EFFICIENCY_RATIO` — мера эффективности/шума (Kaufman efficiency ratio):
скаляр ∈ [0,1] по окну (форма — `docs/spec/indicator-calculation.json`
(`efficiencyRatio`)), ER→1 —
тренд, ER→0 — шум/боковик. Авторски-адресуемый операнд каталога (введён
fork A — `docs/rules/condition-ruletype-granularity.md`): на
него ссылаются условия (классификации фазы и входа) через
`INDICATOR_COMPARE`, и его же потребляет опциональный
шумовой-фильтр-вход `MarketStructureResolver` — единый шаримый ER, без
внутреннего пересчёта.

`OBV` — кумулятивный объёмный индикатор (On-Balance Volume): бегущая
сумма знакового объёма **по ряду идентичности**. Проход продолжает сумму от
последнего записанного значения ряда, а не начинает её заново со своего
окна; ряд начинается нулём там, где записанного значения в окне прохода нет
(затравка и шаг — `docs/spec/indicator-calculation.json` (`obvSeed`,
`obvNext`)). Продолженный ряд прогрев прошёл до затравки, и бары окна после
неё прогревом не отсекаются (граница сохранения —
`docs/spec/indicator-calculation.json` (`indicatorValueStored`)). Поэтому внутри ряда разность значения и предыдущего записанного есть
знаковый объём его бара — на ней и стоят относительные формы операнда. Бар
с непроставленным объёмом при изменившемся закрытии значения не имеет, и
сумма продолжается через него без его вклада: «объём не добыт» не
сливается с «сделок не было» (`docs/rules/absent-value-semantics.md`);
реакция на недобытый объём — предмет добычи свечи, а не вычислителя
(`docs/rules/error-handling-policy.md`). Абсолютный уровень нестабилен
(зависит от момента, с которого ряд начат, и от масштаба/режима объёма),
поэтому **OBV-операнд условия ограничен относительными формами**
(`CROSSED_ABOVE`/`CROSSED_BELOW` против серии/своей скользящей,
направление/динамика); **абсолютный compare OBV с `CONSTANT` не
допускается** (`docs/models/domain/aggregate/Strategy.md`).
Стабильный абсолютный порог по объёму, если понадобится, — отдельный
**нормированный** операнд (volume oscillator / нормированный объём), не
OBV; сейчас не заведён (каталог расширяем по потребности).

## Наследники (значения по типу)

| Класс | Поля значения | Дом формулы |
|---|---|---|
| `AtrValue` | `atr` | `docs/spec/indicator-calculation.json` (`trueRange`, `wilderNext`, `indicatorWindowAverage`) |
| `EmaValue` | `ema` | `docs/spec/indicator-calculation.json` (`emaAlpha`, `emaNext`, `indicatorWindowAverage`) |
| `RsiValue` | `rsi` | `docs/spec/indicator-calculation.json` (`rsiGain`, `rsiLoss`, `wilderNext`, `rsiValue`) |
| `MacdValue` | `macdLine`, `signalLine`, `histogram` | `docs/spec/indicator-calculation.json` (`emaNext`, `macdLine`, `macdHistogram`) |
| `BollingerBandsValue` | `upperBand`, `middleBand`, `lowerBand`, `bandwidth`, `percentB` | `docs/spec/indicator-calculation.json` (`indicatorWindowAverage`, `bollingerVariance`, `bollingerUpperBand`, `bollingerLowerBand`, `bollingerBandwidth`, `bollingerPercentB`) |
| `StochasticValue` | `k`, `d` | `docs/spec/indicator-calculation.json` (`stochasticHighest`, `stochasticLowest`, `stochasticRawK`, `indicatorWindowAverage`) |
| `ObvValue` | `obv` | `docs/spec/indicator-calculation.json` (`obvNext`) |
| `EfficiencyRatioValue` | `efficiencyRatio` | `docs/spec/indicator-calculation.json` (`efficiencySignedMoveSum`, `efficiencyTotalMove`, `efficiencyRatio`) |

Все числовые поля — `BigDecimal`. Волатильность отдельной сущностью не
моделируется — через `AtrValue` / `BollingerBandsValue.bandwidth`.

**Формула поля, вид сглаживания и вид отклонения живут в спеке, а не
здесь.** Колонка выше называет величины, из которых поле собрано; развилки
формы — сглаживание Уайлдера у ATR и RSI, популяционное отклонение у полос
— закрыты нотами тех же величин.

**Вырожденное окно — штатное состояние, и значение на нём не производит
сигнала.** Плоский ряд и нулевой знаменатель встречаются на живом рынке
(стоящий инструмент, пауза торгов), поэтому вычислитель отвечает на них
объявленным значением, а не отказом и не пропуском бара. Значение выбирается
так, чтобы ни одно условие не прочло в нём движения: у центрированного
осциллятора — середина шкалы (RSI, стохастик, положение цены в полосах), у
меры тренда — ноль (эффективность хода). Константа у каждой величины своя —
ноты `docs/spec/indicator-calculation.json` (`rsiValue`, `stochasticRawK`,
`bollingerPercentB`, `efficiencyRatio`).

**Прогрев выводится по типу** — `docs/spec/indicator-calculation.json`
(`derivedWarmup`, `effectiveWarmup`, `indicatorValueStored`); кто пропускает
зону прогрева — `docs/components/IndicatorJob.md`.

**Адресный компонент в условии (D1).** Значение многокомпонентного типа
операнд условия адресует компонентом
(`StrategyConditionOperand.indicatorComponent`). Какие типы
многокомпонентны, какие компоненты допустимы у каждого и какое поле
значения компонент выбирает — дом `docs/rules/strategy-condition-contract.md`;
грунт — `docs/models/domain/other/MarketStructure.md`.

## Персистентность

- **Таблица** — `indicator_values`; гипертаблицей **не является**:
  идентичность строки — не момент, а тройка вычисления, и ряд читается
  окном по ней, а не по времени (`candles` и срезы — гипертаблицы, эта
  таблица нет).
- **Значения по типу — колонками, не JSONB.** Наследники хранятся плоско
  (`atr`, `ema`, `rsi`, `macd_line`, `signal_line`, `histogram`,
  `upper_band`, `middle_band`, `lower_band`, `bandwidth`, `percent_b`,
  `stoch_k`, `stoch_d`, `obv`, `efficiency_ratio`), тип строки называет
  `indicator_type`. Все значения — `numeric(36, 18)` и **обнуляемы**: у
  строки заполнены только колонки своего типа, у прочих значения нет
  (`docs/rules/absent-value-semantics.md`).
- **Обязательны** четыре колонки идентичности: `indicator_type`
  (`varchar(32)`, строкой значения перечня), `instrument_id` и
  `indicator_config_id` (`bigint`), `candle_timestamp` (`timestamptz`).
  Суррогатный ключ `id` — `bigserial`.
- **Ключ уникальности** — `uk_indicator_value_identity`
  `(instrument_id, indicator_config_id, candle_timestamp)`: тот же ключ —
  идентичность вычисления, которой значение ключуется.
- **Индекс чтения** — `ix_indicator_value_latest`
  `(instrument_id, indicator_config_id, candle_timestamp desc)`: обе
  тропы чтения берут последнее значение либо окно назад от последнего.
- **Ссылки** — `fk_indicator_value_config` на `indicator_configs (id)` и
  `fk_indicator_value_instrument` на `instruments (id)`.
- **Audit-поля** — базовые шесть (`docs/models/domain/other/Auditable.md`).

## Правила хранения

- `confirmed` и `warmup` не хранятся: `IndicatorJob` сохраняет только
  значения после warmup-зоны (см.
  `docs/components/IndicatorJob.md`).
- Индикаторы считаются только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, indicator_config_id,
  candle_timestamp)` — ключ по идентичности вычисления, которой значение
  ключуется.
- Свежесть оценивает **потребитель** по `expirationDuration` своей
  настройки: строка результата срока не несёт и о стратегии не знает
  (правило — `docs/rules/market-data-freshness.md`).
- **Точка отсчёта свежести (`referencePoint`) — `candleTimestamp`.**
  Само устаревание считается **на чтение**, колонкой не хранится (единый
  механизм без хранимого состояния свежести); правило —
  `docs/rules/market-data-freshness.md`, форма —
  `docs/spec/market-data-freshness.json`.
- **Retention:** производные следуют за глубиной свечей, на которых
  посчитаны, — `docs/rules/market-data-retention.md` (дом).
