package com.example.tradingbot.client.model.okx.response.balance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceDetail {

    /**
     * Код валюты (например USDT).
     */
    private String ccy;

    /**
     * Equity по этой валюте (сколько всего с учётом маржи/оценок).
     */
    private String eq;

    /**
     * Фактический баланс валюты (“лежит на счету”).
     */
    private String cashBal;

    /**
     * Сколько можно использовать прямо сейчас (не заморожено).
     */
    private String availBal;

    /**
     * Время последнего обновления по этой валюте (Unix ms).
     */
    private String uTime;

    /**
     * Equity в изолированной марже (если применимо).
     */
    private String isoEq;

    /**
     * Доступная equity (если применимо).
     */
    private String availEq;

    /**
     * “Дисконтированная” оценка в USD (если валюта считается залогом не 1:1).
     */
    private String disEq;

    /**
     * Заморозка под спец-стратегии OKX (обычно не нужна).
     */
    private String fixedBal;

    /**
     * Сколько валюты сейчас заморожено (в целом).
     */
    private String frozenBal;

    /**
     * Сколько заморожено под открытые ордера (чтобы их обеспечить).
     */
    private String ordFrozen;

    /**
     * Общий долг по этой валюте (если занимал).
     */
    private String liab;

    /**
     * Плавающий PnL, связанный с этой валютой (если применимо).
     */
    private String upl;

    /**
     * “Долг из-за плавающего убытка” в некоторых режимах (часто пусто).
     */
    private String uplLiab;

    /**
     * Долг в режиме cross (общий по аккаунту).
     */
    private String crossLiab;

    /**
     * Долг в режиме isolated (по отдельным позициям/изол.марже).
     */
    private String isoLiab;

    /**
     * Margin ratio (может быть пустой строкой).
     */
    private String mgnRatio;

    /**
     * Требование initial margin (часто пусто).
     */
    private String imr;

    /**
     * Требование maintenance margin (часто пусто).
     */
    private String mmr;

    /**
     * Начисленные проценты по долгу.
     */
    private String interest;

    /**
     * Индикатор риска принудительного погашения (шкала 0..5).
     */
    private String twap;

    /**
     * Тип принудительного погашения: 0 — нет, 1/2 — разные режимы.
     */
    private String frpType;

    /**
     * Сколько максимум можно занять по этой валюте.
     */
    private String maxLoan;

    /**
     * Equity по этой валюте, пересчитанная в USD.
     */
    private String eqUsd;

    /**
     * “Заморозка/резерв” под потенциальный заём (часто пусто).
     */
    private String borrowFroz;

    /**
     * “Эффективное плечо/левередж” по валюте (если применимо).
     */
    private String notionalLever;

    /**
     * “Equity стратегии” (биржевые режимы/стратегии).
     */
    private String stgyEq;

    /**
     * Плавающий PnL именно в isolated (если применимо).
     */
    private String isoUpl;

    /**
     * Сколько spot-средств сейчас используется как риск-оффсет (portfolio margin).
     */
    private String spotInUseAmt;

    /**
     * Пользовательский лимит spot risk offset (portfolio margin).
     */
    private String clSpotInUseAmt;

    /**
     * Максимально возможный spot risk offset (portfolio margin).
     */
    private String maxSpotInUse;

    /**
     * Spot-баланс в isolated контексте (copy-trading/особые режимы).
     */
    private String spotIsoBal;

    /**
     * Smart-sync equity (для copy trader).
     */
    private String smtSyncEq;

    /**
     * Smart-sync equity для spot copy-trading.
     */
    private String spotCopyTradingEq;

    /**
     * Spot-баланс этой валюты (в единицах валюты).
     */
    private String spotBal;

    /**
     * Средняя цена покупки (cost basis) для spot (в USD).
     */
    private String openAvgPx;

    /**
     * Накопленная средняя цена (в USD).
     */
    private String accAvgPx;

    /**
     * Spot плавающий PnL (в USD).
     */
    private String spotUpl;

    /**
     * Spot плавающий PnL в процентах/доле.
     */
    private String spotUplRatio;

    /**
     * Суммарный PnL по spot (в USD) за всё время/накопленный.
     */
    private String totalPnl;

    /**
     * Суммарный PnL по spot в процентах/доле.
     */
    private String totalPnlRatio;

    /**
     * Статус ограничений по залогу со стороны платформы (0/1/2).
     */
    private String colRes;

    /**
     * Индикатор риска авто-конвертации (0..5).
     */
    private String colBorrAutoConversion;

    /**
     * Устаревшее поле (deprecated), вместо него colRes.
     */
    private Boolean collateralRestrict;

    /**
     * Включён ли режим collateral (для некоторых режимов аккаунта).
     */
    private Boolean collateralEnabled;

    /**
     * Статус автолендинга: unsupported/off/pending/active.
     */
    private String autoLendStatus;

    /**
     * Сколько реально “размещено/смэтчено” в автолендинге.
     */
    private String autoLendMtAmt;

    /**
     * Баланс “trial funds / reward” (бонусные средства).
     */
    private String rewardBal;
}