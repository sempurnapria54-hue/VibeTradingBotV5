# Balance / BalanceContainer

> Статус документа: финальная доменная модель runtime-сущностей `BalanceContainer` и `Balance` для торгового движка.
>
> Документ фиксирует доменную семантику баланса, модель snapshot аккаунта, freshness-policy, `REFRESH_BALANCE`, участие в `DealContext`, `CalculationContext` и `RiskValidator`.
>
> Exchange-specific mapping для OKX должен быть вынесен в отдельный документ: `OKX_Balance_mapping.md`.

---

# 1. Назначение

`BalanceContainer` — persisted account-state snapshot aggregate по exchange account.

Он отражает последнюю известную системе картину баланса / equity / доступных средств аккаунта на бирже.

Простыми словами:

```text
BalanceContainer показывает, сколько средств доступно аккаунту для торговли и risk-policy.
```

`Balance` — currency-level snapshot внутри `BalanceContainer`.

Например, для текущего проекта `ETH-USDT-SWAP` обязательна валюта `USDT` / settle currency инструмента.

---

# 2. Главные инварианты

* `BalanceContainer` не является trading runtime entity как `Order`, `AlgoOrder` или `Position`.
* `BalanceContainer` не имеет trading lifecycle.
* У `BalanceContainer` нет `CREATED / PENDING / ACTIVE / CLOSED` lifecycle.
* У `BalanceContainer` нет собственного `Status`.
* У `BalanceContainer` нет active/closed semantics.
* Runtime-значимость `BalanceContainer` определяется:
  * принадлежностью exchange account;
  * свежестью snapshot;
  * корректностью данных;
  * наличием обязательной settle currency.
* `BalanceContainer` хранит account-level snapshot.
* `Balance` хранит currency-level snapshot внутри `BalanceContainer`.
* В `DealContext` передаётся `BalanceContainer` целиком.
* `BalanceContainer` в `DealContext` — это последняя persisted версия, а не гарантия свежести.
* Свежесть проверяет FSM / handler перед risk-sensitive flow.
* `REFRESH_BALANCE` — единственный runtime-flow обновления `BalanceContainer`.
* `RiskValidator` использует fresh `BalanceContainer`, но не обновляет его сам.
* `StrategyActionCalculator` / `CalculationContext` могут использовать `BalanceContainer` как input, но не вызывают `ClientService` и не создают `REFRESH_BALANCE`.
* Итоговый `Deal.resultProfit` не считается по balance diff.
* Итоговый `Deal.resultProfit` считается через `REFRESH_FILLS` и факты исполнений.
* `REFRESH_BALANCE` после выхода из сделки нужен для актуального account snapshot, а не для расчёта PnL сделки.

---

# 3. Семантика `BalanceContainer`

`BalanceContainer` — это не объект, который бот создаёт на бирже.

Он не выставляется, не отменяется, не закрывается и сам по себе не создаёт live market risk.

Правильная семантика:

```text
BalanceContainer = persisted account-state snapshot aggregate
```

Он используется для:

* sizing;
* risk validation;
* проверки доступных средств;
* проверки available equity;
* проверки обязательной settle currency;
* диагностики frozen funds;
* safety-flow;
* подготовки account snapshot перед следующими сделками.

Он не используется для:

* определения active/closed trading entity;
* подтверждения закрытия позиции;
* расчёта итогового PnL сделки;
* хранения полной копии OKX response;
* хранения истории изменения баланса.

---

# 4. Семантика `Balance`

`Balance` — это snapshot одной валюты внутри `BalanceContainer`.

Пример:

```text
BalanceContainer
  -> account-level snapshot
  -> balances
      -> Balance(USDT)
      -> Balance(BTC)
      -> Balance(ETH)
```

Модель поддерживает несколько валют.

Для текущего проекта runtime-политика проще:

```text
для ETH-USDT-SWAP обязательна валюта USDT / settleCurrency инструмента.
```

Если fresh balance snapshot не содержит обязательную settle currency, это не нормальная ситуация.

Это controlled external/account error.

---

# 5. Freshness-policy

`BalanceContainer` не хранит статус `STALE`.

Актуальность считается вычисляемо:

```text
BalanceContainer.externalUpdatedAt / updatedAt
+ balanceExpirationDuration
-> fresh / stale
```

Рекомендуемый компонент:

```java
public interface BalanceFreshnessChecker {

    /**
     * Проверить, достаточно ли свежий balance snapshot для runtime-flow.
     */
    boolean isFresh(BalanceContainer balanceContainer);
}
```

Правила:

* stale balance — это не `BalanceContainer.Status`;
* stale balance — это не `CalculationError`;
* stale balance — это precondition problem перед risk-sensitive flow;
* если balance absent/stale, handler создаёт `REFRESH_BALANCE` и не вызывает `RiskValidator` на этой итерации;
* `RiskValidator` дополнительно защищается и возвращает `BLOCKED`, если получил absent/stale/invalid balance.

---

# 6. Доменная модель `BalanceContainer`

```java
package com.example.tradingbot.domain.model.core.balance;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Account-level snapshot баланса и доступной equity exchange account.
 *
 * Простыми словами:
 * - это последняя известная системе картина денег на биржевом аккаунте;
 * - используется для sizing, risk validation и safety-flow;
 * - не является trading entity;
 * - не имеет active/closed lifecycle;
 * - обновляется только через REFRESH_BALANCE.
 */
@Getter
@Setter
public class BalanceContainer extends Auditable {

    /**
     * Внутренний технический идентификатор snapshot в БД.
     */
    private Long id;

    /**
     * Идентификатор биржи / exchange account, которому принадлежит snapshot.
     *
     * На первом этапе используется exchangeId.
     * Если позже появится отдельная модель ExchangeAccount, поле можно заменить
     * или дополнить ссылкой на exchangeAccountId.
     */
    private Long exchangeId;

    /**
     * Время обновления account-level snapshot на стороне биржи.
     *
     * Используется для freshness-check.
     */
    private OffsetDateTime externalUpdatedAt;

    /**
     * Total equity аккаунта по данным биржи.
     *
     * Используется как одна из баз для оценки депозита.
     */
    private BigDecimal externalTotalEquity;

    /**
     * Adjusted / effective equity аккаунта по данным биржи.
     *
     * Для risk-policy может быть предпочтительной базой,
     * если биржа рассчитывает её как более безопасную доступную equity.
     */
    private BigDecimal externalAdjustedEquity;

    /**
     * Account-level available equity по данным биржи.
     *
     * Используется для проверки, хватает ли доступных средств
     * перед risk-creating / risk-increasing action.
     */
    private BigDecimal externalAvailableEquity;

    /**
     * Балансы аккаунта по валютам.
     *
     * Для текущего SWAP/USDT runtime обязательна settle currency инструмента,
     * например USDT.
     */
    private List<Balance> balances;

    /**
     * Полностью заменить currency-level balances внутри контейнера.
     *
     * REFRESH_BALANCE использует replace semantics:
     * новый валидный exchange snapshot полностью заменяет старый список валют.
     */
    public void replaceBalances(List<Balance> balances) {
        clearBalances();
        if (isEmpty(balances)) {
            return;
        }
        setBalances(balances);
    }

    private void clearBalances() {
        if (balances == null) {
            balances = new ArrayList<>();
            return;
        }
        balances.clear();
    }
}
```

---

# 7. Доменная модель `Balance`

```java
package com.example.tradingbot.domain.model.core.balance;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Currency-level snapshot баланса внутри BalanceContainer.
 *
 * Простыми словами:
 * - это баланс одной валюты, например USDT;
 * - используется для проверки доступных средств в settle currency;
 * - не является отдельной trading entity;
 * - обновляется только как часть REFRESH_BALANCE.
 */
@Getter
@Setter
public class Balance extends Auditable {

    /**
     * Внутренний технический идентификатор currency balance в БД.
     */
    private Long id;

    /**
     * Идентификатор BalanceContainer, которому принадлежит currency snapshot.
     */
    private Long balanceContainerId;

    /**
     * Валюта баланса по данным биржи.
     *
     * Например: USDT.
     */
    private String externalCurrency;

    /**
     * Время обновления currency-level snapshot на стороне биржи.
     */
    private OffsetDateTime externalUpdatedAt;

    /**
     * Equity по валюте по данным биржи.
     */
    private BigDecimal externalEquity;

    /**
     * Cash balance по валюте по данным биржи.
     */
    private BigDecimal externalCashBalance;

    /**
     * Доступный баланс по валюте по данным биржи.
     */
    private BigDecimal externalAvailableBalance;

    /**
     * Замороженный баланс по валюте по данным биржи.
     *
     * Используется для диагностики, почему часть средств недоступна.
     */
    private BigDecimal externalFrozenBalance;
}
```

---

# 8. Normalized external snapshots

Normalized external snapshots — это не raw OKX DTO и не копия полного response биржи.

Они содержат только поля, которые нужны для обновления доменной модели.

Если поле не хранится в домене и не нужно для обновления домена, оно не должно попадать в normalized external snapshot.

## 8.1. `BalanceContainerExternalSnapshot`

```java
package com.example.tradingbot.domain.model.core.balance.external_snapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Validated normalized account-level balance snapshot.
 *
 * Создаётся внутри ClientService / adapter-layer после валидации raw response биржи.
 *
 * Не является persisted domain entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BalanceContainerExternalSnapshot {

    /**
     * Внутренний идентификатор биржи / exchange account.
     */
    private Long exchangeId;

    /**
     * Время обновления account-level snapshot на стороне биржи.
     */
    private OffsetDateTime externalUpdatedAt;

    /**
     * Total equity аккаунта как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalTotalEquity;

    /**
     * Adjusted / effective equity аккаунта как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalAdjustedEquity;

    /**
     * Account-level available equity как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalAvailableEquity;

    /**
     * Currency-level balances, нужные для обновления домена.
     */
    private List<BalanceExternalSnapshot> balances;
}
```

## 8.2. `BalanceExternalSnapshot`

```java
package com.example.tradingbot.domain.model.core.balance.external_snapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Validated normalized currency-level balance snapshot.
 *
 * Создаётся внутри ClientService / adapter-layer после валидации raw response биржи.
 *
 * Не является persisted domain entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BalanceExternalSnapshot {

    /**
     * Валюта баланса, например USDT.
     */
    private String externalCurrency;

    /**
     * Время обновления currency-level snapshot на стороне биржи.
     */
    private OffsetDateTime externalUpdatedAt;

    /**
     * Equity по валюте как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalEquity;

    /**
     * Cash balance по валюте как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalCashBalance;

    /**
     * Available balance по валюте как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalAvailableBalance;

    /**
     * Frozen balance по валюте как строка из exchange response.
     *
     * Поле уже должно быть провалидировано как parseable decimal.
     */
    private String externalFrozenBalance;
}
```

---

# 9. `REFRESH_BALANCE`

`REFRESH_BALANCE` — read-only runtime `ServiceCommand` для обновления account-level balance snapshot.

Read-only означает:

```text
команда ничего не меняет на бирже.
```

Но она меняет локальный persisted snapshot:

```text
BalanceContainer / Balance в БД.
```

## 9.1. Общий flow

```text
FSM / handler
  -> создаёт REFRESH_BALANCE
  -> RefreshBalanceExecutor
     -> ClientService
        -> raw exchange response
        -> validation
        -> BalanceContainerMapper
        -> BalanceContainerExternalSnapshot
     -> upsert BalanceContainer
     -> replace balances
```

## 9.2. Граница ClientService

`ClientService` внутри себя:

1. получает raw exchange balance response;
2. валидирует структуру response;
3. проверяет обязательные поля;
4. проверяет exchange-specific invariants;
5. маппит только runtime-useful поля через `BalanceContainerMapper` в `BalanceContainerExternalSnapshot`;
6. возвращает наружу уже validated normalized snapshot.

Правило:

```text
Raw exchange DTO не выходит за пределы ClientService / adapter-layer.
```

## 9.3. Ответственность RefreshBalanceExecutor

`RefreshBalanceExecutor`:

* вызывает `ClientService`;
* получает `BalanceContainerExternalSnapshot`;
* не работает с raw OKX DTO;
* не валидирует OKX-specific поля;
* создаёт `BalanceContainer`, если его ещё нет;
* обновляет account-level поля;
* полностью заменяет список `Balance` внутри контейнера;
* сохраняет обновлённый snapshot.

## 9.4. Nullable contract

Для balance normal `null` contract не используем.

```text
successful refresh
  -> обязан вернуть валидный BalanceContainerExternalSnapshot
     с обязательной settleCurrency

empty response / missing settleCurrency / invalid fields
  -> controlled external/account error

API / parse / invariant error
  -> exception / controlled error по общей exchange error policy
```

Причина:

```text
отсутствие balance snapshot не является нормальным фактом вроде “позиции нет”.
```

Для `Position` `null` может означать отсутствие позиции на бирже.

Для `BalanceContainer` успешный `null` не нужен.

---

# 10. Участие в FSM

FSM / handler обязан обеспечить fresh `BalanceContainer`:

* при старте обработки сделки / `PRECHECK`;
* перед risk-creating action;
* перед risk-increasing action;
* перед risk-weakening action;
* при финализации выхода из сделки;
* при emergency / safety finalization.

Если balance absent/stale перед risk-check:

```text
handler создаёт REFRESH_BALANCE
и не вызывает RiskValidator на этой итерации.
```

После успешного refresh следующая итерация FSM пересобирает `DealContext` и продолжает flow.

---

# 11. Участие в DealContext

`DealContext` содержит последний persisted `BalanceContainer` по exchange account.

Пример:

```java
public class DealContext {

    private Deal deal;
    private Exchange exchange;
    private Instrument instrument;
    private StrategyDetail strategyDetail;
    private BalanceContainer balanceContainer;
    private Position position;
    private List<Order> orders;
    private List<AlgoOrder> algoOrders;
    private List<DealActionState> actionStates;
}
```

Важно:

```text
BalanceContainer в DealContext — это не гарантия свежести.
```

Свежесть проверяет FSM / handler перед risk-sensitive flow.

---

# 12. Участие в CalculationContext

`CalculationContext` может включать `BalanceContainer` как input для расчёта конкретного `StrategyAction`.

Но `CalculationContext`:

* не обновляет `BalanceContainer`;
* не вызывает `ClientService`;
* не создаёт `REFRESH_BALANCE`;
* не принимает risk decision.

Пример:

```text
StrategyActionCalculator
  -> собирает CalculationContext для одного action
  -> использует BalanceContainer, если нужен для sizing
  -> возвращает CalculatedStrategyAction
  -> RiskValidator вызывается отдельно, если action risk-sensitive
```

---

# 13. Участие в RiskValidator

`RiskValidator` использует `BalanceContainer` как входной snapshot для risk-policy.

Он может использовать:

* `BalanceContainer.externalTotalEquity`;
* `BalanceContainer.externalAdjustedEquity`;
* `BalanceContainer.externalAvailableEquity`;
* `Balance.externalEquity` по settle currency;
* `Balance.externalCashBalance` по settle currency;
* `Balance.externalAvailableBalance` по settle currency;
* `Balance.externalFrozenBalance` по settle currency.

`RiskValidator` не делает:

* не вызывает `REFRESH_BALANCE`;
* не вызывает `ClientService`;
* не работает с OKX adapter;
* не обновляет `BalanceContainer`;
* не создаёт `ServiceCommand`.

Defensive policy:

```text
absent/stale/invalid BalanceContainer
  -> RiskValidationResult.BLOCKED
  -> code = BALANCE_NOT_FRESH / BALANCE_INVALID
```

---

# 14. Что не храним в BalanceContainer / Balance

Не храним в домене:

* полный OKX response;
* raw request / response;
* validation-only OKX поля;
* borrow/debt детализацию;
* collateral flags;
* copy-trading поля;
* auto-lend поля;
* spot cost basis поля;
* delta / greeks / delta-neutral поля;
* notional breakdown, если он не нужен v1 runtime;
* margin ratio / IMR / MMR, если они не нужны v1 runtime;
* историю изменения баланса.

Если позже `RiskValidator` или safety-flow реально потребует дополнительное поле, его можно добавить точечно.

---

# 15. Open questions

На текущем этапе открытых вопросов по доменной семантике `BalanceContainer` / `Balance` нет.

Вопросы истории / аудита баланса намеренно не входят в этот документ.
