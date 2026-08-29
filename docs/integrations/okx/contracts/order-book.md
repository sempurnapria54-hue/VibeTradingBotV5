# OKX contracts: стакан (order book)

## На какой вопрос отвечает этот файл

Каков контракт операций чтения стакана.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Market Data», секции «GET / Order
book», «GET / Full order book»). При расхождении с офдоком побеждает
офдок; синхронизация — перевыкачка + дифф при каждом заходе
интегратора по источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется: стратегия фазы 1 работает на свечах
(`market-price-data.md`, `candle.md`); стакан не нужен. Док —
покрытие периметра.

## GET /api/v5/market/books

Rate limit 40 req / 2 s по IP. Query: `instId` (обяз.), `sz` —
глубина на сторону, ≤ 400 (default 1). Данные обновляются раз в
50 мс; endpoint отдаёт **серверный кэш** (не мгновенный срез);
в pre-open лучший ask может быть ниже лучшего bid (офдок).

Ответ `data[0]`: `asks[]` / `bids[]`, `ts`, `seqId` (sequence id
сообщения). Элемент уровня — массив строк
`[px, sz, "0", numOrders]`: цена; количество на уровне (деривативы —
контракты, спот — base ccy); третий элемент deprecated (всегда
`"0"`); число ордеров на уровне.

## GET /api/v5/market/books-full

Rate limit 10 req / 2 s по IP. Query: `instId` (обяз.), `sz` ≤ 5000
(default 1). Обновление раз в секунду (во время call auction — тоже
около раза в секунду). Ответ: `asks[]` / `bids[]`, `ts`; элемент —
`[px, sz, numOrders]` (без deprecated-элемента), `seqId`
отсутствует.
