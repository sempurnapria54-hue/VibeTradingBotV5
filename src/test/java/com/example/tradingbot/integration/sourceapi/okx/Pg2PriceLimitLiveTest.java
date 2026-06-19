package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * PG2. price limit — {@code GET /api/v5/public/price-limit} (Public Data),
 * {@code signed:false}. Верхняя граница buy / нижняя граница sell.
 */
@Order(54)
class Pg2PriceLimitLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/public/price-limit";

    @Test
    @Order(10)
    @DisplayName("PG2.1 Прямой — лимиты цены по instId")
    void pg2_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("instId").asText()).isEqualTo(INST_ID);
        assertThat(r.d0().path("buyLmt").isMissingNode()).isFalse();
        assertThat(r.d0().path("sellLmt").isMissingNode()).isFalse();
        assertThat(r.d0().path("enabled").isMissingNode()).isFalse();
        assertThat(r.d0().path("ts").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("PG2.2 Негатив — несуществующий instId (реджект ИЛИ пустой data)")
    void pg2_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "NOPE-USDT-SWAP"), PUBLIC);

        assertRejectOrEmpty("PG2.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("PG2.3 Негатив — пропуск обязательного instId (OKX-реджект)")
    void pg2_3_missingInstId() {
        RawResponse r = get(PATH, null, PUBLIC);

        assertBusinessReject(r);
        observe("PG2.3", r);
    }
}
