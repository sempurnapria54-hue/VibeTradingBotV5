# OKX contracts: максимальные размеры ордера

## На какой вопрос отвечает этот файл

Каков контракт операций оценки максимального размера ордера (`max-size`)
и доступного баланса/эквити под сделку (`max-avail-size`).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секции «Get maximum order
quantity», «Get maximum available balance/equity»). При расхождении
с офдоком побеждает офдок; синхронизация — перевыкачка + дифф при
каждом заходе интегратора по источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(поле-уровневая дистилляция).

## Статус использования

Не используется. Релевантность — преконтроль размера:
серверная оценка потолка `sz` до постановки; собственный расчёт
размера остаётся первичным (`SizeCalculator`).

## GET /api/v5/account/max-size

Permission `Read`; rate limit 20 req / 2 s по User ID. Максимальный
`sz` для buy/sell — соответствует `sz` постановки. Под PM cross
деривативов не поддерживается (офдок).

Query: `instId` (обяз., до 5 одного instType через запятую),
`tdMode` (обяз.: `cross`/`isolated`/`cash`/`spot_isolated`), `ccy`
(маржа — isolated MARGIN / cross MARGIN Futures mode), `px` (опц.;
без него FUTURES/SWAP считаются по текущему price limit; при
нескольких `instId` игнорируется), `leverage` (опц., default —
текущее; MARGIN/FUTURES/SWAP), `tradeQuoteCcy` (SPOT).

Ответ: `instId`, `ccy`, `maxBuy` / `maxSell` — для FUTURES/SWAP — в
**контрактах**; для SPOT/MARGIN — base/quote-валюта по офдоку.

## GET /api/v5/account/max-avail-size

Permission `Read`; rate limit 20 req / 2 s по User ID. Доступный
баланс (isolated, SPOT) / эквити (cross) под сделку.

Query: `instId` (обяз., до 5), `tdMode` (обяз.), `ccy` (маржа),
`reduceOnly` (MARGIN), `px` (цена закрытия, reduceOnly MARGIN),
`tradeQuoteCcy` (SPOT).

Ответ: `instId`, `availBuy` / `availSell` (SPOT/MARGIN: quote на
buy, base на sell; cross MARGIN — в валюте `ccy`).

## Различие двух операций

`max-size` отвечает «какой максимальный `sz` примет биржа»,
`max-avail-size` — «сколько баланса/эквити доступно под сделку»;
первый учитывает плечо/лимиты инструмента, второй — доступность
средств.
