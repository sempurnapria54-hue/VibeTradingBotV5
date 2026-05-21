# API стратегии

## Общие правила

* Стратегия создаётся целиком одним запросом и дальше не редактируется. Для изменений создаётся новая стратегия новой версии.
* Внешний идентификатор API — `internalId`, а не DB `id`.
* Статусы стратегии:

    * `CREATED` — создана, но ещё не используется
    * `ACTIVE` — активная стратегия инструмента
    * `INACTIVE` — временно не участвует в резолве
    * `DELETED` — логически удалена
* Активной может быть только одна стратегия на инструмент.
* Физического удаления нет, только логическое.

---

## 1. Создать стратегию

### `POST /api/strategies`

Создаёт новую стратегию целиком вместе с `details`, `stepsByStatus`, `conditions` и `actions`.

### Request body

Полный объект стратегии на создание.

```json
{
  "internalId": "eth-usdt-swap-v3",
  "instrumentId": 101,
  "name": "ETH SWAP Trend/Grid v3",
  "version": 3,
  "status": "CREATED",
  "details": [
    {
      "marketPhaseType": "BULL_TREND",
      "phaseEntryPolicy": "FOLLOW_PHASE",
      "riskPerTradePercent": 1.0,
      "maxLeverage": 10,
      "targetRiskRewardRatio": 3.0,
      "stepsByStatus": {}
    }
  ]
}
```

### Поведение

* если `internalId` уже существует — ошибка;
* если стратегия невалидна по бизнес-правилам — ошибка;
* если стратегия валидна — сохраняется в статусе `CREATED`.

### Response

`201 Created`

```json
{
  "internalId": "eth-usdt-swap-v3",
  "status": "CREATED"
}
```

### Ошибки

* `400 Bad Request` — невалидная модель
* `409 Conflict` — `internalId` уже существует

---

## 2. Получить стратегию

### `GET /api/strategies/{internalId}`

Возвращает стратегию целиком по внешнему идентификатору.

### Path params

* `internalId` — внешний идентификатор стратегии

### Response

`200 OK`

```json
{
  "internalId": "eth-usdt-swap-v3",
  "instrumentId": 101,
  "name": "ETH SWAP Trend/Grid v3",
  "version": 3,
  "status": "ACTIVE",
  "details": []
}
```

### Ошибки

* `404 Not Found` — стратегия не найдена

---

## 3. Активировать стратегию

### `PUT /api/strategies/{internalId}/activate`

Переводит стратегию в `ACTIVE`.

### Поведение

* допустимые исходные статусы: `CREATED`, `INACTIVE`;
* если стратегия уже `ACTIVE` — запрос идемпотентен, возвращаем текущую;
* если по этому инструменту уже есть другая `ACTIVE` стратегия, она переводится в `INACTIVE`;
* `DELETED` стратегию активировать нельзя.

### Request body

Пустой.

### Response

`200 OK`

```json
{
  "internalId": "eth-usdt-swap-v3",
  "status": "ACTIVE"
}
```

### Ошибки

* `404 Not Found` — стратегия не найдена
* `409 Conflict` — стратегия в состоянии, из которого активация запрещена
* `422 Unprocessable Entity` — стратегия не проходит валидацию перед активацией

---

## 4. Деактивировать стратегию

### `PUT /api/strategies/{internalId}/inactivate`

Переводит стратегию в `INACTIVE`.

### Поведение

* допустимые исходные статусы: `ACTIVE`, `CREATED`;
* если стратегия уже `INACTIVE` — запрос идемпотентен;
* `DELETED` стратегию деактивировать нельзя.

### Request body

Пустой.

### Response

`200 OK`

```json
{
  "internalId": "eth-usdt-swap-v3",
  "status": "INACTIVE"
}
```

### Ошибки

* `404 Not Found`
* `409 Conflict`

---

## 5. Логически удалить стратегию

### `PUT /api/strategies/{internalId}/delete`

Переводит стратегию в `DELETED`.

### Поведение

* физически запись не удаляется;
* удаление разрешено только из `CREATED` и `INACTIVE`;
* если стратегия `ACTIVE`, сначала её нужно перевести в `INACTIVE`;
* если стратегия уже `DELETED`, запрос идемпотентен.

### Request body

Пустой.

### Response

`200 OK`

```json
{
  "internalId": "eth-usdt-swap-v3",
  "status": "DELETED"
}
```

### Ошибки

* `404 Not Found`
* `409 Conflict` — попытка удалить `ACTIVE` стратегию напрямую

---

## Рекомендуемые переходы статусов

```text
CREATED  -> ACTIVE
CREATED  -> INACTIVE
CREATED  -> DELETED

ACTIVE   -> INACTIVE

INACTIVE -> ACTIVE
INACTIVE -> DELETED

DELETED  -> terminal
```
