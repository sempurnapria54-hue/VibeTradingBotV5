## Получить сделки (fills, последние 3 месяца) (REST)

**Зачем нужно (простыми словами):**

* Это «архив» сделок (fills) за **последние ~3 месяца**.
* Нужен, когда даунтайм/рассинхрон был **дольше 3 дней**, или когда ты строишь отчёты/восстанавливаешь историю исполнений.

> Напоминание:
>
> * **Fill** = факт сделки (одно исполнение).
> * **Order** = заявка (один ордер может породить много fills).

---

### Endpoint

`GET /api/v5/trade/fills-history`

---

### Query параметры

* `instType` *(опционально)* — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `instId` *(опционально)* — конкретный инструмент, например `ETH-USDT-SWAP`.
* `ordId` *(опционально)* — ID ордера на бирже (OKX ordId). Удобно, если хочешь увидеть fills только по одному ордеру.
* `after` *(опционально)* — пагинация «старее, чем `billId`» (вернёт записи **раньше** указанного `billId`).
* `before` *(опционально)* — пагинация «новее, чем `billId`» (вернёт записи **позже** указанного `billId`).
* `begin` *(опционально)* — фильтр по времени начала (Unix ms).
* `end` *(опционально)* — фильтр по времени конца (Unix ms).
* `limit` *(опционально)* — сколько записей вернуть за раз. Максимум `100`, по умолчанию `100`.

**Ключевой момент:** `after/before` — это якорь по **billId**, а не по времени.

---

### Доступ / лимиты / аутентификация

* Permission: Read
* Rate limit: **10 requests / 2 seconds**, правило — User ID
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
curl -X GET 'https://www.okx.com/api/v5/trade/fills-history?instType=SWAP&instId=ETH-USDT-SWAP&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

**Пример запроса с begin/end (ms):**

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/fills-history?instType=SWAP&instId=ETH-USDT-SWAP&begin=1761350400000&end=1769251200000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
```

---

### Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",
      "tradeId": "987654321",
      "ordId": "3209990000000000000",
      "clOrdId": "tb_20251201_0007",
      "billId": "600001234567890123",
      "tag": "tb",
      "fillPx": "3100.2",
      "fillSz": "1",
      "side": "buy",
      "posSide": "long",
      "execType": "T",
      "feeCcy": "USDT",
      "fee": "-0.22",
      "ts": "1769253500123"
    }
  ]
}
```

---

## Описание полей (простыми словами)

Набор полей **такой же**, как у `GET /api/v5/trade/fills` (последние 3 дня):

* `tradeId` — ID сделки.
* `ordId` — ID ордера.
* `clOrdId` — наш client order id.
* `billId` — внутренний ID записи (ключевой для пагинации).
* `fillPx` — цена сделки.
* `fillSz` — объём сделки.
* `side` — buy/sell.
* `posSide` — net/long/short.
* `execType` — `T` taker / `M` maker.
* `fee` / `feeCcy` — комиссия.
* `ts` — время сделки.

---

## Пагинация (как идти в прошлое)

1. Запрос без `after`
2. Взял **минимальный `billId`** из ответа
3. Следующий запрос: `after=<minBillId>`
4. Повторять, пока `data` не станет пустым

---

### Что выбрать: fills (3 дня) или fills-history (3 месяца)

* `GET /trade/fills` — быстрый и «дешёвый» для короткого окна.
* `GET /trade/fills-history` — тяжелее по лимитам (10/2s), но покрывает большой период.
