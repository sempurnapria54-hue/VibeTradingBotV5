## Получить bill-записи аккаунта (последние 7 дней) (REST)

**Зачем нужно (простыми словами):**

* Это список **денежных движений по торговому аккаунту**, которые меняют баланс.
* Для торгового бота это основной кандидат на источник **полного финансового результата сделки**:
  * realized PnL;
  * комиссии / rebate;
  * funding;
  * прочие биржевые cashflow-события, если они попадают в сделку.
* В отличие от fills, bills показывают именно **изменение денег на аккаунте**, а не только факт исполнения ордера.

> Важно:
>
> * **Fill** = факт исполнения ордера.
> * **Bill** = запись движения денег / баланса.
> * Для финального `Deal.resultProfit` bills могут быть точнее, потому что туда попадают не только trade executions, но и funding.

---

### Endpoint

`GET /api/v5/account/bills`

---

### Query параметры

* `instType` *(опционально)* — тип инструмента: `SPOT | MARGIN | SWAP | FUTURES | OPTION`.
* `ccy` *(опционально)* — валюта bill-записи, например `USDT`.
* `type` *(опционально)* — тип bill-записи. OKX рекомендует смотреть актуальный список через справочник bill types.
  * Практически важно: trading/cashflow-события, которые реально меняют баланс.
* `subType` *(опционально)* — подтип bill-записи.
  * Для funding в OKX используются отдельные подтипы:
    * `173` — funding fee expense;
    * `174` — funding fee income.
* `after` *(опционально)* — пагинация «старее, чем `billId`» (вернёт записи **раньше** указанного `billId`).
* `before` *(опционально)* — пагинация «новее, чем `billId`» (вернёт записи **позже** указанного `billId`).
* `begin` *(опционально)* — фильтр по времени начала (Unix ms).
* `end` *(опционально)* — фильтр по времени конца (Unix ms).
* `limit` *(опционально)* — сколько записей вернуть за раз. Максимум обычно `100`, по умолчанию `100`.

**Ключевой момент:** `after/before` — это якорь по **billId**.

**Для финализации конкретной сделки обычно нужно фильтровать так:**

```text
instType=SWAP
ccy=USDT
begin=<first_deal_fact_time_ms>
end=<final_deal_fact_time_ms>
```

А уже в коде дополнительно отбирать:

```text
instId == Deal.instrument.externalId
subType/type относятся к торговому результату, комиссии, rebate, funding
```

---

### Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **5 requests / second**.
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
curl -X GET 'https://www.okx.com/api/v5/account/bills?instType=SWAP&ccy=USDT&begin=1768992000000&end=1769251200000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

### Пример запроса только по funding

```bash
curl -X GET 'https://www.okx.com/api/v5/account/bills?instType=SWAP&ccy=USDT&subType=173&begin=1768992000000&end=1769251200000&limit=100' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
```

> Для funding income аналогично использовать `subType=174`.

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
      "subType": "173",
      "ts": "1769253600000",

      "ccy": "USDT",
      "balChg": "-0.08",
      "bal": "1035.71",

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
  * Удобен для идемпотентности.
  * Используется как якорь для пагинации через `after/before`.
* `type` — тип bill-записи.
* `subType` — подтип bill-записи.
  * Для funding:
    * `173` — funding fee expense;
    * `174` — funding fee income.
* `ts` — время bill-события (Unix ms).

#### Инструмент и валюта

* `instType` — тип инструмента (`SWAP`, `FUTURES`, `SPOT`, ...).
* `instId` — инструмент, например `ETH-USDT-SWAP`.
* `ccy` — валюта движения баланса, например `USDT`.
* `mgnMode` — режим маржи: `isolated`, `cross`, `cash`.

#### Денежные поля

* `balChg` — изменение баланса по этой bill-записи.
  * Это главный кандидат для `DealCashFlow.amount`.
  * Может быть положительным или отрицательным.
* `bal` — баланс после события.
* `pnl` — profit/loss в рамках события, если применимо.
* `fee` — комиссия / rebate по событию:
  * отрицательное значение — комиссия списана;
  * положительное значение — rebate начислен.

#### Позиция / ордер

* `ordId` — ID ордера, если bill связан с ордером.
* `sz` — размер, если применимо.
* `posBalChg` — изменение баланса позиции, если применимо.
* `posBal` — баланс позиции после события, если применимо.

#### Переводы / примечания

* `from` — откуда переведены средства, если bill связан с переводом.
* `to` — куда переведены средства, если bill связан с переводом.
* `notes` — текстовое примечание / описание.

---

## Как правильно делать пагинацию

Обычно `data` приходит отсортированным «свежие сверху».

**Чтобы идти в прошлое:**

1. Сделать запрос без `after`.
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

Для сделки лучше сохранять эти записи в отдельную таблицу, например `DealCashFlow`.

Рекомендуемая логика:

```text
1. Определить окно сделки:
   - begin = время первого подтверждённого entry/execution/cashflow факта;
   - end = время последнего exit/finalization факта.

2. Запросить bills:
   GET /api/v5/account/bills?instType=SWAP&ccy=USDT&begin=...&end=...

3. Отфильтровать:
   - instId == Deal.instrument.externalId;
   - ccy == Deal.resultProfitCurrency;
   - type/subType относятся к PnL / fee / rebate / funding.

4. Сохранить как DealCashFlow.

5. FINALIZE_DEAL_EXIT считает:
   Deal.resultProfit = sum(DealCashFlow.amount)
```

---

## Практическая подсказка для бота

* `GET /api/v5/account/bills` покрывает последние 7 дней.
* Если сделка или recovery-окно старше 7 дней — используй `GET /api/v5/account/bills-archive`.
* Funding для сделки искать здесь же, по `subType=173/174`, инструменту и окну времени сделки.
