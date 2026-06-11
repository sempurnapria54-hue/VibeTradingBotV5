package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Сырой currency-level элемент ответа OKX по балансу (data[*].details[*]).
 * За adapter не выходит; нормализуется в BalanceExternalSnapshot. См.
 * docs/models/mapping/Balance.md.
 */
@Getter
@Setter
public class OkxBalanceDetailResponse {

    /** Валюта. */
    private String ccy;

    /** Время обновления currency snapshot (epoch ms). */
    private String uTime;

    /** Equity по валюте. */
    private String eq;

    /** Cash balance. */
    private String cashBal;

    /** Доступный баланс. */
    private String availBal;

    /** Замороженный баланс. */
    private String frozenBal;
}
