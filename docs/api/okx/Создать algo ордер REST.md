### TP/SL/Trailing: создать алгоритмический ордер (REST)

**Endpoint:** `POST /api/v5/trade/order-algo`

Простыми словами: этим запросом ты создаёшь **алго‑ордер** (условный ордер), который **сам выставит сделку** или **закроет позицию**, когда цена дойдёт до условия:

* **TP/SL** (тейк/стоп по триггер‑цене)
* **Trailing Stop** (плавающий стоп)
* (опционально) **Trigger** (выставить market/limit ордер, когда цена пересекла уровень)

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

## 2) Тело запроса (общие поля)

Тело — **JSON объект**.

### Общие поля

* `instId` *(String, обяз.)* — инструмент, например `ETH-USDT-SWAP`.
* `tdMode` *(String, обяз.)* — режим торговли:

    * `isolated` — изолированная маржа (твой кейс)
    * `cross` — кросс‑маржа
    * `cash` — спот без маржи
* `side` *(String, обяз.)* — `buy` или `sell`.
* `ordType` *(String, обяз.)* — тип алго‑ордера:

    * `conditional` — TP/SL (условный)
    * `oco` — OCO (одно отменяет другое) для TP+SL
    * `trigger` — Trigger‑ордер (когда цена пересекла уровень → ставим market/limit)
    * `move_order_stop` — Trailing Stop (плавающий стоп)
* `sz` *(String, обычно обяз.)* — размер. Для SWAP обычно **в контрактах**.
* `tgtCcy` *(String, только для SPOT market)* — единица измерения `sz`:

    * `base_ccy` / `quote_ccy`.
* `algoClOrdId` *(String, опц.)* — твой client‑id для алго‑ордера (удобно для идемпотентности).
* `tag` *(String, опц.)* — твой тег.

### Поля, которые часто нужны для SWAP/FUTURES

* `posSide` *(String, часто нужно для SWAP)* — сторона позиции:

    * `net` / `long` / `short` (зависит от режима позиций аккаунта).
* `reduceOnly` *(String/Boolean, опц.)* — если `true`, то ордер **только уменьшает позицию** (без открытия новой).
* `ccy` *(String, опц.)* — валюта маржи (для USDT‑SWAP обычно `USDT`).
* `closeFraction` *(String, опц.)* — **доля позиции**, которую нужно закрыть при срабатывании.

    * Например `1` = закрыть 100% позиции.
    * Полезно, когда хочешь «закрывающий» TP/SL как часть позиции.

> Практическое правило: для «страхующего» TP/SL по уже открытой позиции обычно ставят `reduceOnly=true` и (по желанию) `closeFraction=1`.

---

## 3) Параметры для TP/SL (ordType = conditional)

### Take Profit

* `tpTriggerPx` *(String, опц.)* — цена, при достижении которой сработает TP.
* `tpTriggerPxType` *(String, опц.)* — тип цены триггера:

    * `last` / `index` / `mark` (часто по умолчанию `last`).
* `tpOrdPx` *(String, опц.)* — цена ордера TP:

    * `-1` означает **исполнить по рынку**.

### Stop Loss

* `slTriggerPx` *(String, опц.)* — цена, при достижении которой сработает SL.
* `slTriggerPxType` *(String, опц.)* — тип цены триггера: `last` / `index` / `mark`.
* `slOrdPx` *(String, опц.)* — цена ордера SL:

    * `-1` означает **исполнить по рынку**.

⚠️ Важно про `conditional` в **net‑режиме**: если одновременно отправить и TP, и SL — биржа может применить только логику SL, а TP проигнорировать.

**Рекомендация:** если хочешь одновременно TP+SL — используй `ordType=oco`.

---

## 4) Параметры для OCO (ordType = oco)

Смысл: задаёшь TP и SL вместе; когда один сработал — второй отменяется.

Обычно используются те же поля, что и для TP/SL:

* `tpTriggerPx`, `tpTriggerPxType`, `tpOrdPx`
* `slTriggerPx`, `slTriggerPxType`, `slOrdPx`

Плюс общие поля: `instId`, `tdMode`, `side`, `sz` (или `closeFraction`), `posSide`, `reduceOnly`, и т.д.

---

## 5) Параметры для Trigger (ordType = trigger)

Смысл: когда цена пересекла `triggerPx`, биржа выставит **обычный ордер** по `orderPx`.

* `triggerPx` *(String, обяз.)* — цена триггера.
* `orderPx` *(String, обяз.)* — цена ордера:

    * `-1` = **market**.
* `triggerPxType` *(String, опц.)* — тип цены триггера: `last` / `index` / `mark` (часто default `last`).

Важно: Trigger‑ордера обычно **не морозят активы** при постановке (биржа проверит баланс в момент срабатывания).

---

## 6) Параметры для Trailing Stop (ordType = move_order_stop)

Смысл: стоп «едет» за ценой. Когда цена развернулась на величину трейла — сработает **market**.

* `callbackRatio` *(String, условно обяз.)* — трейл в процентах (например `0.01` = 1%).
* `callbackSpread` *(String, условно обяз.)* — трейл в абсолютных единицах цены.

    * Можно передать **только одно** из двух: `callbackRatio` или `callbackSpread`.
* `activePx` *(String, опц.)* — цена активации:

    * пока цена не дошла до `activePx`, трейлинг «не включается».
    * если не указать — включается сразу.

**Как биржа считает триггер (простыми словами):**

* Для `sell` / коротких: берёт **максимум** цены после постановки и отнимает трейл.
* Для `buy` / длинных: берёт **минимум** цены после постановки и прибавляет трейл.

---

## 7) Примеры реквестов

### 7.1 TP/SL закрывающий (OCO) для SWAP (market при срабатывании)

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/order-algo' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "tdMode": "isolated",
    "side": "sell",
    "posSide": "net",
    "ordType": "oco",
    "reduceOnly": true,
    "sz": "10",
    "tpTriggerPx": "3200",
    "tpTriggerPxType": "mark",
    "tpOrdPx": "-1",
    "slTriggerPx": "3000",
    "slTriggerPxType": "mark",
    "slOrdPx": "-1",
    "algoClOrdId": "tbOcoTPSL0001",
    "tag": "trading-bot"
  }'
```

### 7.2 Trailing Stop для SWAP (1% трейл + активация)

```bash
curl -X POST 'https://www.okx.com/api/v5/trade/order-algo' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-25T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>' \
  -d '{
    "instId": "ETH-USDT-SWAP",
    "tdMode": "isolated",
    "side": "sell",
    "posSide": "net",
    "ordType": "move_order_stop",
    "reduceOnly": true,
    "sz": "10",
    "callbackRatio": "0.01",
    "activePx": "3150",
    "algoClOrdId": "tbTrailSL0001",
    "tag": "trading-bot"
  }'
```

---

## 8) Пример ответа

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "algoId": "1836487817828872192",
      "algoClOrdId": "tbOcoTPSL0001",
      "clOrdId": "",
      "sCode": "0",
      "sMsg": "",
      "tag": "trading-bot"
    }
  ]
}
```

---

## 9) Описание полей ответа (простыми словами)

* `code` — `0`, если запрос принят.
* `msg` — сообщение об ошибке/инфо.
* `data[]` — результат по каждому алго‑ордеру (обычно один):

    * `algoId` — ID алго‑ордера на стороне OKX (главный идентификатор).
    * `algoClOrdId` — твой client‑id (если задавал).
    * `clOrdId` — устаревшее поле (deprecated).
    * `sCode` — `0` если биржа приняла запрос, иначе код отказа.
    * `sMsg` — текст отказа (если `sCode != 0`).
    * `tag` — твой тег.

---

## 10) Практические заметки для бота

* Для SWAP чаще всего **лучше использовать `mark`** как `tp/slTriggerPxType`, чтобы не ловить «шпильки» last‑цены.
* Для «закрывающих» TP/SL ставь `reduceOnly=true`, чтобы случайно не открыть противоположную позицию.
* Для трейлинга удобно задавать `activePx`, чтобы он не включался «сразу» и не выбивало на шуме.
* Алго‑ордер может быть принят (`sCode=0`), но фактическое исполнение/срабатывание нужно дальше отслеживать через:

    * детали алго‑ордера (`GET /api/v5/trade/order-algo`) и/или
    * историю ордеров/сделок (orders / fills) для факта исполнения.
