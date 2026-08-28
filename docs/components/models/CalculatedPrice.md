# CalculatedPrice

## На какой вопрос отвечает этот файл

Что это за runtime value object `CalculatedPrice`: структура, енумы
`PriceMode` / `StrategyPricePurpose`, под-объекты resolved-цен.

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
| `sendPriceToExchange` | `Boolean` | Нужно ли отправлять цену на биржу. |
| `stopLossPrice` | `ResolvedStopLossPrice` | SL-компонент, если action создаёт/замещает stop-loss. |
| `takeProfitPrice` | `ResolvedTakeProfitPrice` | TP-компонент, если action создаёт/замещает take-profit. |
| `trailingPrice` | `ResolvedTrailingPrice` | Trailing-компонент, если action создаёт/замещает trailing stop. |
| `description` | `String` | Пояснение расчёта (целевое имя; legacy — `explanation`). |

`ResolvedStopLossPrice` / `ResolvedTakeProfitPrice` / `ResolvedTrailingPrice`
— под-объекты резолва конкретных защитных цен; без `CalculatedPrice`
смысла не имеют → разделы, не отдельные RVO (см.
`.claude/decisions/model-granularity.md`). Поля под-объектов:

- `ResolvedStopLossPrice` / `ResolvedTakeProfitPrice`: `triggerPrice`
  (trigger срабатывания), `orderPrice` (нога после срабатывания; `null`
  → рыночное исполнение), `triggerPriceType`
  (`AlgoOrder.TriggerPriceType`: last/index/mark);
- `ResolvedTrailingPrice`: `activationPrice` (цена активации; `null` →
  не отправляется, активен сразу), `callbackRatio` (callback в %),
  `callbackSpread` (callback абсолютным spread'ом; `null` для
  процентного режима).

## Енум `PriceMode`

- `MARKET_LIKE` — цену на биржу не отправляем, но reference price нужна
  для размера, риска и логов;
- `EXPLICIT` — конкретная цена должна быть отправлена на биржу;
- `NOT_REQUIRED` — цена для команды не нужна.

## Енум `StrategyPricePurpose`

В фазе 1 `PriceCalculator` эмитит **только** подмножество:
`ORDER_LIMIT_PRICE`, `ORDER_MARKET_REFERENCE_PRICE`,
`STOP_LOSS_TRIGGER_PRICE`, `TAKE_PROFIT_TRIGGER_PRICE`,
`TRAILING_ACTIVATION_PRICE`. Остальные
значения каталога определены, но в фазе 1 не порождаются (форвард).

## Округление цены

Отдельного enum политики округления нет: `PriceCalculator` округляет
цену по tick size **напрямую** через `RoundingMode` (DOWN/UP по
направлению), консервативно — защитная цена не ухудшается. Конкретные
правила сторон округления — у `docs/components/PriceCalculator.md`.

## Источник цены

Вокабуляр источников цены (`StrategyPriceSource`) для резолва — у
`docs/components/PriceCalculator.md` (расширенный набор калькулятора).
Конфигурационный подмножество для placement — раздел `StrategyPriceSource`
в `docs/models/domain/aggregate/Strategy.md`.
