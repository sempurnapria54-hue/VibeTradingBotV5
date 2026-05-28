# MarketPriceData

## На какой вопрос отвечает этот файл

Что это за runtime value object `MarketPriceData`: структура,
boundary-snapshot, правила использования.

## Назначение

`MarketPriceData` — runtime-данные текущих цен инструмента (last / bid /
ask + время тикера). RVO, **не** persisted (см.
`.claude/decisions/runtime-value-object.md`): историю тикеров не ведём,
кэш на первом этапе не используем, `Auditable` не наследует.

Нужен для входа, проверки условий и калькуляторов. Собирается в
`CalculationContext` (см. `docs/components/models/CalculationContext.md`).
Раздачей занимается `docs/components/MarketPriceDataService.md`.

Flow:

```text
Client model OKX ticker
  -> MarketPriceDataExternalSnapshot
  -> MarketPriceData
  -> CalculationContext
```

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `instrumentId` | `Long` | Внутренний ID инструмента. |
| `externalInstrumentType` | `String` | Тип инструмента на бирже. |
| `externalInstrumentId` | `String` | ID инструмента на бирже. |
| `externalLastPrice` | `BigDecimal` | Последняя цена сделки. |
| `externalAskPrice` | `BigDecimal` | Лучшая цена продажи. |
| `externalBidPrice` | `BigDecimal` | Лучшая цена покупки. |
| `externalTimestamp` | `OffsetDateTime` | Время тикера на бирже. |

`MID_PRICE` не хранится — вычисляется: `midPrice = (externalBidPrice +
externalAskPrice) / 2`.

## MarketPriceDataExternalSnapshot (boundary)

Выход маппера из client-модели биржи до сборки `MarketPriceData`
(`raw-exchange-dto-boundary.md`). Поля: `externalInstrumentType`,
`externalInstrumentId`, `externalLastPrice`, `externalAskPrice`,
`externalBidPrice`, `externalTimestamp` (без `instrumentId` — внутренний
ID добавляется уже при сборке `MarketPriceData`). Сырой OKX DTO за
`ClientService` не выходит; OKX ticker → snapshot маппинг —
`docs/models/mapping/MarketPriceData.md`.

## Правила использования

- `MarketPriceData` можно переиспользовать внутри одного
  `CalculationContext`, но не считать один общий context на весь
  `StrategyStep` (один action = один свежий context, см.
  `docs/processes/strategy-action-calculation.md`).
- Если свежего `MarketPriceData` нет для расчёта — калькулятор возвращает
  controlled calculation error, а не считает по старым данным (см.
  `docs/components/models/CalculationError.md`).
