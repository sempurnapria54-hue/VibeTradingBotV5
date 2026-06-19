package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * TG8. Account rate limit — {@code GET /api/v5/trade/account-rate-limit}
 * (Trade, {@code signed:true}). Без параметров запроса. READ.
 */
@Order(29)
class Tg8AccountRateLimitLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/trade/account-rate-limit";

    @Test
    @Order(10)
    @DisplayName("TG8.1 прямой — account-rate-limit")
    void tg8_1_direct() {
        RawResponse r = get(PATH, null, SIGNED);

        assertOk(r);
        assertThat(r.d0().path("accRateLimit").isMissingNode()).as("TG8.1 accRateLimit present").isFalse();
        assertThat(r.d0().path("ts").isMissingNode()).as("TG8.1 ts present").isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("TG8.2 негатив — сломанный конверт (лишний path-сегмент, OKX-слой)")
    void tg8_2_brokenEnvelope() {
        RawResponse r = get("/api/v5/trade/account-rate-limit-bogus", null, SIGNED);

        assertBusinessReject(r);
        observe("TG8.2", r);
    }
}
