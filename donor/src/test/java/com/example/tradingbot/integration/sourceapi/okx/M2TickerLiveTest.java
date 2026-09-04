package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M2. ticker — {@code GET /api/v5/market/ticker} (Market Data),
 * {@code signed:false}. Источник live-цены для write-цепочек.
 */
@Order(2)
class M2TickerLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/ticker";

    @Test
    @Order(10)
    @DisplayName("M2.1 прямой — ticker(ETH-USDT-SWAP)")
    void m2_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("last").isMissingNode()).isFalse();
        assertThat(new BigDecimal(r.d0().path("last").asText())).isPositive();
        assertThat(r.d0().path("askPx").isMissingNode()).isFalse();
        assertThat(r.d0().path("bidPx").isMissingNode()).isFalse();
        assertThat(r.d0().path("ts").isMissingNode()).isFalse();
    }

    @Test
    @Order(20)
    @DisplayName("M2.2 негатив — несущ. инструмент")
    void m2_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("M2.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("M2.3 негатив — пропуск обязательного instId (OKX-слой)")
    void m2_3_missingInstId() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("M2.3", r);
    }
}
