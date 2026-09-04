package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG9. mark price candles — {@code GET /api/v5/market/mark-price-candles}
 * (Market Data), {@code signed:false}. READ-only, без auth/teardown.
 * {@code instId} — торговый инструмент. {@code data} — массив массивов-строк
 * {@code [ts, o, h, l, c, confirm]} (6 элементов, без объёма).
 */
@Order(51)
class Mg9MarkPriceCandlesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/mark-price-candles";

    @Test
    @Order(10)
    @DisplayName("MG9.1 прямой — mark-price-candles(ETH-USDT-SWAP, 1m, limit=10)")
    void mg9_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
        assertThat(r.d0().size()).isEqualTo(6);
        assertThat(r.d0().path(0).asText("")).isNotBlank();
        assertThat(r.d0().path(5).asText("")).isIn("0", "1");
    }

    @Test
    @Order(20)
    @DisplayName("MG9.2 негатив — bar вне домена (OKX-реджект)")
    void mg9_2_barOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "99z"), PUBLIC);

        assertBusinessReject(r);
        observe("MG9.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG9.3 негатив — несущ. instId (OKX-реджект/пустой)")
    void mg9_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR", "bar", "1m"), PUBLIC);

        assertRejectOrEmpty("MG9.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG9.4 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg9_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("MG9.4", r);
    }
}
