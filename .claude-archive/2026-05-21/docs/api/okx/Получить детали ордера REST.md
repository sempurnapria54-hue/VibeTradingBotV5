### Получить детали ордера (REST)

**Endpoint:** `GET /api/v5/trade/order`

**Зачем нужно (простыми словами):**

* Узнать **текущее/финальное состояние конкретного ордера**: он всё ещё живой, частично исполнен, полностью исполнен, отменён и т.д.
* После даунтайма: если у тебя есть `ordId` или твой `clOrdId`, можно восстановить факт — **исполнился ордер или нет**, сколько реально исполнилось, какие комиссии.

---

## 1) Параметры запроса

**Query (обязательно):**

* `instId` — инструмент, например `ETH-USDT-SWAP`.

**Query (одно из двух обязательно):**

* `ordId` — ID ордера на бирже.
* `clOrdId` — твой client order id.

**Правило приоритета:**

* Если передать и `ordId`, и `clOrdId`, биржа использует `ordId`.

**Важное замечание:**

* Если по ошибке один и тот же `clOrdId` использовался повторно, OKX может вернуть **последний** ордер по этому `clOrdId`. Поэтому в идеале `clOrdId` всегда уникальный.

---

## 2) Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **60 запросов / 2 секунды**

    * правило (кроме опционов): **User ID + Instrument ID**
* Auth headers (REST private):

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO строка в UTC, например `2026-01-24T12:34:56.789Z`)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: добавить `x-simulated-trading: 1` (только если ключи demo).

***Как считается подпись (важно для “рабочего” реквеста):***

* `prehash = timestamp + method + requestPath + body`
* Для `GET` тело (`body`) пустое.
* **Query параметры входят в `requestPath`**.

---

## 3) Примеры запросов

**Пример (по ordId):**

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/order?instId=ETH-USDT-SWAP&ordId=680800019749904384' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

**Пример (по clOrdId):**

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/order?instId=ETH-USDT-SWAP&clOrdId=tbOrd000123' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

## 4) Пример ответа

> Пример ниже — иллюстративный (значения условные). Все числа у OKX приходят строками.

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",
      "tdMode": "isolated",
      "ccy": "USDT",

      "ordId": "680800019749904384",
      "clOrdId": "tbOrd000123",
      "tag": "trading-bot",

      "side": "buy",
      "posSide": "long",
      "ordType": "limit",
      "px": "3120.5",
      "sz": "10",
      "reduceOnly": "false",

      "state": "partially_filled",
      "accFillSz": "3",
      "avgPx": "3120.4",
      "fillPx": "3120.4",
      "fillSz": "1",
      "fillTime": "1769253301000",
      "tradeId": "744876980",

      "pnl": "0",

      "fee": "-0.0123",
      "feeCcy": "USDT",
      "rebate": "0",
      "rebateCcy": "USDT",

      "attachAlgoClOrdId": "tbAttachTPSL0001",
      "tpTriggerPx": "3180",
      "tpTriggerPxType": "mark",
      "tpOrdPx": "-1",
      "slTriggerPx": "3080",
      "slTriggerPxType": "mark",
      "slOrdPx": "-1",

      "attachAlgoOrds": [
        {
          "attachAlgoId": "3209210720722571264",
          "attachAlgoClOrdId": "tbAttachTPSL0001",
          "tpOrdKind": "condition",
          "tpTriggerPx": "3180",
          "tpTriggerRatio": "",
          "tpTriggerPxType": "mark",
          "tpOrdPx": "-1",
          "slTriggerPx": "3080",
          "slTriggerRatio": "",
          "slTriggerPxType": "mark",
          "slOrdPx": "-1",
          "sz": "",
          "amendPxOnTriggerType": "0",
          "failCode": "",
          "failReason": ""
        }
      ],

      "algoClOrdId": "",
      "algoId": "",
      "linkedAlgoOrd": { "algoId": "" },

      "source": "",
      "category": "normal",
      "isTpLimit": "false",
      "cancelSource": "",
      "cancelSourceReason": "",
      "quickMgnType": "manual",

      "lever": "10",
      "stpMode": "",

      "uTime": "1769253310000",
      "cTime": "1769253290000",

      "tgtCcy": "",
      "tradeQuoteCcy": "USDT",
      "pxUsd": "",
      "pxVol": "",
      "pxType": ""
    }
  ]
}
```

---

## 5) Описание полей (простым языком)

Ниже — пояснения к объекту `data[0]`.

### Инструмент и режим торговли

* `instType` — тип инструмента (для тебя обычно `SWAP`).
* `instId` — имя инструмента на бирже.
* `tdMode` — режим торговли/маржи: `isolated` / `cross` / `cash`.
* `ccy` — валюта маржи.
* `lever` — плечо.
* `quickMgnType` — “быстрая маржа” в isolated:

    * `manual` — вручную
    * `auto_borrow` — авто-заём
    * `auto_repay` — авто-погашение

### Идентификаторы

* `ordId` — биржевой ID ордера.
* `clOrdId` — твой клиентский ID.
* `tag` — метка (удобно писать `trading-bot`).

### Что хотели сделать

* `side` — `buy` или `sell`.
* `posSide` — `long` / `short` / `net`.
* `ordType` — тип ордера: `market`, `limit`, `post_only`, `ioc`, `fok` и т.д.
* `px` — цена (для limit).
* `sz` — размер.

    * Для `SWAP` это **контракты**, не USDT.
* `reduceOnly` — `true`, если ордер только уменьшает позицию.

### Что реально произошло (исполнение)

* `state` — состояние:

    * `live` — стоит в стакане
    * `partially_filled` — частично исполнен
    * `filled` — полностью исполнен
    * `canceled` — отменён
    * `mmp_canceled` — спец-отмена (обычно не нужно)
* `accFillSz` — сколько исполнилось суммарно.
* `fillPx` — цена последней сделки.
* `fillSz` — объём последней сделки.
* `fillTime` — время последней сделки.
* `avgPx` — средняя цена исполнения.
* `tradeId` — ID последней сделки.

### Комиссии / ребейты / PnL

* `fee` — комиссия (часто отрицательная).
* `feeCcy` — валюта комиссии.
* `rebate` — возврат для maker (если был).
* `rebateCcy` — валюта возврата.
* `pnl` — PnL без комиссии (часто 0, кроме закрывающих ордеров).

### TP/SL и прикреплённые ордера

* `attachAlgoClOrdId` — твой client id для TP/SL.
* `tpTriggerPx` / `slTriggerPx` — триггер-цены TP/SL.
* `tpTriggerPxType` / `slTriggerPxType` — тип цены: `last` / `index` / `mark`.
* `tpOrdPx` / `slOrdPx` — цена выставляемого ордера после триггера:

    * `-1` обычно значит “по рынку”.
* `attachAlgoOrds[]` — детализация прикреплённых TP/SL:

    * `attachAlgoId` — биржевой ID прикреплённого TP/SL.
    * `attachAlgoClOrdId` — твой client id.
    * `tpOrdKind` — `condition` или `limit`.
    * `tpTriggerRatio` / `slTriggerRatio` — процентный вариант (часто пусто).
    * `failCode` / `failReason` — ошибка, если TP/SL не смог выставиться.

### Прочее

* `source` — откуда появился ордер (алго/трейлинг/триггер), часто пусто.
* `category` — категория (обычно `normal`).
* `isTpLimit` — TP-limit (true/false).
* `cancelSource` — кто отменил (код), если отменили.
* `cancelSourceReason` — причина отмены.
* `stpMode` — self-trade prevention, обычно не нужно.
* `cTime` — когда создали.
* `uTime` — когда обновили.

---