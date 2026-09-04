package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG4. public trades — {@code GET /api/v5/market/trades} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code data} — массив
 * объектов-сделок ({@code tradeId}, {@code px}, {@code sz}, {@code side},
 * {@code source}, {@code ts}).
 */
@Order(46)
class Mg4PublicTradesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/trades";

    @Test
    @Order(10)
    @DisplayName("MG4.1 прямой — trades(ETH-USDT-SWAP, limit=10)")
    void mg4_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("tradeId").asText("")).isNotBlank();
        assertThat(r.d0().path("px").asText("")).isNotBlank();
        assertThat(r.d0().path("side").asText("")).isIn("buy", "sell");
    }

    @Test
    @Order(20)
    @DisplayName("MG4.2 негатив — несущ. instId (OKX-реджект/пустой)")
    void mg4_2_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("MG4.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG4.3 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg4_3_missingInstId() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG4.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG4.4 негатив — limit сверх лимита (limit>500)")
    void mg4_4_limitOverCap() {
        RawResponse r = get(PATH, map("instId", INST_ID, "limit", "9999"), PUBLIC);

        assertHttp200(r);
        observe("MG4.4", r);
        assertThat(r.businessReject() || (r.codeZero() && r.dataSize() <= 500))
                .as("expected reject or data truncated <= 500 (b.code=%s, data.size=%s)",
                        r.code(), r.dataSize())
                .isTrue();
    }
}
