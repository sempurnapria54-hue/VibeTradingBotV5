# OKX contracts: индексные данные (тикеры и свечи индекса)

## На какой вопрос отвечает этот файл

Каков контракт операций чтения данных индекса.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Public Data → REST API», секции «Get index tickers», «Get
index candlesticks», «Get index candlesticks history»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется: стратегия работает по свечам инструмента
(`candle.md`); индексная цена — базис mark price (`mark-price.md`) и
`StrategyPriceSource.INDEX_PRICE` (потенциально). `instId` здесь —
**индекс** (`BTC-USD`, = `uly`), не торговый инструмент.

## GET /api/v5/market/index-tickers

Rate limit 20 req / 2 s по IP. Query: одно из `quoteCcy`
(USD/USDT/BTC/USDC) / `instId` (индекс). Ответ: `instId`, `idxPx`
(текущая индексная цена), `high24h`/`low24h`/`open24h`,
`sodUtc0`/`sodUtc8` (цены открытия суток UTC+0/UTC+8), `ts`.

## GET /api/v5/market/index-candles

Rate limit 20 req / 2 s по IP. Последние 1440 точек. Query: `instId`
(индекс; обяз.), `bar` (default `1m`; 1m/3m/5m/15m/30m/1H/2H/4H;
дневные+ в вариантах UTC+8 `6H/12H/1D/1W/1M/3M` и UTC+0
`6Hutc/...`), `after`/`before` — пагинация по `ts` (одиночный
`before` отдаёт свежайшие), `limit` ≤ 100.

Элемент — массив `[ts, o, h, l, c, confirm]`; `confirm`: `0`
незавершённая / `1` завершённая. Незавершённую свечу не поллить
повторно (офдок).

## GET /api/v5/market/history-index-candles

Rate limit 10 req / 2 s по IP. Параметры и формат — как у
`index-candles`; глубина за пределами свежего окна.
