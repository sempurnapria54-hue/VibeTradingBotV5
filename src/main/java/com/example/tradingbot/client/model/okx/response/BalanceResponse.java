package com.example.tradingbot.client.model.okx.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BalanceResponse {

    private String ccy;
    private String cashBal;
    private String availBal;
    private String eq;
    private String frozenBal;
    private String upl;
}
