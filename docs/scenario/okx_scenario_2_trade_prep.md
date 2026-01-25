# OKX · Сценарий 2 — Подготовить торговые параметры (isolated + плечо + размер позиции)

Документ описывает **Сценарий 2** для торгового бота под OKX (REST API v5): подготовка параметров торговли **перед** выставлением ордера.

> Контекст (зафиксировано в чате):
> - Инструмент: **ETH-USDT-SWAP**
> - Маржа: **isolated**
> - Плечо: **фиксированное x10 по умолчанию**
> - Риск: **1% депозита на сделку**, контролируется через **sz и SL**
> - У пользователя может быть несколько открытых позиций по разным инструментам, но **не более 1 позиции на 1 инструмент**.

---

## 1. Цель сценария

1) Убедиться, что аккаунт/ключи позволяют выполнять торговые действия (правильные permissions и account mode).  
2) Гарантированно выставить **isolated + x10** для инструмента.  
3) Получить **контрактные параметры** (лотность, минимальный размер, шаг цены, ctVal).  
4) Получить **цену (mark price)** и **баланс** (USDT).  
5) На основе риск-менеджмента посчитать корректный `sz` (в контрактах) и округления.  
6) (Опционально) Проверить верхнюю границу по объёму через `max-size`.

---

## 2. Термины и поля

- `instId`: идентификатор инструмента, например `ETH-USDT-SWAP`.
- `tdMode`: режим маржи на уровне ордера: `isolated` или `cross`.
- `mgnMode`: режим маржи на уровне плеча (в `set-leverage`): `isolated` или `cross`.
- `posMode`:
  - `net_mode` — одна позиция на инструмент (лонг/шорт неттируются).
  - `long_short_mode` — хедж-режим (лонг и шорт раздельно), тогда часто нужен `posSide`.
- `acctLv`: уровень account mode (в контексте чата: `acctLv=1` соответствовал Simple mode и блокировал derivatives-эндпоинты).
- `ctVal`: сколько базового актива в 1 контракте (для SWAP).
- `lotSz`: шаг размера позиции (`sz`).
- `minSz`: минимальный `sz`.
- `tickSz`: шаг цены.
- `markPx`: mark price (рекомендуемый источник цены для деривативов при расчётах и триггерах).
- `sz`: размер позиции в контрактах (то, что уходит в `trade/order`).

---

## 3. Пререквизиты

### 3.1 API Key permissions
Для Сценария 2 нужны как минимум:
- **Read** — чтобы читать account/config/balance.
- **Trade** — чтобы вызывать `set-leverage` и далее (в Сценарии 3) ставить ордера.

**Кейс из чата:** при `perm: "read_only"` торговые запросы невозможны. После обновления ключа стало `perm: "read_only,trade"`.

### 3.2 Demo vs Real
Если используете демо-торговлю, нужен заголовок:
- `x-simulated-trading: 1`

Для реальной торговли:
- `x-simulated-trading: 0`

> В Postman удобнее выставлять это в Pre-request Script (см. ниже).

---

## 4. Подпись запросов (Postman)

### 4.1 Заголовки OKX
Для private запросов:
- `OK-ACCESS-KEY`
- `OK-ACCESS-SIGN`
- `OK-ACCESS-TIMESTAMP` (ISO 8601, UTC)
- `OK-ACCESS-PASSPHRASE`
- `Content-Type: application/json`
- `x-simulated-trading: 0|1`

### 4.2 Pre-request Script (Postman)
Вставьте **локально** (не отправляйте секреты):

```javascript
const apiKey = "PASTE_YOUR_OK_ACCESS_KEY";
const secretKey = "PASTE_YOUR_SECRET_KEY";
const passphrase = "PASTE_YOUR_PASSPHRASE";
const simulatedTrading = "0"; // 1 for demo

const method = pm.request.method.toUpperCase();
const path = pm.request.url.getPath();
const query = pm.request.url.getQueryString();
const requestPath = query ? `${path}?${query}` : path;

let body = "";
if (pm.request.body && pm.request.body.mode === "raw" && pm.request.body.raw) {
  body = pm.request.body.raw;
}

const timestamp = new Date().toISOString();
const prehash = timestamp + method + requestPath + body;

const hash = CryptoJS.HmacSHA256(prehash, secretKey);
const sign = CryptoJS.enc.Base64.stringify(hash);

pm.request.headers.upsert({ key: "Content-Type", value: "application/json" });
pm.request.headers.upsert({ key: "OK-ACCESS-KEY", value: apiKey });
pm.request.headers.upsert({ key: "OK-ACCESS-SIGN", value: sign });
pm.request.headers.upsert({ key: "OK-ACCESS-TIMESTAMP", value: timestamp });
pm.request.headers.upsert({ key: "OK-ACCESS-PASSPHRASE", value: passphrase });
pm.request.headers.upsert({ key: "x-simulated-trading", value: simulatedTrading });
```

---

## 5. Основной поток сценария 2 (happy path)

Ниже — последовательность запросов **в том порядке**, который показал себя рабочим.

### Шаг 1 — Проверка account mode и pos mode
**GET**  
`https://www.okx.com/api/v5/account/config`

**Зачем:**
- Проверить `perm` (есть ли `trade`).
- Проверить `posMode` (`net_mode` vs `long_short_mode`).
- Если derivatives-эндпоинты падают `51010`, этот ответ помогает диагностировать “Simple mode”.

**Пример ответа (из чата):**
- `perm: "read_only,trade"`
- `posMode: "net_mode"`

---

### Шаг 2 — Установка плеча и режима маржи (isolated + x10)
**POST**  
`https://www.okx.com/api/v5/account/set-leverage`

**Body:**
```json
{
  "instId": "ETH-USDT-SWAP",
  "lever": "10",
  "mgnMode": "isolated"
}
```

**Пример ответа (из чата):**
```json
{
  "code": "0",
  "data": [
    {
      "instId": "ETH-USDT-SWAP",
      "lever": "10",
      "mgnMode": "isolated",
      "posSide": ""
    }
  ],
  "msg": ""
}
```

**Зачем отдельный запрос, если `tdMode` есть в ордере?**
- `tdMode` (isolated/cross) действительно задаётся при создании ордера, но **плечо** — это отдельная настройка по инструменту/маржинальному режиму.
- Нам важно обеспечить детерминизм: “бот всегда выставляет x10 перед торговлей”, а не “как было выставлено вручную вчера”.

**Рекомендация:** дергать `set-leverage`:
- при старте приложения,
- либо перед первой торговлей конкретным `instId`,
- и кэшировать результат (не вызывать перед каждой сделкой без необходимости).

---

### Шаг 3 — Параметры контракта/лотности инструмента (для расчёта `sz`)
**GET**  
`https://www.okx.com/api/v5/public/instruments?instType=SWAP&instId=ETH-USDT-SWAP`

**Зачем:** без `ctVal/lotSz/minSz/tickSz` вы не сможете:
- правильно посчитать `sz` в контрактах из риск-менеджмента,
- правильно округлить `sz` и цены (и будете ловить ошибки размера/шага).

**Пример ответа (ключевые поля из чата):**
- `ctType: "linear"`
- `ctVal: "0.1"` и `ctValCcy: "ETH"` → **1 контракт = 0.1 ETH**
- `lotSz: "0.01"`
- `minSz: "0.01"`
- `tickSz: "0.01"`

---

### Шаг 4 — Mark price (источник цены для расчёта)
**GET**  
`https://www.okx.com/api/v5/public/mark-price?instType=SWAP&instId=ETH-USDT-SWAP`

**Пример ответа (из чата):**
- `markPx: "3104.22"`

**Зачем:**
- Используем mark price как стабильный “якорь” цены для sizing и для sanity-check’ов (особенно у деривативов).

---

### Шаг 5 — Баланс USDT (база для riskUSDT = 1%)
**GET**  
`https://www.okx.com/api/v5/account/balance?ccy=USDT`

**Кейс “до пополнения”:**
- `totalEq: "0"`, `details: []` (денег нет — считать riskUSDT бессмысленно)

**Кейс “после пополнения”:**
В `data[0].details[0]`:
- `availBal: "56.983941"`
- `cashBal: "56.983941"`
- `eq: "56.983941"`

**Рекомендация:**
- Для расчёта депозита/риска используйте `eq` (equity).
- Для проверки “хватит ли денег открыть позицию” используйте `availBal`.

---

### Шаг 6 (опционально, но полезно) — Ограничение по максимальному `sz`
**GET**  
`https://www.okx.com/api/v5/account/max-size?instId=ETH-USDT-SWAP&tdMode=isolated&leverage=10`

**Зачем:**
- Узнать верхний предел объёма по текущим ограничениям биржи и режимам.
- Помогает отличить “мы неверно посчитали `sz`” от “биржа не разрешает такой размер”.

**Пример ответа (из чата):**
- `maxBuy: "1.8"`
- `maxSell: "1.82"`

> Важно: этот запрос ранее падал `51010` из-за Simple mode. После переключения account mode в UI стал работать.

---

## 6. Формула расчёта `sz` (контракты) из риск-менеджмента

Исходные данные:
- `riskPct` — процент риска (у нас 1%).
- `depositUSDT` — база депозита (например `eq`).
- `riskUSDT = depositUSDT * riskPct`.
- `entryPx` — цена входа (или приближение).
- `slPx` — цена стопа.
- `ctVal` — стоимость контракта в базовой валюте (у нас 0.1 ETH).

### 6.1 Убыток на 1 контракт при стопе
Для linear SWAP:
- `riskPerContractUSDT = abs(entryPx - slPx) * ctVal`

Для ETH-USDT-SWAP:
- `riskPerContractUSDT = abs(entryPx - slPx) * 0.1`

### 6.2 Сырой размер
- `rawSz = riskUSDT / riskPerContractUSDT`

### 6.3 Округления по спецификации инструмента
- `sz = floor(rawSz / lotSz) * lotSz`
- `sz >= minSz`
- цены (`slPx`, `ordPx`, `triggerPx`) округлять к `tickSz`.

---

## 7. Пример расчёта из реальных чисел чата

Баланс:
- `eq = 56.983941 USDT`

Риск 1%:
- `riskUSDT = 56.983941 * 0.01 = 0.56983941`

Инструмент:
- `ctVal = 0.1`
- `lotSz = 0.01`
- `minSz = 0.01`
- `tickSz = 0.01`

Цена:
- `markPx = 3104.22`

### Пример (иллюстративный): стоп на ~1% от входа
- `entryPx = 3104.22`
- `slPx = 3073.18` (пример)
- `ΔP = 31.04`

Риск на 1 контракт:
- `31.04 * 0.1 = 3.104 USDT`

Сырой размер:
- `rawSz = 0.56983941 / 3.104 = 0.18356`

Округление:
- `sz = 0.18` (по шагу 0.01)

Проверка по max-size:
- `0.18 <= 1.8` (OK)

---

## 8. Критичные кейсы и как их обрабатывать

### Кейс A — API Key только read_only
**Симптом:** `account/config` показывает `perm: "read_only"`.  
**Решение:** создать/обновить ключ с `trade` правами → повторить `account/config`.

---

### Кейс B — Ошибка 51010 на derivatives/account эндпоинтах
**Симптом:** `max-size`/`max-avail-size`/и т.п. возвращают:
```json
{ "code": "51010", "msg": "You can't complete this request under your current account mode." }
```

**Причина (в чате):** аккаунт был в **Simple mode** (acctLv=1), где деривативы/часть эндпоинтов недоступны.

**Решение (рабочий путь из чата):**
- В web UI: **Trade → Futures → Settings (шестерёнка) → Account mode / Trading mode**  
- Переключить на режим, поддерживающий Futures/Swap  
- Повторить `account/config` и затем `max-size`

---

### Кейс C — posMode = long_short_mode (hedge)
**Симптом:** `posMode` не `net_mode`.  
**Что меняется:**
- Во многих запросах (плечо/ордера/закрытие) потребуется `posSide` (`long`/`short`).
- Логика “1 позиция на инструмент” становится “1 позиция на instrument+posSide”.

---

### Кейс D — Баланс пустой (details = [])
**Симптом:** `details: []`, `totalEq = 0`.  
**Решение:** пополнить / проверить, что запрашивается правильная валюта `ccy=USDT`.

---

### Кейс E — Несколько инструментов и позиций (≤ 1 позиция на инструмент)
**Рекомендации:**
- Вести состояние и кэш настроек в разрезе `instId`:
  - leverageConfigured(instId) = true/false
  - instrumentSpec(instId)
- Перед расчетом/входом: проверять, что по `instId` нет позиции (`account/positions` — это уже следующий сценарий/мониторинг).

---

### Кейс F — “SL слишком далеко” для x10
Правило проекта:
- по умолчанию x10
- если SL слишком далеко и есть риск, что ликвидация окажется ближе SL → **понизить плечо (x5/x3) или пропустить сделку**.

Практическая реализация:
- в стратегии/валидаторе сделки: если `abs(entryPx - slPx)/entryPx > threshold`, применить policy:
  - `reduceLeverage` (перевыставить через `set-leverage`)
  - либо `rejectTrade`

> Это не точный расчет ликвидации (он зависит от mmr/имп.волы и т.п.), но как safety-guard — рабочая эвристика.

---

## 9. Чеклист “Сценарий 2 выполнен”

- [ ] `GET /account/config` возвращает `perm` с `trade`
- [ ] account mode позволяет деривативы (нет `51010` на `max-size`)
- [ ] `POST /account/set-leverage` → `code=0`, `isolated`, `lever=10`
- [ ] `GET /public/instruments` получены `ctVal`, `lotSz`, `minSz`, `tickSz`
- [ ] `GET /public/mark-price` получен `markPx`
- [ ] `GET /account/balance?ccy=USDT` получены `eq`/`availBal`
- [ ] `GET /account/max-size` (опционально) работает и даёт потолок
- [ ] `sz` рассчитан и округлён по `lotSz`, цены по `tickSz`

---

## 10. История запросов и примеры из чата (кратко)

1) `GET /api/v5/account/config` → проверка `perm`, `posMode`  
2) `POST /api/v5/account/set-leverage` → isolated + x10  
3) `GET /api/v5/public/instruments?instType=SWAP&instId=ETH-USDT-SWAP` → `ctVal=0.1`, `lotSz=minSz=0.01`, `tickSz=0.01`  
4) `GET /api/v5/public/mark-price?...` → `markPx=3104.22`  
5) `GET /api/v5/account/balance?ccy=USDT` → `eq=56.983941`  
6) `GET /api/v5/account/max-size?...` → `maxBuy=1.8`, `maxSell=1.82`  
7) Ошибка `51010` решена сменой account mode в UI (Trade → Futures → Settings → Account mode)

---
