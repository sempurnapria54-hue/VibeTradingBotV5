## Получить историю ордеров (последние 3 месяца) (REST)

**Зачем нужно (простыми словами):**

* Это «архив» завершённых ордеров за **последние ~3 месяца**.
* Используется для **восстановления состояния после даунтайма**: понять, что за это время **исполнилось/закрылось/отменилось**.

---

### Endpoint

`GET /api/v5/trade/orders-history-archive`

---

### Query параметры

* `instType` *(обязательно)* — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `instId` *(опционально)* — конкретный инструмент, например `ETH-USDT-SWAP`.
* `ordType` *(опционально)* — фильтр по типу ордера: `market | limit | post_only | fok | ioc`.
* `state` *(опционально)* — фильтр по финальному состоянию: обычно `filled` или `canceled`.
* `after` *(опционально)* — пагинация «старее, чем этот ordId» (вернёт записи **раньше** указанного `ordId`).
* `before` *(опционально)* — пагинация «новее, чем этот ordId» (вернёт записи **позже** указанного `ordId`).
* `limit` *(опционально)* — сколько записей вернуть за раз. Максимум `100`, по умолчанию `100`.

> Пояснение по after/before простыми словами: это **не время**, а «якорь» по `ordId`.

---

### Доступ / лимиты / аутентификация

* Permission: Read
* Rate limit: **20 requests / 2 seconds**, правило — User ID
* Это приватный REST → нужны стандартные auth headers:

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (UTC ISO строка)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: `x-simulated-trading: 1` (если используешь demo ключи)

---

### Пример запроса

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/orders-history-archive?instType=SWAP&instId=ETH-USDT-SWAP&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

**Пример пагинации (идём глубже в прошлое):**

* Взяли страницу, посмотрели минимальный `ordId` из ответа → кладём его в `after=<minOrdId>` и запрашиваем следующую страницу.

---

### Пример ответа

> Важно: набор полей зависит от `instType` и типа ордера. Для SWAP обычно приходит больше полей (posSide/tdMode/reduceOnly/TP/SL/attachAlgoOrds и т.д.).

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",

      "ordId": "3209990000000000000",
      "clOrdId": "tb_20260124_0001",
      "tag": "tb",

      "side": "buy",
      "posSide": "long",
      "tdMode": "isolated",
      "ccy": "USDT",
      "reduceOnly": "false",

      "ordType": "limit",
      "px": "3100.5",
      "sz": "2",

      "state": "filled",
      "accFillSz": "2",
      "fillPx": "3100.2",
      "fillSz": "2",
      "fillTime": "1769253500123",
      "avgPx": "3100.2",
      "tradeId": "987654321",

      "feeCcy": "USDT",
      "fee": "-0.45",
      "rebateCcy": "",
      "rebate": "",
      "pnl": "",

      "tpTriggerPx": "",
      "tpTriggerPxType": "",
      "tpOrdPx": "",
      "slTriggerPx": "",
      "slTriggerPxType": "",
      "slOrdPx": "",

      "attachAlgoClOrdId": "",
      "attachAlgoOrds": [],

      "source": "",
      "category": "normal",
      "cTime": "1769253499000",
      "uTime": "1769253501000"
    }
  ]
}
```

---

## Описание полей (простыми словами)

### Верхний уровень

* `code` — "0" значит успех.
* `msg` — текст ошибки/сообщение.
* `data[]` — список завершённых ордеров (по фильтрам).

### Поля одного ордера (data[i]) — самое важное

**Идентификация**

* `instId` — инструмент на бирже (например `ETH-USDT-SWAP`).
* `instType` — тип инструмента (`SWAP` для perpetual).
* `ordId` — ID ордера на стороне OKX.
* `clOrdId` — наш client order id (мы сами задаём при создании).
* `tag` — произвольная метка.

**Сторона и режим**

* `side` — `buy` или `sell`.
* `posSide` — сторона позиции: `long | short | net` (зависит от режима позиций).
* `tdMode` — режим торговли: `isolated | cross | cash`.
* `ccy` — валюта маржи (для USDT‑SWAP обычно `USDT`).
* `reduceOnly` — true/false: ордер только уменьшает позицию (не может увеличить).

**Параметры ордера**

* `ordType` — тип: `market | limit | post_only | fok | ioc`.
* `px` — цена (для limit). Для market часто пусто.
* `sz` — размер ордера. Для SWAP это обычно **контракты**, не USDT.

**Состояние и исполнение**

* `state` — финальное состояние: обычно `filled` (исполнен) или `canceled` (отменён).
* `accFillSz` — сколько в итоге исполнилось (может быть меньше `sz`, если частично и потом отменили).
* `fillPx` — цена последнего исполнения.
* `fillSz` — размер последнего исполнения.
* `fillTime` — время последнего исполнения (ms).
* `avgPx` — средняя цена исполнения.
* `tradeId` — ID последней сделки.

**Комиссии и PnL**

* `fee` / `feeCcy` — комиссия и её валюта.
* `rebate` / `rebateCcy` — ребейт (если есть, чаще для maker / spot).
* `pnl` — поле, которое для части режимов/рынков может быть пустым.

**TP/SL (если были прикреплены)**

* `tpTriggerPx`, `tpTriggerPxType`, `tpOrdPx` — параметры тейка.
* `slTriggerPx`, `slTriggerPxType`, `slOrdPx` — параметры стопа.
* `attachAlgoClOrdId` — client id прикреплённых TP/SL.
* `attachAlgoOrds[]` — список прикреплённых algo‑ордеров (если биржа вернула детализацию).

**Время и прочее**

* `cTime` — время создания ордера на бирже (ms).
* `uTime` — время обновления ордера на бирже (ms).
* `source` — источник ордера (откуда появился, строковый код).
* `category` — категория (часто `normal`).

---

### Практическая подсказка для даунтайма

Чтобы «догнать» историю после отключения:

1. Запрашиваешь `orders-history-archive` по `instType=SWAP&instId=...`.
2. Фильтруешь по `uTime/cTime` в своём коде (если нужно), и **апдейтишь** записи ордеров в БД.
3. Для детальных фактов исполнения (каждая сделка/частичное исполнение) — дополнительно читаешь `GET /api/v5/trade/fills-history`.
