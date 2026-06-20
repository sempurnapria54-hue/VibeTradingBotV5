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
    @DisplayName("TG8.2 негатив — битый путь → non-2xx (OKX 404 нестандартное тело)")
    void tg8_2_brokenEnvelope() {
        // RUN-факт (2026-06-19): неизвестный путь OKX отдаёт HTTP 404 с
        // нестандартным телом ({"code":404 (число), "data":{} (объект), ...}),
        // которое не ложится в OkxApiResponse{code:String, data:List} → /raw
        // отдаёт non-2xx (как M1.6 broken-envelope), а не бизнес-реджект 200.
        // Находка C3.
        RawResponse r = get("/api/v5/trade/account-rate-limit-bogus", null, SIGNED);

        log.info("[TG8.2] OBSERVE broken-path http={} body={}", r.status(), r.rawBody());
        assertThat(r.status())
                .as("broken path → non-2xx (OKX 404 non-standard body)")
                .isGreaterThanOrEqualTo(400);
    }
}
