package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * M3. candles — {@code GET /api/v5/market/candles} (Market Data),
 * {@code signed:false}. Форма ответа: {@code b.data} = массив массивов-строк
 * свечи {@code [ts, o, h, l, c, vol, …]} (сырьё), {@code bar} case-sensitive.
 */
@Order(3)
class M3CandlesLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/candles";

    @Test
    @Order(10)
    @DisplayName("M3.1 прямой — candles(ETH-USDT-SWAP, 1m, limit=10)")
    void m3_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "1m", "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().isArray()).isTrue();
        assertThat(r.d0().size()).isGreaterThanOrEqualTo(6);
        assertThat(r.d0().path(0).asText()).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("M3.2 негатив — bar вне домена (OKX-слой)")
    void m3_2_barOutOfDomain() {
        RawResponse r = get(PATH, map("instId", INST_ID, "bar", "99z"), PUBLIC);

        assertBusinessReject(r);
        observe("M3.2", r);
    }

    @Test
    @Order(30)
    @DisplayName("M3.3 негатив — несущ. instId")
    void m3_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR", "bar", "1m"), PUBLIC);

        assertRejectOrEmpty("M3.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("M3.4 негатив — пропуск обязательного instId (OKX-слой)")
    void m3_4_missingInstId() {
        RawResponse r = get(PATH, map("bar", "1m"), PUBLIC);

        assertBusinessReject(r);
        observe("M3.4", r);
    }
}
