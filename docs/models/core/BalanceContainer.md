# BalanceContainer

## На какой вопрос отвечает этот файл

Что это за торговая модель `BalanceContainer` (и вложенная
`Balance`): структура, инварианты, свежесть, участие в runtime.

## Назначение

`BalanceContainer` — persisted account-state snapshot aggregate по
exchange account: последняя известная системе картина баланса /
equity / доступных средств аккаунта на бирже. Используется для
sizing, risk validation, проверки доступных средств и settle
currency, диагностики frozen funds, safety-flow и подготовки
account snapshot перед следующими сделками.

`Balance` — currency-level snapshot одной валюты внутри
`BalanceContainer` (раздел этой модели, не самостоятельная
сущность — см. `.claude/decisions/model-granularity.md`).

```text
BalanceContainer
  -> account-level snapshot
  -> balances
      -> Balance(USDT)
      -> Balance(BTC)
      -> Balance(ETH)
```

Модель поддерживает несколько валют. Для текущего `ETH-USDT-SWAP`
runtime-политика проще: обязательна settle currency инструмента
(`USDT`).

## Структура

### `BalanceContainer` (account-level)

Java-класс `com.example.tradingbot.domain.model.core.balance.BalanceContainer`,
расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний технический идентификатор snapshot в БД. |
| `exchangeId` | `Long` | Биржа / exchange account, которому принадлежит snapshot. На первом этапе — `exchangeId`; при появлении `ExchangeAccount` поле может смениться/дополниться. |
| `externalUpdatedAt` | `OffsetDateTime` | Время обновления account-level snapshot на стороне биржи. База freshness-check. |
| `externalTotalEquity` | `BigDecimal` | Total equity аккаунта по данным биржи. |
| `externalAdjustedEquity` | `BigDecimal` | Adjusted / effective equity. Для risk-policy может быть предпочтительной базой. |
| `externalAvailableEquity` | `BigDecimal` | Account-level available equity — хватает ли средств перед risk-creating / risk-increasing action. |
| `balances` | `List<Balance>` | Балансы по валютам. Для SWAP/USDT обязательна settle currency. |

Метод `replaceBalances(List<Balance>)` — полная замена currency-level
списка (clear + recreate). `REFRESH_BALANCE` использует replace
semantics: новый валидный exchange snapshot полностью заменяет
старый список валют.

### `Balance` (currency-level, раздел модели)

Java-класс `com.example.tradingbot.domain.model.core.balance.Balance`,
расширяет `Auditable`. Snapshot одной валюты внутри контейнера.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор currency balance в БД. |
| `balanceContainerId` | `Long` | Ссылка на родительский `BalanceContainer`. |
| `externalCurrency` | `String` | Валюта по данным биржи (например, `USDT`). |
| `externalUpdatedAt` | `OffsetDateTime` | Время обновления currency-level snapshot на бирже. |
| `externalEquity` | `BigDecimal` | Equity по валюте. |
| `externalCashBalance` | `BigDecimal` | Cash balance по валюте. |
| `externalAvailableBalance` | `BigDecimal` | Доступный баланс по валюте. |
| `externalFrozenBalance` | `BigDecimal` | Замороженный баланс — диагностика, почему часть средств недоступна. |

## Инварианты

- `BalanceContainer` — **не** trading runtime entity (в отличие от
  `Order` / `AlgoOrder` / `Position`): не выставляется, не
  отменяется, не закрывается, сам по себе не создаёт live market
  risk. Не имеет trading lifecycle, собственного `Status`,
  active/closed semantics. Это persisted account-state snapshot
  aggregate.
- Runtime-значимость определяется: принадлежностью exchange account,
  свежестью snapshot, корректностью данных, наличием обязательной
  settle currency.
- `BalanceContainer` хранит account-level snapshot; `Balance` —
  currency-level snapshot внутри контейнера.
- Если fresh balance snapshot не содержит обязательную settle
  currency — это не нормальная ситуация, а controlled
  external/account error.
- Обновление — только через `REFRESH_BALANCE` (единственный
  runtime-flow обновления контейнера; см. форвард-заметку про
  подсистему ServiceCommand в
  `.claude/work/questions/tasks/balance.md`).

## Свежесть (freshness)

`BalanceContainer` **не** хранит статус `STALE` — актуальность
вычисляется:

```text
externalUpdatedAt / updatedAt + balanceExpirationDuration -> fresh / stale
```

- stale balance — это не `Status` контейнера и не `CalculationError`;
  это precondition problem перед risk-sensitive flow.
- Проверку свежести выполняет FSM / handler перед risk-sensitive
  flow (входной владелец — Deal-lifecycle; форвард-заметка для
  миграции Deal — в `.claude/work/questions/tasks/balance.md`).
- Если balance absent/stale перед risk-check — handler создаёт
  `REFRESH_BALANCE` и не вызывает `RiskValidator` на этой итерации;
  после успешного refresh следующая итерация FSM пересобирает
  `DealContext` и продолжает flow.
- Компонент-проверка (`BalanceFreshnessChecker`) и executor
  (`RefreshBalanceExecutor`) — часть adapter/command-подсистемы,
  мигрируются отдельно (форвард-заметки в task-вопросах).

## Normalized external snapshots

`BalanceContainerExternalSnapshot` / `BalanceExternalSnapshot` —
validated normalized snapshots, не persisted domain entity. Создаются
внутри `ClientService` / adapter-layer после валидации raw response
биржи. Содержат **только** поля, нужные для обновления доменной
модели; если поле не хранится в домене и не нужно для его обновления,
оно в normalized snapshot не попадает (raw exchange DTO не выходит за
пределы adapter-layer — см. `docs/rules/raw-exchange-dto-boundary.md`).

- `BalanceContainerExternalSnapshot`: `exchangeId`,
  `externalUpdatedAt`, `externalTotalEquity`, `externalAdjustedEquity`,
  `externalAvailableEquity`, `balances: List<BalanceExternalSnapshot>`.
- `BalanceExternalSnapshot`: `externalCurrency`, `externalUpdatedAt`,
  `externalEquity`, `externalCashBalance`, `externalAvailableBalance`,
  `externalFrozenBalance`.

Числовые поля в snapshot остаются строками, но к моменту создания
snapshot уже провалидированы как parseable decimal. Конкретный OKX
mapping — в `docs/client/okx/rules/okx-balance-mapping.md`.

## Null contract

Для balance normal `null` contract не используется (в отличие от
`Position`, где `null` может означать отсутствие позиции).

```text
successful refresh        -> валидный BalanceContainerExternalSnapshot
                             с обязательной settleCurrency
empty / missing ccy /
  invalid fields          -> controlled external/account error
API / parse / invariant   -> exception / controlled error
```

Причина: отсутствие balance snapshot не является нормальным фактом
вроде «позиции нет».

## Участие в runtime

Везде ниже `BalanceContainer` упоминается как input; владельцы этих
компонентов мигрируются отдельно (форвард-заметки — в
`.claude/work/questions/tasks/balance.md`).

- **DealContext** содержит последний persisted `BalanceContainer` по
  exchange account — это не гарантия свежести (свежесть проверяет
  FSM / handler).
- **CalculationContext** / `StrategyActionCalculator` могут
  использовать `BalanceContainer` как input для sizing, но не
  обновляют его, не вызывают `ClientService`, не создают
  `REFRESH_BALANCE`, не принимают risk decision.
- **RiskValidator** использует fresh `BalanceContainer` как входной
  snapshot (`externalTotalEquity` / `externalAdjustedEquity` /
  `externalAvailableEquity`; по settle currency — `externalEquity` /
  `externalCashBalance` / `externalAvailableBalance` /
  `externalFrozenBalance`), но не обновляет его и не создаёт
  `ServiceCommand`. Defensive policy: absent/stale/invalid →
  `RiskValidationResult.BLOCKED`, code `BALANCE_NOT_FRESH` /
  `BALANCE_INVALID`.

## Чего не хранит домен

Домен хранит только runtime-useful поля. Не хранятся: полный/raw
OKX response, validation-only поля биржи, borrow/debt и collateral
детализация, copy-trading / auto-lend / spot cost basis / greeks
поля, notional/margin breakdown сверх нужного v1, история изменения
баланса. История / аудит баланса в эту модель намеренно не входят
(см. форвард-заметку про аудит). Если risk/safety-flow реально
потребует поле — добавляется точечно: raw DTO → validation →
normalized snapshot → domain.

## Расчёт PnL — не здесь

Итоговый `Deal.resultProfit` **не** считается по balance diff; он
считается через `REFRESH_FILLS` и факты исполнений (правило
принадлежит `Deal` — см. `.claude/decisions/rule-source-of-truth.md`;
форвард-заметка для миграции Deal — в task-вопросах).
`REFRESH_BALANCE` после выхода из сделки нужен для актуального account
snapshot, а не для расчёта PnL сделки.
