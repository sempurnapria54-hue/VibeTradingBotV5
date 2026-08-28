# OKX contracts: ordinary order

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по ordinary order: endpoint'ы, лимиты,
ACK-семантика, пагинация.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Trade»). При расхождении с офдоком
побеждает офдок; синхронизация — перевыкачка + дифф при каждом
заходе интегратора (`.claude/processes/api-docs-completion.md`,
канал — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 1 — соответствие спеке подтверждено).

## Единица размера (`sz`, `accFillSz`) — контракты у SWAP/FUTURES

**Открытая сверка `integrator`**. Дистиллят офдока
единицу `sz` / `accFillSz` не называет, а поле несущее: у нас
`accumulatedFillSize` входит операндом в три из четырёх чисел риска
через отношение к `plannedSizeContracts`
(`docs/models/domain/aggregate/Deal.md`). Действующая
запись — «для SWAP/FUTURES контракты»
(`docs/models/domain/core/Order.md`), и она **предположение**
до наблюдения.

- **Проверка — на уже существующих кейсах**: сопоставить
  отправленный `sz` с `accFillSz` исполненного ордера и с `pos` записи
  positions-history — совпадение по величине подтверждает единицу
  «контракты» на всех трёх поверхностях.
- **Цена ошибки — направленная и тихая:** если `accFillSz` придёт в
  базовой валюте, отношение к `plannedSizeContracts` (контракты)
  завысит или занизит `incurredRiskAmount` в `ctVal` раз, и ни одна
  проверка этого не увидит — обе величины положительны и правдоподобны.

## Endpoints

- **Create** (`SUBMIT_ORDER_COMMAND`): `POST /api/v5/trade/order`. Permission
  `Trade`; rate limit 60 req / 2 s по User ID + Instrument ID.
- **Amend** (доменом **не используется** — REPLACE-only,
  `docs/rules/replace-not-amend.md`; контракт — поверхность
  биржи): `POST /api/v5/trade/amend-order`. Permission `Trade`; rate
  limit 60 req / 2 s по User ID + Instrument ID.
  `newPx`/`newSz`/`attachAlgoOrds` — изменения должны включать
  уже исполненную часть для `partially_filled`. `cxlOnFail` (boolean)
  — биржа отменит ордер, если amend упал. `pxAmendType=0|1` — `1`
  разрешает автокорректировку цены в допустимый диапазон.
- **Cancel** (`CANCEL_ORDER_COMMAND`): `POST /api/v5/trade/cancel-order`.
  Permission `Trade`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Body: `instId` + одно из `ordId` / `clOrdId` (если оба — биржа
  использует `ordId`).
- **Order details** (`REFRESH_ORDER_COMMAND`): `GET /api/v5/trade/order`.
  Permission `Read`; rate limit 60 req / 2 s по User ID + Instrument
  ID. Query: `instId` обязателен, одно из `ordId` / `clOrdId`. Если
  оба — биржа возвращает по `ordId`. Если `clOrdId` переиспользован,
  биржа возвращает **последний** ордер с этим `clOrdId`.
- **Pending** (звено цикла `REFRESH_ORDER_COMMAND`): `GET /api/v5/trade/orders-pending`.
  Permission `Read`; rate limit 60 req / 2 s по User ID. Фильтры:
  `instType`, `instId`, `ordType`, `state` (`live`/`partially_filled`),
  пагинация `after`/`before` по `ordId`, `limit` ≤ 100.
- **History** (звено цикла `REFRESH_ORDER_COMMAND`):
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
`REFRESH_ORDER_COMMAND` или WS `orders`.

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
history не покрывает период). Подробно — в `mapping/Order.md`
evidence-cycle.
