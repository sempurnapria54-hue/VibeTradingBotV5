# OKX API — Сценарий 4  
## «Цена пошла в нашу сторону → двигаем SL на n%» (TP/SL, partial TP, trailing stop)

**Инструмент:** `ETH-USDT-SWAP`  
**Режим маржи:** `isolated`  
**posSide:** `net` (как в твоих ответах из `/api/v5/account/positions`)  
**Цель сценария:** после открытия позиции (или появления позиции) **управлять защитой**: двигать SL на n% в сторону цены и (в зависимости от варианта) держать/двигать TP.

---

## 0) Важные наблюдения из практики и доков

### 0.1. `orders-algo-pending` требует `ordType`
Если сделать:
- `GET /api/v5/trade/orders-algo-pending?instId=ETH-USDT-SWAP`

можно получить:
- `code=51000 msg="Parameter ordType error"`

Правильно:
- `GET /api/v5/trade/orders-algo-pending?ordType=oco&instId=ETH-USDT-SWAP`
- `GET /api/v5/trade/orders-algo-pending?ordType=conditional&instId=ETH-USDT-SWAP`
- `GET /api/v5/trade/orders-algo-pending?ordType=move_order_stop&instId=ETH-USDT-SWAP`

---

### 0.2. Для `posSide=net` нельзя надёжно использовать `conditional` как “TP+SL вместе”
Из документации OKX (и это совпало с твоими тестами): **если в `ordType=conditional` отправить и TP и SL, то TP может игнорироваться**.  
Практический вывод:
- **Нужны TP+SL одновременно → используй `ordType=oco`.**
- Нужен только SL → можно `ordType=conditional`.
- Нужен только TP → можно `ordType=conditional` (TP-only) или отдельный limit reduceOnly (см. Вариант C).

---

### 0.3. `move_order_stop` (Trailing Stop на стороне OKX) — это отдельный algo-ордер
Он **может оставаться live после закрытия позиции**, поэтому нужна “логическая привязка”:
- храним `algoId` trailing-а в БД
- при закрытии позиции делаем **cleanup**: отменяем algo (и лимитки TP, если есть)

---

### 0.4. `reqId` в `amend-algos`: используй простые ASCII буквы/цифры
У тебя было:
- `tbC_RemoveTp0001` → `Parameter reqId error`
- `tbCRemoveTp0001` → ОК

Рекомендация: `reqId` вида `tbSomething0001` (без `_`, пробелов, спецсимволов), длина ≤ 32.

---

## 1) Соглашения по идентификаторам (важно для “связи” и recovery)

### 1.1. `clOrdId` (обычный ордер `/trade/order`)
Рекомендация:
- `tbS4Open0001`, `tbS4Close0001`, `tbS4Tp1_0001` (но лучше без `_`)

### 1.2. `algoClOrdId` (algo-ордер `/trade/order-algo`)
Рекомендация (привязка к позиции):
- `tbS4SL<posId>v1` — для SL/ОСО
- `tbS4TS<posId>v1` — для trailing stop на OKX

### 1.3. Что хранить в БД (минимум)
На уровне инструмента (у тебя допускается максимум 1 позиция на инструмент):
- `instId`
- `posId` (из `/account/positions`)
- `pos` (размер позиции, в **контрактах**)
- `direction` (LONG/SHORT)
- `slAlgoId` (если SL через algo)
- `tpAlgoIds[]` (если TP через algo) или `tpOrdIds[]` (если TP лимитками)
- `trailAlgoId` (если trailing stop на OKX)
- `status` (OPEN/CLOSED)

---

## 2) Общие формулы “двигаем SL на n%”

> SL всегда двигаем только в “правильную” сторону: **никогда не ухудшаем**.

### LONG (позиция buy, закрываем sell)
- `newSL = max(prevSL, lastPrice * (1 - nPct))`

### SHORT (позиция sell, закрываем buy)
- `newSL = min(prevSL, lastPrice * (1 + nPct))`

Где:
- `lastPrice` — текущая цена (например из `markPx`/`last`/тикера — по твоей стратегии)
- `nPct` — например 0.01 для 1%

---

# 3) Варианты сценария 4

## Вариант A — TP “в голове стратегии”, закрытие по trailing-SL (SL двигаем сами)

Идея:
- На бирже держим **только SL** (algo `conditional`)
- Когда цена в нашу сторону — двигаем SL через `amend-algos`
- TP не выставляем (или снимаем, если был)

### A-01 — Контроль позиции (posId, pos, направление)
**GET**
```
https://www.okx.com/api/v5/account/positions?instId=ETH-USDT-SWAP
```

Ожидаемо:
- `pos` > 0 → позиция есть
- запоминаем `posId`, `pos`, `avgPx`

---

### A-02 — Создать SL-only (conditional)
Пример для **LONG**: закрываем `sell`, SL ниже цены.
**POST**
```
https://www.okx.com/api/v5/trade/order-algo
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "conditional",
  "sz": "1",
  "reduceOnly": "true",
  "algoClOrdId": "tbS4SL3208956489289703424v1",
  "slTriggerPx": "3050",
  "slTriggerPxType": "last",
  "slOrdPx": "-1"
}
```

---

### A-03 — Контроль: SL живой (pending algo)
**GET**
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=conditional&instId=ETH-USDT-SWAP
```

Ищем свою запись по `algoClOrdId`, берем `algoId`.

---

### A-04 — Двигаем SL (trailing руками) через amend-algos
**POST**
```
https://www.okx.com/api/v5/trade/amend-algos
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "algoId": "3209586656290754560",
  "reqId": "tbS4AAmend0001",
  "newSlTriggerPx": "3105",
  "newSlOrdPx": "-1",
  "newSlTriggerPxType": "last",
  "cxlOnFail": false
}
```

---

### A-05 — Закрыть позицию
Есть два рабочих способа.

#### A-05a (простой и “универсальный”): противоположный market + reduceOnly
Для **закрытия LONG**:
**POST**
```
https://www.okx.com/api/v5/trade/order
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "market",
  "sz": "1",
  "reduceOnly": "true",
  "clOrdId": "tbS4AClose0001"
}
```

#### A-05b (если используешь endpoint закрытия позиции)
Если у тебя он доступен — можно закрывать через `close-position` (встречается в API v5).  
Но даже с ним **cleanup algo** всё равно лучше делать явным.

---

### A-06 — Cleanup: снять SL algo, чтобы не висел
**POST**
```
https://www.okx.com/api/v5/trade/cancel-algos
```
Body (**обрати внимание: массив**):
```json
[
  {
    "instId": "ETH-USDT-SWAP",
    "algoId": "3209586656290754560"
  }
]
```

---

## Вариант B — Закрытие по TP, при этом SL трейлим руками

Идея:
- На бирже держим **OCO (TP+SL)** — это “правильный” способ для `posSide=net`
- SL двигаем через `amend-algos`
- TP обычно фиксированный, но при желании тоже двигается через `amend-algos`

### B-01 — Контроль позиции
**GET**
```
https://www.okx.com/api/v5/account/positions?instId=ETH-USDT-SWAP
```

---

### B-02 — Создать OCO (TP+SL)
Пример (как у тебя в тестах): TP/SL по `last`, рыночное исполнение `-1`.
**POST**
```
https://www.okx.com/api/v5/trade/order-algo
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "oco",
  "sz": "1",
  "reduceOnly": "true",
  "algoClOrdId": "tbS4BOCO0001",
  "tpTriggerPx": "3300",
  "tpTriggerPxType": "last",
  "tpOrdPx": "-1",
  "slTriggerPx": "3000",
  "slTriggerPxType": "last",
  "slOrdPx": "-1"
}
```

---

### B-03 — Контроль OCO
**GET**
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=oco&instId=ETH-USDT-SWAP
```

---

### B-04 — Двигать SL (и/или TP) через amend-algos
Сдвигаем **оба** сразу (как у тебя работало):
**POST**
```
https://www.okx.com/api/v5/trade/amend-algos
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "algoId": "3209586656290754560",
  "reqId": "tbS4BAmend0001",
  "newSlTriggerPx": "3059",
  "newSlOrdPx": "-1",
  "newSlTriggerPxType": "last",
  "newTpTriggerPx": "3350",
  "newTpOrdPx": "-1",
  "newTpTriggerPxType": "last",
  "cxlOnFail": false
}
```

---

### B-05 — (опционально) Убрать TP, оставить только SL
Это работало у тебя так:
**POST**
```
https://www.okx.com/api/v5/trade/amend-algos
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "algoId": "3209586656290754560",
  "reqId": "tbS4BRemoveTp0001",
  "newTpTriggerPx": "0",
  "newTpOrdPx": "0",
  "cxlOnFail": false
}
```

После этого в `order-algo` ты видел, что `tp*` поля пустые, а `ordType` мог стать `conditional`.

---

### B-06 — Закрытие позиции
- либо сработает TP/SL автоматически (OCO)
- либо закрываешь руками (см. A-05)

### B-07 — Cleanup (рекомендуется всегда)
Снять algo через `cancel-algos` (см. A-06).

---

## Вариант C — Частичные тейки + trailing на остаток

Здесь 2 подварианта:

- **C1 (рекомендуется для бота):** partial TP — обычные limit reduceOnly ордера, SL — один общий algo (conditional/oco) на остаток.
- **C2 (если принципиально нужно, чтобы UI показывал TP внутри позиции):** TP создаём через algo так, чтобы дочерний limit-ордер имел `isTpLimit=true`.

---

### Вариант C1 (рекомендуется) — partial TP = limit reduceOnly, SL = один общий

#### C1-01 — Контроль позиции
**GET**
```
https://www.okx.com/api/v5/account/positions?instId=ETH-USDT-SWAP
```

#### C1-02 — Создать общий SL-only (conditional) на весь объём
(см. A-02)

#### C1-03 — Поставить TP1 (limit reduceOnly)
**POST**
```
https://www.okx.com/api/v5/trade/order
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "limit",
  "px": "3200",
  "sz": "0.25",
  "reduceOnly": "true",
  "clOrdId": "tbS4CTP1L0001"
}
```

#### C1-04 — Поставить TP2 (limit reduceOnly)
**POST**
```
https://www.okx.com/api/v5/trade/order
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "limit",
  "px": "3250",
  "sz": "0.25",
  "reduceOnly": "true",
  "clOrdId": "tbS4CTP2L0001"
}
```

#### C1-05 — Контроль лимиток TP
**GET**
```
https://www.okx.com/api/v5/trade/orders-pending?instId=ETH-USDT-SWAP
```

> Замечание из твоего теста: такие лимитки могут не отображаться в позиции как TP, и у них `isTpLimit` может быть `false`. Для бота это не проблема.

#### C1-06 — Когда TP1/TP2 исполнился: синхронизировать SL под остаток
Практически надёжно:
- либо отменить старый SL algo и создать новый со `sz` равным оставшейся позиции
- либо (если OKX позволяет) применить `amend-algos` с новым `sz` (если параметр доступен в твоей версии API)

#### C1-07 — Trailing SL по n% (как A-04)
`POST /trade/amend-algos` — двигаем `newSlTriggerPx`.

#### C1-08 — Закрыть остаток + cleanup
- закрыть остаток market reduceOnly (A-05)
- отменить SL algo (A-06)
- отменить оставшиеся TP limit ордера (если ещё live): `POST /trade/cancel-order`

---

### Вариант C2 — “как в UI”: TP видны внутри позиции (через algo → дочерняя лимитка `isTpLimit=true`)

Ты проверил: если создать через algo:
- `POST /trade/order-algo` с `tpOrdKind=limit` и `tpOrdPx=...`
то создаётся дочерний лимит-ордер, где в `GET /trade/order?...` видно `isTpLimit=true`.

#### C2-01 — TP1 через algo (создаст linked limit ордер)
**POST**
```
https://www.okx.com/api/v5/trade/order-algo
```
Body (пример из твоих тестов):
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "oco",
  "sz": "0.25",
  "algoClOrdId": "tbTPC2L0001",
  "tpOrdKind": "limit",
  "tpOrdPx": "3200",
  "slTriggerPx": "2500",
  "slTriggerPxType": "last",
  "slOrdPx": "-1",
  "cxlOnClosePos": true
}
```

#### C2-02 — Контроль: взять дочерний `ordId` и проверить `isTpLimit`
Сначала узнаём linked `ordId` из `order-algo`:
**GET**
```
https://www.okx.com/api/v5/trade/order-algo?algoId=3209787612471238656
```

Затем:
**GET**
```
https://www.okx.com/api/v5/trade/order?instId=ETH-USDT-SWAP&ordId=3209787612471238657
```

Ожидаемо (как у тебя):
- `isTpLimit: "true"`
- `linkedAlgoOrd.algoId` указывает на algo

#### C2-03 — TP2 аналогично (ещё один algo)
(по твоему примеру `tbTPC2L0002` и `px=3250`)

> Важно: этот подход создаёт **дополнительный SL в UI**, потому что это OCO.  
> Если “в позиции” не должен отображаться второй SL, то:
> - либо миримся с этим и ведём “настоящий SL” отдельно
> - либо используем C1 (рекомендуется для бота)

#### C2-04 — Trailing SL на остаток
Практически лучше: держать **один основной SL** (A-02/B-02) и двигать только его.  
TP1/TP2 пусть закрывают частями.

#### C2-05 — Cleanup
Отменить:
- все TP algo (если ещё live)
- основной SL algo
- все pending limit-ордера (если остались)

---

## Вариант D — “пусть биржа сама трейлит” (Trailing stop на стороне OKX)

Идея:
- Открываем позицию
- Ставим `move_order_stop` (trailing) как algo-ордер с `reduceOnly=true`
- Если нужно “ужесточить” trailing: чаще всего делаем **cancel + create**, потому что `amend-algos` не для trailing stop
- При закрытии позиции обязательно делаем **cleanup trailing algo**, иначе он может остаться live

---

### D-01 — Открыть позицию
Пример (LONG, market):
**POST**
```
https://www.okx.com/api/v5/trade/order
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "buy",
  "ordType": "market",
  "sz": "1",
  "clOrdId": "tbDOpen0101"
}
```

---

### D-02 — Контроль позиции (берём `posId`)
**GET**
```
https://www.okx.com/api/v5/account/positions?instId=ETH-USDT-SWAP
```

Берём:
- `posId`
- `pos`
- `avgPx`

---

### D-03 — Создать trailing stop (`move_order_stop`)
Для LONG — закрытие `sell`.

**POST**
```
https://www.okx.com/api/v5/trade/order-algo
```

Body (активен сразу):
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "move_order_stop",
  "sz": "1",
  "reduceOnly": "true",
  "callbackRatio": "0.01",
  "algoClOrdId": "tbS4TS3208956489289703424v1"
}
```

---

### D-04 — Контроль trailing algo (и текущего “следующего SL”)
**GET**
```
https://www.okx.com/api/v5/trade/order-algo?algoId=3212522054205456384
```

Полезные поля:
- `callbackRatio`
- `moveTriggerPx` (по сути текущая “линия” trailing)
- `state`

---

### D-05 — Ужесточить trailing (cancel + create)
> Практика + доки: `amend-algos` не про trailing stop (`move_order_stop`).  
> Поэтому делаем явную замену.

#### D-05a — Отменить trailing algo
**POST**
```
https://www.okx.com/api/v5/trade/cancel-algos
```
Body:
```json
[
  {
    "instId": "ETH-USDT-SWAP",
    "algoId": "3212522054205456384"
  }
]
```

#### D-05b — Создать новый trailing с меньшим callbackRatio
Например 0.008:
**POST**
```
https://www.okx.com/api/v5/trade/order-algo
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "move_order_stop",
  "sz": "1",
  "reduceOnly": "true",
  "callbackRatio": "0.008",
  "algoClOrdId": "tbS4TS3208956489289703424v2"
}
```

---

### D-06 — Закрыть позицию
Как и в A-05: противоположный market reduceOnly.
**POST**
```
https://www.okx.com/api/v5/trade/order
```
Body:
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "sell",
  "ordType": "market",
  "sz": "1",
  "reduceOnly": "true",
  "clOrdId": "tbDClose0101"
}
```

---

### D-07 — Cleanup (обязательно): отменить trailing algo (если он ещё live)
Даже если позиция уже закрыта, trailing мог остаться.

**GET (контроль)**
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=move_order_stop&instId=ETH-USDT-SWAP
```

**POST (отмена)**
```
https://www.okx.com/api/v5/trade/cancel-algos
```
Body:
```json
[
  {
    "instId": "ETH-USDT-SWAP",
    "algoId": "3212356318363308032"
  }
]
```

---

# 4) Recovery после рестарта (для всех вариантов)

## R-01 — Узнать, есть ли позиция
**GET**
```
https://www.okx.com/api/v5/account/positions?instId=ETH-USDT-SWAP
```

- `pos=0` → позиции нет → перейти к cleanup ордеров (R-03, R-04)
- `pos>0` → позиция есть → восстановить защиту (R-02)

## R-02 — Найти активные algo-ордера защиты
По очереди:
- OCO:
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=oco&instId=ETH-USDT-SWAP
```
- conditional:
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=conditional&instId=ETH-USDT-SWAP
```
- trailing:
```
https://www.okx.com/api/v5/trade/orders-algo-pending?ordType=move_order_stop&instId=ETH-USDT-SWAP
```

Сопоставление делаем по:
- `algoClOrdId` (наши префиксы `tbS4...`)
- `instId`

## R-03 — Найти pending обычные лимитки TP
**GET**
```
https://www.okx.com/api/v5/trade/orders-pending?instId=ETH-USDT-SWAP
```

## R-04 — Правило консистентности
- Если позиции нет (`pos=0`), а есть pending algo/limit:
  - отменяем всё (cancel-algos + cancel-order)
- Если позиция есть:
  - убеждаемся, что **ровно один “главный SL”** (A/B/C)
  - если trailing на OKX — убедиться, что он соответствует стратегии (callbackRatio), иначе заменить (cancel+create)
  - лимитки TP должны соответствовать стратегии по ценам/объёмам

---

## 5) Короткое резюме “что в итоге лучше”

- **B (OCO + amend-algos SL)** — базовый “прозрачный” вариант для TP+SL.
- **A (только SL, TP в стратегии)** — если хочешь “выйти по trailing” без жёсткого TP.
- **C1** — лучший для бота (partial TP лимитками + один SL).
- **C2** — только если критично, чтобы UI помечал TP как `isTpLimit=true` внутри позиции.
- **D** — trailing на стороне OKX, но обязательно нужен cleanup, иначе algo может зависнуть после закрытия позиции.

---

## Приложение: эндпоинты, которые использовались в сценарии

- `GET  /api/v5/account/positions?instId=ETH-USDT-SWAP`
- `POST /api/v5/trade/order` (market/limit, reduceOnly)
- `GET  /api/v5/trade/orders-pending?instId=ETH-USDT-SWAP`
- `GET  /api/v5/trade/order?instId=ETH-USDT-SWAP&ordId=<ordId>`
- `POST /api/v5/trade/order-algo` (oco / conditional / move_order_stop)
- `GET  /api/v5/trade/orders-algo-pending?ordType=<...>&instId=ETH-USDT-SWAP`
- `GET  /api/v5/trade/order-algo?algoId=<algoId>`
- `POST /api/v5/trade/amend-algos`
- `POST /api/v5/trade/cancel-algos`
