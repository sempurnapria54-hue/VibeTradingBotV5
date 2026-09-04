package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG3. order book full — {@code GET /api/v5/market/books-full} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code data[0]} —
 * объект {@code {asks[], bids[], ts}} (без {@code seqId}); уровень — массив
 * длиной 3.
 */
@Order(45)
class Mg3OrderBookFullLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/books-full";

    @Test
    @Order(10)
    @DisplayName("MG3.1 прямой — books-full(ETH-USDT-SWAP, sz=10)")
    void mg3_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "sz", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("asks").isArray()).isTrue();
        assertThat(r.d0().path("bids").isArray()).isTrue();
        assertThat(r.d0().path("asks").path(0).size()).isEqualTo(3);
        assertThat(r.d0().path("asks").path(0).path(0).asText("")).isNotBlank();
        assertThat(r.d0().path("ts").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("MG3.2 негатив — несущ. instId (OKX-реджект/пустой)")
    void mg3_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("MG3.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG3.3 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg3_3_missingInstId() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG3.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG3.4 негатив — sz сверх лимита (sz>5000)")
    void mg3_4_szOverCap() {
        RawResponse r = get(PATH, map("instId", INST_ID, "sz", "99999"), PUBLIC);

        assertHttp200(r);
        observe("MG3.4", r);
        assertThat(r.businessReject()
                || (r.codeZero() && r.d0().path("asks").size() <= 5000))
                .as("expected reject or asks truncated <= 5000 (b.code=%s, asks.size=%s)",
                        r.code(), r.d0().path("asks").size())
                .isTrue();
    }
}
