package com.example.tradingbot.client.okx.dto;

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
