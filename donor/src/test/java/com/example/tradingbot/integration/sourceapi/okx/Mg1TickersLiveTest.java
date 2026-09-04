package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG1. tickers (плюрал) — {@code GET /api/v5/market/tickers} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown.
 */
@Order(43)
class Mg1TickersLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/tickers";

    @Test
    @Order(10)
    @DisplayName("MG1.1 прямой — tickers(instType=SWAP)")
    void mg1_1_direct() {
        RawResponse r = get(PATH, map("instType", INST_TYPE), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("instId").asText("")).isNotBlank();
        assertThat(r.d0().path("last").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("MG1.2 негатив — пропуск обязательного instType (OKX-реджект)")
    void mg1_2_missingInstType() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG1.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG1.3 негатив — instType вне домена (OKX-реджект/пустой)")
    void mg1_3_instTypeOutOfDomain() {
        RawResponse r = get(PATH, map("instType", "FOO"), PUBLIC);

        assertRejectOrEmpty("MG1.3", r);
    }
}
