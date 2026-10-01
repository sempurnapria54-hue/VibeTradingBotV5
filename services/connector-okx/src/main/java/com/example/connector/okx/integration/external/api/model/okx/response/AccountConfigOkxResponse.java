package com.example.connector.okx.integration.external.api.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Нативный DTO OKX конфигурации счёта (GET /api/v5/account/config,
 * data[0]). Несёт только два поля, которые контур читает, — режим счёта и
 * режим позиций; прочие поля ответа не читаются
 * (docs/models/integrations/okx/AccountConfigOkxResponse.md). Не выходит за
 * IntegrationService/adapter: в доменный словарь режимы переводит граница
 * коннектора (docs/models/mapping/Balance.md).
 */
@Getter
@Setter
public class AccountConfigOkxResponse {

    /** Режим счёта (acctLv): 1 Spot / 2 Futures / 3 Multi-currency margin / 4 Portfolio margin. */
    private String acctLv;

    /** Режим позиций (posMode): long_short_mode / net_mode. */
    private String posMode;
}
