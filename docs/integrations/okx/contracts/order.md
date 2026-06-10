# OKX contracts: ordinary order

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по ordinary order: endpoint'ы, лимиты,
ACK-семантика, пагинация.

## Контекст

Mapping в `Order` — `docs/models/mapping/Order.md` (раздел `## OKX`).
Native response/request поля — `docs/models/integrations/okx/OkxOrderResponse.md`.
Правила OKX (reduce-only invariant, adapter-константы) —
`docs/integrations/okx/rules/`. Доменные модель/lifecycle —
`docs/models/domain/core/Order.md` / `docs/lifecycles/Order.md`.

## Endpoints

- **Create** (`SUBMIT_ORDER`): `POST /api/v5/trade/order`. Permission
  `Trade`; rate limit 60 req / 2 s по User ID + Instrument ID.
- **Amend** (`AMEND_ORDER`): `POST /api/v5/trade/amend-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. `newPx`/`newSz`/`attachAlgoOrds` — изменения должны включать
  уже исполненную часть для `partially_filled`. `cxlOnFail` (boolean)
  — биржа отменит ордер, если amend упал. `pxAmendType=0|1` — `1`
  разрешает автокорректировку цены в допустимый диапазон.
- **Cancel** (`CANCEL_ORDER`): `POST /api/v5/trade/cancel-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Body: `instId` + одно из `ordId` / `clOrdId` (если оба — биржа
  использует `ordId`).
- **Order details** (`REFRESH_ORDER`): `GET /api/v5/trade/order`.
  Permission `Read`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Query: `instId` обязателен, одно из `ordId` / `clOrdId`. Если
  оба — биржа возвращает по `ordId`. Если `clOrdId` переиспользован,
  биржа возвращает **последний** ордер с этим `clOrdId`.
- **Pending** (звено цикла `REFRESH_ORDER`): `GET /api/v5/trade/orders-pending`.
  Permission `Read`; rate limit 60 req / 2 s по User ID. Фильтры:
  `instType`, `instId`, `ordType`, `state` (`live`/`partially_filled`),
  пагинация `after`/`before` по `ordId`, `limit` ≤ 100.
- **History** (звено цикла `REFRESH_ORDER`):
  `GET /api/v5/trade/orders-history` (последние 7 дней; permission
  `Read`; rate limit 40 req / 2 s по User ID),
  `GET /api/v5/trade/orders-history-archive` (последние 3 месяца; rate
  limit 20 req / 2 s по User ID). Отменённые без исполнений хранятся в
  `orders-history` только ~2 часа. Фильтры: `instType` (обязателен в
  history), `instId`, `ordType`, `state` (`filled`/`canceled`/
  `mmp_canceled`), `category` (`normal`/`adl`/`full_liquidation`/
  `partial_liquidation`/`delivery`/`twap` и др.), `begin`/`end` по
  `cTime` (только в history-7d), пагинация `after`/`before` по `ordId`,
  `limit` ≤ 100.

## ACK-семантика

ACK любой create/amend/cancel (`sCode=0`) не является runtime truth
(`docs/rules/ack-not-runtime-truth.md`). Финальные статусы
подтверждаются через order details / pending / history / archive.

### Create response (ACK)

`POST /trade/order` → `data[0]` с `ordId`, `clOrdId`, `tag`, `ts`
(когда OKX закончил обработку), `sCode`, `sMsg`. Top-level
`inTime`/`outTime` — диагностические времена REST-шлюза
(микросекунды), в домен не маппятся. `ordId` после successful submit
сохраняется как `Order.externalId`; статус — `PENDING` до
refresh/search/history.

### Amend response (ACK)

`POST /trade/amend-order` → `data[0]` с `ordId`, `clOrdId`, `reqId`
(если был передан), `ts`, `sCode`, `sMsg`. `sCode=0` — запрос
принят, не «изменение подтверждено». Подтверждение — через
`REFRESH_ORDER` или WS `orders`.

### Cancel response (ACK)

`POST /trade/cancel-order` → `data[0]` с `ordId`, `clOrdId`, `sCode`,
`sMsg`. `sCode != 0` — отказ (ордер уже filled/canceled/не найден).
Финальное `CANCELED` — через refresh / WS.

## Пагинация

`after`/`before` — якорь по `ordId` (не времени), `limit ≤ 100`. Для
глубокой выкачки: `after = min(ordId)` ответа → следующая страница.
История 7 дней дополнительно поддерживает `begin`/`end` по `cTime`
(ms).

## Evidence-cycle

Полный цикл для evidence-not-found: `GET /trade/order` →
`orders-pending` → `orders-history` → `orders-history-archive` (если
history не покрывает период). Подробно — в `mapping/Order.md` §OKX
evidence-cycle.
