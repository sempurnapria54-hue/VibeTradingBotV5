### Обновить (amend) ордер (REST)

**Зачем нужен этот запрос:**

* Меняем **цену** и/или **размер** уже выставленного (но ещё не завершённого) ордера.
* Для `FUTURES/SWAP` можем **обновлять или удалять TP/SL**, которые прикреплены к ордеру (через `attachAlgoOrds`).

**Важно (по смыслу работы):**

* Можно изменить только **незавершённый** ордер (обычно `live` или `partially_filled`). Полностью исполненный или отменённый — уже не изменить.
* Ответ этого эндпоинта — это **acknowledgement** (подтверждение, что запрос принят). Даже при `sCode="0"` это **не гарантия**, что ордер реально уже изменился.

    * Факт изменения подтверждаем через **WS ордер‑канал** или через **GET /api/v5/trade/order**.
* Если ордер частично исполнен, то `newSz` должен включать **уже исполненную часть**. Если `newSz <= filled`, ордер может стать `filled`.

---

### Endpoint

`POST /api/v5/trade/amend-order`

**Permission:** `Trade`

**Rate limit:**

* 60 requests / 2 seconds
* Правило: `User ID + Instrument ID` (для options — по instrument family)

**Auth headers (REST private):**

* OK-ACCESS-KEY
* OK-ACCESS-SIGN
* OK-ACCESS-TIMESTAMP (ISO UTC строка, участвует в подписи)
* OK-ACCESS-PASSPHRASE
* Content-Type: application/json
* Demo trading: `x-simulated-trading: 1`

---

### Request body (JSON)

#### Минимально обязательное

* `instId` — инструмент, например `ETH-USDT-SWAP`
* `ordId` **или** `clOrdId` — какой ордер меняем
* одно из изменений:

    * `newPx` (новая цена)
    * `newSz` (новый размер)
    * `attachAlgoOrds` (изменение/удаление TP/SL)

#### Все поля запроса

* `instId` (String, **required**) — ID инструмента.
* `cxlOnFail` (Boolean, optional, default `false`) — если `true`, то биржа **сама отменит** ордер, если попытка изменения провалится.
* `ordId` (String, conditional) — ID ордера на бирже. Нужен `ordId` или `clOrdId`. Если оба — используют `ordId`.
* `clOrdId` (String, conditional) — client order id.
* `reqId` (String, optional) — твой идентификатор попытки изменения. Удобно для ретраев/логов (вернётся в ответе).

**Изменение размера/цены (обычные ордера):**

* `newSz` (String, conditional) — новый размер (>0). Для partially filled — **включая уже исполненное**.
* `newPx` (String, conditional) — новая цена.

**Только для options (оставлю как справку):**

* `newPxUsd` (String, conditional) — новая цена в USD.
* `newPxVol` (String, conditional) — новая цена как implied volatility (1 = 100%).
* Для options: можно передать **только одно** из `newPx/newPxUsd/newPxVol`, и оно должно соответствовать тому, как ордер был создан.

**Поведение при выходе цены за лимиты:**

* `pxAmendType` (String, optional, default `0`):

    * `0` — если `newPx` выходит за пределы, система **не подправляет** цену (скорее всего будет отказ).
    * `1` — если `newPx` выходит за пределы, система может **подправить** цену до ближайшего допустимого значения.

---

### Как обновлять TP/SL через `attachAlgoOrds` (FUTURES/SWAP)

`attachAlgoOrds` — массив объектов. Каждый объект — это **одна прикреплённая TP/SL‑сущность**, которую ты хочешь изменить.

**Идентификация TP/SL, который меняем:**

* `attachAlgoId` (String, conditional) — ID прикреплённого TP/SL ордера.
* `attachAlgoClOrdId` (String, conditional) — твой client algo id, если задавал при создании.

**Поля для TP (take profit):**

* `newTpTriggerPx` (String, conditional) — цена триггера TP.

    * Если `newTpTriggerPx == "0"` **или** `newTpOrdPx == "0"` → TP считается **удалённым**.
* `newTpTriggerRatio` (String, conditional) — TP как процент/доля (например `0.3` = 30%). Только FUTURES/SWAP.

    * Можно передать **только одно**: `newTpTriggerPx` или `newTpTriggerRatio`.
* `newTpOrdPx` (String, conditional) — цена ордера TP.

    * `-1` означает, что TP будет исполнен **маркетом**.
* `newTpOrdKind` (String, optional) — вид TP:

    * `condition` (по умолчанию)
    * `limit`
* `newTpTriggerPxType` (String, conditional) — тип цены триггера TP: `last` | `index` | `mark`

    * **Если добавляешь TP**, это поле обычно нужно тоже передать.

**Поля для SL (stop loss):**

* `newSlTriggerPx` (String, conditional) — цена триггера SL.

    * Если `newSlTriggerPx == "0"` **или** `newSlOrdPx == "0"` → SL считается **удалённым**.
* `newSlTriggerRatio` (String, conditional) — SL как процент/доля (например `0.3` = 30%). Только FUTURES/SWAP.

    * Можно передать **только одно**: `newSlTriggerPx` или `newSlTriggerRatio`.
* `newSlOrdPx` (String, conditional) — цена ордера SL.

    * `-1` означает, что SL будет исполнен **маркетом**.
* `newSlTriggerPxType` (String, conditional) — тип цены триггера SL: `last` | `index` | `mark`

    * **Если добавляешь SL**, это поле обычно нужно тоже передать.

**Прочее (редко нужно):**

* `sz` (String, conditional) — новый размер TP (актуально для split‑TP, когда тейков несколько и дробятся).
* `amendPxOnTriggerType` (String, optional) — «cost‑price SL» (только для split‑TP SL):

    * `0` — выключено (по умолчанию)
    * `1` — включено

---

### Пример запроса 1: изменить цену и размер ордера

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/amend-order' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "ordId": "1753197687182819328",
    "reqId": "amend-0001",
    "newPx": "3150.5",
    "newSz": "10",
    "cxlOnFail": false,
    "pxAmendType": "0"
  }'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

### Пример запроса 2: сдвинуть SL/TP у прикреплённого TP/SL

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/amend-order' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "ordId": "1753197687182819328",
    "reqId": "amend-sl-tp-0001",
    "attachAlgoOrds": [
      {
        "attachAlgoId": "3209210720722571264",
        "newTpTriggerPx": "3200",
        "newTpOrdPx": "-1",
        "newTpTriggerPxType": "mark",

        "newSlTriggerPx": "3100",
        "newSlOrdPx": "-1",
        "newSlTriggerPxType": "mark",

        "newTpOrdKind": "condition"
      }
    ]
  }'
```

**Как удалить TP или SL:**

* Чтобы удалить TP: передай `newTpTriggerPx: "0"` **или** `newTpOrdPx: "0"`.
* Чтобы удалить SL: передай `newSlTriggerPx: "0"` **или** `newSlOrdPx: "0"`.

---

### Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "clOrdId": "",
      "ordId": "12344",
      "ts": "1695190491421",
      "reqId": "amend-0001",
      "sCode": "0",
      "sMsg": ""
    }
  ],
  "inTime": "1695190491421339",
  "outTime": "1695190491423240"
}
```

---

### Описание полей ответа

**Верхний уровень:**

* `code` — результат запроса (`0` = запрос принят).
* `msg` — сообщение об ошибке (пусто при `code=0`).
* `data[]` — массив результатов (обычно 1 объект).
* `inTime` — время на REST‑шлюзе, когда запрос приняли (µs).
* `outTime` — время на REST‑шлюзе, когда ответ отправили (µs).

**data[0]:**

* `ordId` — ID ордера на OKX.
* `clOrdId` — client order id (если был).
* `reqId` — твой request id (если передавал).
* `ts` — момент, когда система OKX закончила обработку запроса (ms).
* `sCode` — результат именно операции изменения (`0` = принято/успех на уровне исполнения запроса).
* `sMsg` — причина отказа, если `sCode != 0`.

**Ещё раз важно:** `sCode=0` — это не «точно изменилось», а «биржа приняла запрос». Финальный факт — по WS / GET order.

---

## Как это ложится на доменную модель `Order`

Мы **не создаём новую доменную сущность**, переиспользуем уже существующую `Order`.

### Рекомендованный подход в коде (по смыслу)

1. В домене формируем *намерение* (что хотим поменять): например `newPrice`, `newSize`, `newTp/newSl`.
2. Фасад (инфраструктура) собирает `AmendOrderRequest`.
3. После ответа:

    * в `Order.lastRequestId` кладём `reqId` (или генерируем и кладём туда же).
    * в `Order.lastAttemptAt` кладём текущее время.
    * если нужно хранить «результат последней операции»:

        * `lastCreateResultCode/Message` лучше обобщить в будущем (например `lastOperationResultCode/Message`).
        * `exchangeProcessedAt` (если у тебя есть) можно заполнить по `data[0].ts`.
4. Фактические `price/size/TP/SL/state` обновляем **только после** подтверждения (WS order channel или GET order details).

---
