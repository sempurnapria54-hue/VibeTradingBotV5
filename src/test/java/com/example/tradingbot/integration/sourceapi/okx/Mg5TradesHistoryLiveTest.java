package com.example.tradingbot.integration.sourceapi.okx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * MG5. trades history — {@code GET /api/v5/market/history-trades} (Market
 * Data), {@code signed:false}. READ-only, без auth/teardown. {@code data} —
 * массив объектов-сделок (как у {@code trades}); пагинация по {@code tradeId}
 * ({@code type=1}).
 */
@Order(47)
class Mg5TradesHistoryLiveTest extends OkxSourceApiLiveTestBase {

    private static final String PATH = "/api/v5/market/history-trades";

    @Test
    @Order(10)
    @DisplayName("MG5.1 прямой — history-trades(ETH-USDT-SWAP, limit=10)")
    void mg5_1_direct() {
        RawResponse r = get(PATH, map("instId", INST_ID, "limit", "10"), PUBLIC);

        assertOk(r);
        assertThat(r.dataSize()).isPositive();
        assertThat(r.d0().path("tradeId").asText("")).isNotBlank();
        assertThat(r.d0().path("ts").asText("")).isNotBlank();
    }

    @Test
    @Order(20)
    @DisplayName("MG5.2 вариант — пагинация назад по after (tradeId)")
    void mg5_2_paginateByTradeId() {
        RawResponse base = get(PATH, map("instId", INST_ID, "limit", "10"), PUBLIC);
        assertOk(base);
        assertThat(base.dataSize()).isPositive();
        String cursor = base.at(base.dataSize() - 1).path("tradeId").asText("");
        assertThat(cursor).as("cursor tradeId from MG5.1 last element").isNotBlank();

        RawResponse r = get(PATH, map(
                "instId", INST_ID, "type", "1", "after", cursor, "limit", "10"), PUBLIC);

        assertOk(r);
        observe("MG5.2", r);
        assertThat(r.data().isArray()).isTrue();
    }

    @Test
    @Order(30)
    @DisplayName("MG5.3 негатив — несущ. instId (OKX-реджект/пустой)")
    void mg5_3_nonexistentInstId() {
        RawResponse r = get(PATH, map("instId", "FOO-BAR"), PUBLIC);

        assertRejectOrEmpty("MG5.3", r);
    }

    @Test
    @Order(40)
    @DisplayName("MG5.4 негатив — пропуск обязательного instId (OKX-реджект)")
    void mg5_4_missingInstId() {
        RawResponse r = get(PATH, map(), PUBLIC);

        assertBusinessReject(r);
        observe("MG5.4", r);
    }
}
