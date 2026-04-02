# Получить историю algo-ордеров (REST)

**Endpoint:** `GET /api/v5/trade/orders-algo-history`

Эндпоинт возвращает список **algo-ордеров из истории**.

> По смыслу это «history list» для стратегических ордеров (SL / TP / Trigger / Trailing и т.д.), которые уже не
> относятся к текущему pending-состоянию.

> История доступна **за последние 3 месяца**.

---

## Query-параметры

### Обязательные

* `ordType` — тип algo-ордера:

    * `conditional` — single TP/SL (односторонний stop)
    * `oco` — OCO (one-cancels-the-other)
    * `trigger` — trigger order
    * `move_order_stop` — trailing stop

### Обязательно одно из двух

* `state` — статус algo-ордера в истории:

    * `effective`
    * `canceled`
    * `order_failed`

* `algoId` — фильтр по конкретному algo-ордеру.

### Опциональные

* `instType` — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `instId` — инструмент (например `ETH-USDT-SWAP`).
* `after` — пагинация **по `algoId`**: вернуть записи **старее** указанного `algoId`.
* `before` — пагинация **по `algoId`**: вернуть записи **новее** указанного `algoId`.
* `limit` — сколько записей вернуть (максимум `100`, по умолчанию `100`).

---

## Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **20 запросов / 2 секунды**, правило — **User ID**
* Auth headers (REST private):

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO UTC, например `2026-01-24T12:34:56.789Z`)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: добавить `x-simulated-trading: 1` (только если ключи demo).

### Подпись

* `prehash = timestamp + method + requestPath + body`
* `signature = Base64(HMAC_SHA256(secret, prehash))`
* Для `GET` тело (`body`) обычно пустое.
* Query-параметры входят в `requestPath`.

---

## Пример запроса

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-algo-history?ordType=conditional&state=canceled&instType=SWAP&instId=ETH-USDT-SWAP&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

**Пример точечного запроса по `algoId`:**

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-algo-history?ordType=conditional&algoId=3209210720722571264' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
```

---

## Пример ответа

> Пример ниже — иллюстративный (значения условные). Все числа у OKX приходят строками.

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "algoId": "3209210720722571264",
      "algoClOrdId": "tbAlgo0001",
      "clOrdId": "tbOrd0001",
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",
      "ccy": "USDT",
      "tdMode": "isolated",
      "lever": "10",
      "quickMgnType": "manual",
      "ordType": "conditional",
      "state": "canceled",
      "tag": "trading-bot",
      "side": "sell",
      "posSide": "net",
      "reduceOnly": "true",
      "closeFraction": "1",
      "sz": "1",
      "tgtCcy": "",
      "tpTriggerPx": "",
      "tpTriggerPxType": "",
      "tpOrdPx": "",
      "slTriggerPx": "3050",
      "slTriggerPxType": "mark",
      "slOrdPx": "-1",
      "triggerPx": "",
      "triggerPxType": "",
      "ordPx": "",
      "callbackRatio": "",
      "callbackSpread": "",
      "activePx": "",
      "moveTriggerPx": "",
      "actualSz": "0",
      "actualPx": "",
      "actualSide": "",
      "triggerTime": "",
      "last": "3024.5",
      "ordId": "",
      "ordIdList": [],
      "pxVar": "",
      "pxSpread": "",
      "szLimit": "",
      "pxLimit": "",
      "timeInterval": "",
      "amendPxOnTriggerType": "0",
      "failCode": "",
      "attachAlgoOrds": [],
      "cTime": "1769253296789",
      "uTime": "1769254300123"
    }
  ]
}
```

---

# Описание полей ответа

Ниже — пояснения **к объекту `data[i]`**, то есть к одному algo-ордеру из истории.

> Важно: набор заполняемых полей зависит от `ordType`. Для «неприменимых» полей OKX часто возвращает пустую строку `""`
> или пустой массив `[]`.

---

## 1) Инструмент, режимы и идентификаторы

* `instType` — тип инструмента (`SWAP`, `SPOT` и т.д.).
* `instId` — инструмент на бирже (например `ETH-USDT-SWAP`).
* `ccy` — валюта маржи (в части режимов может быть неиспользуемой).
* `tdMode` — режим торговли: `cash | cross | isolated`.
* `lever` — плечо (для `MARGIN / FUTURES / SWAP`).
* `quickMgnType` — quick margin type (для isolated margin в quick margin режимах):

    * `manual`
    * `auto_borrow`
    * `auto_repay`

---

## 2) Algo-идентификаторы и связь с обычными ордерами

* `algoId` — основной ID algo-ордера.
* `algoClOrdId` — твой client-supplied ID algo-ордера.
* `clOrdId` — client order id (если есть связь с обычным ордером / бизнес-корреляцией).
* `ordId` — связанный обычный ордер (может быть пустым, если algo не породил обычный ордер).
* `ordIdList` — список связанных обычных ордеров (может быть несколько, например в split-сценариях).

---

## 3) Тип, состояние, теги

* `ordType` — тип algo-ордера:

    * `conditional`
    * `oco`
    * `trigger`
    * `move_order_stop`

* `state` — итоговое состояние algo-ордера в истории. На практике можно встретить:

    * `effective` — algo успешно сработал
    * `canceled` — algo отменён
    * `order_failed` — algo не смог создать/исполнить целевой ордер
    * `partially_failed` — частичный неуспех в некоторых сценариях

* `tag` — тэг, если он задавался при создании.

---

## 4) Сторона, позиция, reduce-only

* `side` — сторона: `buy` / `sell`.
* `posSide` — сторона позиции: `net` или `long / short`.
* `reduceOnly` — `true/false`: ордер только уменьшает позицию.
* `closeFraction` — доля позиции, которую закрыть при срабатывании (`1` = 100%).

---

## 5) Количество и единицы

* `sz` — количество купить/продать (для `SWAP` обычно **контракты**).
* `tgtCcy` — только для `SPOT market`: `base_ccy` или `quote_ccy`.

---

## 6) TP/SL поля (для close-algo)

**TP:**

* `tpTriggerPx` — триггер TP.
* `tpTriggerPxType` — тип цены TP триггера: `last | index | mark`.
* `tpOrdPx` — цена TP ордера (`-1` обычно означает market).

**SL:**

* `slTriggerPx` — триггер SL.
* `slTriggerPxType` — тип цены SL триггера: `last | index | mark`.
* `slOrdPx` — цена SL ордера (`-1` обычно означает market).

---

## 7) Trigger order

* `triggerPx` — trigger price.
* `triggerPxType` — тип цены триггера: `last | index | mark`.
* `ordPx` — цена выставляемого ордера после trigger (`-1` обычно означает market).

---

## 8) Trailing stop (`move_order_stop`)

* `callbackRatio` — callback ratio (процент / доля отката).
* `callbackSpread` — callback spread (абсолютное значение отката).
* `activePx` — цена активации trailing.
* `moveTriggerPx` — trigger price trailing.

---

## 9) Фактические значения после срабатывания

* `actualSz` — фактическое количество.
* `actualPx` — фактическая цена.
* `actualSide` — что сработало: `tp` или `sl` (для `oco` / `conditional`).
* `triggerTime` — время срабатывания (Unix ms).
* `last` — последняя цена при обработке algo / служебное поле.

---

## 10) Iceberg / TWAP (если применимо)

* `pxVar` — price ratio.
* `pxSpread` — price variance.
* `szLimit` — average amount.
* `pxLimit` — price limit.
* `timeInterval` — time interval (только для TWAP).

---

## 11) Fail / спец-настройки

* `failCode` — причина, почему algo не смог сработать или создать целевой ордер.
* `amendPxOnTriggerType` — cost-price SL для некоторых режимов split-TP:

    * `0` — выключено
    * `1` — включено

---

## 12) Attached TP/SL (вложенный массив)

* `attachAlgoOrds[]` — массив объектов с параметрами attached TP/SL (встречается не во всех режимах).

Подполя одного элемента `attachAlgoOrds[j]`:

* `attachAlgoClOrdId` — твой client id для attached TP/SL.
* `tpTriggerPx`, `tpTriggerPxType`, `tpOrdPx` — TP параметры.
* `slTriggerPx`, `slTriggerPxType`, `slOrdPx` — SL параметры.

> В реальных ответах могут встречаться и расширенные поля attached-объекта (например `attachAlgoId`, `tpOrdKind`,
`failReason`). Их можно описывать дополнительно по аналогии.

---

## 13) Время

* `cTime` — время создания algo-ордера (Unix ms).
* `uTime` — время последнего обновления algo-ордера (Unix ms).

---

## Практическая заметка

* Для реконсиляции после рестарта обычно делают:

    1. `orders-algo-pending` — получить активные algo-ордера.
    2. `orders-algo-history` — подтянуть историю недавних algo-ордеров.
    3. Точечно использовать `order-algo` (details), если нужно проверить конкретный algo-ордер.
