# OKX contracts: market price data (ticker)

## На какой вопрос отвечает этот файл

Каков контракт операции получения тикера.

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Order
Book Trading → Market Data», секции «GET / Tickers», «GET / Ticker»).
Процедура сверки и приоритет офдока —
`.claude/rules/external-source-sync.md`. Последняя сверка: 2026-09-30,
поле-уровневая — обе секции: запрос, лимит и перечень параметров ответа.

## Endpoint

`GET /api/v5/market/ticker`. Permission: Public (auth не нужен).
Rate limit: 20 req / 2 s по IP. Query: `instId` обязателен
(`ETH-USDT-SWAP`).

**Агрегатная форма — `GET /api/v5/market/tickers`** (плюрал): один запрос
отдаёт тикеры всего листинга по `instType` (обязателен: `SPOT`, `SWAP`,
`FUTURES`, `OPTION`, `EVENTS`); `instFamily` необязателен и применим к
деривативам. Rate limit 20 req / 2 s по IP. Элемент — **тот же объект**,
что у единичного чтения: перечни параметров ответа у двух секций офдока
совпадают поимённо — `instType`, `instId`, `last`, `lastSz`, `askPx`,
`askSz`, `bidPx`, `bidSz`, `open24h`, `high24h`, `low24h`, `volCcy24h`,
`vol24h`, `sodUtc0`, `sodUtc8`, `ts`. Поэтому обе формы разбираются одной
нативной моделью (`docs/models/integrations/okx/TickerOkxResponse.md`), а
различаются только числом элементов и ключом запроса. Форму наблюдал и
контур проверки источника на плюральных тикерах
(`GET /api/v5/market/tickers`,
`.claude/work/history/2026-09-16-donor-and-contour-removal/source-api-okx/plan.md`).

**Зачем агрегатная:** срез цен по всему листингу поинструментным обходом
стоил бы сотни запросов из общего бюджета лимитов
(`docs/processes/snapshot-collection.md`).

Текущий рантайм (до рефакторинга на микросервисы) — REST.
WS-альтернатива: public канал `tickers` (URL — `/ws/v5/public`) —
планируемый realtime-источник, отложен.
