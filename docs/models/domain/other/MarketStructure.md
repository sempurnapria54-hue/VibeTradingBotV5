# MarketStructure

## На какой вопрос отвечает этот файл

Что это за модель `MarketStructure`: структура, енум `Type`, вложенные
ценовые уровни `MarketPriceLevel`, правила хранения и актуальности.

## Назначение

`MarketStructure` — готовый результат расчёта структуры рынка
(уровни, диапазоны, тренд), рассчитанный `MarketStructureJob` (вычисление
делегируется компоненту `MarketStructureResolver` —
`docs/components/MarketStructureResolver.md`) по закрытым свечам для
конкретной **настройки-владельца** `StrategyMarketStructureSetting`.
Результат ключуется настройкой-владельцем (одна типизированная FK
`strategyMarketStructureSettingId`), **не шарится** и не ключуется по
идентичности конфигурации — реестр `market_structure_configs` убран
ревизией трек D (см.
`docs/decisions/market-data-result-identity-keying.md`). Persisted-модель
рыночных данных, не про бизнес-цикл сделки → `other` (см.
`.claude/decisions/models-core-vs-other.md`).

Готовит данные для входов от диапазона, grid, SL за структурный уровень,
breakout-условий и сопровождения позиции. Потребители (evaluator,
калькуляторы, `MarketPhaseResolver` при классификации фазы на чтение)
читают готовую структуру через
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
| `strategyMarketStructureSettingId` | `Long` | FK на настройку-владельца `StrategyMarketStructureSetting` (`strategy_market_structure_settings.id`) — owner-ключевание (см. `docs/decisions/market-data-result-identity-keying.md`). |
| `type` | `Type` | Тип структуры рынка. |
| `windowStartAt` | `OffsetDateTime` | Начало окна свечей расчёта. |
| `windowEndAt` | `OffsetDateTime` | Конец окна свечей расчёта. |
| `confirmedAt` | `OffsetDateTime` | Свеча, на которой структура подтверждена. |
| `levels` | `List<MarketPriceLevel>` | Ценовые уровни структуры (см. раздел ниже). |
| `breakoutEvent` | `MarketBreakoutEvent` | Предвычисленное событие подтверждённого пробоя; `null` — пробоя в окне нет (см. раздел ниже). |

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

## MarketBreakoutEvent (раздел)

Предвычисленное событие подтверждённого пробоя структурного уровня (без
родителя смысла не имеет → раздел `MarketStructure`, не отдельная модель).
Детекцию (буфер `breakoutBufferPercents` + удержание
`breakoutConfirmationBars`, оба из `MarketStructureParams`) делает
`MarketStructureResolver`; условие `RANGE_BREAKOUT_CONFIRMED` читает событие
**готовым** (детекция — на стороне резолвера, не в условии). Форма
зафиксирована на `CODE` шага 3.

| Поле | Тип | Назначение |
|---|---|---|
| `brokenLevelType` | `MarketPriceLevel.Type` | Тип сломанного уровня (`RANGE_HIGH`/`RANGE_LOW`/`RESISTANCE`/`SUPPORT`). |
| `direction` | `Direction` | Направление пробоя (`UP` / `DOWN`). |
| `levelPrice` | `BigDecimal` | Цена сломанного уровня. |
| `confirmedAt` | `OffsetDateTime` | Свеча, на которой пробой подтверждён (удержание баров завершилось). |

`Direction`: `UP` (закрытие выше сопротивления / верхней границы), `DOWN`
(закрытие ниже поддержки / нижней границы).

## Семантика классификации (как считается)

Вычисляет `MarketStructureResolver`
(`docs/components/MarketStructureResolver.md`) по закрытым свечам окна
`lookbackBars`, потребляя готовые каталожные скаляры — ER (дискриминатор
тренд/шум) и ATR (толеранс кластеризации, D3) — по «мягким» ключам
`StrategyMarketStructureSetting.efficiencyRatioKey` / `atrKey`, и
`MarketStructureParams`. Скаляры резолверу подаёт `MarketStructureJob`,
извлекая их из готового `IndicatorValue` (fork-A —
`docs/decisions/derived-market-data-code-increments.md`):

- **ER-вход не объявлен** (`efficiencyRatioKey` null) → резолвер считает
  внутренний прокси (нетто-ход окна / суммарный побарный ход — мини-ER по
  ценам закрытия); **ATR не объявлен** (`atrKey` null) → толеранс
  кластеризации откатывается на долю цены.
- **Вход объявлен, но не готов / устарел** → `MarketStructureJob` пишет
  консервативный `UNKNOWN` (не proxy, не падение): объявление входа —
  намерение на нём считать.

Численные пороги — **хвост пользователя** (per-настройка
`MarketStructureParams`), включая `trendEfficiencyThreshold` (D2) и
`levelToleranceAtrMultiplier` (D3); их значения провизорны (STRUCT-Q1).
Точная арифметика (окно/формула ER, дефолты при `null`-порогах) — деталь
реализации (`CODE`).

1. **Свинг-пивоты.** `SWING_HIGH` — бар, чей `high` — локальный максимум в
   окне `swingLookbackBars` баров с каждой стороны; симметрично
   `SWING_LOW`. *Источник говорит* (свинг-фильтр отсекает шум)
   [Kaufman гл. 5].
2. **Кластеризация уровней.** Пивоты группируются в ценовые уровни в
   пределах **волатильность-относительного толеранса** (`k·ATR`, где `k =
   levelToleranceAtrMultiplier`, D3; при необъявленном ATR — fallback на
   долю цены); уровень с `≥ minTouches` касаниями — подтверждён. `SUPPORT`
   — подтверждённый уровень-пол ниже цены, `RESISTANCE` — потолок выше.
   *Источник говорит* (уровень = многократно удержанная цена; разброс
   относительно волатильности) [Kaufman гл. 8].
3. **Диапазон → `RANGE`.** Подтверждённые `RESISTANCE` (верх) и `SUPPORT`
   (низ) окаймляют недавнюю цену; ширина полосы ∈ `[minRangeWidthPercents,
   maxRangeWidthPercents]` от средней цены; цена осциллировала между
   границами (`≥ minTouches` к каждой). Уровни: `RANGE_HIGH` / `RANGE_LOW`
   (+ `SWING_*`). *Источник говорит* [Kaufman гл. 8].
4. **Тренд → `UPTREND` / `DOWNTREND`.** Пивоты дают higher-high+higher-low
   (вверх) или lower-high+lower-low (вниз), и чистый ход доминирует над
   шумом (`ER ≥ trendEfficiencyThreshold`, D2). Уровни: `SWING_*`
   (+ граничные `SUPPORT`/`RESISTANCE`). *Источник говорит* (тренд =
   персистентность + ER-доминирование чистого хода) [Kaufman гл. 1
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
- Уникальность: `UNIQUE(instrument_id, strategy_market_structure_setting_id,
  window_end_at)` (ключ по настройке-владельцу — owner-ключевание, см.
  `docs/decisions/market-data-result-identity-keying.md`).
- **Per-настроечный `structureType` отсутствует** (`StrategyMarketStructureSetting`
  его не несёт): `MarketStructure.Type` — **выход** расчёта
  (`MarketStructureResolver` его выводит), не вход настройки. Реестра
  конфигураций и «идентичности конфигурации» больше нет — результат
  ключуется настройкой-владельцем; канонизация `params` не нужна.
  - **Бывший краевой случай STRUCT-Q2 снят по построению.** Soft-ключи
    входов резолвера `efficiencyRatioKey` / `atrKey` живут на настройке;
    при owner-ключевании каждая настройка структуры (со своими ER/ATR-
    ключами) пишет в **свою** строку под своим
    `strategy_market_structure_setting_id` — разделяемого ряда, на котором
    возникала коллизия, нет (см.
    `docs/decisions/derived-market-data-code-increments.md` §Краевой случай
    идентичности).
- **Свежесть на чтение:** `expiredAt = windowEndAt +
  ownerSetting.expirationDuration` считается в runtime, колонкой не
  хранится; у строки результата один владелец, под него и оценивается
  свежесть (`docs/rules/market-data-freshness.md`).
- **Retention:** результаты не чистятся (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
