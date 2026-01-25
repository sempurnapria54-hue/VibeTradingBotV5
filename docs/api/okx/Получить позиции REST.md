### Получить позиции (REST)

**Endpoint:** `GET /api/v5/account/positions`
**Документация:** [https://app.okx.com/docs-v5/](https://app.okx.com/docs-v5/)

**Query (все опционально):**

* `instType` — тип инструмента: `MARGIN | SWAP | FUTURES | OPTION`.
* `instId` — id инструмента (например `ETH-USDT-SWAP`). Можно перечислить до **10** `instId` через запятую.
* `posId` — id позиции. Можно перечислить до **20** `posId`. **Важно:** после полного закрытия позиции `posId` живёт ограниченное время (в доке: 30 дней), потом позиция/posId «очищаются».

**Что именно вернёт (логика):**

* Возвращает **только те позиции, которые сейчас существуют как “актуальные”** (по сути snapshot текущего состояния).
* Если у тебя режим позиций **net** → вернёт одну запись `posSide=net` (pos может быть +/− в деривативах).
* Если режим **long/short** → вернёт отдельные записи `posSide=long` и/или `posSide=short` (а `pos` обычно положительный).

**Доступ / лимиты / аутентификация:**

* Permission: Read
* Rate limit: **10 requests / 2 seconds**, правило — User ID
* Auth headers (REST private): те же, что и для balance (OK-ACCESS-*)

---

#### Пример запроса

```bash
curl -X GET 'https://www.okx.com/api/v5/account/positions?instType=SWAP&instId=ETH-USDT-SWAP' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

#### Пример ответа

> Ниже пример “широкий” (много полей будут пустыми строками, потому что они актуальны только для margin/options/PM).

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",

      "mgnMode": "isolated",
      "posId": "1752810569801498626",
      "posSide": "net",

      "pos": "1",
      "availPos": "",
      "hedgedPos": "",

      "avgPx": "3100.5",
      "nonSettleAvgPx": "",
      "markPx": "3112.3",
      "last": "3111.9",
      "idxPx": "3112.0",
      "usdPx": "1",
      "bePx": "3098.7",

      "upl": "11.8",
      "uplRatio": "0.0038",
      "uplLastPx": "11.4",
      "uplRatioLastPx": "0.0037",

      "lever": "10",
      "liqPx": "2790.1",
      "imr": "",
      "mmr": "12.34",
      "mgnRatio": "7.12",
      "margin": "120.0",
      "notionalUsd": "3111.9",

      "realizedPnl": "0",
      "settledPnl": "",
      "pnl": "0",
      "fee": "0",
      "fundingFee": "0",
      "liqPenalty": "",

      "ccy": "USDT",
      "interest": "",
      "liab": "",
      "liabCcy": "",
      "pendingCloseOrdLiabVal": "0",

      "adl": "0",

      "tradeId": "1234567890",
      "cTime": "1769250000000",
      "uTime": "1769253296789",

      "closeOrderAlgo": [
        {
          "algoId": "3209210720722571264",
          "slTriggerPx": "3050",
          "slTriggerPxType": "mark",
          "tpTriggerPx": "3300",
          "tpTriggerPxType": "mark",
          "closeFraction": "1"
        }
      ],

      "spotInUseAmt": "",
      "spotInUseCcy": "",
      "clSpotInUseAmt": "",
      "maxSpotInUseAmt": "",

      "optVal": "",
      "deltaBS": "",
      "deltaPA": "",
      "gammaBS": "",
      "gammaPA": "",
      "thetaBS": "",
      "thetaPA": "",
      "vegaBS": "",
      "vegaPA": "",

      "baseBal": "",
      "quoteBal": "",
      "baseBorrowed": "",
      "quoteBorrowed": "",
      "baseInterest": "",
      "quoteInterest": "",

      "posCcy": "",
      "bizRefId": "",
      "bizRefType": ""
    }
  ]
}
```

---

### Описание полей (что они значат)

***Верхний уровень***

* `code` — `"0"` если успех.
* `msg` — текст ошибки/сообщения.
* `data[]` — список позиций (snapshot).

***Идентификация и режимы***

* `instType` — тип инструмента (`SWAP`/`FUTURES`/`OPTION`/`MARGIN`).
* `instId` — id инструмента (например `ETH-USDT-SWAP`).
* `mgnMode` — режим маржи: `cross` (кросс) или `isolated` (изолированная).
* `posId` — id позиции. Может “устаревать/очищаться” спустя время после полного закрытия.
* `posSide` — “сторона позиции”: `net` или `long`/`short` (зависит от режима позиций).

***Размер позиции***

* `pos` — размер позиции. В net-режиме для деривативов знак может означать long/short; в long/short-режиме обычно положительное число.
* `availPos` — сколько можно закрыть прямо сейчас (актуально для margin/options).
* `hedgedPos` — “хеджирующий объём” (в основном про delta-neutral/особые режимы).

***Цены (важно для SL/TP/ликвидации и расчётов)***

* `avgPx` — средняя цена входа.
* `nonSettleAvgPx` — “средняя без влияния расчётов/settlement” (в доке: для cross-futures, где есть settlement).
* `markPx` — mark price (биржевая “контрольная” цена риска).
* `last` — последняя цена сделки (last).
* `idxPx` — индексная цена (index).
* `usdPx` — “USD-цена” залоговой валюты (в основном для деривативов/опционов, когда залог не USD).
* `bePx` — breakeven price (цена безубытка).

***Unrealized PnL (плавающая прибыль/убыток)***

* `upl` — unrealized PnL по mark price (главное значение).
* `uplRatio` — upl в относительном виде (доля/процент).
* `uplLastPx` — unrealized PnL по last price (в доке: “в основном для отображения”).
* `uplRatioLastPx` — то же, но относительное.

***Маржа, плечо, риск, ликвидация***

* `lever` — плечо (не для опционов и некоторых PM-режимов).
* `liqPx` — расчётная цена ликвидации (“примерная/оценочная”).
* `imr` — initial margin requirement (в доке: для cross).
* `mmr` — maintenance margin (сколько нужно держать, чтобы не ликвиднуло).
* `mgnRatio` — margin ratio (насколько близко к риску).
* `margin` — “сколько маржи лежит в позиции” (в доке: для isolated можно увеличивать/уменьшать).
* `notionalUsd` — “номинал позиции” в USD (размер позиции в деньгах).

***Реализованный PnL и комиссии***

* `realizedPnl` — реализованный PnL (в доке дана формула, что туда входит).
* `settledPnl` — “settled PnL” (в доке: для cross-futures при settlement).
* `pnl` — суммарный PnL по закрывающим ордерам **без** комиссий.
* `fee` — суммарная комиссия (плюс — rebate, минус — списание).
* `fundingFee` — суммарный funding (актуально для SWAP).
* `liqPenalty` — штрафы при ликвидации (в доке: значение отрицательное).

***Технические поля по обеспечению/долгам (чаще не для твоего SWAP-изолированного сценария)***

* `ccy` — валюта, в которой “занята маржа/обеспечение”.
* `interest` — проценты по долгу (для margin-режимов).
* `liab` — долг (только margin).
* `liabCcy` — валюта долга (только margin).
* `pendingCloseOrdLiabVal` — “объём долга под ордера на закрытие” (в доке: для isolated margin).

***ADL и прочие риск-индикаторы***

* `adl` — шкала ADL (auto-deleveraging), 0..5: чем меньше, тем слабее сигнал ADL.

***Времена и “последнее событие”***

* `tradeId` — id последней сделки.
* `cTime` — время создания позиции (ms).
* `uTime` — время последнего обновления позиции (ms).

***TP/SL “прикреплённые” к позиции (если ты ставил algo TP/SL через strategy/attached orders)***

* `closeOrderAlgo[]` — список “стратегий закрытия” (появляется при определённых условиях).

    * `algoId` — id algo-ордера.
    * `slTriggerPx` — цена-триггер SL.
    * `slTriggerPxType` — тип цены для SL: `last | index | mark`.
    * `tpTriggerPx` — цена-триггер TP.
    * `tpTriggerPxType` — тип цены для TP: `last | index | mark`.
    * `closeFraction` — доля закрытия при срабатывании (в доке: `1` = 100%).

***Portfolio margin / spot offset (скорее всего не нужно твоему SWAP-боту)***

* `spotInUseAmt`, `spotInUseCcy`, `clSpotInUseAmt`, `maxSpotInUseAmt` — поля про “spot-hedge/offset” в PM.

***Опционы (греки) — тебе не нужно, но поля могут присутствовать пустыми***

* `optVal` — стоимость позиции опциона.
* `deltaBS/deltaPA`, `gammaBS/gammaPA`, `thetaBS/thetaPA`, `vegaBS/vegaPA` — греки опциона (в USD-базе / coin-базе).

***Deprecated поля (в доке помечены как устаревшие)***

* `baseBal`, `quoteBal`, `baseBorrowed`, `quoteBorrowed`, `baseInterest`, `quoteInterest` — устаревшие поля для margin.

***Прочее***

* `posCcy` — “валюта позиции” (в основном для margin-позиций).
* `bizRefId`, `bizRefType` — внешние “бизнес-ссылки” (например купоны/промо и т.п.).
