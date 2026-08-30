# OkxTickerResponse (OKX market ticker)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели котировки источника.

## Поля DTO

| OKX field | Тип (raw) | Используется | Назначение |
|---|---|---|---|
| `instType` | string | нет | Тип инструмента; в coded DTO есть, в snapshot не маппится. |
| `instId` | string | да | Имя инструмента (`ETH-USDT-SWAP`). |
| `last` | string (decimal) | да | Last traded price. |
| `askPx` | string (decimal) | да | Best ask. |
| `bidPx` | string (decimal) | да | Best bid. |
| `askSz` | string (decimal) | да | Объём на лучшем ask — **вводится шагом 7**: операнд измерителя ёмкости `Order.bookDepthAtPlacement`. |
| `bidSz` | string (decimal) | да | Объём на лучшем bid. Там же. |
| `ts` | string (epoch millis) | да | Время тикера. |

Таблица выровнена под **худой coded DTO** (`OkxTickerResponse.java`:
`instType`/`instId`/`last`/`askPx`/`bidPx`/`ts`; сопровождение сделки добавляет
`askSz`/`bidSz`): держим только заведённые поля, карваута на полное
зеркало биржи нет.

Числа OKX приходят строками; обязательные числовые строки парсятся
в `BigDecimal`. `MID_PRICE` источником не передаётся — вычисляется
доменно как `(bidPx + askPx) / 2` (см.
`docs/models/mapping/MarketPriceData.md`).

## Поля, которые НЕ входят в DTO

OKX `market/ticker` отдаёт больше полей, чем содержит coded DTO:
`lastSz`, `open24h`, `high24h`/`low24h`, `vol24h`/`volCcy24h`,
`sodUtc0`/`sodUtc8` (24h-агрегаты и SOD-метрики). Доменно не
используются и в DTO не заведены. **`askSz`/`bidSz` из этого перечня
выведены шагом 7** — они переехали в таблицу используемых.

`markPx`/`idxPx` ранее значились в таблице полей DTO — в coded DTO их
**нет** (перенесены сюда при doc-sync). Возвращает ли `market/ticker`
их вообще — **требует офдок-сверки** (`integrator`): mark/index price
отдаются отдельными эндпоинтами (`public/mark-price`,
`market/index-tickers`). До сверки как факт ticker'а не утверждается.
