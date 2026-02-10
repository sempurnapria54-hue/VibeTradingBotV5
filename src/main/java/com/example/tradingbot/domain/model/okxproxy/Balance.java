package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Balance {

    private String currency;
    private String cashBalance;
    private String availableBalance;
    private String equity;
    private String frozenBalance;
    private String unrealizedProfit;
}
