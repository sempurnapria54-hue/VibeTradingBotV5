# OKX contracts: market price data (ticker)

## На какой вопрос отвечает этот файл

Каков контракт операции получения тикера.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Market Data», секции «GET / Tickers»,
«GET / Ticker»). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора
(`.claude/processes/api-docs-completion.md`, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(существование/путь по манифесту; поле-уровневая перевычитка — при
заходе по теме).

## Endpoint

`GET /api/v5/market/ticker`. Permission: Public (auth не нужен).
Rate limit: 20 req / 2 s по IP + Instrument ID. Query: `instId`
обязателен (`ETH-USDT-SWAP`).

Текущий рантайм (до рефакторинга на микросервисы) — REST.
WS-альтернатива: public канал `tickers` (URL — `/ws/v5/public`) —
планируемый realtime-источник, отложен.
