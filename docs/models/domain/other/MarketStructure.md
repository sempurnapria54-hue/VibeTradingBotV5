# MarketStructure

## На какой вопрос отвечает этот файл

Что это за модель `MarketStructure`: структура, енум `Type`, вложенные
ценовые уровни `MarketPriceLevel`, правила хранения и актуальности.

## Назначение

`MarketStructure` — готовый результат расчёта структуры рынка
(уровни, диапазоны, тренд), рассчитанный `MarketStructureJob` (вычисление
делегируется компоненту `MarketStructureResolver` —
`docs/components/MarketStructureResolver.md`) по закрытым свечам для
**конфигурации расчёта** (`timeframe` + canonical-`params`),
зарегистрированной в реестре `market_structure_configs`. Результат **шарится** всеми настройками
`StrategyMarketStructureSetting`, которые эту конфигурацию запрашивают
(ключ — по идентичности считаемого, не по настройке; см.
`docs/decisions/market-data-result-identity-keying.md`). Persisted-модель
рыночных данных, не про бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

Готовит данные для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции. Потребители (evaluator,
калькуляторы, `MarketPhaseJob`) читают готовую структуру через
`docs/components/MarketStructureService.md` и сами уровни по свечам не
ищут. Для классификации фазы `MarketStructure` фигурирует **операндом**
авторских правил (`MARKET_STRUCTURE`-операнд, в т.ч. тест `Type` через
`MARKET_STRUCTURE_IS`), не входом скоринга — см.
`docs/decisions/market-phase-conditional-classification.md`.

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
`StrategyMarketStructureSetting.expirationDuration`: точка отсчёта
свежести (`referencePoint`) — **`windowEndAt`**, а `confirmedAt` — гейт
использования без look-ahead, **не** точка отсчёта (правило —
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

## Семантика классификации (как считается)

Вычисляет `MarketStructureResolver`
(`docs/components/MarketStructureResolver.md`) по закрытым свечам окна
`lookbackBars`, опционально потребляя готовые каталожные `IndicatorValue`
(ER — дискриминатор тренд/шум; ATR — пол свинг-шума), и
`MarketStructureParams`. Численные пороги — **хвост пользователя**
(per-настройка `MarketStructureParams`); точная арифметика (толерансы,
окно/формула ER, критерий «наклон EMA согласен») — деталь реализации
(`CODE`).

1. **Свинг-пивоты.** `SWING_HIGH` — бар, чей `high` — локальный максимум в
   окне `swingLookbackBars` баров с каждой стороны; симметрично
   `SWING_LOW`. Опциональный шумовой фильтр: пивот засчитывается, если
   экскурсия от соседнего пивота превышает волатильностный пол (`k·ATR`).
   *Источник говорит* (свинг-фильтр отсекает шум) [Kaufman гл. 5].
2. **Кластеризация уровней.** Пивоты группируются в ценовые уровни в
   пределах толеранса; уровень с `≥ minTouches` касаниями — подтверждён.
   `SUPPORT` — подтверждённый уровень-пол ниже цены, `RESISTANCE` —
   потолок выше. *Источник говорит* (уровень = многократно удержанная
   цена) [Kaufman гл. 8].
3. **Диапазон → `RANGE`.** Подтверждённые `RESISTANCE` (верх) и `SUPPORT`
   (низ) окаймляют недавнюю цену; ширина полосы ∈ `[minRangeWidthPercents,
   maxRangeWidthPercents]` от средней цены; цена осциллировала между
   границами (`≥ minTouches` к каждой). Уровни: `RANGE_HIGH` / `RANGE_LOW`
   (+ `SWING_*`). *Источник говорит* [Kaufman гл. 8].
4. **Тренд → `UPTREND` / `DOWNTREND`.** Пивоты дают higher-high+higher-low
   (вверх) или lower-high+lower-low (вниз), и чистый ход доминирует над
   шумом (ER высок / наклон EMA согласен). Уровни: `SWING_*` (+ последний
   пробитый уровень как `SUPPORT`/`RESISTANCE`). *Источник говорит* (тренд
   = персистентность + ER-доминирование чистого хода) [Kaufman гл. 1
   «Measuring Noise», гл. 8].
5. **Пробой.** Уровень пробит, когда цена закрывается за ним на
   `≥ breakoutBufferPercents` и держится `≥ breakoutConfirmationBars`
   закрытых баров; до этого уровень жив. Подтверждённый пробой
   переклассифицирует структуру (диапазон→тренд) и **экспонируется явным
   предвычисленным событием** (`breakoutEvent`: сломанный уровень +
   направление + `confirmedAt`), которое условие `RANGE_BREAKOUT_CONFIRMED`
   читает готовым — **детекция здесь, на стороне резолвера** (буфер +
   подтверждение — из `MarketStructureParams`), не в условии; точная форма
   `breakoutEvent` — `CODE`. *Источник говорит* (ложный пробой отсекается
   запасом + удержанием N баров) [Kaufman гл. 8].
6. **`UNKNOWN`** — консервативный дефолт: ширина вне границ / мало касаний
   / противоречивая геометрия / шум высок (ER низок, диапазон не
   квалифицируется). Согласуется с матрицей `UNKNOWN → NO_TRADE`.
   *Источник говорит* (боковик у границы опознать трудно) [Kaufman гл. 17].
7. **`confirmedAt` / окно.** `confirmedAt` — бар, на котором
   подтверждающее свидетельство завершилось (`breakoutConfirmationBars`
   пройдены / `minTouches` достигнуты): гейт «использовать без look-ahead».
   `windowStartAt` / `windowEndAt` — границы окна `lookbackBars`; точка
   отсчёта свежести — `windowEndAt`, не `confirmedAt` (см. §Правила
   хранения).

**Валидный уровень:** с `≥ minTouches` касаниями, не сломанный
подтверждённым пробоем. Жив, пока его не сломал подтверждённый пробой или
не переподтвердил новый расчёт (expiration ловит «расчёт остановился», не
«смерть уровня»).

## Правила хранения

- Считается только по закрытым свечам (без look-ahead).
- Уникальность: `UNIQUE(instrument_id, config_id, window_end_at)`
  (ключ по идентичности считаемого через реестр конфигураций — см.
  `docs/decisions/market-data-result-identity-keying.md`).
- Каноническая форма `params` вычисляется один раз — при регистрации
  конфигурации в реестре (не на каждом результате); отдельные `version` /
  `canonicalJson` на `MarketStructureParams` не нужны (params immutable,
  см. `docs/models/domain/aggregate/Strategy.md`).
- **Идентичность конфигурации структуры = `timeframe` +
  canonical-`params`.** Вид расчёта структуры один (`MarketStructureResolver`
  выводит `Type` как **выход**), поэтому type-дискриминатора в идентичности
  нет — в отличие от `IndicatorValue`, где `Type` различает виды.
  Per-настроечный `structureType` **удалён** (`StrategyMarketStructureSetting`
  его не несёт): тип — результат расчёта, не вход настройки (см.
  `docs/decisions/market-data-result-identity-keying.md`).
- **Свежесть на чтение:** `expiredAt = windowEndAt +
  askingSetting.expirationDuration` считается в runtime, колонкой не
  хранится; на общей строке (ключ по `config_id`) единого `expiredAt` нет
  — своё под каждую запрашивающую настройку
  (`docs/rules/market-data-freshness.md`).
- **Retention:** результаты не чистятся (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
