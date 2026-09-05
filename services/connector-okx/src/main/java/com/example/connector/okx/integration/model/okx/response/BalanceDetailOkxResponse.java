package com.example.connector.okx.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой currency-level элемент ответа OKX по балансу (data[*].details[*]).
 * За adapter не выходит; нормализуется в BalanceExternalSnapshot. См.
 * docs/models/mapping/Balance.md.
 *
 * <p>{@code uTime} несёт явный {@link JsonProperty}: Lombok (beanspec)
 * даёт аксессор {@code getuTime()}, чьё выводимое имя свойства Jackson 3
 * (дефолт RestClient в SB4) НЕ матчит с ключом {@code uTime} → поле
 * биндилось в null (находка F4). Явное имя фиксирует бинд.
 */
@Getter
@Setter
public class BalanceDetailOkxResponse {

    /** Валюта. */
    private String ccy;

    /** Время обновления currency snapshot (epoch ms). */
    @JsonProperty("uTime")
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
