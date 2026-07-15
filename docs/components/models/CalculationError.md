# CalculationError

## На какой вопрос отвечает этот файл

Что это за runtime value object `CalculationError`: структура, енум
`CalculationErrorType`, политика реакции.

## Назначение

`CalculationError` — контролируемая ошибка расчёта параметров действия
(возвращается в `StrategyActionCalculationResult` при `ERROR`). RVO, не
persisted (см. `.claude/decisions/runtime-value-object.md`).

Используется только для контролируемых случаев: нет актуальной цены / entry
price; нет ATR для ATR-based SL; нет market structure для structure-based
SL; невозможно округлить цену по tick size; невозможно рассчитать размер
из-за `minSz`/`lotSz`; не хватает обязательных input data. Unexpected
exceptions в `CalculationError` **не** превращаются — для них коды
`RuntimeErrorCode` (см. `docs/rules/runtime-error-classification.md`).

## Механизм сигнализации

Контролируемую ошибку расчёта суб-калькуляторы (`CalculationContextFactory`,
`PriceCalculator`, `SizeCalculator`) сигнализируют **броском** внутреннего
`CalculationException`, несущего `CalculationError`. `StrategyActionCalculator`
его перехватывает и возвращает `StrategyActionCalculationResult` со статусом
`ERROR` и этим `CalculationError` — внешний контракт калькулятор-слоя возвратный
(Result), не бросковый. Unexpected exceptions так не оборачиваются (ловятся на
границе FSM, `RuntimeErrorCode`). `code` — freeform-строка; пример
temporary-кода — `NO_MARKET_PRICE` (нет свежей рыночной цены).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `code` | `String` | Машинный код ошибки расчёта. |
| `type` | `CalculationErrorType` | Тип ошибки расчёта. |
| `message` | `String` | Человекочитаемое описание. |
| `retryable` | `Boolean` | Можно ли повторить расчёт позже без изменения стратегии. |

## Енум `CalculationErrorType`

- `TEMPORARY` — данные задержались, сервис временно недоступен.
- `PERMANENT` — action невозможно корректно рассчитать в текущей
  конфигурации.

## Политика реакции

```text
TEMPORARY
  -> DealActionState = RETRY_PENDING
  -> текущий StrategyStep ждёт retry текущего action;
     следующие actions step не выполняются до разрешения текущего

PERMANENT
  -> DealActionState = FAILED
  -> Deal -> ERROR для статусов, где live risk уже есть или мог появиться
  -> дальше ErrorHandler / safety-flow
```

`DealActionState`-статусы — см. `docs/models/domain/other/DealActionState.md`
(lifecycle — `docs/lifecycles/DealActionState.md`); реакция FSM —
`docs/processes/deal-management.md`.
