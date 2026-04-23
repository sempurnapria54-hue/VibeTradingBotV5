package com.example.tradingbot.domain.model.core.balance.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BalanceContainerExternalSnapshot extends Auditable {

    /**
     * Внутренний идентификатор биржи.
     */
    private Long exchangeId;

    /**
     * Суммарная оценка активов аккаунта в USD (total equity).
     * Полезно для риск-менеджмента (1% от депозита и т.д.).
     */
    private String totalEquity;

    /**
     * Суммарный нереализованный PnL аккаунта в USD.
     */
    private String unrealizedProfit;

    /**
     * Список балансов по валютам (1 элемент = 1 валюта).
     */
    private List<BalanceExternalSnapshot> balanceExternalSnapshots;
}
