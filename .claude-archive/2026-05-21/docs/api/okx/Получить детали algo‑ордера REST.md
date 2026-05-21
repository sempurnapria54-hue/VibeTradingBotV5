# Получить детали algo-ордера (REST)

**Endpoint:** `GET /api/v5/trade/order-algo`

Эндпоинт возвращает **детали algo-ордера**.

* Ответ всегда приходит в виде массива `data: []`.
* На практике ожидаем **0 или 1** элемент в `data`:

    * `data=[]` — algo-ордер не найден по заданным идентификаторам.
    * `data=[{...}]` — найден один algo-ордер.

---

## Query-параметры

### Обязательные (по смыслу)

Нужно передать **хотя бы один** из:

* `algoId` — внешний ID algo-ордера (ID от биржи).
* `algoClOrdId` — твой client-id algo-ордера.

### Правило приоритета

* Если передать и `algoId`, и `algoClOrdId`, OKX использует **`algoId`**.

> В актуальном описании этого endpoint `instId` не указан как обязательный query-параметр для запроса деталей
> algo-ордера.

---

## Доступ / аутентификация (private REST)

* Permission: **Read**
* Auth headers:

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO UTC)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: `x-simulated-trading: 1` (только если ключи demo).

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
      "state": "live",
      "tag": "",
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
      "actualSz": "",
      "actualPx": "",
      "actualSide": "",
      "triggerTime": "",
      "last": "",
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
      "cTime": "1769253296789"
    }
  ]
}
```

---

# Полное описание полей ответа

Ниже — пояснения **к объекту `data[i]`**, то есть к одному algo-ордеру.

> Важно: набор заполняемых полей зависит от `ordType`. Для «неприменимых» полей OKX часто возвращает пустую строку `""`
> или пустой массив `[]`.

---

## 1) Инструмент, режимы и базовые параметры

* `instType` — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `instId` — инструмент на бирже (например `ETH-USDT-SWAP`).
* `ccy` — валюта маржи (может быть неиспользуемой/пустой в части режимов).
* `tdMode` — режим торговли: `cash | cross | isolated`.
* `lever` — плечо (для `MARGIN/FUTURES/SWAP`).
* `quickMgnType` — quick margin type (для isolated margin в quick margin режимах):

    * `manual` — вручную
    * `auto_borrow` — авто-заём
    * `auto_repay` — авто-погашение

---

## 2) Algo-идентификаторы и связь с обычными ордерами

* `algoId` — основной ID algo-ордера на бирже.
* `algoClOrdId` — твой client-supplied ID algo-ордера.
* `clOrdId` — client order id (может встречаться в сценариях связывания с обычным ордером).
* `ordId` — последний связанный обычный ордер (может быть пустым, пока ничего не создавалось).
* `ordIdList` — список связанных `ordId` (может быть несколько при split-TP/SL и похожих сценариях).

---

## 3) Тип, состояние и теги

* `ordType` — тип algo-ордера:

    * `conditional` — single TP/SL (односторонний stop)
    * `oco` — OCO (one-cancels-the-other)
    * `trigger` — trigger order
    * `move_order_stop` — trailing stop
* `state` — состояние algo-ордера:

    * `live` — активен
    * `pause` — на паузе
* `tag` — тэг (если задавался при создании; удобно ставить `trading-bot`).

---

## 4) Сторона, позиция, reduce-only

* `side` — сторона: `buy` / `sell`.
* `posSide` — сторона позиции:

    * `net` — net mode
    * `long` / `short` — hedge mode
* `reduceOnly` — `true/false`: ордер только уменьшает позицию.
* `closeFraction` — доля позиции, которую закрыть при срабатывании (например `1` = 100%).

---

## 5) Количество и единицы

* `sz` — количество купить/продать (для `SWAP` обычно **контракты**, не USDT).
* `tgtCcy` — только для `SPOT market`:

    * `base_ccy` — размер задан в базовой валюте
    * `quote_ccy` — размер задан в котируемой валюте

---

## 6) TP/SL поля (для close-algo)

**TP:**

* `tpTriggerPx` — триггер TP.
* `tpTriggerPxType` — тип цены TP триггера: `last | index | mark`.
* `tpOrdPx` — цена TP ордера:

    * `-1` обычно означает market

**SL:**

* `slTriggerPx` — триггер SL.
* `slTriggerPxType` — тип цены SL триггера: `last | index | mark`.
* `slOrdPx` — цена SL ордера:

    * `-1` обычно означает market

---

## 7) Trigger order

* `triggerPx` — trigger price.
* `triggerPxType` — тип цены триггера: `last | index | mark`.
* `ordPx` — цена выставляемого ордера после trigger:

    * `-1` обычно означает market

---

## 8) Trailing stop (`move_order_stop`)

* `callbackRatio` — callback ratio (процент/доля отката).
* `callbackSpread` — callback spread (абсолютное значение отката).
* `activePx` — цена активации trailing.
* `moveTriggerPx` — trigger price trailing.

---

## 9) Фактические значения после срабатывания

* `actualSz` — фактическое количество.
* `actualPx` — фактическая цена.
* `actualSide` — что сработало: `tp` или `sl` (для `oco`/`conditional`).
* `triggerTime` — время срабатывания (Unix ms).
* `last` — “последняя цена” при размещении (служебное поле).

---

## 10) Iceberg / TWAP (если применимо)

* `pxVar` — price ratio.
* `pxSpread` — price variance.
* `szLimit` — average amount.
* `pxLimit` — price limit.
* `timeInterval` — time interval (только TWAP).

---

## 11) Fail / спец-настройки

* `failCode` — причина, почему algo не смог сработать.

    * Для «деталей» может быть пусто, пока ордер `live`.
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

> В некоторых ответах/эндпоинтах можно встретить расширенный вариант attached-объекта (с `attachAlgoId`, `tpOrdKind`,
`failReason` и т.д.). Если он появится у тебя в реальных ответах — просто расширь эту секцию по аналогии.

---

## 13) Время

* `cTime` — время создания algo-ордера (Unix ms).

---

## Практическая заметка

* Для реконсиляции после рестарта обычно делают:

    1. `orders-algo-pending` (и `orders-pending`) — получить снапшот «что живое».
    2. Точечно дергать `order-algo` (details) по `algoId` для тех ордеров, которые важны для стратегии.
