### Получить историю ордеров (последние 7 дней) (REST)

**Endpoint:** `GET /api/v5/trade/orders-history`

**Что делает:**

* Возвращает **завершённые** ордера, которые **были размещены за последние 7 дней** (и в том числе те, которые были размещены 7 дней назад, но завершились в последние 7 дней).
* **Важно:** незавершённые ордера, которые **просто отменили**, биржа хранит в истории **только ~2 часа**. Поэтому если тебе важно «увидеть отменённые неисполненные», их нужно забирать быстро.

---

## 1) Query параметры (фильтры)

* `instType` (**обязательно**) — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.

    * Для твоего бота: обычно `SWAP`.
* `instFamily` (опц.) — семейство инструментов (актуально для `FUTURES/SWAP/OPTION`).
* `instId` (опц.) — конкретный инструмент, например `ETH-USDT-SWAP`.
* `ordType` (опц.) — тип ордера. Можно список через запятую.

    * Частые: `market`, `limit`, `post_only`, `fok`, `ioc`, `optimal_limit_ioc`.
* `state` (опц.) — состояние (история завершённых):

    * `filled` — исполнен.
    * `canceled` — отменён.
    * `mmp_canceled` — отменён защитой Market Maker Protection (в основном про опционы/PM).
* `category` (опц.) — «категория/источник завершения» (например `normal`, `adl`, `full_liquidation`, `partial_liquidation`, `delivery`, `twap` и т.д.).

    * Для обычной торговли чаще всего `normal`.
* `after` (опц.) — пагинация: вернуть записи **раньше** указанного `ordId`.
* `before` (опц.) — пагинация: вернуть записи **новее** указанного `ordId`.
* `begin` (опц.) — фильтр по `cTime` (время создания ордера) **от** (ms Unix).
* `end` (опц.) — фильтр по `cTime` **до** (ms Unix).
* `limit` (опц.) — сколько записей вернуть. `1..100`, по умолчанию `100`.

---

## 2) Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **40 requests / 2 seconds**, правило — **User ID**
* Auth headers (REST private):

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO строка, текущая UTC; эта же строка должна участвовать в подписи)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: добавить `x-simulated-trading: 1` (если работаешь в demo)

***Как считается подпись:***

* `prehash = timestamp + method + requestPath + body`, затем `HMAC_SHA256(secret, prehash)` и `Base64`.
* Для GET body обычно пустой.

---

## 3) Пример запроса

### 3.1 История исполненных ордеров по инструменту (обычный кейс)

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-history?instType=SWAP&instId=ETH-USDT-SWAP&state=filled&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

### 3.2 История за конкретное окно времени (важно после даунтайма)

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-history?instType=SWAP&instId=ETH-USDT-SWAP&begin=1769240000000&end=1769260000000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
```

---

## 4) Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",

      "ordId": "680800019749904384",
      "clOrdId": "tbORD000123",
      "tag": "",

      "tdMode": "isolated",
      "ccy": "USDT",
      "side": "buy",
      "posSide": "net",

      "ordType": "limit",
      "px": "3120.5",
      "sz": "10",

      "state": "filled",
      "accFillSz": "10",
      "fillPx": "3120.4",
      "fillSz": "10",
      "fillTime": "1769253296789",
      "avgPx": "3120.4",
      "tradeId": "744876980",

      "feeCcy": "USDT",
      "fee": "-0.045",
      "rebateCcy": "",
      "rebate": "",
      "pnl": "0",

      "attachAlgoClOrdId": "",
      "attachAlgoOrds": [],
      "tpTriggerPx": "",
      "tpTriggerPxType": "",
      "tpOrdPx": "",
      "slTriggerPx": "",
      "slTriggerPxType": "",
      "slOrdPx": "",

      "reduceOnly": "false",
      "stpMode": "",
      "source": "",
      "cancelSource": "",
      "cancelSourceReason": "",

      "algoId": "",
      "algoClOrdId": "",
      "linkedAlgoOrd": { "algoId": "" },
      "isTpLimit": "false",

      "category": "normal",
      "tradeQuoteCcy": "USDT",
      "cTime": "1769253296000",
      "uTime": "1769253297000",

      "pxType": "",
      "pxUsd": "",
      "pxVol": ""
    }
  ]
}
```

---

## 5) Описание полей ответа (простыми словами)

### Верхний уровень

* `code` — `"0"`, если успех.
* `msg` — сообщение об ошибке/пояснение (обычно пусто при успехе).
* `data[]` — список ордеров.

### Поля одного ордера (`data[i]`)

**Идентификация:**

* `instType` — тип инструмента (`SWAP` и т.п.).
* `instId` — имя инструмента на бирже (например `ETH-USDT-SWAP`).
* `ordId` — ID ордера на стороне OKX.
* `clOrdId` — наш client order id (если задавали при создании).
* `tag` — метка (если задавали).

**Режим и сторона:**

* `tdMode` — режим торговли (`isolated/cross/cash`). Для SWAP-бота обычно `isolated`.
* `ccy` — валюта маржи/обеспечения (для USDT‑SWAP обычно `USDT`).
* `side` — `buy/sell`.
* `posSide` — `net/long/short` (зависит от режима позиций аккаунта).

**Параметры ордера:**

* `ordType` — тип ордера (`limit/market/post_only/...`).
* `px` — цена (для limit). Для market часто пустая строка.
* `sz` — размер ордера. Для SWAP/FUTURES это обычно **контракты**.
* `tgtCcy` — единицы размера для SPOT market (`base_ccy/quote_ccy`). Для SWAP обычно не нужно (может быть пусто).

**Исполнение:**

* `state` — финальный статус:

    * `filled` — исполнен,
    * `canceled` — отменён,
    * `mmp_canceled` — отменён защитой MMP.
* `accFillSz` — сколько всего исполнилось (накопительно).
* `fillPx` — цена последнего исполнения.
* `fillSz` — размер последнего исполнения.
* `fillTime` — время последнего исполнения (ms Unix).
* `avgPx` — средняя цена исполнения.
* `tradeId` — ID последней сделки по этому ордеру.

**Комиссии/ребейты/PnL:**

* `feeCcy` — валюта комиссии.
* `fee` — комиссия (часто отрицательная).
* `rebateCcy` — валюта ребейта (если есть).
* `rebate` — ребейт (если есть).
* `pnl` — PnL **без комиссии**. Важно: бывает осмысленным в основном для ордеров, которые закрывают позицию.

**TP/SL, которые были прикреплены при выставлении ордера:**

* `attachAlgoClOrdId` — client id «прикреплённого» TP/SL (если задавали).
* `attachAlgoOrds[]` — массив детальных записей по прикреплённым TP/SL.
* `tpTriggerPx / tpTriggerPxType / tpOrdPx` — параметры тейк-профита.
* `slTriggerPx / slTriggerPxType / slOrdPx` — параметры стоп-лосса.

**Прочее:**

* `reduceOnly` — `true/false`: ордер только уменьшает позицию (полезно для закрывающих ордеров).
* `stpMode` — режим защиты от самоторговли.
* `source` — «откуда появился» ордер (код).
* `cancelSource` — источник отмены (код).
* `cancelSourceReason` — причина отмены (если биржа дала).
* `algoId / algoClOrdId` — если ордер связан с algo-логикой.
* `linkedAlgoOrd.algoId` — связанный algoId (часто для OCO, если применимо).
* `isTpLimit` — `true/false`: это TP-limit или нет.

**Категории и время:**

* `category` — категория/тип завершения (`normal`, `adl`, `liquidation` и т.д.).
* `tradeQuoteCcy` — котируемая валюта, обычно `USDT`.
* `cTime` — время создания ордера (ms Unix).
* `uTime` — время последнего обновления ордера (ms Unix).

**Опционы (для SWAP можно игнорировать):**

* `pxType` — тип цены опциона (`px/pxUsd/pxVol`).
* `pxUsd` — цена опциона в USD.
* `pxVol` — implied volatility.

---

## 6) Как это использовать после даунтайма (в 2 строчки)

* Для «догоняния» событий после отсутствия связи: делай `GET /trade/orders-history` (по `instId` + `begin/end` окна даунтайма) **и** параллельно `GET /trade/fills` (чтобы увидеть сделки/фактические исполнения).
* `orders-history` отвечает «как биржа видит ордер», а `fills` — «какие сделки реально прошли» (это самый надёжный источник факта исполнения).
