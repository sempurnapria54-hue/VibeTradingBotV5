package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Balance extends Auditable {

    /** Валюта баланса (например USDT). */
    private String currency;
    /** Общий денежный баланс по валюте. */
    private String cashBalance;
    /** Доступный для операций баланс. */
    private String availableBalance;
    /** Текущая стоимость активов (equity). */
    private String equity;
    /** Замороженная часть баланса. */
    private String frozenBalance;
    /** Нереализованный PnL по валюте. */
    private String unrealizedProfit;
}
