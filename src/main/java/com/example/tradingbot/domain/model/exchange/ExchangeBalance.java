package com.example.tradingbot.domain.model.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeBalance {

    private String currency;
    private String cashBalance;
    private String availableBalance;
    private String equity;
    private String frozenBalance;
    private String unrealizedProfit;
}
