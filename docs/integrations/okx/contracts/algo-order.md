# OKX contracts: algo-order

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по algo-ордеру: endpoint'ы, лимиты,
ACK-семантика, ordType-specific body, evidence-cycle.

## Контекст

Mapping в `AlgoOrder` — `docs/models/mapping/AlgoOrder.md` (раздел
`## OKX`). Native response/request поля —
`docs/models/integrations/okx/OkxAlgoOrderResponse.md`. Правила OKX —
`docs/integrations/okx/rules/`. Доменные модель/lifecycle —
`docs/models/domain/core/AlgoOrder.md` / `docs/lifecycles/AlgoOrder.md`.

## Endpoints

- **Create** (`SUBMIT_ALGO_ORDER`): `POST /api/v5/trade/order-algo`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — общие поля (`instId`, `tdMode`, `side`, `ordType`, `sz`,
  `posSide`, `reduceOnly`, `algoClOrdId`) + ordType-specific.
- **Amend** (`AMEND_ALGO_ORDER`): `POST /api/v5/trade/amend-algos`.
  Permission `Trade`; rate limit как `cancel-algos`. Body — `instId`,
  `algoId` (если известен), `algoClOrdId`, новые trigger/trailing
  значения.
- **Cancel** (`CANCEL_ALGO_ORDER`): `POST /api/v5/trade/cancel-algos`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — `instId` + `algoId` (обязателен; `algoClOrdId` опц.,
  диагностика). Отказ через `sCode != 0` (algo уже сработал/закрыт/
  отменён/не найден).
- **Details** (`REFRESH_ALGO_ORDER`): `GET /api/v5/trade/order-algo`.
  Permission `Read`. Query: одно из `algoId` (приоритет) /
  `algoClOrdId`; `instId` опц. Ответ — массив `data`, ожидается 0 или
  1 элемент.
- **Pending** (звено цикла `REFRESH_ALGO_ORDER`): `GET /api/v5/trade/orders-algo-pending`.
  Permission `Read`. Фильтры по `ordType`, `instType`, `instId`,
  `algoId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.
- **History** (звено цикла `REFRESH_ALGO_ORDER`):
  `GET /api/v5/trade/orders-algo-history`. Permission `Read`; rate
  limit 20 req / 2 s по User ID. История доступна за последние 3
  месяца. Query: **`ordType` обязателен** (вычисляется из
  `conditionType`); + одно из `state` (`effective`/`canceled`/
  `order_failed`/`partially_failed`) или `algoId`; опц. `instType`,
  `instId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.

## ACK-семантика

ACK любой create/cancel (`sCode=0`) не runtime truth
(`docs/rules/ack-not-runtime-truth.md`). `CANCEL_ALGO_ORDER` не
ставит `CANCELED`; `AMEND_ALGO_ORDER` не подтверждён без
refresh/search/history. Submit использует stable client id
(`internalId → algoClOrdId`); перед retry — refresh/search по
`algoClOrdId`.

### Create response (ACK)

`POST /trade/order-algo` → `data[0]` с `algoId`, `algoClOrdId`,
`clOrdId` (deprecated), `sCode`, `sMsg`, `tag`.

### Cancel response (ACK)

`POST /trade/cancel-algos` → `data[0]` с `algoId`, `algoClOrdId`,
`sCode`, `sMsg`.

## ordType-specific create body

- `conditional` — TP **или** SL (если оба в `conditional` в
  net-режиме, биржа может проигнорировать TP — для одновременных
  TP+SL использовать `oco`).
- `oco` — TP и SL вместе; срабатывание одного отменяет другой.
- `trigger` — `triggerPx` + `orderPx` (`-1` = market) + опц.
  `triggerPxType` (default `last`). Активы при постановке обычно
  **не морозятся** (проверка баланса в момент срабатывания).
- `move_order_stop` — trailing: ровно одно из `callbackRatio`
  (`0.01` = 1%) / `callbackSpread` (абсолют); опц. `activePx` — без
  него трейлинг включается сразу. Расчёт триггера: long → min +
  spread/ratio; short → max − spread/ratio.

`closeFraction` (доля позиции при срабатывании, `1` = 100%) на
первом этапе не используем. Для protective обычно `reduceOnly=true`;
для SWAP рекомендуется `tpTriggerPxType=mark`.

## Evidence-cycle

Полный цикл: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Подробно — в `mapping/AlgoOrder.md` §OKX
evidence-cycle.
