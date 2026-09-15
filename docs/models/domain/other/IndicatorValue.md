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
  (`docs/architecture/market-data-collection.md` §«Пригодность для
  детекторов»); стратегии, которая их заказала, не существует, и привязка
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
| `indicatorConfigId` | `Long` | FK на идентичность вычисления (`indicator_configs.id`): тип, таймфрейм, канонические параметры. См. §«Ключевание — идентичностью вычисления». |
| `candleTimestamp` | `OffsetDateTime` | Время свечи, на которой рассчитан индикатор. |

Конкретное значение лежит в наследнике (по типу индикатора).

## Енум `Type`

`ATR`, `EMA`, `RSI`, `MACD`, `STOCHASTIC`, `BOLLINGER_BANDS`, `OBV`,
`EFFICIENCY_RATIO`.

`EFFICIENCY_RATIO` — мера эффективности/шума (Kaufman efficiency ratio):
скаляр ∈ [0,1] по окну (`= |чистый ход| / Σ|побарных ходов|`), ER→1 —
тренд, ER→0 — шум/боковик. Авторски-адресуемый операнд каталога (введён
fork A — `docs/rules/condition-ruletype-granularity.md`): на
него ссылаются условия (классификации фазы и входа) через
`INDICATOR_COMPARE`, и его же потребляет опциональный
шумовой-фильтр-вход `MarketStructureResolver` — единый шаримый ER, без
внутреннего пересчёта.

`OBV` — кумулятивный объёмный индикатор (On-Balance Volume): бегущая
сумма знакового объёма от старта расчёта. Абсолютный уровень нестабилен
(зависит от глубины загруженной истории и от масштаба/режима объёма),
поэтому **OBV-операнд условия ограничен относительными формами**
(`CROSSED_ABOVE`/`CROSSED_BELOW` против серии/своей скользящей,
направление/динамика); **абсолютный compare OBV с `CONSTANT` не
допускается** (`docs/models/domain/aggregate/Strategy.md`).
Стабильный абсолютный порог по объёму, если понадобится, — отдельный
**нормированный** операнд (volume oscillator / нормированный объём), не
OBV; сейчас не заведён (каталог расширяем по потребности).

## Наследники (значения по типу)

| Класс | Поля значения |
|---|---|
| `AtrValue` | `atr` |
| `EmaValue` | `ema` |
| `RsiValue` | `rsi` |
| `MacdValue` | `macdLine`, `signalLine`, `histogram` |
| `BollingerBandsValue` | `upperBand`, `middleBand`, `lowerBand`, `bandwidth`, `percentB` |
| `StochasticValue` | `k`, `d` |
| `ObvValue` | `obv` |
| `EfficiencyRatioValue` | `efficiencyRatio` |

Все числовые поля — `BigDecimal`. Волатильность отдельной сущностью не
моделируется — через `AtrValue` / `BollingerBandsValue.bandwidth`.

**Адресный компонент в условии (D1).** Многокомпонентные типы (`MACD`,
`STOCHASTIC`, `BOLLINGER_BANDS`) в операнде условия адресуются полем
`StrategyConditionOperand.indicatorComponent` — автор выбирает осмысленную
часть (например, `MACD_LINE`/`HISTOGRAM`, `STOCH_K`, `PERCENT_B`); снимает
масштаб-зависимость абсолютного compare. Одно-компонентные (`EMA`/`RSI`/
`ATR`/`OBV`/`EFFICIENCY_RATIO`) компонент не несут. Контракт и справочник
«тип → компоненты» — `docs/rules/strategy-condition-contract.md`,
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
- **Обязательны** четыре колонки идентичности: `indicator_type`,
  `instrument_id`, `indicator_config_id`, `candle_timestamp`.
- **Ключ уникальности** — `uk_indicator_value_identity`
  `(instrument_id, indicator_config_id, candle_timestamp)`: тот же ключ,
  что объявляет §«Ключевание — идентичностью вычисления».
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
  candle_timestamp)` — ключ по идентичности вычисления (см. §«Ключевание
  — идентичностью вычисления»).
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
