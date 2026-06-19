package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M5. balance — {@code GET /api/v5/account/balance} (Account),
 * {@code signed:true}. Под /raw {@code ccy} опционален (all-ccy достижим).
 */
@Order(5)
class M5BalanceLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/account/balance";

    @Test
    @Order(10)
    @DisplayName("M5.1 прямой — balance(USDT)")
    void m5_1_direct() {
        RawResponse r = get(PATH, map("ccy", "USDT"), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("totalEq").isMissingNode()).isFalse();
        assertThat(r.d0().path("details").isArray()).isTrue();
        assertThat(detailsCarry(r, "USDT")).as("details несут USDT").isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("M5.2 вариант — balance без ccy (все валюты)")
    void m5_2_withoutCcy() {
        RawResponse r = get(PATH, map(), SIGNED);

        assertOk(r);
        assertThat(r.d0().path("details").isArray()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("M5.3 негатив — несущ. валюта (наблюдение)")
    void m5_3_nonexistentCcy() {
        RawResponse r = get(PATH, map("ccy", "ZZZ"), SIGNED);

        observe("M5.3", r);
        assertThat(r.codeZero() || r.businessReject())
                .as("non-existent ccy: ok-with-empty-details или reject (наблюдение)")
                .isTrue();
    }

    /** Есть ли в {@code data[0].details} запись по валюте {@code ccy}. */
    private boolean detailsCarry(RawResponse r, String ccy) {
        JsonNode details = r.d0().path("details");
        if (!details.isArray()) {
            return false;
        }
        for (JsonNode element : details) {
            if (ccy.equals(element.path("ccy").asText(null))) {
                return true;
            }
        }
        return false;
    }
}
