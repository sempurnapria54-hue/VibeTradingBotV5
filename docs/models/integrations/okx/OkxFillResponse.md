# OkxFillResponse (OKX trade fills)

## На какой вопрос отвечает этот файл

Какие поля у OKX fill response (одна сделка / одно исполнение).

## Контекст

Raw OKX `FillResponse` — элемент `data[]` ответов
`GET /api/v5/trade/fills` (последние 3 дня) и
`GET /api/v5/trade/fills-history` (последние 3 месяца). Эти fills-эндпоинты
в runtime фазы 1 **не используются**: команда `REFRESH_FILLS` снята на шаге 7
(`docs/decisions/pnl-finalization-mechanics.md` реш.1) — order-fill-метрики
(`accFillSz`/`avgPx`) идут прямо из `OkxOrderResponse` (`REFRESH_ORDER_COMMAND`), а
число P&L — из positions-history/bills. Поля ниже оставлены как справка
(deep-архив — `OKX-Q2`).

`Fill` как persisted entity в фазе 1 **не вводим** — **OKX-Q1 закрыт**
(persisted `TradeFill` не материализуется,
`docs/decisions/result-profit-source.md`). Поэтому ниже описаны поля DTO
биржи, а не маппинг в доменную сущность.

Раw OKX DTO не выходит за adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`).

Mapping — `docs/models/mapping/TradeFill.md` (стаб, **OKX-Q1 закрыт**:
`TradeFill` в фазе 1 не вводится). Контракт endpoint'ов / rate limits /
пагинация — `docs/integrations/okx/contracts/fills.md`.

## Различие fills и orders

- **Order** = заявка (может исполниться частично или несколько раз).
- **Fill** = факт одной сделки (одно исполнение одного ордера).

Один ордер может породить несколько fills.

## Поля одного fill (по архивному источнику)

Поля документированы; сужение до used не выполняется — **OKX-Q1 закрыт**
(`TradeFill` в фазе 1 не вводится, fills не персистятся отдельно).

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
