## Доменная модель для `GET /api/v5/account/balanceExternalSnapshot` + маппинг

Ниже — **доменная модель для хранения snapshot баланса в БД** (без persistence-аннотаций, но с audit-полями) + **маппинг
в YAML** (OKX response → доменная модель). Можно копировать в md.

---

## 1) Доменная сущность `Balance`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot баланса аккаунта (ответ /account/balanceExternalSnapshot).
 *
 * Это доменная модель (без persistence-аннотаций).
 * Хранение в БД делается отдельным persistence-слоем.
 */
@Getter
@Setter
public class Balance {

    /** Внутренний идентификатор записи в БД (если используешь). */
    private Long id;

    /**
     * Фильтр запроса `ccy` (что именно запрашивали).
     * Например "USDT" или "BTC,ETH". Можно хранить для аудита.
     */
    private String requestCurrencies;

    // --------- Exchange timestamps ---------

    /** uTime — время обновления snapshot на бирже (ms -> Instant). */
    private Instant sourceUpdatedAt;

    // --------- Account-level aggregates (обычно в USD-оценках) ---------

    /** totalEq — суммарная equity по аккаунту (в USD-оценке). */
    private BigDecimal totalEquityUsd;

    /** isoEq — equity изолированной маржи в USD (в некоторых режимах). */
    private BigDecimal isolatedEquityUsd;

    /** adjEq — effective/adjusted equity в USD (используется биржей для риск-расчётов). */
    private BigDecimal adjustedEquityUsd;

    /** availEq — доступная equity на уровне аккаунта (в некоторых режимах). */
    private BigDecimal availableEquityUsd;

    /** ordFroz — заморожено под ордера (USD). */
    private BigDecimal orderFrozenUsd;

    /** imr — initial margin requirement (USD) для cross/PM-логики. */
    private BigDecimal initialMarginRequirementUsd;

    /** mmr — maintenance margin requirement (USD) для cross/PM-логики. */
    private BigDecimal maintenanceMarginRequirementUsd;

    /** borrowFroz — резерв/потенциальный IMR под заём (USD); часто пусто вне нужных режимов. */
    private BigDecimal borrowFrozenUsd;

    /** mgnRatio — margin ratio на уровне аккаунта; может быть null/пусто. */
    private BigDecimal marginRatio;

    /** notionalUsd — общий notional (экспозиция) в USD. */
    private BigDecimal notionalUsd;

    /** notionalUsdForBorrow — notional по borrow в USD. */
    private BigDecimal notionalUsdForBorrow;

    /** notionalUsdForSwap — notional по swap в USD. */
    private BigDecimal notionalUsdForSwap;

    /** notionalUsdForFutures — notional по futures в USD. */
    private BigDecimal notionalUsdForFutures;

    /** notionalUsdForOption — notional по options в USD. */
    private BigDecimal notionalUsdForOption;

    /** upl — unrealized PnL на уровне аккаунта (USD) в некоторых режимах. */
    private BigDecimal unrealizedPnlUsd;

    // --------- Delta-neutral / Greeks-like account metrics (обычно не нужны для SWAP-бота) ---------

    /** delta — чувствительность портфеля к движению базового актива (актуально для опционов/сложных портфелей). */
    private BigDecimal delta;

    /** deltaLever — delta с поправкой на плечо/риск (биржевой риск-индикатор). */
    private BigDecimal deltaLever;

    /** deltaNeutralStatus — статус delta-neutral режима/стратегии (если используется). */
    private String deltaNeutralStatus;

    // --------- Details (per currency) ---------

    /** details[] — детализация по валютам (например, USDT). */
    private List<BalanceCurrencyDetail> details;

    // --------- Auditing (DB) ---------

    /** createdAt — когда запись создана в нашей БД. */
    private Instant createdAt;

    /** updatedAt — когда запись обновлена в нашей БД. */
    private Instant updatedAt;

    /** createdBy — кем создана (опционально). */
    private String createdBy;

    /** updatedBy — кем обновлена (опционально). */
    private String updatedBy;

    /**
     * Детализация баланса по одной валюте (элемент массива details[]).
     *
     * Важно: многие поля могут приходить пустой строкой "" — тогда считаем их null.
     */
    @Getter
    @Setter
    public static class BalanceCurrencyDetail {

        /** ccy — код валюты (например USDT). */
        private String currency;

        /** eq — equity по валюте ("сколько всего по валюте" с учётом оценок/маржи). */
        private BigDecimal equity;

        /** cashBal — фактический баланс валюты ("лежит на счету"). */
        private BigDecimal cashBalance;

        /** availBal — доступный баланс (не заморожено). */
        private BigDecimal availableBalance;

        /** uTime — время обновления по этой валюте (ms -> Instant). */
        private Instant sourceUpdatedAt;

        // ---- Isolated / available equity ----

        /** isoEq — equity по валюте в isolated (если применимо). */
        private BigDecimal isolatedEquity;

        /** availEq — доступная equity по валюте (если применимо). */
        private BigDecimal availableEquity;

        /** disEq — дисконтированная оценка в USD (если валюта не 1:1 как залог). */
        private BigDecimal discountedEquityUsd;

        // ---- Freezes ----

        /** fixedBal — заморозка под спец-стратегии OKX (обычно не нужно). */
        private BigDecimal fixedBalance;

        /** frozenBal — общий замороженный баланс. */
        private BigDecimal frozenBalance;

        /** ordFrozen — заморожено под ордера (по валюте). */
        private BigDecimal orderFrozen;

        // ---- Liabilities / interest ----

        /** liab — общий долг по валюте (если занимал). */
        private BigDecimal liability;

        /** upl — unrealized PnL, привязанный к этой валюте (если применимо). */
        private BigDecimal unrealizedPnl;

        /** uplLiab — долг/обязательства из-за плавающего убытка (в некоторых режимах). */
        private BigDecimal unrealizedPnlLiability;

        /** crossLiab — долг в cross-режиме. */
        private BigDecimal crossLiability;

        /** isoLiab — долг в isolated-режиме. */
        private BigDecimal isolatedLiability;

        /** mgnRatio — margin ratio по валюте (если применимо). */
        private BigDecimal marginRatio;

        /** imr — initial margin requirement по валюте (если применимо). */
        private BigDecimal initialMarginRequirement;

        /** mmr — maintenance margin requirement по валюте (если применимо). */
        private BigDecimal maintenanceMarginRequirement;

        /** interest — начисленные проценты по долгу. */
        private BigDecimal interest;

        // ---- Forced repayment / risk (биржевые индикаторы риска) ----

        /** twap — индикатор риска forced repayment (0..5). */
        private String twap;

        /** frpType — тип forced repayment: 0 нет, 1/2 — разные режимы. */
        private String frpType;

        /** maxLoan — максимальный доступный заём по валюте. */
        private BigDecimal maxLoan;

        // ---- USD conversions / leverage ----

        /** eqUsd — equity по валюте, пересчитанная в USD. */
        private BigDecimal equityUsd;

        /** borrowFroz — резерв под потенциальный заём по валюте (часто пусто). */
        private BigDecimal borrowFrozen;

        /** notionalLever — эффективное плечо/левередж по валюте (если применимо). */
        private BigDecimal notionalLeverage;

        /** stgyEq — strategy equity (обычно не нужно). */
        private BigDecimal strategyEquity;

        /** isoUpl — unrealized PnL в isolated по валюте (если применимо). */
        private BigDecimal isolatedUnrealizedPnl;

        // ---- Portfolio / Smart-sync / Copy-trading ----

        /** spotInUseAmt — spot in use amount (portfolio margin). */
        private BigDecimal spotInUseAmount;

        /** clSpotInUseAmt — пользовательский лимит spot risk offset amount (portfolio margin). */
        private BigDecimal clSpotInUseAmount;

        /** maxSpotInUse — максимальный spot risk offset amount (portfolio margin). */
        private BigDecimal maxSpotInUse;

        /** spotIsoBal — spot isolated balanceExternalSnapshot (copy-trading/особые режимы). */
        private BigDecimal spotIsolatedBalance;

        /** smtSyncEq — smart sync equity (copy trader). */
        private BigDecimal smartSyncEquity;

        /** spotCopyTradingEq — smart sync equity для spot copy-trading. */
        private BigDecimal spotCopyTradingEquity;

        // ---- Spot cost basis / PnL ----

        /** spotBal — spot баланс валюты (в единицах валюты). */
        private BigDecimal spotBalance;

        /** openAvgPx — средняя цена покупки (себестоимость) spot (USD). */
        private BigDecimal openAveragePriceUsd;

        /** accAvgPx — накопленная средняя цена spot (USD). */
        private BigDecimal accumulatedAveragePriceUsd;

        /** spotUpl — spot unrealized PnL (USD). */
        private BigDecimal spotUnrealizedPnlUsd;

        /** spotUplRatio — spot unrealized PnL ratio. */
        private BigDecimal spotUnrealizedPnlRatio;

        /** totalPnl — накопленный total PnL по spot (USD). */
        private BigDecimal totalPnlUsd;

        /** totalPnlRatio — накопленный total PnL ratio по spot. */
        private BigDecimal totalPnlRatio;

        // ---- Collateral / restrictions ----

        /** colRes — статус ограничений по залогу со стороны платформы (0/1/2). */
        private String collateralRestrictionStatus;

        /** colBorrAutoConversion — индикатор риска авто-конвертации (0..5). */
        private String collateralBorrowAutoConversion;

        /** collateralRestrict — устаревшее поле (deprecated), если приходит — храним как есть. */
        private Boolean collateralRestrict;

        /** collateralEnabled — включён ли режим collateral. */
        private Boolean collateralEnabled;

        // ---- Auto-lend ----

        /** autoLendStatus — статус автолендинга: unsupported/off/pending/active. */
        private String autoLendStatus;

        /** autoLendMtAmt — сколько реально размещено в автолендинге. */
        private BigDecimal autoLendMatchedAmount;

        // ---- Misc ----

        /** rewardBal — trial/reward баланс (бонусные средства). */
        private BigDecimal rewardBalance;
    }
}
```

---

## 2) Маппинг (YAML): OKX `GET /account/balanceExternalSnapshot` → `Balance`

```yaml
# верхний уровень: OKX отдаёт data[0] как агрегат по аккаунту
uTime: sourceUpdatedAt                               # ms -> Instant

# account aggregates (USD)
totalEq: totalEquityUsd                              # equity аккаунта в USD
isoEq: isolatedEquityUsd                             # equity isolated в USD (если применимо)
adjEq: adjustedEquityUsd                             # adjusted/effective equity в USD
availEq: availableEquityUsd                          # доступная equity в USD
ordFroz: orderFrozenUsd                              # заморожено под ордера (USD)
imr: initialMarginRequirementUsd                     # initial margin requirement (USD)
mmr: maintenanceMarginRequirementUsd                 # maintenance margin requirement (USD)
borrowFroz: borrowFrozenUsd                          # резерв под заём (USD)
mgnRatio: marginRatio                                # margin ratio (может быть пусто)

# notionals (USD)
notionalUsd: notionalUsd                             # общий notional в USD
notionalUsdForBorrow: notionalUsdForBorrow           # notional borrow в USD
notionalUsdForSwap: notionalUsdForSwap               # notional swap в USD
notionalUsdForFutures: notionalUsdForFutures         # notional futures в USD
notionalUsdForOption: notionalUsdForOption           # notional option в USD

# pnl + delta-neutral метрики
upl: unrealizedPnlUsd                                # unrealized pnl в USD (если применимо)
delta: delta                                         # delta (обычно для опционов/портфелей)
deltaLever: deltaLever                               # delta lever

deltaNeutralStatus: deltaNeutralStatus               # статус delta-neutral режима

# details (массив -> список)
details: details                                     # details[] -> List<BalanceCurrencyDetail>

# поля details[*]
details[*].ccy: details[*].currency                                  # код валюты

details[*].eq: details[*].equity                                     # equity по валюте

details[*].cashBal: details[*].cashBalance                           # cash balanceExternalSnapshot

details[*].availBal: details[*].availableBalance                     # available balanceExternalSnapshot

details[*].uTime: details[*].sourceUpdatedAt                          # ms -> Instant

details[*].isoEq: details[*].isolatedEquity                           # isolated equity

details[*].availEq: details[*].availableEquity                        # available equity

details[*].disEq: details[*].discountedEquityUsd                       # discount equity (USD)

details[*].fixedBal: details[*].fixedBalance                           # fixed balanceExternalSnapshot

details[*].frozenBal: details[*].frozenBalance                         # frozen balanceExternalSnapshot

details[*].ordFrozen: details[*].orderFrozen                           # order frozen

details[*].liab: details[*].liability                                  # liability

details[*].upl: details[*].unrealizedPnl                               # unrealized pnl (per currency)

details[*].uplLiab: details[*].unrealizedPnlLiability                  # upl liability

details[*].crossLiab: details[*].crossLiability                        # cross liability

details[*].isoLiab: details[*].isolatedLiability                       # isolated liability

details[*].mgnRatio: details[*].marginRatio                            # margin ratio

details[*].imr: details[*].initialMarginRequirement                    # imr

details[*].mmr: details[*].maintenanceMarginRequirement                # mmr

details[*].interest: details[*].interest                               # interest

details[*].twap: details[*].twap                                       # forced repayment risk indicator

details[*].frpType: details[*].frpType                                 # forced repayment type

details[*].maxLoan: details[*].maxLoan                                 # max loan

details[*].eqUsd: details[*].equityUsd                                 # equity (USD)

details[*].borrowFroz: details[*].borrowFrozen                         # borrow frozen

details[*].notionalLever: details[*].notionalLeverage                  # notional leverage

details[*].stgyEq: details[*].strategyEquity                           # strategy equity

details[*].isoUpl: details[*].isolatedUnrealizedPnl                    # iso upl

# portfolio / copy-trading

details[*].spotInUseAmt: details[*].spotInUseAmount                    # spot in use amount

details[*].clSpotInUseAmt: details[*].clSpotInUseAmount                # cl spot in use amount

details[*].maxSpotInUse: details[*].maxSpotInUse                       # max spot in use

details[*].spotIsoBal: details[*].spotIsolatedBalance                  # spot iso balanceExternalSnapshot

details[*].smtSyncEq: details[*].smartSyncEquity                       # smart sync equity

details[*].spotCopyTradingEq: details[*].spotCopyTradingEquity         # spot copy trading eq

# spot cost basis / pnl

details[*].spotBal: details[*].spotBalance                             # spot balanceExternalSnapshot

details[*].openAvgPx: details[*].openAveragePriceUsd                   # open avg px (USD)

details[*].accAvgPx: details[*].accumulatedAveragePriceUsd             # acc avg px (USD)

details[*].spotUpl: details[*].spotUnrealizedPnlUsd                    # spot upl (USD)

details[*].spotUplRatio: details[*].spotUnrealizedPnlRatio             # spot upl ratio

details[*].totalPnl: details[*].totalPnlUsd                            # total pnl (USD)

details[*].totalPnlRatio: details[*].totalPnlRatio                     # total pnl ratio

# collateral

details[*].colRes: details[*].collateralRestrictionStatus              # collateral restriction status

details[*].colBorrAutoConversion: details[*].collateralBorrowAutoConversion # collateral auto conversion risk

details[*].collateralRestrict: details[*].collateralRestrict           # deprecated

details[*].collateralEnabled: details[*].collateralEnabled             # collateral enabled

# auto-lend

details[*].autoLendStatus: details[*].autoLendStatus                   # auto lend status

details[*].autoLendMtAmt: details[*].autoLendMatchedAmount             # auto lend matched amount

# misc

details[*].rewardBal: details[*].rewardBalance                         # reward balanceExternalSnapshot
```

**Правила конвертации (коротко, чтобы маппинг был “рабочим”):**

* Пустые строки `""` → `null` (для BigDecimal/Instant/Boolean).
* Числа приходят строками → `BigDecimal`.
* `uTime` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* `requestCurrencies` — не из ответа OKX, а из твоего входного запроса (`ccy=...`).
