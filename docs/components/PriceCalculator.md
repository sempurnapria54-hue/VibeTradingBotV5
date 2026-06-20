# PriceCalculator

## На какой вопрос отвечает этот файл

Кто рассчитывает цены действия (компонент-калькулятор цены): контракт,
формулы SL/TP/trailing/limit/structure, округление, вокабуляр источников
цены.

## Назначение

`PriceCalculator` отвечает только за расчёт цен и возвращает
`CalculatedPrice` (см. `docs/components/models/CalculatedPrice.md`). Размер
не считает, полный риск сделки не проверяет, не решает, нужно ли выполнять
action. ATR/структуру по свечам не считает — берёт готовые `AtrValue` /
`MarketStructure`.

## Контракт

`CalculatedPrice calculate(CalculationContext context)`.

## Формулы

**Limit placement:**

```text
basePrice = resolveBasePrice(source)
offset    = basePrice * percents / 100        # обычная цена
offset    = (rangeHigh - rangeLow) * percents / 100   # для диапазона
rawPrice  = basePrice + offset  (offsetSide = ABOVE)
rawPrice  = basePrice - offset  (offsetSide = BELOW)
roundedPrice = roundByTickSize(rawPrice)
```

**Stop-loss by entry percent:** LONG `SL = entry - entry*dist%/100`;
SHORT `SL = entry + entry*dist%/100`.

**Stop-loss by ATR** (`distancePercents = 150` = 1.5 ATR): LONG `SL =
base - ATR*dist%/100`; SHORT `SL = base + ATR*dist%/100` (ATR из
`AtrValue`).

**Market structure stop-loss:** LONG `base = swingLow|rangeLow; SL = base
- base*dist%/100`; SHORT `base = swingHigh|rangeHigh; SL = base +
base*dist%/100`.

**Take-profit:** LONG `TP = entry + entry*triggerProfit%/100`; SHORT `TP =
entry - entry*triggerProfit%/100`.

**Trailing activation:** при `activationProfitPercents = null` —
`activePrice = null`, не отправляется. Иначе LONG `activePrice = entry +
entry*activation%/100` (+ `activationBuffer%`); SHORT — симметрично вниз.
Callback — ratio/percent на биржу.

## Округление по tick size

Цена округляется по `InstrumentExternalRules.externalTickSize`. На первом
этапе политика `CONSERVATIVE` (см. `PriceRoundingPolicy` в
`docs/components/models/CalculatedPrice.md`):

```text
LIMIT BUY conservative  -> вниз
LIMIT SELL conservative -> вверх
LONG SL  -> вниз     SHORT SL -> вверх
LONG TP  -> вверх    SHORT TP -> вниз
```

## Что задаёт резолв цены (фактические драйверы)

Резолв базовой цены в фазе 1 управляется не расширенным
`StrategyPriceSource`, а связкой:

- **`StrategyPriceBaseType`** (тип базы placement): `RANGE_LOW`,
  `RANGE_HIGH`, `SWING_LOW`, `SWING_HIGH`, `SUPPORT`, `RESISTANCE`
  (берутся из `MarketStructure` по `structureKey`), `ENTRY_PRICE`
  (цена-ориентир входа), `MARKET_PRICE` (рыночная цена по
  `StrategyPriceSource`);
- **`StrategyPriceSource`** (подмножество для `MARKET_PRICE`-базы):
  `LAST_PRICE`, `MARK_PRICE`, `INDEX_PRICE`, `BEST_BID_PRICE`,
  `BEST_ASK_PRICE`, `MID_PRICE`;
- **`StopLossCalculationType`** для SL: `ENTRY_PRICE_PERCENT`,
  `ATR_PERCENT`, `MARKET_STRUCTURE_BUFFER_PERCENT`;
- **`TrailingSettings`** для активации/callback трейлинга.

`MARK_PRICE`/`INDEX_PRICE` в фазе 1 **проксируются на last price**: OKX
ticker не несёт mark/index, поэтому обе мапятся на
`externalLastPrice`.

Расширенный ~30-значный вокабуляр источников цены — **форвард**; в коде
`StrategyPriceSource` не расширяется. Конфигурационное подмножество для
placement (`LAST_PRICE` … `MID_PRICE`) — раздел `StrategyPriceSource` в
`docs/models/domain/aggregate/Strategy.md`.

## Ветки условий algo (`conditionType`)

`PriceCalculator` обрабатывает: `STOP_LOSS`/`PARTIAL_STOP_LOSS` (SL),
`TAKE_PROFIT`/`PARTIAL_TAKE_PROFIT` (TP), `OCO_FULL` (SL + TP вместе),
`TRAILING_PERCENTS`/`TRAILING_VALUE` (trailing).

## Источник цены → тип цены (примеры)

| Источник | Тип цены (`StrategyPricePurpose`) | Кейс |
|---|---|---|
| `LAST_PRICE`/`MARK_PRICE` | `ORDER_MARKET_REFERENCE_PRICE` | market-like entry: цену не шлём, reference для size/risk/audit |
| `LAST_PRICE`/`MARK_PRICE`/bid/ask | `ORDER_LIMIT_PRICE` | лимитка от соответствующей цены |
| `RANGE_LOW`/`RANGE_HIGH` | `ORDER_LIMIT_PRICE` | grid-уровни |
| `SWING_LOW`/`SWING_HIGH`/`ATR_VALUE`/`ENTRY_AVERAGE_PRICE` | `STOP_LOSS_TRIGGER_PRICE` | структурный/ATR/процентный SL |
| `ENTRY_AVERAGE_PRICE` | `TAKE_PROFIT_TRIGGER_PRICE` / `TRAILING_ACTIVATION_PRICE` | TP / активация trailing |
| `BREAKEVEN_PRICE` | `STOP_LOSS_TRIGGER_PRICE` | перенос SL в безубыток |
| `FILL_AVERAGE_PRICE` | `ACTUAL_EXECUTION_PRICE` | восстановление реальной цены по fills |
| `POSITION_LIQUIDATION_PRICE` | `LIQUIDATION_GUARD_PRICE` | проверка близости к ликвидации |
| `INSTRUMENT_TICK_SIZE` | `PRICE_ROUNDING_STEP` | шаг округления цены |

## Контролируемые ошибки

Сигнализирует контролируемую ошибку расчёта (бросает `CalculationException`,
которое `StrategyActionCalculator` превращает в `CalculationError` в
`ERROR`-результате — см. `docs/components/models/CalculationError.md`
§«Механизм сигнализации»), если нет актуальной цены / entry
price / ATR / market structure / `tickSize`, либо цена стала невалидной
после округления (см. `docs/components/models/CalculationError.md`).
