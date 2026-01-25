### Получить открытые ордера (REST)

**Endpoint:** `GET /api/v5/trade/orders-pending`

Эндпоинт возвращает **все “незавершённые” ордера** (то есть те, которые ещё не стали `filled`/`canceled`). Обычно это состояния `live` и `partially_filled`.

---

#### Query параметры (опционально)

* `instType` — тип инструмента:

    * `SPOT`, `MARGIN`, `SWAP`, `FUTURES`, `OPTION`
    * Если не передать — биржа вернёт по умолчанию (но лучше всегда задавать, чтобы не получить «мусор»).
* `instFamily` — «семейство» инструмента (актуально для `FUTURES`/`SWAP`/`OPTION`).
* `instId` — инструмент, например `ETH-USDT-SWAP`.
* `ordType` — фильтр по типам ордеров. Можно перечислить несколько через запятую, например:

    * `market`, `limit`, `post_only`, `fok`, `ioc`, `optimal_limit_ioc`, `elp` …
* `state` — фильтр по состоянию:

    * `live`, `partially_filled`
* `after` — пагинация: вернуть записи **старее** указанного `ordId`.
* `before` — пагинация: вернуть записи **новее** указанного `ordId`.
* `limit` — сколько записей вернуть (максимум `100`, по умолчанию `100`).

---

#### Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **60 запросов / 2 секунды**, правило — **User ID**
* Auth headers (REST private):

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO строка в UTC, например `2026-01-24T12:34:56.789Z`)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: добавить `x-simulated-trading: 1` (только если ключи demo).

***Как считается подпись (важно для “рабочего” реквеста):***

* `prehash = timestamp + method + requestPath + body`, затем `HMAC_SHA256(secret, prehash)` и `Base64`.
* Для `GET` тело (`body`) обычно пустое.

---

#### Пример запроса

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-pending?instType=SWAP&instId=ETH-USDT-SWAP&state=live,partially_filled&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

#### Пример ответа

> Пример ниже — иллюстративный (значения условные). Все числа у OKX приходят строками.

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",

      "ordId": "1752588852617379840",
      "clOrdId": "tbOrd0001",
      "tag": "",

      "side": "buy",
      "posSide": "net",
      "tdMode": "isolated",

      "ordType": "limit",
      "px": "3120.5",
      "sz": "1",
      "tgtCcy": "",

      "state": "live",
      "accFillSz": "0",
      "fillPx": "",
      "fillSz": "0",
      "fillTime": "",
      "avgPx": "",
      "tradeId": "",

      "ccy": "USDT",
      "lever": "10",
      "reduceOnly": "false",
      "quickMgnType": "",

      "fee": "0",
      "feeCcy": "USDT",
      "rebate": "0",
      "rebateCcy": "",
      "pnl": "0",

      "attachAlgoClOrdId": "",
      "tpTriggerPx": "",
      "tpTriggerPxType": "",
      "tpOrdPx": "",
      "slTriggerPx": "",
      "slTriggerPxType": "",
      "slOrdPx": "",
      "attachAlgoOrds": [],

      "algoClOrdId": "",
      "algoId": "",
      "linkedAlgoOrd": { "algoId": "" },
      "isTpLimit": "false",

      "pxType": "",
      "pxUsd": "",
      "pxVol": "",

      "stpId": "",
      "stpMode": "cancel_maker",

      "source": "",
      "cancelSource": "",
      "cancelSourceReason": "",

      "tradeQuoteCcy": "USDT",
      "cTime": "1769253296789",
      "uTime": "1769253296789"
    }
  ]
}
```

---

## Описание полей (что они значат)

Ниже — пояснения **к объекту `data[i]`**, то есть к одному ордеру.

### 1) Инструмент и идентификаторы

* `instType` — тип инструмента (`SWAP`, `SPOT` …).
* `instId` — инструмент на бирже (например `ETH-USDT-SWAP`).
* `ordId` — ID ордера на стороне OKX.
* `clOrdId` — твой клиентский ID ордера (то, что задаёшь сам при создании). Удобно для идемпотентности и поиска.
* `tag` — «тэг»/метка, если ты её передавал (часто пусто).

### 2) Сторона, режим позиции и режим торговли

* `side` — сторона ордера: `buy` или `sell`.
* `posSide` — сторона позиции:

    * в **net-режиме** обычно будет `net`
    * в **hedge-режиме** могут быть `long` / `short`
* `tdMode` — режим торговли:

    * `isolated` — изолированная маржа (то, что нужно твоему боту)
    * `cross` — кросс-маржа
    * `cash` — спот без маржи

### 3) Тип и параметры ордера

* `ordType` — тип ордера (`limit`, `market`, `post_only`, `fok`, `ioc`, ...).
* `px` — цена (для `limit`). Для `market` часто будет пусто.
* `sz` — размер ордера (кол-во). Для SWAP это обычно **контракты**.
* `tgtCcy` — только для `SPOT market`: в чём задан `sz`:

    * `base_ccy` (в базовой валюте)
    * `quote_ccy` (в котируемой валюте)

### 4) Статус и прогресс исполнения

* `state` — состояние ордера:

    * `live` — активный ордер в стакане
    * `partially_filled` — частично исполнен, остаток ещё жив
* `accFillSz` — сколько уже исполнено (накопленно).
* `fillPx` — цена **последнего** исполнения (если были сделки).
* `fillSz` — размер **последнего** исполнения.
* `fillTime` — время **последнего** исполнения.
* `avgPx` — средняя цена исполнения (если ничего не исполнялось — пусто).
* `tradeId` — ID **последней** сделки по этому ордеру.

### 5) Плечо, reduce-only и маржинальная валюта

* `ccy` — валюта маржи (особенно важно для `isolated` в `MARGIN/FUTURES/SWAP`). Для USDT‑маржинального SWAP обычно это `USDT`.
* `lever` — плечо (строкой), актуально для `MARGIN/FUTURES/SWAP`.
* `reduceOnly` — `true/false` (строкой): ордер **только уменьшает** позицию и не может её увеличить.
* `quickMgnType` — Quick Margin type (встречается в режимах quick margin; обычно пусто).

### 6) Комиссия, ребейт и PnL

* `fee` — накопленная комиссия по ордеру (часто отрицательная строка, если уже были исполнения).
* `feeCcy` — валюта комиссии.
* `rebate` — ребейт (возврат) для maker‑сделок (если применимо).
* `rebateCcy` — валюта ребейта.
* `pnl` — PnL **без учёта комиссии**. Обычно заполняется, когда ордер реально закрывает позицию и по нему есть сделки; иначе часто `0`.

### 7) TP/SL, прикреплённые к ордеру

Важно: у OKX TP/SL может быть «прикреплён» к основному ордеру (attach). Тогда в ордере появляются поля про TP/SL.

* `attachAlgoClOrdId` — твой клиентский ID для «прикреплённых» algo‑ордеров (TP/SL), если ты его задавал.

* `tpTriggerPx` — триггер-цена тейк-профита.

* `tpTriggerPxType` — тип цены триггера TP:

    * `last` / `index` / `mark`

* `tpOrdPx` — цена исполнения TP (для limit‑TP; для market‑TP может быть пусто).

* `slTriggerPx` — триггер-цена стоп-лосса.

* `slTriggerPxType` — тип цены триггера SL (`last` / `index` / `mark`).

* `slOrdPx` — цена исполнения SL.

* `attachAlgoOrds` — список «прикреплённых» деталей TP/SL, если они есть. Это массив объектов.

Поля одного элемента `attachAlgoOrds[j]`:

* `attachAlgoId` — ID прикреплённого algo‑ордера на OKX (по нему можно потом amend-ить TP/SL).

* `attachAlgoClOrdId` — твой клиентский ID прикреплённого algo‑ордера.

* `tpOrdKind` — вид TP: `condition` или `limit`.

* `tpTriggerPx` — триггер TP.

* `tpTriggerRatio` — триггер TP в процентах (например `0.3` = 30%). Только для `FUTURES/SWAP`.

* `tpTriggerPxType` — тип цены триггера TP (`last/index/mark`).

* `tpOrdPx` — цена TP.

* `slTriggerPx` — триггер SL.

* `slTriggerRatio` — триггер SL в процентах (например `0.3` = 30%). Только для `FUTURES/SWAP`.

* `slTriggerPxType` — тип цены триггера SL (`last/index/mark`).

* `slOrdPx` — цена SL.

* `sz` — размер (актуально для split‑TP, когда тейки дробятся).

* `amendPxOnTriggerType` — «Cost-price SL» (для некоторых режимов split‑TP):

    * `0` — выключено
    * `1` — включено

* `failCode` — код ошибки, если TP/SL не удалось поставить.

* `failReason` — текст причины ошибки.

* `linkedAlgoOrd` — объект «связанного» algo‑ордера (используется, например, в OCO):

    * `linkedAlgoOrd.algoId` — ID связанного algo‑ордера.

* `algoId` — algo ID. Может заполниться, когда algo‑ордер сработал (triggered), иначе часто пусто.

* `algoClOrdId` — client algo ID (если ты задавал).

* `isTpLimit` — `true/false`: это TP‑limit ордер или нет.

### 8) Параметры опционов (для SWAP обычно пусто)

* `pxUsd` — цена опциона в USD (только для `OPTION`).
* `pxVol` — implied volatility (только для `OPTION`).
* `pxType` — тип цены для опционов (`px` / `pxVol` / `pxUsd`).

### 9) Self-Trade Prevention (обычно можно игнорировать)

* `stpMode` — режим самоторговли (пример: `cancel_maker`).
* `stpId` — deprecated (обычно пусто).

### 10) Откуда пришёл ордер и кто отменил

* `source` — источник ордера (код строкой). Например:

    * `6` — ордер, созданный trigger‑ордером
    * `7` — ордер, созданный TP/SL
    * `13` — ордер, созданный algo‑ордером
    * `25` — ордер, созданный trailing stop
    * `34` — ордер, созданный chase‑ордером
* `cancelSource` — код источника отмены (если ордер отменён).
* `cancelSourceReason` — причина отмены (если биржа её дала).

### 11) Временные поля

* `cTime` — время создания ордера (Unix ms).
* `uTime` — время последнего обновления (Unix ms).

### 12) Прочее

* `tradeQuoteCcy` — котируемая валюта торговли (например `USDT`).
* `category` — категория ордера (например `normal`).

---

### Практическая заметка (важно для бота)

* WS канал `orders` **не даёт начальный snapshot**. Он начинает слать события **только при изменениях**.
* Поэтому при старте/после реконнекта часто делают так:

    1. REST: `GET /api/v5/trade/orders-pending` (снять snapshot)
    2. WS: подписаться на `orders` и дальше жить на событиях.
