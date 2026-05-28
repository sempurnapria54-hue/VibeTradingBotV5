# OkxFillResponse (OKX trade fills)

## На какой вопрос отвечает этот файл

Какие поля у OKX fill response (одна сделка / одно исполнение).

## Контекст

Raw OKX `FillResponse` — элемент `data[]` ответов
`GET /api/v5/trade/fills` (последние 3 дня) и
`GET /api/v5/trade/fills-history` (последние 3 месяца). Используется
client-layer для последующего матчинга с известными `Order` /
`AlgoOrder` / `Position` facts через `RefreshFillsExecutor`
(`docs/components/RefreshFillsExecutor.md`).

`Fill` как persisted entity на первом этапе **не вводим** (см.
`docs/components/RefreshFillsExecutor.md`; материализация `TradeFill`
— OKX-Q1 в `.claude/work/questions/open-questions.md`). Поэтому ниже
описаны поля DTO биржи, а не маппинг в доменную сущность.

Раw OKX DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`).

Mapping (когда `TradeFill` материализуется) —
`docs/models/mapping/TradeFill.md` (стаб, ссылка на OKX-Q1). Контракт
endpoint'ов / rate limits / пагинация —
`docs/integrations/okx/contracts/fills.md`.

## Различие fills и orders

- **Order** = заявка (может исполниться частично или несколько раз).
- **Fill** = факт одной сделки (одно исполнение одного ордера).

Один ордер может породить несколько fills.

## Поля одного fill (по архивному источнику)

Поля документированы; сужение до used отложено до материализации
`TradeFill` (см. OKX-Q1).

| OKX field | Назначение |
|---|---|
| `instType` | тип инструмента (`SWAP`/`SPOT`/...) |
| `instId` | инструмент (например `ETH-USDT-SWAP`) |
| `tradeId` | id сделки на бирже |
| `ordId` | id ордера, который породил сделку |
| `clOrdId` | client order id ордера (если был задан) |
| `billId` | внутренний id записи; **якорь для пагинации** через `after`/`before` |
| `tag` | метка (если передавалась) |
| `fillPx` | цена этой сделки |
| `fillSz` | объём сделки (контракты для SWAP/FUTURES) |
| `side` | `buy` / `sell` |
| `posSide` | `net` / `long` / `short` (зависит от режима позиций) |
| `execType` | `T` taker / `M` maker (направление по ликвидности) |
| `feeCcy` | валюта комиссии |
| `fee` | комиссия (обычно отрицательная — списание; положительная — rebate) |
| `ts` | время сделки (Unix ms) |

Все числа приходят строками; numeric → `BigDecimal` при парсинге.
