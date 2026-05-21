### Закрыть позицию (маркетом) (REST)

**Endpoint:** `POST /api/v5/trade/close-position`

Простыми словами: биржа **сама закроет текущую позицию** по рынку.
Это удобнее, чем выставлять обратный market-ордер вручную, потому что биржа уже знает текущий размер позиции.

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

## 2) Request body (JSON)

### Обязательные поля

* `instId` *(String, обяз.)* — инструмент, например `ETH-USDT-SWAP`.
* `mgnMode` *(String, обяз.)* — режим маржи позиции:

    * `isolated` — изолированная (твой кейс)
    * `cross` — кросс

### Поля, которые зависят от режима позиций

* `posSide` *(String, условно обяз.)* — какую сторону закрываем:

    * `net` — если аккаунт в режиме **net** (одна позиция на инструмент)
    * `long` / `short` — если аккаунт в режиме **long/short** (две позиции на инструмент)

> Практика:
>
> * если ты работаешь только в `net`, то всегда передавай `posSide=net`.
> * если допускаешь long/short режим — это поле становится критичным.

### Дополнительные поля

* `ccy` *(String, опц.)* — валюта маржи (для USDT-SWAP обычно `USDT`).
* `autoCxl` *(Boolean, опц.)* — автоматически отменить все активные ордера по инструменту перед закрытием.

    * Рекомендуется ставить `true`, чтобы не было конфликтов (например активный лимит, который снова откроет позицию).

---

## 3) Пример запроса (curl)

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/close-position' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "mgnMode": "isolated",
    "posSide": "net",
    "ccy": "USDT",
    "autoCxl": true
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
      "instId": "ETH-USDT-SWAP",
      "posSide": "net"
    }
  ]
}
```

---

## 5) Описание полей ответа (простыми словами)

**Верхний уровень:**

* `code` — `0`, если REST-запрос принят.
* `msg` — сообщение/ошибка.
* `data[]` — массив результатов (обычно один объект).

**data[0]:**

* `instId` — инструмент.
* `posSide` — какая сторона позиции закрывалась.

⚠️ Важно: в ответе **нет ordId** и нет “финального статуса позиции”. Это просто подтверждение, что биржа приняла запрос.

---

## 6) Как понять, что позиция реально закрылась

После `close-position` обязательно подтверждаем факт закрытия:

1. `GET /api/v5/account/positions?instId=...`

* позиция исчезла из списка **или** `pos=0`

2. (опционально) `GET /api/v5/trade/fills?instId=...`

* появились сделки, которые закрыли позицию

3. (лучше) private WS каналы:

* `positions` / `orders`

---

## 7) Практические заметки для бота

* `autoCxl=true` снижает риск, что активный ордер снова откроет позицию.
* Даже если `close-position` вернул ошибку — всё равно проверяем snapshot позиции:

    * позиция могла уже закрыться сама (например TP/SL/ликвидация/ручное закрытие) во время даунтайма.
