# Balance — mapping между слоями

## На какой вопрос отвечает этот файл

Как баланс переходит между слоями.

## Source-agnostic ядро

### Mapping-flow

```text
source REST response (средства + конфигурация счёта) -> raw DTO
  -> IntegrationService validation
  -> BalanceContainerMapper -> BalanceContainerExternalSnapshot
  -> RefreshBalanceExecutor -> BalanceContainer / Balance
```

**Снимок собирается из двух ответов источника:** средств и конфигурации
счёта. Режим счёта и режим позиций — посылки контура, которые преконтроль
меряет на том же свежем снимке, что и средства
(`docs/rules/trading-constraints.md`), поэтому читаются они одним вызовом
границы, а не своей операцией.

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
| `externalAccountLevel` | сырой режим счёта источника |
| `externalPositionMode` | сырой режим позиций источника |

**Режимы переводятся в доменный словарь на границе коннектора** — при
сборке контейнера из снапшота: доменные `accountMode` и `positionMode`
(`docs/models/domain/core/BalanceContainer.md`). **Значение вне словаря
источника и пустое значение дают пустоту режима, а не угаданный режим и не
отказ чтения.** Довод: пустой режим в свежем снимке преконтроль читает как
режим вне контура (`docs/spec/risk-limits.json`, величина
`accountModeOutOfContour`), то есть неизвестность запирает действия кодом,
который её и называет; отказ чтения поднял бы безусловную биржевую ступень
по поводу, у которого есть свой адресный код, и заодно лишил бы ядро
снимка средств для прочих проверок. Значение вне словаря граница оставляет
в логе.

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

`RefreshBalanceExecutor` обновляет account-level поля контейнера — включая
режим счёта и режим позиций — и полностью заменяет список `Balance` (replace semantics — новый valid
snapshot полностью заменяет старый список currency balances; см.
`BalanceContainer.replaceBalances`). Строковые числовые поля парсятся
в `BigDecimal` при записи в домен.

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **Structural:** `response != null`; `data != null`; ровно один
  account snapshot; `data[0] != null`; `data[0].details` не null и не
  пустой. Пустой `data` — отказ класса `EXCHANGE_ERROR`: ответа о счёте
  нет вовсе, и повтор осмыслен.
- **Account-level required:** `externalUpdatedAt` и `totalEq` (decimal)
  заполнены и парсятся. `adjEq` и `availEq` парсятся, когда заполнены, а
  пустыми законны: источник ведёт их не во всяком режиме счёта (у OKX —
  ниже, в заметках валидации источника), и риск-контур их не читает
  (`docs/models/domain/core/BalanceContainer.md`).
- **Currency-level required** (для обязательной settle currency,
  например `USDT`): `details` содержит запись `ccy == settleCurrency`;
  обязательные поля строки — капитал, денежный и свободный остаток
  (`eq`, `cashBal`, `availBal`) — заполнены и парсятся: их читает
  преконтроль ядра (`docs/components/RiskValidator.md`); замороженный
  остаток парсится, когда заполнен. Ответ без строки
  settle currency — отказ класса `EXTERNAL_INVARIANT_VIOLATION`:
  площадка ответила успехом, тот же ответ придёт на повтор, и недостача
  обязательного поля есть нарушение инварианта контракта
  (`docs/rules/controlled-exchange-exceptions.md`). Классы отказа
  границы и их повторяемость — `docs/components/IntegrationService.md`.
- **Numeric:** числа приходят строками; обязательные парсятся в
  `BigDecimal`; пустая строка в обязательном поле недопустима;
  отрицательные available/frozen запрещены. Отрицательные equity и
  cash граница пропускает: это признак обязательства, и читает его
  преконтроль ядра (ниже).
- **Project policy:** settle currency соответствует инструменту.
  Validation-only поля используются только внутри `IntegrationService`,
  в snapshot не попадают. **Режима счёта граница не отвергает:** она его
  переводит и везёт в снимке, а вне контура его отвергает преконтроль
  ядра на свежем снимке (`docs/components/RiskValidator.md`).
- **Конфигурация счёта:** ответ несёт ровно одну запись; пустой `data` —
  отказ класса `EXCHANGE_ERROR`, как у самого баланса: сведений о счёте
  нет, и повтор осмыслен.
- **Признаков заёмных средств граница не проверяет:** полей обязательств
  (`liab`, `borrowFroz`, `interest` и соседних) сырой DTO не несёт, и они
  не вводятся. Правило «только свои средства»
  (`docs/rules/trading-constraints.md`) проверяет преконтроль ядра; чем
  держится полнота признака — дом `docs/components/RiskValidator.md`,
  раздел проверок средств счёта.

### Error policy

- **Temporary API problem и ответ без сведений о счёте** (timeout,
  connection reset, 5xx, gateway недоступен; `code != "0"`; пустой
  `data` баланса либо конфигурации счёта): класс `EXCHANGE_ERROR` либо `EXCHANGE_UNREACHABLE` (отказ в
  ключах счёта — своим классом, `docs/components/IntegrationService.md`);
  `REFRESH_BALANCE_COMMAND` retry; risk-creating action не выполняется;
  `Deal` остаётся в текущем статусе, если нет другой опасной аномалии.
- **Account invariant violation** (нет строки settle currency, пустые
  обязательные поля, числа не парсятся, inconsistent response): класс
  `EXTERNAL_INVARIANT_VIOLATION`; risk-creating action не выполняется;
  реакция — контролируемого исключения интеграции
  (`docs/rules/controlled-exchange-exceptions.md`: `Deal → ERROR`,
  `ExchangeAccount.safetyRung = TRADE_BLOCKED`). Свежий снимок без строки
  расчётной валюты граница не производит; преконтроль отвергает его и
  сам (`docs/components/RiskValidator.md`).
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

### `AccountConfigOkxResponse` → snapshot

См. инвентарь — `docs/models/integrations/okx/AccountConfigOkxResponse.md`;
контракт — `docs/integrations/okx/contracts/account-config.md`.

| OKX field | Snapshot field |
|---|---|
| `data[0].acctLv` | `externalAccountLevel` |
| `data[0].posMode` | `externalPositionMode` |

**Перевод в доменный словарь (при сборке контейнера):**

| OKX значение | Доменное значение |
|---|---|
| `acctLv = 1` | `accountMode = SPOT` |
| `acctLv = 2` | `accountMode = FUTURES` |
| `acctLv = 3` | `accountMode = MULTI_CURRENCY_MARGIN` |
| `acctLv = 4` | `accountMode = PORTFOLIO_MARGIN` |
| `posMode = net_mode` | `positionMode = NET` |
| `posMode = long_short_mode` | `positionMode = LONG_SHORT` |
| иное либо пусто | пусто (след в логе) |

### OKX validation notes

- **Structural:** `code == "0"`.
- **Режим счёта:** `adjEq` и `availEq` площадка ведёт только в режимах
  мультивалютной и портфельной маржи; счёт контура — в режиме `Futures`
  (`acctLv=2`, `docs/integrations/okx/contracts/order-precheck.md`), и
  там оба поля приходят пустыми законно
  (`docs/integrations/okx/contracts/account-position-risk.md` — то же
  ограничение у `adjEq`). Наблюдением на demo не подтверждено.
- **Два вызова на снимок:** `GET /api/v5/account/balance` и
  `GET /api/v5/account/config`, оба приватные; конфигурация читается после
  того, как ответ баланса прошёл валидацию, — отвергнутый баланс второго
  запроса не стоит.
- **Path note:** правильный путь — `GET /api/v5/account/balance`. В
  старых архивных доках встречалась опечатка
  `balanceExternalSnapshot` — это не реальный endpoint OKX.
- **Query:** `ccy` — опционально, до 20 через запятую. Для runtime
  бота передаётся settle currency (`USDT`).

Дополнительные OKX-поля добавляются точечно (raw DTO → validation →
normalized snapshot → domain) только если реально нужны
runtime-домену.
