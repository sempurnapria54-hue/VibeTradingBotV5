# MarketStructure

## На какой вопрос отвечает этот файл

Что это за модель `MarketStructure`: структура, енум `Type`, вложенные
ценовые уровни `MarketPriceLevel`, правила хранения и актуальности.

## Назначение

`MarketStructure` — готовый результат расчёта структуры рынка
(уровни, диапазоны, тренд), рассчитанный `MarketStructureJob` по закрытым
свечам для **конфигурации расчёта** (тип + `timeframe` +
canonical-`params`), зарегистрированной в реестре
`market_structure_configs`. Результат **шарится** всеми настройками
`StrategyMarketStructureSetting`, которые эту конфигурацию запрашивают
(ключ — по идентичности считаемого, не по настройке; см.
`docs/decisions/market-data-result-identity-keying.md`). Persisted-модель
рыночных данных, не про бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

Готовит данные для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции. Потребители (evaluator,
калькуляторы, `MarketPhaseJob`) читают готовую структуру через
`docs/components/MarketStructureService.md` и сами уровни по свечам не
ищут.

## Структура

Java-класс, наследует `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID результата расчёта. |
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `configId` | `Long` | Ссылка на конфигурацию расчёта (тип + `timeframe` + canonical-`params`) в реестре `market_structure_configs` (см. `docs/decisions/market-data-result-identity-keying.md`). |
| `type` | `Type` | Тип структуры рынка. |
| `windowStartAt` | `OffsetDateTime` | Начало окна свечей расчёта. |
| `windowEndAt` | `OffsetDateTime` | Конец окна свечей расчёта. |
| `confirmedAt` | `OffsetDateTime` | Свеча, на которой структура подтверждена. |
| `levels` | `List<MarketPriceLevel>` | Ценовые уровни структуры (см. раздел ниже). |

## Енум `Type`

`RANGE`, `UPTREND`, `DOWNTREND`, `UNKNOWN`.

Отдельного `Status` у `MarketStructure` нет. Если структура сломалась,
`MarketStructureJob` сохраняет новый результат (например, `type =
UNKNOWN`). Актуальность проверяется через
`StrategyMarketStructureSetting.expirationDuration` и
`confirmedAt` / `windowEndAt` (правило —
`docs/rules/market-data-freshness.md`).

## MarketPriceLevel (раздел)

Конкретный ценовой уровень внутри `MarketStructure` (без родителя смысла
не имеет → раздел, не отдельная модель, см.
`.claude/decisions/model-granularity.md`). Java-класс, наследует
`Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Технический ID уровня. |
| `type` | `Type` | Тип уровня. |
| `price` | `BigDecimal` | Цена уровня. |
| `detectedAt` | `OffsetDateTime` | Свеча, на которой уровень найден. |
| `confirmedAt` | `OffsetDateTime` | Свеча, на которой уровень подтверждён. |

`MarketPriceLevel.Type`: `RANGE_LOW`, `RANGE_HIGH`, `SWING_LOW`,
`SWING_HIGH`, `SUPPORT`, `RESISTANCE`. Эти же значения используются
strategy-layer для `StrategyPriceBaseType` / `StrategyPricePlacement`
(см. `docs/models/domain/aggregate/Strategy.md`).

## Правила хранения

- Считается только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, config_id, window_end_at)`
  (ключ по идентичности считаемого через реестр конфигураций — см.
  `docs/decisions/market-data-result-identity-keying.md`).
- Каноническая форма `params` вычисляется один раз — при регистрации
  конфигурации в реестре (не на каждом результате); отдельные `version` /
  `canonicalJson` на `MarketStructureParams` не нужны (params immutable,
  см. `docs/models/domain/aggregate/Strategy.md`).
