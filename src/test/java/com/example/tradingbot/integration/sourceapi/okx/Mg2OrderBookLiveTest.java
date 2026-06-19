package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG2. order book — {@code GET /api/v5/market/books} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code data[0]} —
 * объект {@code {asks[], bids[], ts, seqId}}; уровень — массив длиной 4.
 */
@Order(44)
class Mg2OrderBookLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/books";

    @Test
    @Order(10)
    @DisplayName("MG2.1 прямой — books(ETH-USDT-SWAP, sz=5)")
    void mg2_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "sz", "5"), PUBLIC);

        assertOk(r);
        assertThat(r.d0().path("asks").isArray()).isTrue();
        assertThat(r.d0().path("bids").isArray()).isTrue();
        assertThat(r.d0().path("asks").path(0).size()).isEqualTo(4);
        assertThat(r.d0().path("asks").path(0).path(0).asText("")).isNotBlank();
        assertThat(r.d0().path("ts").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("MG2.2 негатив — несущ. instId (OKX-реджект/пустой)")
    void mg2_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("MG2.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG2.3 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg2_3_missingInstId() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG2.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG2.4 негатив — sz сверх лимита (sz>400)")
    void mg2_4_szOverCap() {
        RawResponse r = get(PATH, map("instId", INST_ID, "sz", "5000"), PUBLIC);

        assertHttp200(r);
        observe("MG2.4", r);
        assertThat(r.businessReject()
                || (r.codeZero() && r.d0().path("asks").size() <= 400))
                .as("expected reject or asks truncated <= 400 (b.code=%s, asks.size=%s)",
                        r.code(), r.d0().path("asks").size())
                .isTrue();
    }
}
