# OKX contracts: максимальные размеры ордера

## На какой вопрос отвечает этот файл

Каков контракт операций оценки максимального размера ордера (`max-size`)
и доступного баланса/эквити под сделку (`max-avail-size`).

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Trading
Account → REST API», секции «Get maximum order quantity», «Get maximum
available balance/equity»). Процедура сверки и приоритет офдока —
`.claude/rules/external-source-sync.md`. Последняя сверка: 2026-06-11
(поле-уровневая дистилляция).

## Статус использования

Не используется, и в преконтроль не берётся
(`.claude/decisions/server-side-precheck-not-adopted.md`). Оба вопроса у
собственного преконтроля уже имеют ответ из персистированных данных:
потолок размера — торговые ограничения инструмента (`SIZE_ABOVE_LIMIT`),
доступность средств — маржа акта против свободного остатка свежего
снимка (`BALANCE_NOT_ENOUGH`; `docs/components/RiskValidator.md`).
Собственный расчёт размера первичен (`SizeCalculator`), а расхождение с
серверной оценкой приходит громким отказом постановки.

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
