# IndicatorValue

## На какой вопрос отвечает этот файл

Что это за модель `IndicatorValue`: структура abstract-базы, наследники
по типам индикаторов, енум `Type`, правила хранения.

## Назначение

`IndicatorValue` — готовое значение технического индикатора,
рассчитанное `IndicatorJob` по закрытым свечам и настройке
`StrategyIndicatorSetting`. Persisted-модель рыночных данных, не про
бизнес-цикл сделки → `other` (см.
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
| `setting` | `StrategyIndicatorSetting` | Настройка стратегии, по которой рассчитано значение. |
| `candleTimestamp` | `OffsetDateTime` | Время свечи, на которой рассчитан индикатор. |

Конкретное значение лежит в наследнике (по типу индикатора).

## Енум `Type`

`ATR`, `EMA`, `RSI`, `MACD`, `STOCHASTIC`, `BOLLINGER_BANDS`, `OBV`.

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

Все числовые поля — `BigDecimal`. Волатильность отдельной сущностью не
моделируется — через `AtrValue` / `BollingerBandsValue.bandwidth`.

## Правила хранения

- `confirmed` и `warmup` не хранятся: `IndicatorJob` сохраняет только
  значения после warmup-зоны (см.
  `docs/components/IndicatorJob.md`).
- Индикаторы считаются только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, strategy_indicator_setting_id,
  candle_timestamp)`.
- Свежесть значения проверяет `MarketDataExpirationChecker` по
  `StrategyIndicatorSetting.expirationDuration` (правило —
  `docs/rules/market-data-freshness.md`).
