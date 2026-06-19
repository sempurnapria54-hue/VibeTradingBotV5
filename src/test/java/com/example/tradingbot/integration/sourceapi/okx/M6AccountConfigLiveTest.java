package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M6. account-config — {@code GET /api/v5/account/config} (Account),
 * {@code signed:true}. Без параметров. Негативы только auth (см. I-cred).
 */
@Order(6)
class M6AccountConfigLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/config";

    @Test
    @Order(10)
    @DisplayName("M6.1 прямой — account-config()")
    void m6_1_direct() {
        RawResponse r = get(PATH, map(), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("acctLv").isMissingNode()).isFalse();
        assertThat(r.d0().path("posMode").isMissingNode()).isFalse();
    }
}
