# TickerOkxResponse (OKX market ticker)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели котировки источника.

## Поля DTO

| OKX field | Тип (raw) | Используется | Назначение |
|---|---|---|---|
| `instType` | string | да | Тип инструмента; переход runtime-цены кладёт его в `externalInstrumentType` (`docs/models/mapping/MarketPriceData.md`), срез тикера его не читает. |
| `instId` | string | да | Имя инструмента (`ETH-USDT-SWAP`). |
| `last` | string (decimal) | да | Last traded price. |
| `askPx` | string (decimal) | да | Best ask. |
| `bidPx` | string (decimal) | да | Best bid. |
| `askSz` | string (decimal) | да | Объём на лучшем ask — **вводится шагом 7**: операнд измерителя ёмкости `Order.bookDepthAtPlacement`. |
| `bidSz` | string (decimal) | да | Объём на лучшем bid. Там же. |
| `ts` | string (epoch millis) | да | Время тикера. |
| `vol24h` | string (decimal) | да | Объём за сутки: у деривативов — числом контрактов, у спота — в базовой валюте (офдок) — **вводится сбором срезов**: переход среза тикера кладёт его в `MarketTicker.volume` (`docs/models/domain/other/MarketTicker.md`); переход runtime-цены его не читает. |

Таблица выровнена под **худой coded DTO** (`TickerOkxResponse.java`:
`instType`/`instId`/`last`/`askPx`/`bidPx`/`ts`; сопровождение сделки добавляет
`askSz`/`bidSz`, сбор срезов — `vol24h`): держим только заведённые поля, карваута на полное
зеркало биржи нет.

Числа OKX приходят строками; обязательные числовые строки парсятся
в `BigDecimal`. `MID_PRICE` источником не передаётся — это величина
runtime-модели цены (`docs/components/models/MarketPriceData.md`
§«Середина спреда»); переход её не маппит
(`docs/models/mapping/MarketPriceData.md`).

## Поля, которые НЕ входят в DTO

OKX `market/ticker` — и элемент `market/tickers`, состав у них один
(`docs/integrations/okx/contracts/market-price-data.md`) — отдаёт больше
полей, чем содержит coded DTO:
`lastSz`, `open24h`, `high24h`/`low24h`, `volCcy24h`,
`sodUtc0`/`sodUtc8` (24h-агрегаты и SOD-метрики). Доменно не
используются и в DTO не заведены. **`askSz`/`bidSz` из этого перечня
выведены шагом 7, `vol24h` — сбором срезов** — они переехали в таблицу
используемых.

`markPx`/`idxPx` тикер не несёт вовсе — ни единичный, ни агрегатный
(офдок, сверка 2026-09-30): mark/index price отдаются отдельными
эндпоинтами (`public/mark-price`, `market/index-tickers`).
