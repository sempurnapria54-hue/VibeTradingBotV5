# OKX Balance mapping

> Статус документа: exchange-specific mapping-дока для `BalanceContainer` / `Balance` на OKX.
>
> Документ описывает, как OKX balance endpoint, raw response DTO и normalized external snapshot превращаются в доменную модель `BalanceContainer` / `Balance`.
>
> Документ не заменяет `Balance.md`. Доменная модель, runtime-семантика, freshness-policy и участие в FSM/RiskValidator описаны в `Balance.md`.

---

# 1. Назначение

Эта дока отвечает на вопрос:

```text
как данные OKX balance response попадают в доменную модель BalanceContainer / Balance
и какие поля OKX request/response используются client-layer / mapper-layer.
```

Документ нужен для:

* `OkxClientService`;
* `OkxRestClient`;
* OKX response DTO;
* `BalanceContainerMapper`;
* `BalanceContainerExternalSnapshot`;
* `BalanceExternalSnapshot`;
* `RefreshBalanceExecutor`;
* risk-layer, который использует уже доменный `BalanceContainer`.

---

# 2. Приоритет источников

Если источники противоречат друг другу, использовать такой приоритет:

```text
1. Текущие явно подтверждённые решения по Balance / BalanceContainer.
2. OKX endpoint-доки.
3. Актуальная доменная модель Balance.md.
4. Общие проектные документы:
   - Статусы торговых сущностей.md;
   - Сервисные команды.md;
   - FSM этапы сделки.md;
   - Жизненный цикл сделки.md;
   - Оценка рисков.md;
   - Калькуляторы действий стратегии.md.
5. Java-классы текущей реализации.
6. Legacy Balance-доки.
```

OKX endpoint-доки являются источником истины по:

* endpoint;
* request fields;
* response fields;
* auth;
* rate limits;
* особенностям OKX.

Доменная модель проекта определяет:

* что хранить в `BalanceContainer`;
* что хранить в `Balance`;
* что считать validation-only;
* как работает `REFRESH_BALANCE`;
* как snapshot используется в FSM и risk-layer.

---

# 3. Границы ответственности

## 3.1. Что описывает эта дока

Эта дока описывает:

* какой OKX endpoint используется для `REFRESH_BALANCE`;
* какой query используется для settle currency;
* какие поля OKX response валидируются;
* какие поля OKX response маппятся в normalized external snapshot;
* какие поля OKX response не выходят за пределы adapter-layer;
* как `BalanceContainerExternalSnapshot` обновляет доменную модель;
* какие ошибки считаются controlled external/account error.

## 3.2. Что не описывает эта дока

Эта дока не описывает подробно:

* доменную семантику `BalanceContainer` — см. `Balance.md`;
* lifecycle `Deal` — см. `Жизненный цикл сделки.md`;
* FSM handlers — см. `FSM этапы сделки.md`;
* command-layer — см. `Сервисные команды.md`;
* risk-layer — см. `Оценка рисков.md`;
* аудит / историю баланса.

---

# 4. OKX endpoint

## 4.1. Получить баланс

```text
GET /api/v5/account/balanceExternalSnapshot
```

Используется в `REFRESH_BALANCE`.

Основной запрос для проекта:

```text
GET /api/v5/account/balanceExternalSnapshot?ccy={settleCurrency}
```

Для текущего `ETH-USDT-SWAP`:

```text
GET /api/v5/account/balanceExternalSnapshot?ccy=USDT
```

Назначение:

```text
получить account-level snapshot баланса и currency-level details по settle currency.
```

## 4.2. Query parameters

```text
ccy — опционально, одна валюта или список до 20 валют через запятую.
```

Для runtime торгового бота рекомендуется передавать settle currency инструмента.

Причина:

```text
для SWAP/USDT risk и sizing требуют обязательную USDT / settleCurrency запись.
```

## 4.3. Доступ / лимиты / auth

```text
Permission: Read
Rate limit: 10 requests / 2 seconds
Rate limit rule: User ID
```

Private REST headers:

```text
OK-ACCESS-KEY
OK-ACCESS-SIGN
OK-ACCESS-TIMESTAMP
OK-ACCESS-PASSPHRASE
Content-Type: application/json
```

Для demo trading:

```text
x-simulated-trading: 1
```

---

# 5. Основной mapping-flow

Целевая цепочка:

```text
OKX REST response
  -> raw OKX DTO
  -> OkxClientService validation
  -> BalanceContainerMapper
  -> BalanceContainerExternalSnapshot
  -> RefreshBalanceExecutor
  -> BalanceContainer / Balance
```

Главное правило:

```text
Raw OKX DTO не выходит за пределы ClientService / adapter-layer.
```

`RefreshBalanceExecutor` работает только с validated normalized snapshot.

---

# 6. Raw OKX response

Упрощённая структура response:

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "uTime": "1769253296789",
      "totalEq": "1023.45",
      "adjEq": "1023.45",
      "availEq": "1023.45",
      "details": [
        {
          "ccy": "USDT",
          "uTime": "1769253296789",
          "eq": "1023.45",
          "cashBal": "1023.45",
          "availBal": "900.00",
          "frozenBal": "123.45"
        }
      ]
    }
  ]
}
```

OKX response может содержать намного больше полей.

Не все эти поля должны попадать в домен или normalized snapshot.

---

# 7. ClientService validation

`OkxClientService` валидирует raw response до маппинга в `BalanceContainerExternalSnapshot`.

## 7.1. Минимальные structural checks

Проверить:

```text
response != null;
code == "0";
data != null;
data содержит ровно один account snapshot;
data[0] != null;
data[0].details != null;
data[0].details не пустой.
```

Если `data` пустой или содержит неожиданное количество account snapshots, это controlled external/account error.

## 7.2. Account-level required fields

Проверить:

```text
data[0].uTime заполнен и парсится как epoch millis;
data[0].totalEq заполнен и парсится как decimal;
data[0].adjEq заполнен и парсится как decimal;
data[0].availEq заполнен и парсится как decimal.
```

Пустые строки для этих полей не допускаются.

## 7.3. Currency-level required fields

Для обязательной settle currency, например `USDT`, проверить:

```text
details содержит запись ccy == settleCurrency;
details[*].uTime заполнен и парсится как epoch millis;
details[*].eq заполнен и парсится как decimal;
details[*].cashBal заполнен и парсится как decimal;
details[*].availBal заполнен и парсится как decimal;
details[*].frozenBal заполнен и парсится как decimal.
```

Если settle currency отсутствует, это controlled external/account error.

## 7.4. Numeric validation

Правила:

```text
числа OKX приходят строками;
обязательные числовые строки должны парситься в BigDecimal;
пустая строка в обязательном поле недопустима;
пустая строка в validation-only поле трактуется по OKX policy;
отрицательные значения для equity / cash / available / frozen запрещены,
если для конкретного поля и режима это не разрешено явной OKX policy.
```

## 7.5. Project policy validation

Для текущего проекта проверить, что raw response не показывает состояние аккаунта, несовместимое с политикой проекта.

Минимально:

```text
нет borrow/debt признаков, если borrow запрещён;
нет активных liability, если проект торгует только собственными средствами;
нет account-level режима, который конфликтует с isolated-only policy, если это видно из response/config;
обязательная settle currency соответствует инструменту.
```

Validation-only поля используются только внутри `ClientService`.

Они не попадают в `BalanceContainerExternalSnapshot`.

---

# 8. Mapping в `BalanceContainerExternalSnapshot`

## 8.1. Account-level mapping

| OKX field | Snapshot field | Комментарий |
|---|---|---|
| internal exchange/account source | `exchangeId` | Идентификатор биржи / exchange account внутри системы. |
| `data[0].uTime` | `externalUpdatedAt` | epoch millis -> `OffsetDateTime`. |
| `data[0].totalEq` | `externalTotalEquity` | Total equity аккаунта. |
| `data[0].adjEq` | `externalAdjustedEquity` | Adjusted / effective equity. |
| `data[0].availEq` | `externalAvailableEquity` | Account-level available equity. |
| `data[0].details` | `balances` | Маппится в список `BalanceExternalSnapshot`. |

## 8.2. Target model

```java
public class BalanceContainerExternalSnapshot {

    private Long exchangeId;
    private OffsetDateTime externalUpdatedAt;
    private String externalTotalEquity;
    private String externalAdjustedEquity;
    private String externalAvailableEquity;
    private List<BalanceExternalSnapshot> balances;
}
```

Числовые поля остаются строками в normalized external snapshot, но к моменту создания snapshot они уже должны быть провалидированы как parseable decimal.

---

# 9. Mapping в `BalanceExternalSnapshot`

## 9.1. Currency-level mapping

| OKX field | Snapshot field | Комментарий |
|---|---|---|
| `details[*].ccy` | `externalCurrency` | Валюта, например `USDT`. |
| `details[*].uTime` | `externalUpdatedAt` | epoch millis -> `OffsetDateTime`. |
| `details[*].eq` | `externalEquity` | Equity по валюте. |
| `details[*].cashBal` | `externalCashBalance` | Cash balance по валюте. |
| `details[*].availBal` | `externalAvailableBalance` | Available balance по валюте. |
| `details[*].frozenBal` | `externalFrozenBalance` | Frozen balance по валюте. |

## 9.2. Target model

```java
public class BalanceExternalSnapshot {

    private String externalCurrency;
    private OffsetDateTime externalUpdatedAt;
    private String externalEquity;
    private String externalCashBalance;
    private String externalAvailableBalance;
    private String externalFrozenBalance;
}
```

---

# 10. Что не маппим в normalized snapshot

Не маппим в `BalanceContainerExternalSnapshot` / `BalanceExternalSnapshot`:

```text
isoEq;
ordFroz;
imr;
mmr;
borrowFroz;
mgnRatio;
notionalUsd;
notionalUsdForBorrow;
notionalUsdForSwap;
notionalUsdForFutures;
notionalUsdForOption;
upl;
delta;
deltaLever;
deltaNeutralStatus;
liab;
uplLiab;
crossLiab;
isoLiab;
interest;
twap;
frpType;
maxLoan;
eqUsd;
borrowFroz;
notionalLever;
stgyEq;
isoUpl;
spotInUseAmt;
clSpotInUseAmt;
maxSpotInUse;
spotIsoBal;
smtSyncEq;
spotCopyTradingEq;
spotBal;
openAvgPx;
accAvgPx;
spotUpl;
spotUplRatio;
totalPnl;
totalPnlRatio;
colRes;
colBorrAutoConversion;
collateralRestrict;
collateralEnabled;
autoLendStatus;
autoLendMtAmt;
rewardBal;
raw response;
requestCurrencies.
```

Причина:

```text
normalized external snapshot содержит только поля,
которые обновляют доменную модель.
```

Если поле нужно только для validation / diagnostics, оно остаётся внутри raw OKX DTO и adapter-layer.

---

# 11. Обновление доменной модели

`RefreshBalanceExecutor` получает `BalanceContainerExternalSnapshot` и обновляет домен.

## 11.1. Account-level update

```text
BalanceContainerExternalSnapshot.exchangeId
  -> BalanceContainer.exchangeId

BalanceContainerExternalSnapshot.externalUpdatedAt
  -> BalanceContainer.externalUpdatedAt

BalanceContainerExternalSnapshot.externalTotalEquity
  -> parse BigDecimal
  -> BalanceContainer.externalTotalEquity

BalanceContainerExternalSnapshot.externalAdjustedEquity
  -> parse BigDecimal
  -> BalanceContainer.externalAdjustedEquity

BalanceContainerExternalSnapshot.externalAvailableEquity
  -> parse BigDecimal
  -> BalanceContainer.externalAvailableEquity
```

## 11.2. Currency-level replace

```text
BalanceContainerExternalSnapshot.balances[*]
  -> map to Balance
  -> set balanceContainerId
  -> replace BalanceContainer.balances
```

Refresh uses replace semantics:

```text
новый valid OKX snapshot полностью заменяет старый список currency balances.
```

---

# 12. Error policy

## 12.1. Temporary API problem

Примеры:

```text
timeout;
connection reset;
5xx;
gateway/API временно недоступен.
```

Реакция:

```text
REFRESH_BALANCE retry;
risk-creating action не выполняется;
Deal остаётся в текущем статусе, если нет другой опасной аномалии.
```

## 12.2. Invalid response / account invariant violation

Примеры:

```text
code != "0";
data пустой;
data содержит несколько account snapshots;
нет settleCurrency;
обязательные поля пустые;
числа не парсятся;
обнаружены borrow/debt признаки;
response inconsistent.
```

Реакция:

```text
controlled external/account error;
risk-creating action не выполняется;
RiskValidator при absent/stale/invalid balance возвращает BLOCKED;
для active Deal возможен переход Deal -> ERROR по FSM policy;
для account-level safety problem возможен Exchange HOLD.
```

## 12.3. Normal null contract

Для balance normal `null` contract не используется.

```text
successful ClientService.getBalance(...)
  -> BalanceContainerExternalSnapshot

empty / missing / invalid response
  -> exception / controlled external/account error
```

---

# 13. Runtime usage summary

`BalanceContainer` после refresh используется в:

* `DealContext`;
* `CalculationContext`, если нужен для sizing;
* `RiskValidator`;
* FSM precondition checks;
* safety-flow / emergency finalization;
* account snapshot after exit.

Не используется для:

* расчёта `Deal.resultProfit`;
* active/closed semantics;
* cleanup order/algo/position flow;
* хранения raw OKX response;
* истории изменения баланса.

---

# 14. Open questions

На текущем этапе открытых вопросов по OKX Balance mapping нет.

Если позже `RiskValidator` или safety-flow потребуют дополнительные OKX-поля, они добавляются точечно:

```text
raw DTO -> validation -> normalized snapshot -> domain
```

Только если поле реально нужно runtime-домену.
