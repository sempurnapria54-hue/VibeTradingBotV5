package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG7. index candles — {@code GET /api/v5/market/index-candles} (Market Data),
 * {@code signed:false}. READ-only, без auth/teardown. {@code data} — массив
 * массивов-строк свечи индекса {@code [ts, o, h, l, c, confirm]} (6 элементов,
 * без объёма).
 */
@Order(49)
class Mg7IndexCandlesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/index-candles";

    @Test
    @Order(10)
    @DisplayName("MG7.1 прямой — index-candles(ETH-USDT, 1m, limit=10)")
    void mg7_1_direct() {
        RawResponse r = get(PATH, map("instId", INDEX_INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
        assertThat(r.d0().size()).isEqualTo(6);
        assertThat(r.d0().path(0).asText("")).isNotBlank();
        assertThat(r.d0().path(5).asText("")).isIn("0", "1");
    }

    @Test
    @Order(20)
    @DisplayName("MG7.2 негатив — bar вне домена (OKX-реджект)")
    void mg7_2_barOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INDEX_INST_ID, "bar", "99z"), PUBLIC);

        assertBusinessReject(r);
        observe("MG7.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("MG7.3 негатив — несущ. индекс instId (OKX-реджект/пустой)")
    void mg7_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR", "bar", "1m"), PUBLIC);

        assertRejectOrEmpty("MG7.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG7.4 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg7_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("MG7.4", r);
    }
}
