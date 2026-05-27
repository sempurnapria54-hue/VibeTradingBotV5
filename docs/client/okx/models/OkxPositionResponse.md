# OkxPositionResponse (OKX positions)

## На какой вопрос отвечает этот файл

Какие поля у OKX positions response — что приходит от биржи и что из
этого используется для `Position`.

## Контекст

Raw response эндпоинта `GET /api/v5/account/positions`. Используется
client-layer (`OkxClientService` / `OkxRestClient` / response DTO) для
маппинга в `PositionExternalSnapshot`. Доменная семантика — в
`docs/models/core/Position.md` и `docs/lifecycles/Position.md`;
mapping/валидация — в `docs/client/okx/rules/okx-position-mapping.md`.
Raw DTO не выходит за adapter-layer (`docs/rules/raw-exchange-dto-boundary.md`).

В net mode по инструменту ожидается одна запись `posSide=net`; `pos`
может быть положительным или отрицательным; `posId` может жить
ограниченное время после полного закрытия.

## Поля, которые используются (маппятся в `PositionExternalSnapshot`)

| OKX field | Назначение | Snapshot field |
|---|---|---|
| `posId` | биржевой ID позиции | `externalId` |
| `pos` | размер со знаком | `abs(pos)` → `externalSize`; знак → `direction` |
| `avgPx` | средняя цена входа | `externalAverageEntryPrice` |
| `markPx` | mark price | `externalMarkPrice` |
| `liqPx` | цена ликвидации | `externalLiquidationPrice` |
| `margin` | маржа позиции | `externalMargin` |
| `upl` | нереализованный PnL | `externalUnrealizedProfit` |
| `cTime` | время создания на бирже | `externalCreatedAt` |
| `uTime` | время обновления на бирже | `externalModifiedAt` |

## Поля для request / validation (в adapter-layer, не в домене)

`instId`, `instType`, `mgnMode`, `posSide`, `lever` — используются для
request и валидации в `ClientService`, в `Position` /
`PositionExternalSnapshot` не хранятся.

## Поля, которые НЕ переносятся в Position

`availPos`, `hedgedPos`, `last`, `idxPx`, `usdPx`, `bePx`, `uplRatio`,
`uplLastPx`, `uplRatioLastPx`, `imr`, `mmr`, `mgnRatio`,
`notionalUsd`, `realizedPnl`, `settledPnl`, `pnl`, `fee`,
`fundingFee`, `liqPenalty`, `ccy`, `interest`, `liab`, `liabCcy`,
`pendingCloseOrdLiabVal`, `adl`, `tradeId`, `closeOrderAlgo[]`,
spot/option/margin-specific поля, `bizRefId`, `bizRefType`, raw
response.

Почему: `availPos` не нужен (partial exit — через reduce-only
`Order`/`AlgoOrder`, не close-position); `bePx` не нужен для
live-risk; `tradeId` — id последней сделки/fill, не позиции;
`realizedPnl`/`fee`/`fundingFee`/`pnl` — для итоговой аналитики через
fills/finalization, не через `Position`; `closeOrderAlgo[]` —
защита живёт через `Order`/`AlgoOrder`; raw response не часть домена.

## Close-position response

```json
{ "code": "0", "msg": "", "data": [ { "instId": "ETH-USDT-SWAP", "posSide": "net" } ] }
```

`code = 0` → ACK success; `code != 0` → command failed. ACK не
является runtime truth (см. `docs/rules/ack-not-runtime-truth.md`).
