package com.example.tradingbot.integration.model.okx.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой account-level ответ OKX по балансу (GET /account/balance,
 * data[0]). За adapter не выходит; нормализуется в
 * BalanceContainerExternalSnapshot. См. docs/models/mapping/Balance.md.
 *
 * <p>{@code uTime} несёт явный {@link JsonProperty}: Lombok (beanspec)
 * даёт аксессор {@code getuTime()}, чьё выводимое имя свойства Jackson 3
 * (дефолт RestClient в SB4) НЕ матчит с ключом {@code uTime} → поле
 * биндилось в null (находка F4). Явное имя фиксирует бинд.
 */
@Getter
@Setter
public class BalanceOkxResponse {

    /** Время обновления account snapshot (epoch ms). */
    @JsonProperty("uTime")
    private String uTime;

    /** Total equity аккаунта. */
    private String totalEq;

    /** Adjusted / effective equity. */
    private String adjEq;

    /** Account-level available equity. */
    private String availEq;

    /** Балансы по валютам. */
    private List<BalanceDetailOkxResponse> details;
}
