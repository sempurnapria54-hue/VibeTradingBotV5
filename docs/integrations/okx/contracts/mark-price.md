# OKX contracts: mark price (текущая и свечи)

## На какой вопрос отвечает этот файл

Каков контракт операций чтения mark price.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
разделы «Public Data → REST API», секции «Get mark price», «Get mark
price candlesticks», «Get mark price candlesticks history»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 3, поле-уровневая дистилляция). Рантайм-сверка:
2026-06-19 (контур source-api, demo — рантайм-заметка в
/api/v5/public/mark-price; провенанс `рантайм`).

## Статус использования

Не используется напрямую. Форвард-кандидат **В-8** (шаг 5,
риск/преконтроль): дистанция до ликвидации считается от mark price;
protective algo для SWAP рекомендованы с `tpTriggerPxType=mark`
(`algo-order.md`) — мониторинг той же цены, по которой триггерится
биржа. Mark price строится от спот-индекса с защитой от манипуляции
ценой контракта (офдок).

## GET /api/v5/public/mark-price

Rate limit 10 req / 2 s по IP + Instrument ID. Query: `instType`
(обяз.: MARGIN/SWAP/FUTURES/OPTION), `instFamily` / `instId` (опц.).
Ответ: `instType`, `instId`, `markPx`, `ts`.

**Рантайм (2026-06-19, demo, контур source-api / PG1):** `instType`
фактически **необязателен при заданном `instId`** — запрос
`?instId=ETH-USDT-SWAP` без `instType` вернул `code=0` с корректным
элементом (`instType` выводится из `instId`). Офдок помечает `instType`
обязательным — расхождение зафиксировано прогоном (провенанс
`рантайм`). Сопутствующие негативы: битый `instType` (с instId) →
реджект `51000` («Parameter instType error»); несущ. `instId` →
`51001`.

## GET /api/v5/market/mark-price-candles

Rate limit 20 req / 2 s по IP. Последние 1440 точек. Query: `instId`
(**торговый инструмент**, например `BTC-USD-SWAP`; обяз.), `bar`
(default `1m`; набор и UTC-варианты как у свечей индекса —
`index-data.md`), `after`/`before` по `ts`, `limit` ≤ 100.

Элемент — `[ts, o, h, l, c, confirm]`; `confirm`: `0` незавершённая
/ `1` завершённая; незавершённую не поллить повторно.

## GET /api/v5/market/history-mark-price-candles

Rate limit 20 req / 2 s по IP. Параметры и формат — как у
`mark-price-candles`; глубина за пределами свежего окна.
