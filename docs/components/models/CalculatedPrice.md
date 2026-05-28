# CalculatedPrice

## На какой вопрос отвечает этот файл

Что это за runtime value object `CalculatedPrice`: структура, енумы
`PriceMode` / `StrategyPricePurpose`, под-объекты resolved-цен, политика
округления.

## Назначение

`CalculatedPrice` — рассчитанная цена (или набор цен) для действия,
результат `PriceCalculator` (см. `docs/components/PriceCalculator.md`).
RVO, не persisted (см. `.claude/decisions/runtime-value-object.md`).
Формулы расчёта и таблица «источник цены → тип цены» — у компонента
`PriceCalculator`, здесь только структура данных.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `purpose` | `StrategyPricePurpose` | Назначение цены. |
| `priceMode` | `PriceMode` | Режим цены. |
| `basePrice` | `BigDecimal` | Базовая цена, от которой считали. |
| `rawPrice` | `BigDecimal` | Сырая цена до округления. |
| `roundedPrice` | `BigDecimal` | Цена после округления по tick size. |
| `sendPriceToExchange` | `boolean` | Нужно ли отправлять цену на биржу. |
| `stopLossPrice` | `ResolvedStopLossPrice` | SL-компонент, если action создаёт/меняет stop-loss. |
| `takeProfitPrice` | `ResolvedTakeProfitPrice` | TP-компонент, если action создаёт/меняет take-profit. |
| `trailingPrice` | `ResolvedTrailingPrice` | Trailing-компонент, если action создаёт/меняет trailing stop. |
| `description` | `String` | Пояснение расчёта (целевое имя; legacy — `explanation`). |

`ResolvedStopLossPrice` / `ResolvedTakeProfitPrice` / `ResolvedTrailingPrice`
— под-объекты резолва конкретных защитных цен (trigger/order price,
activation/callback); без `CalculatedPrice` смысла не имеют → разделы, не
отдельные RVO (см. `.claude/decisions/model-granularity.md`).

## Енум `PriceMode`

- `MARKET_LIKE` — цену на биржу не отправляем, но reference price нужна
  для размера, риска и логов;
- `EXPLICIT` — конкретная цена должна быть отправлена на биржу;
- `NOT_REQUIRED` — цена для команды не нужна.

## Енум `StrategyPricePurpose`

Назначение рассчитанной цены: `ORDER_LIMIT_PRICE`,
`ORDER_MARKET_REFERENCE_PRICE`, `ORDER_AMEND_PRICE`, `ENTRY_PLANNED_PRICE`,
`ENTRY_AVERAGE_PRICE`, `BREAKEVEN_PRICE`,
`ATTACHED_STOP_LOSS_TRIGGER_PRICE`, `ATTACHED_STOP_LOSS_ORDER_PRICE`,
`ATTACHED_TAKE_PROFIT_TRIGGER_PRICE`, `ATTACHED_TAKE_PROFIT_ORDER_PRICE`,
`STOP_LOSS_TRIGGER_PRICE`, `STOP_LOSS_ORDER_PRICE`,
`TAKE_PROFIT_TRIGGER_PRICE`, `TAKE_PROFIT_ORDER_PRICE`,
`TRAILING_ACTIVATION_PRICE`, `TRAILING_CALLBACK_SPREAD`,
`TRAILING_CALLBACK_RATIO`, `TRIGGER_ORDER_TRIGGER_PRICE`,
`TRIGGER_ORDER_EXECUTION_PRICE`, `POSITION_CLOSE_REFERENCE_PRICE`,
`ACTUAL_EXECUTION_PRICE`, `POSITION_RISK_REFERENCE_PRICE`,
`LIQUIDATION_GUARD_PRICE`, `PRICE_VALIDATION_MIN_PRICE`,
`PRICE_VALIDATION_MAX_PRICE`, `PRICE_ROUNDING_STEP`.

## Енум `PriceRoundingPolicy`

`CONSERVATIVE` (безопасное округление, не ухудшает смысл защитной цены;
используется на первом этапе), `AGGRESSIVE` (для повышения шанса
исполнения), `NEAREST` (к ближайшему tick). Конкретные правила сторон
округления — у `docs/components/PriceCalculator.md`.

## Источник цены

Вокабуляр источников цены (`StrategyPriceSource`) для резолва — у
`docs/components/PriceCalculator.md` (расширенный набор калькулятора).
Конфигурационный подмножество для placement — раздел `StrategyPriceSource`
в `docs/models/domain/aggregate/Strategy.md`.
