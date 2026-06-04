# OkxOrderResponse (OKX ordinary order)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели OKX ordinary order response (включая
вложенный массив `attachAlgoOrds`) и какие из них использует bot.

## Контекст

Нативная модель источника OKX. Возвращается `GET /trade/order`,
`/trade/orders-pending`, `/trade/orders-history`,
`/trade/orders-history-archive` и create/amend/cancel ACK (с
сокращённым набором). Не выходит за `IntegrationService`/adapter — см.
`docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `OrderExternalSnapshot` и далее в `Order` —
`docs/models/mapping/Order.md` (раздел `## OKX`). Доменная модель и
статусы — `docs/models/domain/core/Order.md` и
`docs/lifecycles/Order.md`. Контракт endpoint'ов / rate limits / ACK
— `docs/integrations/okx/contracts/order.md`. Правила OKX
(`isolated`/`net` константы, reduce-only invariant) —
`docs/integrations/okx/rules/`.

## Инвентарь полей ordinary order

Все числа OKX отдаёт строками; типы ниже — семантические.

### Используемые

| OKX field | Тип | Семантика |
|---|---|---|
| `instId` | string | имя инструмента (`ETH-USDT-SWAP`) |
| `instType` | string | тип инструмента (`SWAP`/...) |
| `ordId` | string | биржевой order id |
| `clOrdId` | string | client order id |
| `ordType` | string | тип ордера (`market`/`limit`/`post_only`/`fok`/`ioc`/`optimal_limit_ioc`/...) |
| `side` | string | `buy` / `sell` |
| `state` | string | сырой статус (`live`/`partially_filled`/`filled`/`canceled`/`mmp_canceled`) |
| `px` | string-decimal | цена; для market часто пусто |
| `sz` | string-decimal | размер (контракты для SWAP/FUTURES) |
| `accFillSz` | string-decimal | исполненный объём (накопленно) |
| `avgPx` | string-decimal | средняя цена исполнения |
| `fee` | string-decimal | комиссия (обычно отрицательная) |
| `cTime` | string-ms | время создания |
| `uTime` | string-ms | время обновления |
| `reduceOnly` | string-bool | reduce-only факт (используется adapter'ом для invariant validation) |
| `tdMode` | string | режим торговли (`isolated`/`cross`/`cash`); adapter сверяет `=isolated` |
| `posSide` | string | сторона позиции (`net`/`long`/`short`); adapter сверяет `=net` |
| `attachAlgoClOrdId` | string | top-level attached client id |
| `tpTriggerPx` / `tpTriggerPxType` / `tpOrdPx` | string | top-level TP (если ставился через attach) |
| `slTriggerPx` / `slTriggerPxType` / `slOrdPx` | string | top-level SL |
| `attachAlgoOrds[]` | array | вложенные attached TP/SL (см. ниже) |

### Подобъект `attachAlgoOrds[*]`

| OKX field | Тип | Семантика |
|---|---|---|
| `attachAlgoId` | string | биржевой attached algo id |
| `attachAlgoClOrdId` | string | client id вложенного TP/SL — основной ключ матчинга |
| `algoId` | string | algo id после trigger |
| `algoClOrdId` | string | diagnostic / future |
| `tpOrdKind` | string | `condition`/`limit` |
| `tpTriggerPx` / `tpTriggerPxType` / `tpOrdPx` | string | TP параметры (дубль top-level в pending/details/history) |
| `slTriggerPx` / `slTriggerPxType` / `slOrdPx` | string | SL параметры |
| `tpTriggerRatio` / `slTriggerRatio` | string-decimal | триггер в доле (FUTURES/SWAP) |
| `sz` | string-decimal | размер (для split-TP) |
| `amendPxOnTriggerType` | string | `0`/`1` cost-price SL для split-TP |
| `failCode` / `failReason` | string | ошибка постановки attached |

Полноценного `state` у `attachAlgoOrds[*]` нет — статус резолвится по
набору фактов (см. `docs/lifecycles/Order.md`).

### Не используется bot'ом (отбрасываются на маппинге)

`ccy` (валюта маржи; adapter использует USDT-policy), `lever` (плечо;
сверка против биржевого максимума `externalMaxLeverage`), `fillPx`/`fillSz`/`fillTime`/
`tradeId` (поля «последнего исполнения» — факт собирается через
fills, `docs/models/mapping/TradeFill.md`), `feeCcy`/`rebate`/
`rebateCcy`/`pnl` (для итоговой аналитики через fills/finalization),
`tag` (метка; диагностика), `source`/`cancelSource`/
`cancelSourceReason`/`category` (`normal`/`adl`/`liquidation`/
`delivery`/`twap`/...; диагностика), `stpMode`/`stpId` (self-trade
prevention), `quickMgnType`, `pxType`/`pxUsd`/`pxVol` (options),
`tgtCcy` (SPOT market), `tradeQuoteCcy`, `linkedAlgoOrd.algoId`,
`algoId`/`algoClOrdId` (top-level — связь с algo логикой),
`isTpLimit`.

## ACK ответы create/amend/cancel

Сокращённый набор — `ordId`, `clOrdId`, `tag`, `reqId` (только amend
echo), `ts`, `sCode`, `sMsg`. Top-level `inTime`/`outTime` —
диагностические времена REST-шлюза (микросекунды). Маппинг ACK в
domain и правила semantics — `docs/integrations/okx/contracts/order.md`
и `docs/rules/ack-not-runtime-truth.md`.

## Конвертация (общая для всех числовых полей)

`empty string → null`; numeric string → `BigDecimal`; timestamp
string → epoch millis / `Instant`; `state` остаётся raw string при
выходе из источника (резолвинг — позже, на стороне
`mapping/Order.md`).
