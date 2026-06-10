# IndicatorValue

## На какой вопрос отвечает этот файл

Что это за модель `IndicatorValue`: структура abstract-базы, наследники
по типам индикаторов, енум `Type`, правила хранения.

## Назначение

`IndicatorValue` — готовое значение технического индикатора,
рассчитанное `IndicatorJob` по закрытым свечам для конкретной
**настройки-владельца** `StrategyIndicatorSetting`. Значение ключуется
настройкой-владельцем (одна типизированная FK `strategyIndicatorSettingId`),
**не шарится** между настройками и не ключуется по идентичности
конфигурации — реестр `indicator_configs` убран ревизией трек D (см.
`docs/decisions/market-data-result-identity-keying.md`). Persisted-модель
рыночных данных, не про бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

Потребители читают готовые значения и **не** считают индикаторы сами:
`StrategyConditionEvaluator` (условия), `StrategyActionCalculator` /
`PriceCalculator` (цены, например SL = entry − 1.5·ATR),
`MarketPhaseClassifier` (классификация фазы на чтение через
`MarketPhaseService`). Раздачей готовых значений занимается
`docs/components/IndicatorService.md`.

## Структура (abstract база)

Java abstract-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID значения. |
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `strategyIndicatorSettingId` | `Long` | FK на настройку-владельца `StrategyIndicatorSetting` (`strategy_indicator_settings.id`) — owner-ключевание (см. `docs/decisions/market-data-result-identity-keying.md`). |
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

**Адресный компонент в условии (D1).** Многокомпонентные типы (`MACD`,
`STOCHASTIC`, `BOLLINGER_BANDS`) в операнде условия адресуются полем
`StrategyConditionOperand.indicatorComponent` — автор выбирает осмысленную
часть (например, `MACD_LINE`/`HISTOGRAM`, `STOCH_K`, `PERCENT_B`); снимает
масштаб-зависимость абсолютного compare. Одно-компонентные (`EMA`/`RSI`/
`ATR`/`OBV`/`EFFICIENCY_RATIO`) компонент не несут. Контракт и справочник
«тип → компоненты» — `docs/decisions/strategy-condition-authoring-contract.md`,
грунт — `docs/decisions/derived-market-data-code-increments.md`.

## Правила хранения

- `confirmed` и `warmup` не хранятся: `IndicatorJob` сохраняет только
  значения после warmup-зоны (см.
  `docs/components/IndicatorJob.md`).
- Индикаторы считаются только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, strategy_indicator_setting_id,
  candle_timestamp)` (ключ по настройке-владельцу — owner-ключевание, см.
  `docs/decisions/market-data-result-identity-keying.md`).
- Свежесть значения проверяет `MarketDataExpirationChecker` по
  `expirationDuration` **настройки-владельца** `StrategyIndicatorSetting`:
  у строки результата ровно один владелец, под него и оценивается
  свежесть (правило — `docs/rules/market-data-freshness.md`).
- **Точка отсчёта свежести (`referencePoint`) — `candleTimestamp`.**
  `expiredAt = candleTimestamp + ownerSetting.expirationDuration`
  считается **на чтение**, колонкой не хранится (единый механизм без
  хранимого состояния свежести; `docs/rules/market-data-freshness.md`).
- **Retention:** значения не чистятся (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
