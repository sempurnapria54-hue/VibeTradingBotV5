# OKX order mapping

## На какой вопрос отвечает этот файл

Как OKX ordinary order (request/response) маппится в доменные
`Order` / `AttachedAlgoOrder`, как резолвится статус и как
проверяется reduce-only invariant.

## Контекст

Exchange-specific mapping для OKX. Доменная модель/статусы — в
`docs/models/core/Order.md` и `docs/lifecycles/Order.md`, эта дока
их не заменяет. Поля raw response — в
`docs/client/okx/models/OkxOrderResponse.md`. Command-flow
(`CREATE_ORDER → SUBMIT_ORDER → REFRESH_*`) — cross-cutting
command-подсистема (форвард-заметки в `.claude/work/questions/tasks/order.md`).

## Endpoints

- **Create** (`SUBMIT_ORDER`): `POST /api/v5/trade/order`. Permission
  `Trade`; rate limit 60 req / 2 s по User ID + Instrument ID.
- **Amend** (`AMEND_ORDER`): `POST /api/v5/trade/amend-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Поля `newPx`/`newSz`/`attachAlgoOrds` — изменения должны включать
  уже исполненную часть для `partially_filled`. `cxlOnFail` (boolean) —
  биржа отменит ордер, если amend упал. `pxAmendType=0|1` — `1`
  разрешает автокорректировку цены в допустимый диапазон.
- **Cancel** (`CANCEL_ORDER`): `POST /api/v5/trade/cancel-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Body: `instId` + одно из `ordId` / `clOrdId` (если оба — биржа
  использует `ordId`).
- **Order details** (`REFRESH_ORDER`): `GET /api/v5/trade/order`.
  Permission `Read`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Query: `instId` обязателен, одно из `ordId` / `clOrdId`. Если оба
  — биржа возвращает по `ordId`. Если `clOrdId` переиспользован, биржа
  возвращает **последний** ордер с этим `clOrdId`.
- **Pending** (`REFRESH_PENDING_ORDERS`): `GET /api/v5/trade/orders-pending`.
  Permission `Read`; rate limit 60 req / 2 s по User ID. Фильтры:
  `instType`, `instId`, `ordType`, `state` (`live`/`partially_filled`),
  пагинация `after`/`before` по `ordId`, `limit` ≤ 100.
- **History** (`REFRESH_ORDER_HISTORY`): `GET /api/v5/trade/orders-history`
  (последние 7 дней; permission `Read`; rate limit 40 req / 2 s по User
  ID), `GET /api/v5/trade/orders-history-archive` (последние 3 месяца;
  rate limit 20 req / 2 s по User ID). Отменённые без исполнений хранятся
  в `orders-history` только ~2 часа. Фильтры: `instType` (обязателен в
  history), `instId`, `ordType`, `state` (`filled`/`canceled`/
  `mmp_canceled`), `category` (`normal`/`adl`/`full_liquidation`/
  `partial_liquidation`/`delivery`/`twap` и др.), `begin`/`end` по
  `cTime` (только в history-7d), пагинация `after`/`before` по `ordId`,
  `limit` ≤ 100.

ACK любой create/amend/cancel (`sCode=0`) не является runtime truth
(`docs/rules/ack-not-runtime-truth.md`): финальные статусы
подтверждаются через order details / pending / history / archive.
`CANCEL_ORDER` не ставит `CANCELED`, `AMEND_ORDER` не считается
подтверждённым — без refresh/search/history факта.

### Create response (ACK)

`POST /trade/order` возвращает `data[0]` с `ordId`, `clOrdId`, `tag`,
`ts` (когда OKX закончил обработку), `sCode`, `sMsg`. Top-level `inTime`
/ `outTime` — диагностические времена REST-шлюза (микросекунды), в
домен не маппятся. `ordId` после successful submit можно сохранить как
`Order.externalId`; статус — `PENDING` до refresh/search/history.

### Amend response (ACK)

`POST /trade/amend-order` возвращает `data[0]` с `ordId`, `clOrdId`,
`reqId` (если был передан в запросе), `ts`, `sCode`, `sMsg`. `sCode=0`
— запрос принят, не «изменение подтверждено». Подтверждение — через
`REFRESH_ORDER` или WS `orders`.

### Cancel response (ACK)

`POST /trade/cancel-order` возвращает `data[0]` с `ordId`, `clOrdId`,
`sCode`, `sMsg`. `sCode != 0` означает отказ (ордер уже filled /
canceled / не найден). Подтверждение `CANCELED` — через refresh / WS,
не из ACK.

### Amend TP/SL через `attachAlgoOrds`

В body `POST /trade/amend-order` `attachAlgoOrds` — массив объектов
для изменения прикреплённых TP/SL. Идентификация — `attachAlgoId`
(биржевой) или `attachAlgoClOrdId` (client). Поля: `newTpTriggerPx`/
`newTpOrdPx`/`newTpTriggerPxType`/`newTpOrdKind` (TP),
`newSlTriggerPx`/`newSlOrdPx`/`newSlTriggerPxType` (SL),
`newTpTriggerRatio`/`newSlTriggerRatio` (триггер в доле, только
FUTURES/SWAP — взаимоисключимо с `newTp/SlTriggerPx`). Удаление TP —
`newTpTriggerPx="0"` **или** `newTpOrdPx="0"`; SL — аналогично.

## ClientService константы

`OkxClientService` сам выставляет `tdMode = isolated`, `posSide = net`
(не из domain `Order`, не как произвольные аргументы). В `Order` не
хранятся.

## OrderResponse → OrderExternalSnapshot

См. таблицу полей в `docs/client/okx/models/OkxOrderResponse.md`.
Правила: `empty → null`; numeric → `BigDecimal`; `state` остаётся raw
в `externalStatus` (резолвинг позже); `reduceOnly` не маппится в
snapshot.

## Резолвинг статуса (OrderExternalStatusResolver)

`externalStatus` → `Order.Status` (FSM напрямую не использует, см.
`docs/rules/external-status-resolution.md`):

| OKX raw `state` | Domain status | closeReason |
|---|---|---|
| `live` | `ACTIVE` | `null` |
| `partially_filled` | `PARTIALLY_COMPLETED` | `null` |
| `filled` | `COMPLETED` | `FILLED` |
| `canceled` | `CANCELED` | context-dependent |
| `mmp_canceled` | `CANCELED` | `UNKNOWN` (можно расширить) |
| unknown value | — | бросает `ExternalStatusException(UNKNOWN_EXTERNAL_STATUS)` → safety-каскад |

### Order evidence-cycle / not found

`ExternalNotFoundException` — только после **полного** order
evidence-cycle: `GET /trade/order` → `orders-pending` →
`orders-history` → `orders-history-archive` (если history не
покрывает период). Поиск: есть `externalId` → по `ordId`; нет →
по `clOrdId = internalId`. Пустой ответ одного endpoint не даёт
`MISSING_AFTER_REFRESH`. После полного цикла без находки →
`Order.ERROR` + `MISSING_AFTER_REFRESH` → safety-каскад
(`docs/rules/external-status-resolution.md`). Доп. факты сделки
(`REFRESH_FILLS`, `REFRESH_POSITION`) запрашиваются отдельными
командами; `RefreshOrderExecutor` не сопровождает сделку целиком.

## Domain Order → request mapping

**Create** (`CreateOrderRequest`): `Instrument.externalId → instId`;
`isolated → tdMode`; `Order.side → side`; `net → posSide`;
`Order.type/exec settings → ordType`; `Order.size → sz`;
`Order.price → px` (если нужен типу); `Order.internalId → clOrdId`;
`Order.positionReducingOnly → reduceOnly`; `Order.attachedAlgoOrders
→ attachAlgoOrds` (future DTO-поле для entry-with-attached-SL).
После successful submit: можно сохранить `externalId` (если вернулся
`ordId`); `Order` остаётся `PENDING` до refresh/search/history;
per-item error классифицируется (retryable → RETRY_PENDING;
non-retryable → `Order.ERROR`/`Deal.ERROR` по ситуации).

Дополнительные поля create body (по источнику архива, при необходимости):
`ccy` — валюта маржи (для USDT-SWAP обычно `USDT`); `tag` — метка
(adapter может ставить общий `tb`-тег); `stpMode` — self-trade
prevention (`cancel_maker`/`cancel_taker`/`cancel_both`). В domain
`Order` не хранятся — adapter-policy. `expTime` (header, ms) —
«срок годности запроса»; при необходимости задаётся в client-layer.

**Attached TP/SL при create (один элемент `attachAlgoOrds[*]`):**
`attachAlgoClOrdId` (client id), `tpTriggerPx`/`tpTriggerRatio`,
`tpOrdPx` (`-1` = market после trigger), `tpOrdKind`
(`condition`/`limit`, default `condition`), `slTriggerPx`/
`slTriggerRatio`, `slOrdPx` (`-1` = market), `tpTriggerPxType`/
`slTriggerPxType` (`last`/`index`/`mark`, default `last`), `sz`
(для split-TP), `amendPxOnTriggerType` (`0`/`1` cost-price SL для
split). `tpTriggerPx` vs `tpTriggerRatio` — взаимоисключимо;
аналогично для SL.

**Amend**: `instId` + одно из `ordId` (предпочтительно) / `clOrdId`;
`reqId` (опц., echo в ACK для логов/retry-корреляции); `newPx`,
`newSz` (для `partially_filled` — **включая** уже исполненное;
`newSz ≤ filled` может перевести в `filled`); `cxlOnFail` (default
`false`); `pxAmendType` (default `0`); `attachAlgoOrds[*]` —
см. «Amend TP/SL» в §Endpoints.

**Cancel**: `instId` + одно из `ordId` (предпочтительно) / `clOrdId`.

**Pagination (pending / history / archive):** `after`/`before` — якорь
по `ordId` (не времени), `limit ≤ 100`. Для глубокой выкачки: `after =
min(ordId)` ответа → следующая страница. История 7 дней дополнительно
поддерживает `begin`/`end` по `cTime` (ms).

## reduce-only invariant

`Order.positionReducingOnly` (доменное намерение) → OKX `reduceOnly`
в create request. `OrderResponse.reduceOnly` **не** маппится в
`OrderExternalSnapshot` и не обновляет `positionReducingOnly` — но
adapter может сравнить:

```text
expected = Order.positionReducingOnly
actual   = OrderResponse.reduceOnly
mismatch -> EXCHANGE_INVARIANT_VIOLATION
         -> Order.ERROR, closeReason = EXCHANGE_INVARIANT_VIOLATION,
            Deal.ERROR, Exchange.HOLD
```

Если биржа не поддерживает reduce-only/close-only — adapter может
проигнорировать `positionReducingOnly`; unsupported exchange на
первом этапе не блокируем.

## Attached protection

`OrderResponse.attachAlgoOrds[*]` →
`AttachedAlgoOrderExternalSnapshot`; матчинг по `internalId`
(`attachAlgoClOrdId`). Status: `PENDING` после `SUBMIT_ORDER`;
`ACTIVE` только после `REFRESH_ORDER`/`REFRESH_PENDING_ORDERS`, если
найден по `internalId` и нет `failCode`/`failReason`; заполненные
`failCode`/`failReason` → `ERROR`. Missing-policy по статусу parent —
в `docs/lifecycles/Order.md`.

## Целевые расхождения с текущим кодом (target refactoring)

- `createOrder` не должен принимать `tradeMode`/`positionSide`
  аргументами — `OkxClientService` сам ставит `isolated`/`net`.
- `CreateOrderRequest` должен принимать `reduceOnly` и
  `attachAlgoOrds` (для `ENTRY_ATTACHED_STOP_LOSS`).
- `OrderResponse.state` комментарий: raw статус OKX; pending —
  `live`/`partially_filled`; details/history — `filled`/`canceled`/
  `mmp_canceled` и др. terminal.
- `OrderResponse.reduceOnly` → только adapter invariant validation,
  не в `OrderExternalSnapshot`.
