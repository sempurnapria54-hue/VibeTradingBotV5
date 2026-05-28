# OKX contracts: market price data (ticker)

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции получения тикера.

## Контекст

Mapping в `MarketPriceData` — `docs/models/mapping/MarketPriceData.md`
(раздел `## OKX`). RVO — `docs/components/models/MarketPriceData.md`.

## Endpoint

`GET /api/v5/market/ticker`. Permission: Public (auth не нужен).
Rate limit: 20 req / 2 s по IP + Instrument ID. Query: `instId`
обязателен (`ETH-USDT-SWAP`).

WS-альтернатива: public канал `tickers` (URL — `/ws/v5/public`),
основной runtime-источник; REST — fallback при отсутствии WS / при
старте до подъёма WS. Подробнее по WS-каналам — OKX-Q4.
