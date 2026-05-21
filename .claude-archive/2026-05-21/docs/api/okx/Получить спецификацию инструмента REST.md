### X.Y Получить спецификацию инструмента (REST + опционально WS-обновления)

**Зачем это нужно боту:**

* Для SWAP/FUTURES на OKX параметр `sz` в ордерах — **в контрактах**, а не в USDT.
* Чтобы торговать «от суммы USDT», боту нужна спецификация инструмента: `ctVal/ctValCcy/ctType`, ограничения `minSz/lotSz`, шаг цены `tickSz` и лимиты `max*`.
* Эти данные лучше кэшировать (в БД/в памяти) и обновлять при старте + периодически, а изменения ловить через WS `instruments`.

---

## 1) REST snapshot

**Endpoint:** `GET /api/v5/public/instruments`

**Query параметры:**

* `instType` — **обязательный**, тип инструмента: `SPOT` / `MARGIN` / `SWAP` / `FUTURES` / `OPTION`
* `instId` — **опционально**, точный инструмент, например `ETH-USDT-SWAP`

**Доступ / лимиты / аутентификация:**

* Public endpoint (auth **не нужен**)
* Rate limit: **20 requests / 2 seconds**
* Rule: **IP + Instrument Type**

**Пример запроса (конкретно под твой кейс SWAP):**

```bash
curl -X GET 'https://www.okx.com/api/v5/public/instruments?instType=SWAP&instId=ETH-USDT-SWAP'
```

**Пример ответа (примерный, значения зависят от инструмента):**

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",
      "instFamily": "ETH-USDT",
      "uly": "ETH-USDT",

      "baseCcy": "ETH",
      "quoteCcy": "USDT",
      "settleCcy": "USDT",

      "ctType": "linear",
      "ctVal": "0.01",
      "ctValCcy": "ETH",
      "ctMult": "",

      "tickSz": "0.1",
      "lotSz": "1",
      "minSz": "1",

      "maxLmtSz": "100000",
      "maxMktSz": "100000",
      "maxTwapSz": "100000",
      "maxIcebergSz": "100000",
      "maxTriggerSz": "100000",
      "maxStopSz": "100000",

      "posLmtAmt": "",
      "posLmtPct": "",
      "maxPlatOILmt": "",

      "lever": "50",

      "state": "live",
      "listTime": "1600000000000",
      "expTime": "",
      "openType": "",
      "ruleType": "normal",

      "category": "1",
      "groupId": "1",

      "alias": "",
      "stk": "",
      "optType": ""
    }
  ]
}
```

### Описание полей (простыми словами)

#### Идентификация инструмента

* `instType` — тип инструмента: `SWAP` (perpetual), `FUTURES` (expiry), `SPOT` и т.д.
* `instId` — **имя инструмента на бирже**, то, что ты используешь везде (`ETH-USDT-SWAP`).
* `instFamily` — «семейство» инструментов (часто базовый символ), удобно для некоторых лимитов.
* `uly` — underlying (базовый), чаще полезно для деривативов.
* `baseCcy` — базовая валюта (для `ETH-USDT-SWAP` это `ETH`).
* `quoteCcy` — котируемая валюта (обычно `USDT`).
* `settleCcy` — валюта расчётов/маржи по контракту (для USDT‑маржинальных SWAP это обычно `USDT`).

#### Контрактные параметры (критично для расчёта `sz`)

* `ctType` — тип контракта:

    * `linear` — линейный (USDT‑маржинальный, PnL в USDT)
    * `inverse` — обратный (маржа/расчёт в базовой валюте)
* `ctVal` — **стоимость 1 контракта** (сколько базовой валюты «внутри» одного контракта).
* `ctValCcy` — в какой валюте задан `ctVal`.
* `ctMult` — мультипликатор контракта (иногда пустой, зависит от типа инструмента).

**Как это использовать для расчёта количества контрактов от суммы в USDT (общая идея):**

* Если контракт линейный и `ctVal` задан в базовой валюте (`ctValCcy = baseCcy`), то:

    * `baseQty = usdtNotional / price`
    * `contracts = baseQty / ctVal`
    * затем округлить по `lotSz` и проверить `minSz`.

#### Шаги и минималки (критично для валидного ордера)

* `tickSz` — минимальный шаг цены (`px`).
* `lotSz` — шаг размера (`sz`). Для деривативов это шаг **в контрактах**.
* `minSz` — минимальный размер ордера (в контрактах для SWAP/FUTURES).

#### Ограничения по размеру ордеров (полезно для валидации/защиты)

* `maxLmtSz` — максимум `sz` для одного **limit** ордера.
* `maxMktSz` — максимум `sz` для одного **market** ордера.
* `maxTwapSz` — максимум `sz` для TWAP.
* `maxIcebergSz` — максимум `sz` для iceberg.
* `maxTriggerSz` — максимум `sz` для trigger ордеров.
* `maxStopSz` — максимум `sz` для stop market.
* `maxLmtAmt` / `maxMktAmt` — лимиты в USD для SPOT/MARGIN (для SWAP часто пусто/не используется).

#### Статус и «жизненный цикл» инструмента

* `state` — статус инструмента:

    * `live` — торгуется
    * `suspend` — временно остановлен
    * `preopen` — ещё не торгуется (перед стартом)
    * `expired`/`test` — неактуален/тестовый
* `listTime` — время листинга (ms Unix).
* `expTime` — время экспирации/делистинга (для FUTURES/OPTION), для SWAP обычно пусто.
* `openType` — тип открытия (например, call auction), чаще важно для SPOT.
* `ruleType` — тип правил торговли (`normal`, `pre_market`).

#### Прочие поля (обычно не критично твоему SWAP‑боту)

* `category`, `groupId` — системные категории/группы биржи.
* `alias` — алиас (чаще для фьючей).
* `stk`, `optType` — страйк/тип опциона (актуально только для OPTION).
* `posLmtAmt`, `posLmtPct`, `maxPlatOILmt` — лимиты позиций (можно хранить, но в базовой логике чаще не нужно).

---

## 2) WS: обновления спецификации (опционально)

**Когда нужно:** если биржа поменяла `tickSz/lotSz/minSz/state` или добавила новый инструмент — это прилетит через WS.

**Public WS:** `wss://ws.okx.com:8443/ws/v5/public`

**Канал:** `instruments`

**Подписка (пример):**

```json
{
  "op": "subscribe",
  "args": [
    {
      "channel": "instruments",
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP"
    }
  ]
}
```

**Пример push‑события (формат как у REST, но приходит по одному/нескольким инструментам):**

```json
{
  "arg": {
    "channel": "instruments",
    "instType": "SWAP",
    "instId": "ETH-USDT-SWAP"
  },
  "data": [
    {
      "instType": "SWAP",
      "instId": "ETH-USDT-SWAP",
      "tickSz": "0.1",
      "lotSz": "1",
      "minSz": "1",
      "state": "live",
      "uTime": "1730000000000"
    }
  ]
}
```

**Как применять у себя:**

* REST используем для **первичного snapshot** (при старте/по расписанию).
* WS используем, чтобы **обновлять кэш** при изменениях (если пришёл инструмент с тем же `instId` — перезаписали поля спецификации, обновили `sourceUpdatedAt`).
