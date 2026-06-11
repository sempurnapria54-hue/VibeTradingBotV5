package com.example.tradingbot.integration.model.okx.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Сырой account-level ответ OKX по балансу (GET /account/balance,
 * data[0]). За adapter не выходит; нормализуется в
 * BalanceContainerExternalSnapshot. См. docs/models/mapping/Balance.md.
 */
@Getter
@Setter
public class OkxBalanceResponse {

    /** Время обновления account snapshot (epoch ms). */
    private String uTime;

    /** Total equity аккаунта. */
    private String totalEq;

    /** Adjusted / effective equity. */
    private String adjEq;

    /** Account-level available equity. */
    private String availEq;

    /** Балансы по валютам. */
    private List<OkxBalanceDetailResponse> details;
}
