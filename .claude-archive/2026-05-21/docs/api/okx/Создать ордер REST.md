## Создать ордер (REST)

**Endpoint:** `POST /api/v5/trade/order`

**Назначение:** создать обычный ордер (SPOT/MARGIN/FUTURES/SWAP/OPTION). Для твоего бота (SWAP ETH‑USDT‑SWAP, isolated, плечо до x10) — это основной способ открыть/закрыть позицию (в зависимости от side/posSide/reduceOnly и режима позиций).

---

### 1) Доступ / лимиты / аутентификация

* **Permission:** Trade
* **Rate limit:** 60 requests / 2 seconds
* **Правило лимита:** `User ID + Instrument ID` (для опционов есть нюансы, тебе обычно не нужно)
* **Auth headers (REST private):**

  * `OK-ACCESS-KEY`
  * `OK-ACCESS-SIGN`
  * `OK-ACCESS-TIMESTAMP` (ISO строка, UTC)
  * `OK-ACCESS-PASSPHRASE`
  * `Content-Type: application/json`
  * (опционально) `expTime: 1597026383085` — «срок годности запроса» (ms). Если биржа получит запрос позже этого времени — он считается протухшим.
  * Demo trading: `x-simulated-trading: 1`

***Как считается подпись (важно для “рабочего” реквеста):***

* `prehash = timestamp + method + requestPath + body`
* подпись = `Base64(HMAC_SHA256(secret, prehash))`
* Для POST **body обязателен** и именно он участвует в подписи.

---

### 2) Тело запроса (Request body)

> Все числа у OKX приходят/уходят **строками**.

#### Обязательные поля (минимум)

* `instId` (String, **обяз**) — инструмент, например `ETH-USDT-SWAP`.
* `tdMode` (String, **обяз**) — режим торговли:

  * `isolated` — **то, что ты используешь**
  * `cross` — кросс
  * `cash` — спот без маржи
* `side` (String, **обяз**) — `buy` или `sell`.
* `ordType` (String, **обяз**) — тип ордера: `market`, `limit`, `post_only`, `fok`, `ioc`, `optimal_limit_ioc` (и др.).
* `sz` (String, **обяз**) — размер ордера.

  * Для **FUTURES/SWAP**: это **кол-во контрактов**, не USDT.

#### Условно‑обязательные поля

* `px` (String) — цена. Нужна для `limit/post_only/fok/ioc` и т.п. Для `market` обычно не передаётся.

#### Поля, которые тебе важны для SWAP

* `posSide` (String) — сторона позиции:

  * В **net** режиме (одна позиция на инструмент): можно не передавать (по умолчанию `net`).
  * В **long/short** режиме: **обязательно** `long` или `short`.
  * Для SWAP/FUTURES.
* `reduceOnly` (Boolean) — «только уменьшать позицию».

  * Важно, чтобы случайно не открыть позицию в обратную сторону.
  * По доке: применяется к `FUTURES/SWAP` в **net** режиме и к MARGIN (есть нюансы режимов аккаунта).
* `clOrdId` (String) — твой **client order id**.

  * Очень полезно для идемпотентности: при ретраях/даунтайме можно искать ордер по `clOrdId`.
* `tag` (String) — метка.
* `ccy` (String) — валюта маржи (для некоторых MARGIN кейсов; для твоего USDT‑SWAP часто не нужно).
* `stpMode` (String) — защита от самоторговли (`cancel_maker`, `cancel_taker`, `cancel_both`).

#### 2.1 Прикрепить TP/SL сразу при создании (`attachAlgoOrds`)

Поле `attachAlgoOrds` (Array<Object>) — список объектов TP/SL, которые будут «прикреплены» к ордеру.

Один элемент массива (объект TP/SL) может содержать:

* `attachAlgoClOrdId` (String) — твой client‑id для этого прикреплённого TP/SL.

* `tpTriggerPx` (String) — цена триггера TP (для `condition`).

* `tpTriggerRatio` (String) — TP триггер в доле (0.3 = 30%). **Либо** `tpTriggerPx`, **либо** `tpTriggerRatio`.

* `tpOrdPx` (String) — цена исполнения TP:

  * для `condition` обычно нужна вместе с `tpTriggerPx`
  * для `limit` можно указать `tpOrdPx`, а `tpTriggerPx` не обязателен
  * если `-1` — TP исполнится по рынку

* `tpOrdKind` (String) — `condition` или `limit` (по умолчанию `condition`).

* `slTriggerPx` (String) — цена триггера SL.

* `slTriggerRatio` (String) — SL триггер в доле. **Либо** `slTriggerPx`, **либо** `slTriggerRatio`.

* `slOrdPx` (String) — цена исполнения SL (если `-1` — исполнение по рынку).

* `tpTriggerPxType` / `slTriggerPxType` (String) — тип цены триггера: `last` / `index` / `mark`.

  * По умолчанию: `last`.

* `sz` (String) — размер TP для split‑TP (если тейки дробятся). Обычно тебе не нужно, если не делаешь split.

* `amendPxOnTriggerType` (String) — «cost‑price SL» для split‑TP (0/1). Обычно можно игнорировать.

---

### 3) Пример запроса (ETH-USDT-SWAP, isolated, limit + attach TP/SL)

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/order' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T18:30:00.000Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "tdMode": "isolated",
    "clOrdId": "tbO20260125_0001",
    "tag": "tb",
    "side": "buy",
    "posSide": "net",
    "ordType": "limit",
    "px": "2500",
    "sz": "10",
    "reduceOnly": false,
    "attachAlgoOrds": [
      {
        "attachAlgoClOrdId": "tbTPSL20260125_0001",
        "tpTriggerPxType": "last",
        "slTriggerPxType": "last",
        "tpOrdKind": "condition",
        "tpTriggerPx": "2600",
        "tpOrdPx": "-1",
        "slTriggerPx": "2450",
        "slOrdPx": "-1"
      }
    ]
  }'
  # -H 'x-simulated-trading: 1'   # только для demo
```

> Примечание: `sz` для SWAP — **контракты**. Если ты задаёшь риск в USDT, надо отдельно конвертировать USDT → контракты (ctVal/lotSz/minSz + текущая цена).

---

### 4) Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "clOrdId": "tbO20260125_0001",
      "ordId": "312269865356374016",
      "tag": "tb",
      "ts": "1695190491421",
      "sCode": "0",
      "sMsg": ""
    }
  ],
  "inTime": "1695190491421339",
  "outTime": "1695190491423240"
}
```

**Что важно в ответе:**

* `code/msg` — статус запроса на уровне API.
* `data[0].sCode/sMsg` — результат выполнения события (например, биржа могла отклонить ордер).
* `ordId` — ID ордера на OKX (главный идентификатор в дальнейших запросах).
* `ts` — когда система OKX закончила обработку заявки.
* `inTime/outTime` — диагностические времена на REST‑шлюзе (микросекунды).

---
