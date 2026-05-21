### Отменить ордер (cancel) (REST)

**Зачем нужен этот запрос:**

* Отменить **незавершённый** ордер (обычно `live` или `partially_filled`).
* Если ордер уже **filled** (полностью исполнен) или уже **canceled** — отменить нельзя.

**Ключевая мысль:**

* Ответ `POST cancel-order` — это **ack** (подтверждение, что запрос принят).
* Ордер считается реально отменённым **только когда**:

    * по WS `orders` придёт обновление со `state="canceled"`, **или**
    * `GET /api/v5/trade/order` вернёт `state="canceled"`.

---

### Endpoint

`POST /api/v5/trade/cancel-order`

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

**Минимально обязательное:**

* `instId` — инструмент (например `ETH-USDT-SWAP`)
* `ordId` **или** `clOrdId` — какой ордер отменяем

**Поля:**

* `instId` (String, **required**) — ID инструмента.
* `ordId` (String, conditional) — ID ордера на OKX.
* `clOrdId` (String, conditional) — client order id.

    * Должно быть передано **одно из**: `ordId` или `clOrdId`.
    * Если передать оба — обычно используется `ordId`.

---

### Пример запроса 1: отмена по `ordId`

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/cancel-order' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "ordId": "1753197687182819328"
  }'
  # -H 'x-simulated-trading: 1'   # только для demo
```

### Пример запроса 2: отмена по `clOrdId`

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/cancel-order' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "clOrdId": "tbO20260125_0001"
  }'
```

---

### Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "ordId": "1753197687182819328",
      "clOrdId": "tbO20260125_0001",
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

* `code` — результат запроса (`0` = запрос принят REST-шлюзом).
* `msg` — сообщение об ошибке (пусто при `code=0`).
* `data[]` — массив результатов (обычно 1 объект).
* `inTime` — время на REST-шлюзе, когда запрос приняли (µs).
* `outTime` — время на REST-шлюзе, когда ответ отправили (µs).

**data[0]:**

* `ordId` — ID ордера на OKX.
* `clOrdId` — client order id (если был).
* `sCode` — результат операции отмены на уровне OKX:

    * `0` — запрос на отмену принят
    * не `0` — отмена отклонена (например: ордер уже filled/уже canceled/не найден)
* `sMsg` — причина отказа (если `sCode != 0`).

**Ещё раз важно:** даже при `sCode=0` ордер считается отменённым **только** после подтверждения в `orders` WS или `GET /trade/order`.

---

## WS вариант (коротко, для понимания)

Отменять можно и через **private WS** (и лимит общий с REST cancel).

**Пример cancel-order в WS:**

```json
{
  "id": "cancel-0001",
  "op": "cancel-order",
  "args": [
    {
      "instId": "ETH-USDT-SWAP",
      "ordId": "1753197687182819328"
    }
  ]
}
```

Сначала придёт **ack**, а затем (если отменилось) в канале `orders` придёт апдейт со `"state":"canceled"`.

---

## Практическая рекомендация для бота

1. Отправили cancel → получили `sCode=0` → считаем, что **заявка на отмену принята**.
2. Далее ждём подтверждение:

* WS `orders` (лучший вариант), или
* fallback: `GET /trade/order` с небольшим polling.

3. Только после подтверждения ставим `externalStatus="canceled"` и `status=CANCELED`.
