# OkxAlgoOrderResponse (OKX algo-order)

## На какой вопрос отвечает этот файл

Какие поля у OKX algo-order response и какие из них используются для
`AlgoOrder`.

## Контекст

Raw OKX `AlgoOrderResponse` (из `GET /trade/order-algo`, pending,
history). Маппится в `AlgoOrderExternalSnapshot`. Доменная модель и
статусы — в `docs/models/core/AlgoOrder.md` и
`docs/lifecycles/AlgoOrder.md`; mapping/валидация — в
`docs/client/okx/rules/okx-algo-order-mapping.md`. Raw DTO не выходит
за adapter-layer (`docs/rules/raw-exchange-dto-boundary.md`).

## Поля, которые используются (→ `AlgoOrderExternalSnapshot`)

| OKX field | Назначение | Snapshot field |
|---|---|---|
| `algoClOrdId` | stable client id | `internalId` |
| `algoId` | биржевой algo id | `externalId` |
| `state` | сырой статус (не для FSM напрямую) | `externalStatus` |
| `failCode` | код ошибки | `failCode` |
| `actualSz` | факт. размер срабатывания | `externalSize` |
| `actualPx` | факт. цена срабатывания | `externalPrice` |
| `triggerTime` | время срабатывания | `externalTriggerTime` |
| `ordId` / `ordIdList` | связанные ordinary order ids | `linkedOrderExternalIds` |
| `slTriggerPx` | внешнее значение SL trigger | `condition.trigger.stopLoss.externalValue` |
| `slTriggerPxType` | тип цены SL (`last`/`index`/`mark`) | `condition.trigger.stopLoss.externalType` |
| `tpTriggerPx` | внешнее значение TP trigger | `condition.trigger.takeProfit.externalValue` |
| `tpTriggerPxType` | тип цены TP | `condition.trigger.takeProfit.externalType` |
| `activePx` | цена активации trailing | `condition.trailing.activationPrice.externalValue` |
| `moveTriggerPx` | текущее значение trailing | `condition.trailing.externalPrice` |

Если OKX не возвращает тип цены активации trailing,
`condition.trailing.activationPrice.externalType` остаётся null —
это нормально, не нарушение invariant.

## Поля для validation (в adapter-layer, не в snapshot/домене)

`ordType`, `side`, `actualSide`, `tdMode`, `posSide`, `reduceOnly`,
`closeFraction` — **не маппятся**: проверяются adapter-layer как
invariant (см. `okx-algo-order-mapping.md`) либо остаются в raw audit.

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp
string → `Instant`; `state` остаётся raw string в `externalStatus`
(резолвинг — позже).
