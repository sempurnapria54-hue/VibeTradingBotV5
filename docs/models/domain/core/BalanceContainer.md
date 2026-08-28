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
| `externalAdjustedEquity` | `BigDecimal` | Adjusted / effective equity. |
| `externalAvailableEquity` | `BigDecimal` | Account-level available (свободный) equity. **Базой риск-политики не является** (C6 `DOCS_CHECK_21`) — диагностическая величина уровня аккаунта. |
| `balances` | `List<Balance>` | Балансы по валютам. Для SWAP/USDT обязательна settle currency. |

**Единица и валютный состав account-level полей не объявлены** (C6
`DOCS_CHECK_21`). Все три — величины **уровня аккаунта**: они агрегируют
все валюты аккаунта, а их номинацию офдок источника не называет и
модель не записывает (`docs/integrations/okx/contracts/balance.md`,
`docs/models/mapping/Balance.md` — описания без единицы). Поэтому:

- **риск-контур их не читает** — база риска берётся из строки `Balance`
  расчётной валюты (§`Balance` ниже,
  `docs/decisions/per-trade-risk-policy.md` §«Определение и база»); тем
  самым посылка о номинации перестала быть несущей, и добывать её
  отдельно не требуется;
- **сравнивать их с величинами в расчётной валюте нельзя** — ни в
  неравенствах лимитов, ни в допуске сверки; если такая нужда возникнет
  (второй инструмент с другой settle-ccy, вторая биржа), номинация
  становится несущей и добывается прогоном контура тестов API источника
  **до** ввода потребителя.

Метод `replaceBalances(List<Balance>)` — полная замена currency-level
списка (clear + recreate). `REFRESH_BALANCE_COMMAND` использует replace
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
| `externalAvailableBalance` | `BigDecimal` | Доступный баланс по валюте. У строки **расчётной валюты инструмента** это и есть **база риска** — операнд сайзинга и всех трёх лимитов риска на сделку (`docs/decisions/per-trade-risk-policy.md` §«Определение и база»). Единица определена по построению: `externalCurrency` строки. |
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
- Обновление — только через `REFRESH_BALANCE_COMMAND` (единственный
  runtime-flow обновления контейнера; см. форвард-заметку про
  подсистему ServiceCommand в
  `.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-balance.md`).

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
  миграции Deal — в `.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-balance.md`).
- Если balance absent/stale перед risk-check — свежесть добывается
  звеном `REFRESH_BALANCE_COMMAND` через `REFRESH_DEAL_CONTEXT_ACTION`
  (handler добывающие `REFRESH_*` напрямую не эмитит,
  `docs/components/SystemActionExecutor.md`), `RiskValidator` на этой
  итерации не вызывается; после успешного refresh следующая итерация FSM
  пересобирает `DealContext` и продолжает flow.
- Компонент-проверка (`BalanceFreshnessChecker`) и executor
  (`RefreshBalanceExecutor`) — часть adapter/command-подсистемы,
  мигрируются отдельно (форвард-заметки в task-вопросах).

## Normalized external snapshots

`BalanceContainerExternalSnapshot` / `BalanceExternalSnapshot` —
validated normalized snapshots, не persisted domain entity. Создаются
внутри `IntegrationService` / adapter-layer после валидации raw response
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
mapping — в `docs/models/mapping/Balance.md`.

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
`.claude/work/history/2026-05-27-миграция-торговых-сущностей/tasks-balance.md`).

- **DealContext** содержит последний persisted `BalanceContainer` по
  exchange account — это не гарантия свежести (свежесть проверяет
  FSM / handler).
- **CalculationContext** / `StrategyActionCalculator` могут
  использовать `BalanceContainer` как input для sizing, но не
  обновляют его, не вызывают `IntegrationService`, не создают
  `REFRESH_BALANCE_COMMAND`, не принимают risk decision.
- **RiskValidator** использует fresh `BalanceContainer` как входной
  snapshot; **базу риска** он читает из строки `Balance` расчётной
  валюты (`externalAvailableBalance`), прочие поля строки
  (`externalEquity` / `externalCashBalance` / `externalFrozenBalance`) и
  account-level величины — диагностика. Валидатор не обновляет снапшот и
  не создаёт `ServiceCommand`. Defensive policy: absent/stale/invalid →
  `RiskValidationResult.BLOCKED`, code `BALANCE_NOT_FRESH` /
  `BALANCE_INVALID`; отсутствие строки расчётной валюты либо
  непозитивный `externalAvailableBalance` — это `BALANCE_INVALID`
  (`docs/components/RiskValidator.md` §«Конкретные проверки»).

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

Итоговый `Deal.resultProfit` **не** считается по balance diff;
заголовочное число = net `realizedPnl` из positions-history, категорийная
разбивка — из bills (правило принадлежит `Deal` — см.
`.claude/decisions/rule-source-of-truth.md`,
`docs/decisions/result-profit-source.md`). `REFRESH_BALANCE_COMMAND` после выхода из
сделки нужен для актуального account snapshot, а не для расчёта PnL сделки.
