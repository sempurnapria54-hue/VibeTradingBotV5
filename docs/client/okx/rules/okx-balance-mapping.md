# OKX balance mapping

## На какой вопрос отвечает этот файл

Как данные OKX balance response попадают в доменную модель
`BalanceContainer` / `Balance` и какие поля валидируются.

## Контекст

Exchange-specific mapping для OKX. Доменная семантика, freshness,
участие в FSM/RiskValidator — в `docs/models/core/BalanceContainer.md`,
эта дока её не заменяет. Поля raw response — в
`docs/client/okx/models/OkxBalanceResponse.md`.

Приоритет источников при противоречии: подтверждённые решения по
Balance → OKX endpoint-доки → доменная модель → общие доки → код →
legacy.

## Endpoint

```text
GET /api/v5/account/balanceExternalSnapshot?ccy={settleCurrency}
```

Для текущего `ETH-USDT-SWAP`: `?ccy=USDT`. Назначение — account-level
snapshot баланса + currency-level details по settle currency. Путь
подтверждён архивной API-докой
`.claude-archive/2026-05-21/docs/api/okx/Получить баланс REST.md`
(заголовок эндпоинта + curl-пример).

- Query: `ccy` — опционально, одна валюта или список до 20 через
  запятую. Для runtime бота передаётся settle currency инструмента
  (SWAP/USDT risk и sizing требуют обязательную `USDT`-запись).
- Доступ: Permission `Read`; rate limit 10 req / 2 s по User ID.
- Private REST headers: `OK-ACCESS-KEY`, `OK-ACCESS-SIGN`,
  `OK-ACCESS-TIMESTAMP`, `OK-ACCESS-PASSPHRASE`,
  `Content-Type: application/json`. Demo trading:
  `x-simulated-trading: 1`.

## Mapping-flow

```text
OKX REST response -> raw OKX DTO -> OkxClientService validation
  -> BalanceContainerMapper -> BalanceContainerExternalSnapshot
  -> RefreshBalanceExecutor -> BalanceContainer / Balance
```

Raw OKX DTO не выходит за пределы `ClientService` / adapter-layer
(см. `docs/rules/raw-exchange-dto-boundary.md`); `RefreshBalanceExecutor`
работает только с validated normalized snapshot.

## Валидация в ClientService (до маппинга)

**Structural:** `response != null`; `code == "0"`; `data != null`;
ровно один account snapshot; `data[0] != null`; `data[0].details`
не null и не пустой. Пустой `data` или неожиданное число snapshots —
controlled external/account error.

**Account-level required:** `uTime` (epoch millis), `totalEq`,
`adjEq`, `availEq` (decimal) заполнены и парсятся. Пустые строки
недопустимы.

**Currency-level required** (для обязательной settle currency,
например `USDT`): `details` содержит запись `ccy == settleCurrency`;
`uTime`, `eq`, `cashBal`, `availBal`, `frozenBal` заполнены и парсятся.
Отсутствие settle currency — controlled external/account error.

**Numeric:** числа приходят строками; обязательные парсятся в
`BigDecimal`; пустая строка в обязательном поле недопустима;
отрицательные equity/cash/available/frozen запрещены, если не
разрешены явной OKX policy.

**Project policy:** нет borrow/debt признаков (если borrow запрещён);
нет активных liability (торгуем только собственными средствами); нет
account-режима, конфликтующего с isolated-only policy; settle currency
соответствует инструменту. Validation-only поля используются только
внутри `ClientService`, в snapshot не попадают.

## Mapping в snapshot

**Account-level → `BalanceContainerExternalSnapshot`:**

| OKX field | Snapshot field |
|---|---|
| internal exchange/account source | `exchangeId` |
| `data[0].uTime` | `externalUpdatedAt` (epoch millis → `OffsetDateTime`) |
| `data[0].totalEq` | `externalTotalEquity` |
| `data[0].adjEq` | `externalAdjustedEquity` |
| `data[0].availEq` | `externalAvailableEquity` |
| `data[0].details` | `balances` (список `BalanceExternalSnapshot`) |

**Currency-level → `BalanceExternalSnapshot`:**

| OKX field | Snapshot field |
|---|---|
| `details[*].ccy` | `externalCurrency` |
| `details[*].uTime` | `externalUpdatedAt` |
| `details[*].eq` | `externalEquity` |
| `details[*].cashBal` | `externalCashBalance` |
| `details[*].availBal` | `externalAvailableBalance` |
| `details[*].frozenBal` | `externalFrozenBalance` |

Числовые поля в snapshot остаются строками, но уже провалидированы
как parseable decimal. Список не маппимых полей — в
`docs/client/okx/models/OkxBalanceResponse.md`.

## Обновление домена

`RefreshBalanceExecutor` обновляет account-level поля контейнера и
полностью заменяет список `Balance` (replace semantics: новый valid
snapshot полностью заменяет старый список currency balances; см.
`BalanceContainer.replaceBalances`). Строковые числовые поля парсятся
в `BigDecimal` при записи в домен.

## Error policy

- **Temporary API problem** (timeout, connection reset, 5xx, gateway
  недоступен): `REFRESH_BALANCE` retry; risk-creating action не
  выполняется; Deal остаётся в текущем статусе, если нет другой
  опасной аномалии.
- **Invalid response / account invariant violation** (`code != "0"`,
  пустой/множественный `data`, нет settleCurrency, пустые
  обязательные поля, числа не парсятся, borrow/debt признаки,
  inconsistent response): controlled external/account error;
  risk-creating action не выполняется; `RiskValidator` при
  absent/stale/invalid возвращает `BLOCKED`; для active Deal возможен
  переход `Deal -> ERROR` по FSM policy; для account-level safety
  problem возможен `Exchange HOLD`.
- **Normal null contract не используется:** успешный
  `getBalance(...)` обязан вернуть валидный snapshot с settleCurrency;
  empty/missing/invalid → exception / controlled error.

Дополнительные OKX-поля добавляются точечно (raw DTO → validation →
normalized snapshot → domain) только если реально нужны runtime-домену.
