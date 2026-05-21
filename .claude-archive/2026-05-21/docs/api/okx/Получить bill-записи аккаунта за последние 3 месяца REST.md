## Получить bill-записи аккаунта (последние 3 месяца) (REST)

**Зачем нужно (простыми словами):**

* Это архив bill-записей аккаунта за **последние ~3 месяца**.
* Нужен, если сделка финализируется после даунтайма / задержки и обычного `GET /api/v5/account/bills` за последние 7 дней уже недостаточно.
* Для торгового бота это источник **денежных фактов** для `DealCashFlow`:
  * realized PnL;
  * комиссии / rebate;
  * funding;
  * прочие cashflow-события, если они относятся к сделке.

> Важно:
>
> * Это не fills и не orders.
> * Это записи изменения баланса аккаунта.
> * Для полного `Deal.resultProfit` такие записи могут быть основным источником истины.

---

### Endpoint

`GET /api/v5/account/bills-archive`

---

### Query параметры

* `instType` *(опционально)* — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `ccy` *(опционально)* — валюта bill-записи, например `USDT`.
* `type` *(опционально)* — тип bill-записи. Актуальный список типов лучше брать из OKX bill types.
* `subType` *(опционально)* — подтип bill-записи.
  * Для funding:
    * `173` — funding fee expense;
    * `174` — funding fee income.
* `after` *(опционально)* — пагинация «старее, чем `billId`» (вернёт записи **раньше** указанного `billId`).
* `before` *(опционально)* — пагинация «новее, чем `billId`» (вернёт записи **позже** указанного `billId`).
* `begin` *(опционально)* — фильтр по времени начала (Unix ms).
* `end` *(опционально)* — фильтр по времени конца (Unix ms).
* `limit` *(опционально)* — сколько записей вернуть за раз. Максимум обычно `100`, по умолчанию `100`.

**Ключевой момент:** `after/before` — это якорь по **billId**.

---

### Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **5 requests / 2 seconds**.
* Rate limit rule: **User ID**.
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
curl -X GET 'https://www.okx.com/api/v5/account/bills-archive?instType=SWAP&ccy=USDT&begin=1761350400000&end=1769251200000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

### Пример запроса только по funding за окно сделки

```bash
curl -X GET 'https://www.okx.com/api/v5/account/bills-archive?instType=SWAP&ccy=USDT&subType=174&begin=1761350400000&end=1769251200000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
```

---

### Пример ответа

> Пример ниже — иллюстративный. Все числа у OKX обычно приходят строками.

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SWAP",
      "billId": "600001234567890123",
      "type": "2",
      "subType": "1",
      "ts": "1769253500123",

      "ccy": "USDT",
      "balChg": "12.34",
      "bal": "1035.79",

      "posBalChg": "",
      "posBal": "",

      "sz": "1",
      "pnl": "12.56",
      "fee": "-0.22",

      "mgnMode": "isolated",
      "instId": "ETH-USDT-SWAP",
      "ordId": "3209990000000000000",

      "from": "",
      "to": "",
      "notes": ""
    },
    {
      "instType": "SWAP",
      "billId": "600001234567890124",
      "type": "2",
      "subType": "174",
      "ts": "1769253600000",

      "ccy": "USDT",
      "balChg": "0.08",
      "bal": "1035.87",

      "posBalChg": "",
      "posBal": "",

      "sz": "",
      "pnl": "0",
      "fee": "0",

      "mgnMode": "isolated",
      "instId": "ETH-USDT-SWAP",
      "ordId": "",

      "from": "",
      "to": "",
      "notes": "funding fee"
    }
  ]
}
```

---

## Описание полей ответа (простыми словами)

### Верхний уровень

* `code` — `"0"`, если успех.
* `msg` — текст ошибки/сообщение.
* `data[]` — список bill-записей.

### Один bill (data[i])

#### Идентификация

* `billId` — ID bill-записи на стороне OKX.
  * Используется для идемпотентности.
  * Используется как якорь пагинации через `after/before`.
* `type` — тип bill-записи.
* `subType` — подтип bill-записи.
  * `173` — funding fee expense.
  * `174` — funding fee income.
* `ts` — время bill-события (Unix ms).

#### Инструмент и валюта

* `instType` — тип инструмента (`SWAP`, `FUTURES`, `SPOT`, ...).
* `instId` — инструмент, например `ETH-USDT-SWAP`.
* `ccy` — валюта движения баланса, например `USDT`.
* `mgnMode` — режим маржи: `isolated`, `cross`, `cash`.

#### Денежные поля

* `balChg` — изменение баланса.
  * Главный кандидат для `DealCashFlow.amount`.
  * Положительное значение увеличивает результат, отрицательное уменьшает.
* `bal` — баланс после события.
* `pnl` — profit/loss в рамках события, если применимо.
* `fee` — комиссия / rebate:
  * отрицательное значение — комиссия списана;
  * положительное значение — rebate начислен.

#### Позиция / ордер

* `ordId` — ID ордера, если bill связан с ордером.
* `sz` — размер, если применимо.
* `posBalChg` — изменение баланса позиции, если применимо.
* `posBal` — баланс позиции после события, если применимо.

#### Переводы / примечания

* `from` — откуда переведены средства, если применимо.
* `to` — куда переведены средства, если применимо.
* `notes` — текстовое примечание / описание.

---

## Как правильно делать пагинацию

Обычно `data` приходит отсортированным «свежие сверху».

**Чтобы идти в прошлое:**

1. Запрос без `after`.
2. Взять минимальный `billId` из ответа.
3. Следующий запрос выполнить с `after=<minBillId>`.
4. Повторять, пока `data` не станет пустым.

Если одновременно используешь `begin/end` и `after/before`, логика такая:

```text
сначала биржа фильтрует по begin/end,
потом применяет пагинацию по after/before.
```

---

## Как использовать для финализации Deal

Если обычного 7-дневного endpoint уже недостаточно:

```text
1. Определить окно сделки.
2. Запросить bills-archive по instType + ccy + begin/end.
3. Отфильтровать записи по instId сделки.
4. Оставить только финансовые subType/type, которые должны участвовать в profit.
5. Сохранить их как DealCashFlow.
6. FINALIZE_DEAL_EXIT считает Deal.resultProfit = sum(DealCashFlow.amount).
```

---

## Практическая подсказка для бота

* `GET /api/v5/account/bills-archive` покрывает последние 3 месяца.
* Для сделок старше 3 месяцев нужен отдельный deep archive flow, если будет нужен.
* Funding за сделку ищется здесь же по `subType=173/174`, `instId` и окну времени сделки.
