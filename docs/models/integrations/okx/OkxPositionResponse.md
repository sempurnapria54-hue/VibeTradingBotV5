# OkxPositionResponse (OKX positions)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели OKX positions response и какие из них
использует bot.

## Контекст

Нативная модель источника OKX. Возвращается `GET
/api/v5/account/positions`. Не выходит за `IntegrationService`/adapter —
`docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `PositionExternalSnapshot` и далее в `Position` —
`docs/models/mapping/Position.md` (раздел `## OKX`). Доменная модель и
статусы — `docs/models/domain/core/Position.md` и
`docs/lifecycles/Position.md`. Контракт endpoint'а / rate limits /
close-position ACK — `docs/integrations/okx/contracts/position.md`.
Правила OKX (`isolated`/`net` константы, leverage validation) —
`docs/integrations/okx/rules/`.

В net mode по инструменту ожидается одна запись `posSide=net`; `pos`
может быть положительным или отрицательным; `posId` может жить
ограниченное время после полного закрытия (биржа держит ~30 дней).

## Инвентарь полей

### Используемые

| OKX field | Тип | Семантика |
|---|---|---|
| `instId` | string | имя инструмента (для сверки expected) |
| `instType` | string | тип инструмента (для сверки) |
| `posId` | string | биржевой id позиции |
| `pos` | string-decimal | размер со знаком (`+` long, `−` short в net mode) |
| `avgPx` | string-decimal | средняя цена входа |
| `markPx` | string-decimal | mark price |
| `liqPx` | string-decimal | расчётная цена ликвидации |
| `margin` | string-decimal | маржа позиции |
| `upl` | string-decimal | unrealized PnL (по mark) |
| `cTime` | string-ms | время создания |
| `uTime` | string-ms | время обновления |
| `mgnMode` | string | режим маржи (`isolated`/`cross`); adapter сверяет `=isolated` |
| `posSide` | string | сторона позиции (`net`/`long`/`short`); adapter сверяет `=net` |
| `lever` | string-decimal | плечо; adapter сверяет `≤` биржевого максимума (`externalMaxLeverage`) |

### Не используется bot'ом (отбрасывается на маппинге)

- **Производные / альтернативные цены и PnL:** `availPos`,
  `hedgedPos`, `last`, `idxPx`, `usdPx`, `bePx`, `nonSettleAvgPx`,
  `uplRatio`, `uplLastPx`, `uplRatioLastPx`.
- **Risk-метрики:** `imr`, `mmr`, `mgnRatio`, `notionalUsd`, `adl`.
- **Реализованный PnL и комиссии** (агрегация через fills/finalization,
  не через `Position`): `realizedPnl`, `settledPnl`, `pnl`, `fee`,
  `fundingFee`, `liqPenalty`.
- **Margin / debt / interest** (margin-режимы, для USDT-SWAP не
  применимо): `ccy`, `interest`, `liab`, `liabCcy`,
  `pendingCloseOrdLiabVal`.
- **Последняя сделка:** `tradeId` (id последней сделки/fill, не
  позиции).
- **`closeOrderAlgo[]`** — список «стратегий закрытия», прикреплённых
  к позиции (`algoId`, `slTriggerPx`, `slTriggerPxType`, `tpTriggerPx`,
  `tpTriggerPxType`, `closeFraction`); защита доменно живёт как
  `AlgoOrder`, из позиции в snapshot не дублируется.
- **Portfolio margin / spot offset / options греки / spot PnL** —
  `spotInUseAmt`, `spotInUseCcy`, `clSpotInUseAmt`, `maxSpotInUseAmt`,
  `optVal`, `deltaBS/PA`, `gammaBS/PA`, `thetaBS/PA`, `vegaBS/PA`,
  spot/margin-specific поля.
- **Deprecated** (помечены устаревшими): `baseBal`, `quoteBal`,
  `baseBorrowed`, `quoteBorrowed`, `baseInterest`, `quoteInterest`.
- **Прочее:** `posCcy`, `bizRefId`, `bizRefType`.

Причины фильтра: `availPos` не нужен (partial exit — через
reduce-only `Order`/`AlgoOrder`, не close-position); `bePx` не нужен
для live-risk; `realizedPnl`/`fee`/`fundingFee`/`pnl` —
агрегация в `Deal.resultProfit` через fills/finalization, не через
`Position`.

## Close-position response

```json
{ "code": "0", "msg": "", "data": [ { "instId": "ETH-USDT-SWAP", "posSide": "net" } ] }
```

`code = 0` → ACK success; `code != 0` → command failed. ACK не
является runtime truth (`docs/rules/ack-not-runtime-truth.md`); в
ответе **нет `ordId`** и нет финального статуса позиции —
подтверждение через `REFRESH_POSITION` (+ опционально `fills`, WS).

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp
string → epoch millis / `Instant`.
