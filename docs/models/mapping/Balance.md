# Balance — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменные `BalanceContainer` / `Balance` ложатся на нативные модели
источников, нормализуются через `BalanceContainerExternalSnapshot` /
`BalanceExternalSnapshot`, какие поля валидируются.

## Контекст

Mapping-слой для `BalanceContainer`/`Balance`. Доменная модель —
`docs/models/domain/core/BalanceContainer.md`. Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`,
`docs/rules/market-data-freshness.md`. Контракт endpoint'а —
`docs/integrations/<name>/contracts/balance.md`.

Текущие источники: **OKX**.

Приоритет источников при противоречии: подтверждённые решения по
Balance → endpoint-доки источника → доменная модель → общие доки →
код → legacy.

## Source-agnostic ядро

### Mapping-flow

```text
source REST response -> raw DTO -> IntegrationService validation
  -> BalanceContainerMapper -> BalanceContainerExternalSnapshot
  -> RefreshBalanceExecutor -> BalanceContainer / Balance
```

Raw DTO не выходит за пределы `IntegrationService` / adapter-layer
(`docs/rules/raw-exchange-dto-boundary.md`); `RefreshBalanceExecutor`
работает только с validated normalized snapshot.

### Account-level → `BalanceContainerExternalSnapshot`

| Snapshot field | Семантика |
|---|---|
| `exchangeId` | внутренний идентификатор источника |
| `externalUpdatedAt` | время обновления account snapshot |
| `externalTotalEquity` | total equity аккаунта |
| `externalAdjustedEquity` | adjusted / effective equity |
| `externalAvailableEquity` | account-level available equity |
| `balances[]` | список currency-level `BalanceExternalSnapshot` |

### Currency-level → `BalanceExternalSnapshot`

| Snapshot field | Семантика |
|---|---|
| `externalCurrency` | валюта (`USDT`) |
| `externalUpdatedAt` | время обновления currency snapshot |
| `externalEquity` | equity по валюте |
| `externalCashBalance` | cash balance |
| `externalAvailableBalance` | available balance |
| `externalFrozenBalance` | frozen balance |

### Обновление домена

`RefreshBalanceExecutor` обновляет account-level поля контейнера и
полностью заменяет список `Balance` (replace semantics — новый valid
snapshot полностью заменяет старый список currency balances; см.
`BalanceContainer.replaceBalances`). Строковые числовые поля парсятся
в `BigDecimal` при записи в домен.

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **Structural:** `response != null`; `data != null`; ровно один
  account snapshot; `data[0] != null`; `data[0].details` не null и не
  пустой. Пустой `data` или неожиданное число snapshots — controlled
  external/account error.
- **Account-level required:** `externalUpdatedAt`, `totalEq`,
  `adjEq`, `availEq` (decimal) заполнены и парсятся.
- **Currency-level required** (для обязательной settle currency,
  например `USDT`): `details` содержит запись `ccy == settleCurrency`;
  все обязательные поля currency заполнены и парсятся. Отсутствие
  settle currency — controlled external/account error.
- **Numeric:** числа приходят строками; обязательные парсятся в
  `BigDecimal`; пустая строка в обязательном поле недопустима;
  отрицательные equity/cash/available/frozen запрещены, если не
  разрешены явной policy.
- **Project policy:** нет borrow/debt признаков (если borrow
  запрещён); нет активных liability (торгуем только собственными
  средствами); нет account-режима, конфликтующего с isolated-only
  policy; settle currency соответствует инструменту. Validation-only
  поля используются только внутри `IntegrationService`, в snapshot не
  попадают.

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx, gateway
  недоступен): `REFRESH_BALANCE_COMMAND` retry; risk-creating action не
  выполняется; `Deal` остаётся в текущем статусе, если нет другой
  опасной аномалии.
- **Invalid response / account invariant violation** (`code != "0"`,
  пустой/множественный `data`, нет settleCurrency, пустые
  обязательные поля, числа не парсятся, borrow/debt признаки,
  inconsistent response): controlled external/account error;
  risk-creating action не выполняется; `RiskValidator` при
  absent/stale/invalid возвращает `BLOCKED`; для active Deal возможен
  переход `Deal → ERROR` по FSM policy; для account-level safety
  problem возможен `Exchange.TRADE_BLOCKED` (ступень 2).
- **Normal null contract не используется:** успешный refresh обязан
  вернуть валидный snapshot с settleCurrency; empty/missing/invalid
  → exception / controlled error.

## OKX

### `OkxBalanceResponse` → snapshot

См. инвентарь — `docs/models/integrations/okx/OkxBalanceResponse.md`.

**Account-level → `BalanceContainerExternalSnapshot`:**

| OKX field | Snapshot field |
|---|---|
| `data[0].uTime` | `externalUpdatedAt` (epoch millis → `OffsetDateTime`) |
| `data[0].totalEq` | `externalTotalEquity` |
| `data[0].adjEq` | `externalAdjustedEquity` |
| `data[0].availEq` | `externalAvailableEquity` |
| `data[0].details` | `balances` |

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
`docs/models/integrations/okx/OkxBalanceResponse.md`.

### OKX validation notes

- **Structural:** `code == "0"`.
- **Path note:** правильный путь — `GET /api/v5/account/balance`. В
  старых архивных доках встречалась опечатка
  `balanceExternalSnapshot` — это не реальный endpoint OKX.
- **Query:** `ccy` — опционально, до 20 через запятую. Для runtime
  бота передаётся settle currency (`USDT`).

Дополнительные OKX-поля добавляются точечно (raw DTO → validation →
normalized snapshot → domain) только если реально нужны
runtime-домену.
