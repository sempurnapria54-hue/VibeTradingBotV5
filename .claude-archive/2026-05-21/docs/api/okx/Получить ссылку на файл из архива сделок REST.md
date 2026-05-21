## Архив сделок: **получить ссылку** (REST)

**Зачем нужно (простыми словами):**

* Это второй шаг после **POST /trade/fills-archive**.
* Этот запрос сообщает:

    * **готов ли архив**,
    * если готов — даёт **ссылку на файл** (`fileHref`).

---

### Endpoint

`GET /api/v5/trade/fills-archive`

---

### Query параметры

* `year` *(String, required)* — год из 4 цифр, например `2025`.
* `quarter` *(String, required)* — квартал: `Q1 | Q2 | Q3 | Q4` (**регистр важен**).

---

### Доступ / лимиты / аутентификация

* Permission: **Read**
* Rate limit: **10 запросов / 2 секунды**, правило — User ID
* Auth headers (REST private):

    * `OK-ACCESS-KEY`
    * `OK-ACCESS-SIGN`
    * `OK-ACCESS-TIMESTAMP` (ISO строка в UTC)
    * `OK-ACCESS-PASSPHRASE`
    * `Content-Type: application/json`
* Demo trading: добавить `x-simulated-trading: 1` (если demo)

---

## 1) Пример запроса

```bash
curl -X GET 'https://www.okx.com/api/v5/trade/fills-archive?year=2025&quarter=Q3' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo
```

---

## 2) Пример ответа (архив ещё генерируется)

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "year": "2025",
      "quarter": "Q3",
      "state": "ongoing",
      "ts": "1646892328000",
      "fileHref": ""
    }
  ]
}
```

### Что это значит

* `state=ongoing` — файл ещё готовится.
* `fileHref` пустой — скачивать пока нечего.

---

## 3) Пример ответа (архив готов)

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "year": "2025",
      "quarter": "Q3",
      "state": "finished",
      "ts": "1646999999000",
      "fileHref": "https://static.okx.com/.../fills-archive-2025-Q3.csv.gz"
    }
  ]
}
```

### Что это значит

* `state=finished` — файл готов.
* `fileHref` — ссылка на скачивание.

**Важно:**

* `fileHref` чаще всего **временная ссылка** → скачивать лучше сразу.

---

## 4) Описание полей (простыми словами)

### Верхний уровень

* `code` — `"0"`, если успех.
* `msg` — текст ошибки/сообщение.
* `data[]` — результат по этому `year+quarter`.

### data[0]

* `year` — год, который ты запросил.
* `quarter` — квартал, который ты запросил.
* `state` — состояние архива:

    * `ongoing` — ещё генерируется
    * `finished` — готов, можно скачивать
    * `failed` — ошибка генерации (обычно нужно повторить POST)
* `ts` — время (ms Unix), когда биржа обновила состояние / сформировала ссылку.
* `fileHref` — ссылка на файл (если `state=finished`).

---

## 5) Как правильно использовать в боте

1. Ты сделал `POST /trade/fills-archive` (запросил генерацию).
2. Раз в N минут/часов проверяешь `GET /trade/fills-archive`.
3. Как только `state=finished` → скачиваешь файл по `fileHref`.
4. Парсишь файл и сохраняешь fills в БД.

> На практике шаг 2 лучше делать редко (из-за лимитов и потому что генерация долгая).
