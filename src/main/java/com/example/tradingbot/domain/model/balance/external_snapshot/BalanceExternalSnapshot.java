package com.example.tradingbot.domain.model.balance.external_snapshot;

import com.example.tradingbot.domain.model.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BalanceExternalSnapshot extends Auditable {

    /**
     * Валюта баланса (например USDT).
     */
    private String currency;
    /**
     * Общий денежный баланс по валюте.
     */
    private String cashBalance;
    /**
     * Доступный для операций баланс.
     */
    private String availableBalance;
    /**
     * Текущая стоимость активов (equity).
     */
    private String equity;
    /**
     * Замороженная часть баланса.
     */
    private String frozenBalance;
    /**
     * Нереализованный PnL по валюте.
     */
    private String unrealizedProfit;
}
