package com.example.tradingbot.client.model.okx.response.balance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceResponse {

    /**
     * Время обновления данных аккаунта (Unix ms).
     */
    private String uTime;

    /**
     * Суммарная equity в USD (оценка всего аккаунта).
     */
    private String totalEq;

    /**
     * Equity изолированной маржи в USD (актуально для некоторых режимов).
     */
    private String isoEq;

    /**
     * “Effective/adjusted equity” в USD — equity с поправками OKX для риск-расчётов
     * (например дисконт залога, долги и т.п.).
     */
    private String adjEq;

    /**
     * Доступная equity на уровне аккаунта (в некоторых режимах).
     */
    private String availEq;

    /**
     * Заморожено под ордера (USD).
     */
    private String ordFroz;

    /**
     * Initial margin requirement (USD) — требование initial margin (чаще для cross/PM).
     */
    private String imr;

    /**
     * Maintenance margin requirement (USD) — требование maintenance margin (чаще для cross/PM).
     */
    private String mmr;

    /**
     * “Потенциальный IMR под заём” (USD), часто пусто вне нужных режимов.
     */
    private String borrowFroz;

    /**
     * Margin ratio (может быть пустой строкой).
     */
    private String mgnRatio;

    /**
     * Номинальная стоимость позиций/экспозиции в USD (для риска/лимитов биржи).
     */
    private String notionalUsd;

    /**
     * Номинальная стоимость (USD) для Borrow (заём/маржинальные режимы).
     */
    private String notionalUsdForBorrow;

    /**
     * Номинальная стоимость (USD) для SWAP.
     */
    private String notionalUsdForSwap;

    /**
     * Номинальная стоимость (USD) для Futures (expiry).
     */
    private String notionalUsdForFutures;

    /**
     * Номинальная стоимость (USD) для Option.
     */
    private String notionalUsdForOption;

    /**
     * Unrealized PnL на уровне аккаунта (USD) (чаще для multi-ccy/PM).
     */
    private String upl;

    /**
     * Delta-показатель чувствительности портфеля к движению базового актива
     * (в основном актуально для опционов/сложных портфелей).
     */
    private String delta;

    /**
     * Delta с учётом “плеча/риска” (биржевой риск-индикатор), часто не нужен для простого SWAP-бота.
     */
    private String deltaLever;

    /**
     * Статус delta-neutral режима (если используется).
     */
    private String deltaNeutralStatus;

    /**
     * Детализация по валютам: 1 элемент = 1 валюта (например USDT).
     */
    private List<BalanceDetail> details;
}
