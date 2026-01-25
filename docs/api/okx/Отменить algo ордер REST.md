### TP/SL/Trailing: отменить algo-ордер (REST)

**Endpoint:** `POST /api/v5/trade/cancel-algos`

Простыми словами: этим запросом ты отменяешь **алго-ордер** (TP/SL/OCO/Trailing/Trigger), который ранее создал через `POST /trade/order-algo`.

---

## 1) Доступ / лимиты / аутентификация

**Permission:** Trade

**Rate limit:** `20 requests / 2 seconds`
**Правило:** User ID + Instrument ID

**Auth headers (REST private):**

* `OK-ACCESS-KEY`
* `OK-ACCESS-SIGN`
* `OK-ACCESS-TIMESTAMP` (ISO строка в UTC, должна совпадать с тем, что участвовало в подписи)
* `OK-ACCESS-PASSPHRASE`
* `Content-Type: application/json`
* (demo) `x-simulated-trading: 1`

***Подпись:***

* `prehash = timestamp + method + requestPath + body`
* `sign = Base64(HMAC_SHA256(secret, prehash))`

---

## 2) Тело запроса

Тело — **JSON объект**.

### Обязательные поля

* `instId` *(String, обяз.)* — инструмент, например `ETH-USDT-SWAP`.
* `algoId` *(String, обяз.)* — ID алго-ордера на стороне OKX.

### Дополнительные поля

* `algoClOrdId` *(String, опц.)* — твой client-id алго-ордера.

    * Обычно достаточно `algoId`, но `algoClOrdId` удобен для логов/проверок.

> Важно: отмена работает для алго-ордеров, которые ещё активны. Если алго уже сработал/закрыт/отменён — OKX вернёт отказ (через `sCode/sMsg`).

---

## 3) Пример запроса (curl)

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/cancel-algos' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "algoId": "1836487817828872192"
  }'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

## 4) Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "algoId": "1836487817828872192",
      "algoClOrdId": "tbTrailSL0001",
      "sCode": "0",
      "sMsg": ""
    }
  ]
}
```

---

## 5) Описание полей ответа (простыми словами)

**Верхний уровень:**

* `code` — `0`, если REST-запрос принят.
* `msg` — сообщение/ошибка.
* `data[]` — результат отмены по каждому алго-ордеру (обычно один).

**data[0]:**

* `algoId` — какой алго отменяли.
* `algoClOrdId` — твой client-id (если был).
* `sCode` — результат отмены:

    * `0` — отмена принята
    * не `0` — отмена не удалась (например алго уже сработал/не найден/нельзя отменить)
* `sMsg` — причина отказа.

---

## 6) Практические заметки для бота

* Ответ `sCode=0` означает «биржа приняла отмену».
  Факт, что алго реально стал неактивным, лучше подтверждать через:

    * `GET /api/v5/trade/order-algo?algoId=...` (получить детали алго)
    * или через периодический reconcile (fills/orders), если алго мог успеть сработать.

* Для идемпотентности удобно хранить у себя:

    * `algoId`
    * `algoClOrdId`
    * `lastCancelResultCode/Message`
    * `lastAttemptAt`
