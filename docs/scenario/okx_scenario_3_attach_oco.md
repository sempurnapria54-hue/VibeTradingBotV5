# OKX API — Сценарий 3 (AttachAlgoOrds): вход в позицию + биржевой TP/SL (OCO) + апдейт одним запросом

Документ описывает **Сценарий 3** в выбранном варианте: **TP/SL “прикреплённые” к основному ордеру** через `attachAlgoOrds`.

Цель сценария:  
- Открыть позицию (market)  
- Убедиться, что позиция открыта  
- Создать **биржевую защиту TP/SL** (чтобы закрытие сработало даже при обрыве связи)  
- Научиться **обновлять TP и SL одним универсальным запросом**  
- Иметь способ восстановиться после рестарта бота

---

## 0) Ключевые понятия (важно понять до запросов)

### 0.1 Что происходит при `attachAlgoOrds`
Ты отправляешь `POST /trade/order` и в теле добавляешь `attachAlgoOrds` с TP/SL. После **полного исполнения** входного ордера OKX создаёт **отдельный algo-ордер защиты**, в нашем кейсе это оказался **`ordType = oco`** (TP и SL в одном).

### 0.2 Почему не работает `amend-order`
`POST /trade/amend-order` не применим к уже **filled** market-ордеру: биржа вернёт ошибку вида “order has already been filled or canceled”.

### 0.3 Почему `attachAlgoId` != `algoId`
В `GET /trade/order` в блоке `attachAlgoOrds[]` есть поле `attachAlgoId`. Его **нельзя** напрямую использовать как `algoId` для `amend-algos`.  
Правильный путь: взять `attachAlgoClOrdId` и по нему найти реальный `algoId` через `GET /trade/order-algo?algoClOrdId=...`.

---

## 1) Предусловия

- Инструмент: **ETH-USDT-SWAP**
- Режим маржи: **isolated**
- Плечо: до **10x**
- Аккаунт в режиме позиции **net** (в примерах `posSide=net`)
- В Postman настроены подпись и заголовки `OK-ACCESS-*`
- Для demo: `x-simulated-trading: 1`

---

## 2) Ограничения идентификаторов (обязательно)

- `clOrdId`, `attachAlgoClOrdId`, `reqId` должны быть **строго alphanumeric** (буквы/цифры), без дефисов, ≤ 32 символов.  
Иначе будет `51000 Parameter clOrdId error`.

---

## 3) Сценарий 3 — 5 запросов (проверено в Postman)

### Запрос №1 — Создание ордера + прикреплённые TP/SL

**POST**  
`https://www.okx.com/api/v5/trade/order`

**Body (пример)**
```json
{
  "instId": "ETH-USDT-SWAP",
  "tdMode": "isolated",
  "side": "buy",
  "ordType": "market",
  "sz": "1",
  "clOrdId": "tbS3Entry0002",
  "attachAlgoOrds": [
    {
      "attachAlgoClOrdId": "tbS3TPSL0002",
      "tpTriggerPx": "3300",
      "tpTriggerPxType": "last",
      "tpOrdPx": "-1",
      "slTriggerPx": "3000",
      "slTriggerPxType": "last",
      "slOrdPx": "-1"
    }
  ]
}
```

Ожидаемый успех:
- `code = "0"`
- приходит `ordId`

---

### Запрос №2 — Получить детали ордера (filled + увидеть attach блок)

**GET**  
`https://www.okx.com/api/v5/trade/order?instId=ETH-USDT-SWAP&clOrdId=tbS3Entry0002`

Проверяем:
- `state = "filled"`
- `avgPx` заполнен
- В `attachAlgoOrds[]` есть:
  - `attachAlgoClOrdId` (у нас: `tbS3TPSL0002`)
  - `attachAlgoId` (важно: НЕ использовать его как algoId для amend-algos)

---

### Запрос №3 — Найти реальный algoId прикреплённого TP/SL по attachAlgoClOrdId

**GET**  
`https://www.okx.com/api/v5/trade/order-algo?algoClOrdId=tbS3TPSL0002`

В ответе получаем:
- `algoId` (реальный, например `3209210720722571264`)
- `ordType = "oco"`
- `state = "live"`
- `reduceOnly = "true"` (рекомендуется для защиты)

> Этот шаг обязателен, потому что `attachAlgoId` из запроса №2 напрямую для amend-algos не подходит.

---

### Запрос №4 — Универсальный апдейт TP и SL одним запросом (amend-algos)

**POST**  
`https://www.okx.com/api/v5/trade/amend-algos`

**Body (пример: SL 3000→3070, TP 3300→3320)**
```json
{
  "instId": "ETH-USDT-SWAP",
  "algoId": "3209210720722571264",
  "reqId": "tbS3AmendTPSL0001",
  "newSlTriggerPx": "3070",
  "newSlTriggerPxType": "last",
  "newSlOrdPx": "-1",
  "newTpTriggerPx": "3320",
  "newTpTriggerPxType": "last",
  "newTpOrdPx": "-1"
}
```

Ожидаемый успех:
- `code = "0"`
- `sCode = "0"`

**Замечание:**  
Если нужно менять **только SL** или **только TP** — передавай только соответствующие `newSl*` или `newTp*`. Но для “универсального” запроса удобнее обновлять оба.

---

### Запрос №5 — Проверка текущих значений TP/SL (после amend)

**GET**  
`https://www.okx.com/api/v5/trade/order-algo?algoClOrdId=tbS3TPSL0002`

Ожидаем:
- `slTriggerPx = "3070"`
- `tpTriggerPx = "3320"`
- `state = "live"`
- `ordType = "oco"`

---

## 4) Типовые кейсы и поведение (ветвления)

### 4.1 Ошибка `Parameter clOrdId error` (51000)
Причина: `clOrdId` / `attachAlgoClOrdId` / `reqId` содержат дефисы или спецсимволы.  
Действие: генерировать новый alphanumeric id.

### 4.2 `51503 Your order has already been filled or canceled` при `amend-order`
Причина: пытаемся менять уже исполненный market-ордер через `POST /trade/amend-order`.  
Действие: использовать **`POST /trade/amend-algos`** и реальный `algoId` из запроса №3.

### 4.3 `51527 ... attached TP/SL orders does not exist` при `amend-algos`
Причина: в `algoId` ошибочно подставлен `attachAlgoId` из `GET /trade/order`.  
Действие: выполнить запрос №3 и взять настоящий `algoId`.

### 4.4 `orders-algo-pending` возвращает пусто
Это не признак отсутствия защиты. В attach-сценарии правильный способ читать защиту:
- `GET /trade/order` (видим attach блок)
- `GET /trade/order-algo?algoClOrdId=...` (видим реальный OCO)

### 4.5 Частичный fill
Если вход не сразу `filled` (редко для market, но возможно):
- Повторять запрос №2 до `state=filled`
- Только после полного исполнения гарантированно появляется корректная биржевая защита.

### 4.6 Net vs Hedge mode (posSide)
В hedge-mode могут потребоваться поля `posSide` при входе.  
В нашем прогоне `posSide=net`, поэтому `posSide` в запросе №1 не использовали.

---

## 5) Reconcile после рестарта бота (минимальный алгоритм)

1) Найти текущую позицию:
- `GET /api/v5/account/positions?instId=ETH-USDT-SWAP`
2) Если позиция есть, проверить защиту:
- Если у тебя сохранён `attachAlgoClOrdId` → `GET /trade/order-algo?algoClOrdId=...`
- Если не сохранён, но есть `clOrdId` входа → `GET /trade/order?...` и извлечь `attachAlgoClOrdId`
3) Если защиты нет — поставить новую (через отдельный сценарий восстановления, например `order-algo oco`).

---

## 6) Рекомендации по “failsafe TP”

Если TP нужен **только как страховка** (на случай обрыва связи), а не как “рабочий тейк” стратегии:
- Ставь TP дальше (например 4–6R), чтобы он редко мешал нормальной логике, но гарантировал закрытие при отсутствии связи.

---

## 7) Что сохранять в БД

- `clOrdId` входа
- `ordId` входа
- `attachAlgoClOrdId` (ключ для восстановления)
- `algoId` (реальный — можно переоткрыть через запрос №3, но хранить удобно)
- Последние `tpTriggerPx`/`slTriggerPx`
- `avgPx`, `sz`, `side`, `tdMode`, `posSide`

---

## 8) Итог

Сценарий 3 в варианте **attachAlgoOrds** получился удобным:
- защита на бирже создаётся автоматически как **OCO**
- TP+SL можно обновлять **одним запросом** `amend-algos`
- состояние защиты читается через `order-algo?algoClOrdId=...`
