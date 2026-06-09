# IndicatorValue

## На какой вопрос отвечает этот файл

Что это за модель `IndicatorValue`: структура abstract-базы, наследники
по типам индикаторов, енум `Type`, правила хранения.

## Назначение

`IndicatorValue` — готовое значение технического индикатора,
рассчитанное `IndicatorJob` по закрытым свечам для **конфигурации
расчёта** (тип + `timeframe` + canonical-`params`), зарегистрированной в
реестре `indicator_configs`. Значение **шарится** всеми настройками
`StrategyIndicatorSetting`, которые эту конфигурацию запрашивают (ключ —
по идентичности считаемого, не по настройке; см.
`docs/decisions/market-data-result-identity-keying.md`). Persisted-модель
рыночных данных, не про бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

Потребители читают готовые значения и **не** считают индикаторы сами:
`StrategyConditionEvaluator` (условия), `StrategyActionCalculator` /
`PriceCalculator` (цены, например SL = entry − 1.5·ATR), `MarketPhaseJob`
(расчёт фазы). Раздачей готовых значений занимается
`docs/components/IndicatorService.md`.

## Структура (abstract база)

Java abstract-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID значения. |
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `configId` | `Long` | Ссылка на конфигурацию расчёта (тип + `timeframe` + canonical-`params`) в реестре `indicator_configs` (см. `docs/decisions/market-data-result-identity-keying.md`). |
| `candleTimestamp` | `OffsetDateTime` | Время свечи, на которой рассчитан индикатор. |

Конкретное значение лежит в наследнике (по типу индикатора).

## Енум `Type`

`ATR`, `EMA`, `RSI`, `MACD`, `STOCHASTIC`, `BOLLINGER_BANDS`, `OBV`,
`EFFICIENCY_RATIO`.

`EFFICIENCY_RATIO` — мера эффективности/шума (Kaufman efficiency ratio):
скаляр ∈ [0,1] по окну (`= |чистый ход| / Σ|побарных ходов|`), ER→1 —
тренд, ER→0 — шум/боковик. Авторски-адресуемый операнд каталога (введён
fork A — `docs/decisions/efficiency-ratio-as-catalog-indicator.md`): на
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
допускается** (`docs/decisions/volume-condition-semantics.md`).
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

## Правила хранения

- `confirmed` и `warmup` не хранятся: `IndicatorJob` сохраняет только
  значения после warmup-зоны (см.
  `docs/components/IndicatorJob.md`).
- Индикаторы считаются только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, config_id, candle_timestamp)`
  (ключ по идентичности считаемого через реестр конфигураций — см.
  `docs/decisions/market-data-result-identity-keying.md`).
- Свежесть значения проверяет `MarketDataExpirationChecker` по
  `expirationDuration` **запрашивающей** `StrategyIndicatorSetting`:
  значение шарится между настройками, свежесть оценивается под каждую
  запрашивающую настройку в runtime (правило —
  `docs/rules/market-data-freshness.md`).
- **Точка отсчёта свежести (`referencePoint`) — `candleTimestamp`.**
  `expiredAt = candleTimestamp + askingSetting.expirationDuration`
  считается **на чтение**, не хранится колонкой; на общей строке (ключ по
  `config_id`) единого `expiredAt` нет — своё под каждую запрашивающую
  настройку (`docs/rules/market-data-freshness.md`).
- **Retention:** значения не чистятся (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
