# OkxOrderResponse (OKX ordinary order)

## На какой вопрос отвечает этот файл

Какие поля у OKX ordinary order response (включая attached
`attachAlgoOrds`) и какие из них используются.

## Контекст

Raw OKX `OrderResponse` (из `GET /trade/order`, pending, history).
Маппится в `OrderExternalSnapshot` / `AttachedAlgoOrderExternalSnapshot`.
Доменная модель и статусы — в `docs/models/core/Order.md` и
`docs/lifecycles/Order.md`; mapping/валидация — в
`docs/client/okx/rules/okx-order-mapping.md`. Raw DTO не выходит за
adapter-layer (`docs/rules/raw-exchange-dto-boundary.md`); FSM не
использует raw status напрямую (`docs/rules/external-status-resolution.md`).

## Поля ordinary order (→ `OrderExternalSnapshot`)

| OKX field | Назначение | Snapshot field |
|---|---|---|
| `clOrdId` | stable client id | `internalId` |
| `ordId` | биржевой order id | `externalId` |
| `ordType` | тип ордера (`optimal_limit_ioc`/`limit`/`market`) | `type` |
| `side` | `buy` / `sell` | `side` |
| `state` | сырой статус (не для FSM напрямую) | `externalStatus` |
| `px` | цена | `price` (empty→null) |
| `sz` | размер (контракты для SWAP/FUTURES) | `size` |
| `accFillSz` | исполненный объём | `accumulatedFillSize` |
| `avgPx` | средняя цена исполнения | `averagePrice` |
| `fee` | комиссия | `fee` |
| `cTime` | время создания (ms) | `externalCreatedAt` |
| `uTime` | время обновления (ms) | `externalModifiedAt` |
| `attachAlgoOrds` | attached protection | `attachedAlgoOrders` |
| `attachAlgoClOrdId` | top-level attached client id | `attachedAlgoInternalId` |
| `tpTriggerPx` | top-level TP trigger | `takeProfitTriggerPrice` (future) |
| `slTriggerPx` | top-level SL trigger | `stopLossTriggerPrice` |
| `reduceOnly` | reduce-only факт | **не маппится** (только invariant validation в adapter) |

## Поля только для adapter / validation / диагностика (не в snapshot)

`instType`, `instId` — для сверки с expected Instrument; `tdMode`,
`posSide`, `ccy`, `lever` — adapter-константы / validation;
`fillPx`/`fillSz`/`fillTime`/`tradeId` — поля «последнего исполнения»;
факт исполнения собирается из fills (`docs/components/RefreshFillsExecutor.md`,
`docs/client/okx/rules/okx-fills-mapping.md`). `feeCcy`/`rebate`/
`rebateCcy`/`pnl` — для итоговой аналитики через fills/finalization.
`tag` — метка, может быть полезна для диагностики. `source` /
`cancelSource` / `cancelSourceReason` / `category` (`normal`/`adl`/
`liquidation`/`delivery`/`twap` и др.) — диагностика, в snapshot не
выводится. `stpMode` / `stpId` — self-trade prevention. `quickMgnType`,
`pxType`/`pxUsd`/`pxVol` (options), `tgtCcy` (SPOT market),
`tradeQuoteCcy`, `linkedAlgoOrd.algoId`, `algoId`/`algoClOrdId`,
`isTpLimit` — не в snapshot.

## Поля внутри `attachAlgoOrds[*]` дополнительные (pending/details/history)

В response `attachAlgoOrds[*]` помимо полей в таблице ниже может
приходить: `tpTriggerPx`/`tpTriggerPxType`/`tpOrdPx` (TP параметры
вложенного объекта; биржа дублирует top-level), `slTriggerPxType`/
`slOrdPx` (SL), `tpTriggerRatio`/`slTriggerRatio` (триггер в доле,
только FUTURES/SWAP), `amendPxOnTriggerType` (`0`/`1` cost-price SL
для split-TP), `attachAlgoClOrdId` (см. ниже). Маппинг см. в таблице
attached protection.

## Поля attached protection `attachAlgoOrds[*]` (→ `AttachedAlgoOrderExternalSnapshot`)

| OKX field | Snapshot field | Комментарий |
|---|---|---|
| `attachAlgoId` | `externalAttachedId` | attached algo id из embedded block. |
| `attachAlgoClOrdId` | `internalId` | client id, **основной ключ матчинга**. |
| `algoId` | `externalId` | algo id, если появился после trigger/создания. |
| `algoClOrdId` | не маппится | diagnostic/future. |
| `tpOrdKind` | `externalType` / future | для SL-only можно не использовать. |
| `sz` | `size` | string→BigDecimal в mapper. |
| `slTriggerPx` | `stopLossTriggerPrice` | trigger SL. |
| `failCode` | `failCode` | если заполнен → attached ERROR. |
| `failReason` | `failReason` | диагностика ошибки. |

У `attachAlgoOrds` нет полноценного `state` как у ordinary order —
attached резолвится по набору фактов (см. `docs/lifecycles/Order.md`).

## Поля request-константы / validation (в adapter-layer, не в домене)

`tdMode` (=`isolated`), `posSide` (=`net`) — константы
`OkxClientService`, в `Order` не хранятся. `reduceOnly` из response —
только для invariant validation, в `OrderExternalSnapshot` не
маппится.

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; `state`
остаётся raw string в `externalStatus` (резолвинг — позже).
