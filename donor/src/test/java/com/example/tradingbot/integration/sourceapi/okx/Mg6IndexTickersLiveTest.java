package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG6. index tickers — {@code GET /api/v5/market/index-tickers} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code instId} — индекс
 * (напр. {@code ETH-USDT}). {@code data} — массив объектов индекса
 * ({@code instId}, {@code idxPx}, {@code ts}, …).
 */
@Order(48)
class Mg6IndexTickersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/index-tickers";

    @Test
    @Order(10)
    @DisplayName("MG6.1 прямой — index-tickers(instId=ETH-USDT)")
    void mg6_1_direct() {
        RawResponse r = get(PATH, map("instId", INDEX_INST_ID), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("instId").asText()).isEqualTo(INDEX_INST_ID);
        assertThat(r.d0().path("idxPx").asText("")).isNotBlank();
        assertThat(r.d0().path("ts").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("MG6.2 вариант — index-tickers по quoteCcy")
    void mg6_2_byQuoteCcy() {
        RawResponse r = get(PATH, map("quoteCcy", "USDT"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("idxPx").asText("")).isNotBlank();
    }

    @Test
    @Order(30)
    @DisplayName("MG6.3 негатив — несущ. индекс instId (OKX-реджект/пустой)")
    void mg6_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("MG6.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG6.4 негатив — пропуск обязательного фильтра (ни instId, ни quoteCcy)")
    void mg6_4_missingFilter() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG6.4", r);
    }
}
