# Balance — mapping между слоями

## На какой вопрос отвечает этот файл

Как баланс переходит между слоями.

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
| `exchangeAccountId` | внутренний ключ строки биржевого счёта у ядра |
| `externalUpdatedAt` | момент, на который источник собрал сведения о счёте; база свежести снимка (`docs/models/domain/core/BalanceContainer.md`) |
| `externalTotalEquity` | total equity аккаунта |
| `externalAdjustedEquity` | adjusted / effective equity |
| `externalAvailableEquity` | account-level available equity |
| `balances[]` | список currency-level `BalanceExternalSnapshot` |

### Currency-level → `BalanceExternalSnapshot`

| Snapshot field | Семантика |
|---|---|
| `externalCurrency` | валюта (`USDT`) |
| `externalUpdatedAt` | время последнего изменения остатка валюты; у счёта без движения средств стоит на месте и базой свежести не служит |
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
  отрицательные available/frozen запрещены. Отрицательные equity и
  cash граница пропускает: это признак обязательства, и читает его
  преконтроль ядра (ниже).
- **Project policy:** нет account-режима, конфликтующего с
  isolated-only policy; settle currency соответствует инструменту.
  Validation-only поля используются только внутри `IntegrationService`,
  в snapshot не попадают.
- **Признаков заёмных средств граница не проверяет:** полей обязательств
  (`liab`, `borrowFroz`, `interest` и соседних) сырой DTO не несёт.
  Правило «только свои средства» (`docs/rules/trading-constraints.md`)
  проверяет преконтроль ядра по той части, которую снимок выражает, —
  отрицательному остатку либо капиталу строки расчётной валюты
  (`docs/components/RiskValidator.md`).

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx, gateway
  недоступен): `REFRESH_BALANCE_COMMAND` retry; risk-creating action не
  выполняется; `Deal` остаётся в текущем статусе, если нет другой
  опасной аномалии.
- **Invalid response / account invariant violation** (`code != "0"`,
  пустой/множественный `data`, нет settleCurrency, пустые
  обязательные поля, числа не парсятся, inconsistent response):
  controlled external/account error; risk-creating action не
  выполняется; свежий снимок без строки расчётной валюты преконтроль
  отвергает (`docs/components/RiskValidator.md`); для active Deal возможен
  переход `Deal → ERROR` по FSM policy; для account-level safety
  problem возможен `ExchangeAccount.safetyRung = TRADE_BLOCKED` (ступень 2).
- **Normal null contract не используется:** успешный refresh обязан
  вернуть валидный snapshot с settleCurrency; empty/missing/invalid
  → exception / controlled error.

## OKX

### `BalanceOkxResponse` → snapshot

См. инвентарь — `docs/models/integrations/okx/BalanceOkxResponse.md`.

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

**Два `uTime` ответа значат разное**, и потому снимок датируется полем
счёта, а не строки валюты: `data[0].uTime` — момент сбора сведений о
счёте, `details[*].uTime` — последнее изменение остатка валюты, у тихого
счёта сколь угодно старое. Семантика и её офдок — инвентарь
`docs/models/integrations/okx/BalanceOkxResponse.md`.

Числовые поля в snapshot остаются строками, но уже провалидированы
как parseable decimal. Список не маппимых полей — в
`docs/models/integrations/okx/BalanceOkxResponse.md`.

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
