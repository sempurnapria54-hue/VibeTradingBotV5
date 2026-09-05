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

**Агрегатная форма — `GET /api/v5/market/tickers`** (плюрал): один запрос
отдаёт тикеры всего листинга по `instType`. Rate limit 20 req / 2 s по
IP. Элемент — тот же объект, что у единичного чтения
(`docs/models/integrations/okx/TickerOkxResponse.md`), плюс суточные
объёмы (`vol24h`, `volCcy24h`). Форма подтверждена контуром проверки
источника (`.claude/tests/source-api/okx/plan.md` §«MG1. Tickers
(плюрал) — GET /api/v5/market/tickers (Market Data)»).

**Зачем агрегатная:** срез цен по всему листингу поинструментным обходом
стоил бы сотни запросов из общего бюджета лимитов
(`docs/processes/snapshot-collection.md`).
Rate limit: 20 req / 2 s по IP + Instrument ID. Query: `instId`
обязателен (`ETH-USDT-SWAP`).

Текущий рантайм (до рефакторинга на микросервисы) — REST.
WS-альтернатива: public канал `tickers` (URL — `/ws/v5/public`) —
планируемый realtime-источник, отложен.
